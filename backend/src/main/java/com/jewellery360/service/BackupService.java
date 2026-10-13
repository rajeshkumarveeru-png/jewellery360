package com.jewellery360.service;

import com.jewellery360.domain.AppUser;
import com.jewellery360.domain.BackupRun;
import com.jewellery360.repository.AppUserRepository;
import com.jewellery360.repository.BackupRunRepository;
import com.jewellery360.repository.PropertyRepository;
import com.jewellery360.domain.Property;
import com.jewellery360.security.AuthenticatedUser;
import com.jewellery360.support.Errors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Whole-database backups with pg_dump (custom format) into app.backup.dir, a history in backup_run, scheduled backups
 * with retention, download, and restore with pg_restore.
 *
 * Restore safety: platform admin only, typed confirmation, an automatic backup of the current data first, and
 * pg_restore runs in a single transaction, so a failed restore leaves the database exactly as it was.
 * The backup history itself is kept across a restore (it describes files on this server, not shop data).
 */
@Slf4j
@Service
public class BackupService {
    private static final DateTimeFormatter FILE_TS = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneId.of("Asia/Kolkata"));
    public static final String CONFIRM_WORD = "RESTORE";

    private final BackupRunRepository runs;
    private final AppUserRepository users;
    private final JdbcTemplate jdbc;
    private final AuditService audit;
    private final AtomicBoolean busy = new AtomicBoolean(false);

    // folder and PostgreSQL tools can be changed at any time on the Backups page (platform properties override application.properties)
    private volatile Path dir;
    private final Path defaultDir;
    private final List<Path> oldDirs = new java.util.concurrent.CopyOnWriteArrayList<>();
    private final PropertyRepository props;
    private final int keep;
    private final boolean scheduleEnabled;
    private final String cron;
    private volatile String pgDump, pgRestore;
    private final String host, port, database, username, password;

    public BackupService(BackupRunRepository runs, AppUserRepository users, JdbcTemplate jdbc, AuditService audit, PropertyRepository props,
                         @Value("${app.backup.dir:./backups}") String dir,
                         @Value("${app.backup.keep:14}") int keep,
                         @Value("${app.backup.schedule-enabled:true}") boolean scheduleEnabled,
                         @Value("${app.backup.cron:0 30 1 * * *}") String cron,
                         @Value("${app.backup.pg-dump:pg_dump}") String pgDump,
                         @Value("${app.backup.pg-restore:pg_restore}") String pgRestore,
                         @Value("${spring.datasource.url}") String url,
                         @Value("${spring.datasource.username}") String username,
                         @Value("${spring.datasource.password:}") String password) {
        this.runs = runs;
        this.users = users;
        this.jdbc = jdbc;
        this.audit = audit;
        this.props = props;
        this.dir = Paths.get(dir).toAbsolutePath().normalize();
        this.defaultDir = this.dir;
        this.keep = Math.max(1, keep);
        this.scheduleEnabled = scheduleEnabled;
        this.cron = cron;
        this.pgDump = pgDump;
        this.pgRestore = pgRestore;
        URI u = URI.create(url.replaceFirst("^jdbc:", ""));
        this.host = u.getHost() == null ? "localhost" : u.getHost();
        this.port = u.getPort() < 0 ? "5432" : Integer.toString(u.getPort());
        this.database = u.getPath().replaceFirst("^/", "");
        this.username = username;
        this.password = password;
    }

    @Configuration
    @EnableScheduling
    static class SchedulingOn {}

    /* ------------------------------------------------------------------ settings (folder and tools) */

    static final String P_DIR = "BACKUP_DIR", P_DUMP = "BACKUP_PG_DUMP", P_RESTORE = "BACKUP_PG_RESTORE", P_OLD_DIRS = "BACKUP_OLD_DIRS";

    /** Applies the folder / tool paths saved on the Backups page, and finds the PostgreSQL tools when they are not on PATH. */
    @jakarta.annotation.PostConstruct
    void loadSettings() {
        try {
            platform(P_DIR).ifPresent(v -> dir = Paths.get(v).toAbsolutePath().normalize());
            platform(P_DUMP).ifPresent(v -> pgDump = v);
            platform(P_RESTORE).ifPresent(v -> pgRestore = v);
            platform(P_OLD_DIRS).ifPresent(v -> { for (String s : v.split("\n")) if (!s.isBlank()) oldDirs.add(Paths.get(s.trim())); });
        } catch (Exception e) {
            log.warn("Backup settings could not be read: {}", e.getMessage());
        }
        if (!toolAvailable(pgDump) || !toolAvailable(pgRestore)) detectTools().ifPresent(bin -> {
            pgDump = bin.resolve(exe("pg_dump")).toString();
            pgRestore = bin.resolve(exe("pg_restore")).toString();
            log.info("Using PostgreSQL client tools found in {}", bin);
        });
    }

    /** {directory, pgDump?, pgRestore?} - platform administrator only. The folder is created if needed and must be writable. */
    @org.springframework.transaction.annotation.Transactional
    public Map<String, Object> updateSettings(AuthenticatedUser me, com.fasterxml.jackson.databind.JsonNode body) {
        requireAdmin(me);
        if (busy.get()) throw Errors.bad("A backup or restore is running. Try again when it has finished.");
        String d = body.path("directory").asText("").trim();
        if (d.isEmpty()) throw Errors.bad("Choose the backup folder.");
        Path newDir = Paths.get(d);
        if (!newDir.isAbsolute()) throw Errors.bad("Enter the full folder path, e.g. D:\\Jewellery360\\backups.");
        newDir = newDir.toAbsolutePath().normalize();
        try {
            Files.createDirectories(newDir);
            Path probe = Files.createTempFile(newDir, "j360-write-test", ".tmp");
            Files.delete(probe);
        } catch (Exception e) {
            throw Errors.bad("The server cannot write to " + newDir + " (" + e.getMessage() + ").");
        }
        String dump = tool(body.path("pgDump").asText("").trim(), "pg_dump", pgDump);
        String restore = tool(body.path("pgRestore").asText("").trim(), "pg_restore", pgRestore);
        Map<String, Object> before = Map.of("directory", dir.toString(), "pgDump", pgDump, "pgRestore", pgRestore);
        if (!newDir.equals(dir) && !oldDirs.contains(dir)) oldDirs.add(0, dir);   // backups already taken stay downloadable
        while (oldDirs.size() > 10) oldDirs.remove(oldDirs.size() - 1);
        dir = newDir;
        pgDump = dump;
        pgRestore = restore;
        savePlatform(P_DIR, dir.toString());
        savePlatform(P_DUMP, pgDump);
        savePlatform(P_RESTORE, pgRestore);
        savePlatform(P_OLD_DIRS, String.join("\n", oldDirs.stream().map(Path::toString).toList()));
        audit.log(me, "BACKUP_SETTINGS", "BackupRun", null, before, Map.of("directory", dir.toString(), "pgDump", pgDump, "pgRestore", pgRestore));
        return status(me);
    }

    /** A tool path from the form (empty = keep / auto-detect). Only pg_dump / pg_restore programs are accepted. */
    private String tool(String given, String name, String current) {
        if (given.isEmpty()) return current;
        Path p = Paths.get(given);
        if (Files.isDirectory(p)) p = p.resolve(exe(name));
        String file = p.getFileName() == null ? "" : p.getFileName().toString().toLowerCase(Locale.ROOT);
        if (!file.equals(name) && !file.equals(name + ".exe")) throw Errors.bad("Pick the " + name + " program (or the PostgreSQL bin folder).");
        if (!Files.isRegularFile(p)) throw Errors.bad(p + " was not found on the server.");
        if (!toolAvailable(p.toString())) throw Errors.bad(p + " could not be run.");
        return p.toString();
    }

    /** Folder picker for the Backups page: the drives / root, or the sub-folders of one folder on the server. */
    public Map<String, Object> folders(AuthenticatedUser me, String path) {
        requireAdmin(me);
        Map<String, Object> m = new LinkedHashMap<>();
        List<Map<String, String>> out = new ArrayList<>();
        if (path == null || path.isBlank()) {
            for (java.io.File r : java.io.File.listRoots()) out.add(Map.of("name", r.getPath(), "path", r.getPath()));
            m.put("path", "");
            m.put("parent", null);
        } else {
            Path p = Paths.get(path).toAbsolutePath().normalize();
            if (!Files.isDirectory(p)) throw Errors.bad(p + " is not a folder on the server.");
            try (var s = Files.list(p)) {
                s.filter(Files::isDirectory).filter(x -> { try { return !Files.isHidden(x); } catch (IOException e) { return false; } })
                 .sorted(Comparator.comparing(x -> x.getFileName().toString().toLowerCase(Locale.ROOT))).limit(300)
                 .forEach(x -> out.add(Map.of("name", x.getFileName().toString(), "path", x.toString())));
            } catch (IOException | SecurityException e) {
                throw Errors.bad("The server cannot open " + p + ".");
            }
            m.put("path", p.toString());
            m.put("parent", p.getParent() == null ? "" : p.getParent().toString());
        }
        m.put("folders", out);
        return m;
    }

    private static String exe(String name) { return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win") ? name + ".exe" : name; }

    /** Newest PostgreSQL installation in the usual places (Windows Program Files, /usr/lib/postgresql). */
    private Optional<Path> detectTools() {
        List<Path> roots = List.of(Paths.get("C:\\Program Files\\PostgreSQL"), Paths.get("/usr/lib/postgresql"), Paths.get("/opt/homebrew/opt"));
        List<Path> bins = new ArrayList<>();
        for (Path root : roots) {
            if (!Files.isDirectory(root)) continue;
            try (var s = Files.list(root)) { s.map(v -> v.resolve("bin")).filter(b -> Files.isRegularFile(b.resolve(exe("pg_dump")))).forEach(bins::add); }
            catch (IOException ignored) { /* not readable */ }
        }
        bins.sort(Comparator.comparing((Path b) -> b.getParent().getFileName().toString(), Comparator.comparingInt(BackupService::versionOf)).reversed());
        return bins.stream().filter(b -> toolAvailable(b.resolve(exe("pg_dump")).toString())).findFirst();
    }

    private static int versionOf(String folder) { try { return Integer.parseInt(folder.replaceAll("[^0-9].*$", "")); } catch (Exception e) { return 0; } }

    private Optional<String> platform(String key) {
        return props.findByCompanyIsNullAndUserIsNullAndKeyAndActiveTrue(key).map(Property::getValue).filter(v -> v != null && !v.isBlank());
    }

    private void savePlatform(String key, String value) {
        Property p = props.findByCompanyIsNullAndUserIsNullAndKeyAndActiveTrue(key).orElseGet(() -> { Property n = new Property(); n.setKey(key); return n; });
        p.setValue(value);
        props.save(p);
    }

    /* ------------------------------------------------------------------ read */

    public Map<String, Object> status(AuthenticatedUser me) {
        requireAdmin(me);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("directory", dir.toString());
        m.put("defaultDirectory", defaultDir.toString());
        m.put("pgDump", pgDump);
        m.put("pgRestore", pgRestore);
        m.put("scheduleEnabled", scheduleEnabled);
        m.put("schedule", describeCron(cron));
        m.put("cron", cron);
        m.put("keep", keep);
        m.put("running", busy.get());
        m.put("toolsAvailable", toolAvailable(pgDump) && toolAvailable(pgRestore));
        Optional<BackupRun> last = runs.findFirstByStatusAndKindNotOrderByStartedAtDesc("SUCCESS", "RESTORE");
        m.put("lastSuccess", last.map(BackupService::view).orElse(null));
        List<BackupRun> recent = runs.findTop50ByOrderByStartedAtDesc();
        m.put("lastFailed", recent.stream().filter(r -> "FAILED".equals(r.getStatus())).findFirst().map(BackupService::view).orElse(null));
        try {
            m.put("freeSpace", Files.getFileStore(Files.createDirectories(dir)).getUsableSpace());
        } catch (IOException e) {
            m.put("freeSpace", null);
        }
        m.put("history", recent.stream().map(BackupService::view).toList());
        m.put("confirmWord", CONFIRM_WORD);
        return m;
    }

    public Path file(AuthenticatedUser me, Long id) {
        requireAdmin(me);
        BackupRun r = runs.findById(id).orElseThrow(TenantContext::notFound);
        Path p = pathOf(r);
        if (p == null || !Files.isRegularFile(p)) throw Errors.bad("This backup file is no longer on the server.");
        audit.log(me, "BACKUP_DOWNLOAD", "BackupRun", r.getId(), null, Map.of("file", r.getFileName()));
        return p;
    }

    /* ------------------------------------------------------------------ backup */

    public Map<String, Object> backupNow(AuthenticatedUser me) {
        requireAdmin(me);
        BackupRun r = runBackup("MANUAL", user(me));
        audit.log(me, "BACKUP_CREATE", "BackupRun", r.getId(), null, Map.of("file", String.valueOf(r.getFileName()), "status", r.getStatus()));
        if ("FAILED".equals(r.getStatus())) throw Errors.bad("Backup failed: " + r.getMessage());
        return view(r);
    }

    @Scheduled(cron = "${app.backup.cron:0 30 1 * * *}", zone = "Asia/Kolkata")
    public void scheduled() {
        if (!scheduleEnabled) return;
        BackupRun r = runBackup("SCHEDULED", null);
        if ("FAILED".equals(r.getStatus())) log.error("Scheduled backup failed: {}", r.getMessage());
    }

    private BackupRun runBackup(String kind, AppUser by) {
        if (!busy.compareAndSet(false, true)) throw Errors.conflict("A backup or restore is already running. Try again in a minute.");
        try {
            return dump(kind, by);
        } finally {
            busy.set(false);
        }
    }

    private BackupRun dump(String kind, AppUser by) {
        BackupRun r = new BackupRun();
        r.setKind(kind);
        r.setStatus("RUNNING");
        r.setCreatedBy(by);
        runs.save(r);
        String name = "jewellery360-" + FILE_TS.format(r.getStartedAt()) + "-" + kind.toLowerCase(Locale.ROOT) + ".dump";
        Path target = dir.resolve(name);
        try {
            Files.createDirectories(dir);
            Path tmp = dir.resolve(name + ".part");
            Result res = exec(List.of(pgDump, "-h", host, "-p", port, "-U", username, "-d", database, "-Fc", "--no-owner", "--no-privileges", "-f", tmp.toString()), 30);
            if (res.code != 0) {
                Files.deleteIfExists(tmp);
                return finish(r, "FAILED", null, 0, res.tail());
            }
            Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            BackupRun done = finish(r, "SUCCESS", name, Files.size(target), null);
            applyRetention();
            return done;
        } catch (Exception e) {
            log.error("Backup failed", e);
            return finish(r, "FAILED", null, 0, e.getMessage());
        }
    }

    /** Keeps the newest {@code keep} backup files; older files are deleted and their history row marked EXPIRED. */
    private void applyRetention() {
        List<BackupRun> ok = runs.findByStatusAndKindNotOrderByStartedAtDesc("SUCCESS", "RESTORE");
        for (int i = keep; i < ok.size(); i++) {
            BackupRun old = ok.get(i);
            try {
                Path p = pathOf(old);
                if (p != null) Files.deleteIfExists(p);
            } catch (IOException e) {
                log.warn("Could not delete old backup {}", old.getFileName(), e);
                continue;
            }
            old.setStatus("EXPIRED");
            old.setMessage("Removed after keeping the latest " + keep + " backups.");
            runs.save(old);
        }
    }

    /* ------------------------------------------------------------------ upload & restore */

    /** Stores an uploaded .dump (e.g. from another server) after checking pg_restore can read it. */
    public Map<String, Object> upload(AuthenticatedUser me, MultipartFile file) {
        requireAppAdmin(me);
        if (file == null || file.isEmpty()) throw Errors.bad("Choose a backup file.");
        BackupRun r = new BackupRun();
        r.setKind("UPLOAD");
        r.setStatus("RUNNING");
        r.setCreatedBy(user(me));
        runs.save(r);
        String name = "jewellery360-" + FILE_TS.format(r.getStartedAt()) + "-upload.dump";
        Path target = dir.resolve(name);
        try {
            Files.createDirectories(dir);
            try (InputStream in = file.getInputStream()) { Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING); }
            Result check = exec(List.of(pgRestore, "-l", target.toString()), 2);
            if (check.code != 0) {
                Files.deleteIfExists(target);
                finish(r, "FAILED", null, 0, "Not a Jewellery360 backup file (pg_restore could not read it).");
                throw Errors.bad("That file is not a valid backup.");
            }
            BackupRun done = finish(r, "SUCCESS", name, Files.size(target), "Uploaded: " + Objects.toString(file.getOriginalFilename(), ""));
            audit.log(me, "BACKUP_UPLOAD", "BackupRun", done.getId(), null, Map.of("file", name));
            return view(done);
        } catch (IOException e) {
            finish(r, "FAILED", null, 0, e.getMessage());
            throw Errors.bad("Could not save the uploaded file: " + e.getMessage());
        }
    }

    /**
     * Replaces ALL data with the chosen backup. A fresh safety backup is taken first. Runs in one transaction:
     * on any error nothing changes.
     */
    public Map<String, Object> restore(AuthenticatedUser me, Long id, String confirm) {
        requireAppAdmin(me);
        if (!CONFIRM_WORD.equals(confirm == null ? null : confirm.trim()))
            throw Errors.bad("Type " + CONFIRM_WORD + " to confirm. This replaces all current data with the backup.");
        BackupRun source = runs.findById(id).orElseThrow(TenantContext::notFound);
        Path file = pathOf(source);
        if (!"SUCCESS".equals(source.getStatus()) || file == null || !Files.isRegularFile(file)) throw Errors.bad("This backup file is not available.");
        if (!busy.compareAndSet(false, true)) throw Errors.conflict("A backup or restore is already running. Try again in a minute.");
        AppUser by = user(me);
        try {
            BackupRun safety = dump("PRE_RESTORE", by);
            if (!"SUCCESS".equals(safety.getStatus())) throw Errors.bad("Could not take a safety backup first, so nothing was restored: " + safety.getMessage());

            BackupRun r = new BackupRun();
            r.setKind("RESTORE");
            r.setStatus("RUNNING");
            r.setCreatedBy(by);
            r.setFileName(source.getFileName());
            runs.save(r);
            List<Map<String, Object>> history = jdbc.queryForList("SELECT * FROM backup_run ORDER BY id");

            Result res = exec(List.of(pgRestore, "-h", host, "-p", port, "-U", username, "-d", database, "--clean", "--if-exists", "--no-owner",
                    "--no-privileges", "--single-transaction", "--exit-on-error", file.toString()), 60);
            if (res.code != 0) {
                finish(r, "FAILED", source.getFileName(), 0, res.tail());
                audit.log(me, "BACKUP_RESTORE_FAILED", "BackupRun", source.getId(), null, Map.of("file", source.getFileName()));
                throw Errors.bad("Restore failed and your data was left unchanged: " + res.tail());
            }
            // The backup history describes files on this server: put it back as it was before the restore.
            restoreHistory(history, r.getId(), "Restored from " + source.getFileName() + ". Safety copy: " + safety.getFileName(), by);
            audit.log(me, "BACKUP_RESTORE", "BackupRun", source.getId(), null, Map.of("file", source.getFileName(), "safetyBackup", safety.getFileName()));
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("restoredFrom", source.getFileName());
            m.put("safetyBackup", safety.getFileName());
            m.put("message", "Data restored. Everyone should sign in again. If the backup came from an older version, restart the server so it can upgrade the database.");
            return m;
        } finally {
            busy.set(false);
        }
    }

    private void restoreHistory(List<Map<String, Object>> rows, Long restoreRunId, String message, AppUser by) {
        jdbc.update("DELETE FROM backup_run");
        Set<Long> userIds = new HashSet<>(jdbc.queryForList("SELECT id FROM app_user", Long.class));
        for (Map<String, Object> row : rows) {
            Long id = ((Number) row.get("id")).longValue();
            Object createdBy = row.get("created_by_id");
            Long cb = createdBy == null ? null : ((Number) createdBy).longValue();
            if (cb != null && !userIds.contains(cb)) cb = null;
            boolean isThis = id.equals(restoreRunId);
            jdbc.update("INSERT INTO backup_run (id, kind, status, file_name, file_size, started_at, finished_at, message, created_by_id) VALUES (?,?,?,?,?,?,?,?,?)",
                    id, row.get("kind"), isThis ? "SUCCESS" : row.get("status"), row.get("file_name"), row.get("file_size"), row.get("started_at"),
                    isThis ? Timestamp.from(Instant.now()) : row.get("finished_at"), isThis ? message : row.get("message"),
                    isThis && by != null && userIds.contains(by.getId()) ? by.getId() : cb);
        }
        jdbc.execute("SELECT setval(pg_get_serial_sequence('backup_run','id'), GREATEST((SELECT COALESCE(MAX(id),0) FROM backup_run), 1))");
    }

    /* ------------------------------------------------------------------ helpers */

    private BackupRun finish(BackupRun r, String status, String fileName, long size, String message) {
        r.setStatus(status);
        r.setFileName(fileName);
        r.setFileSize(size);
        r.setFinishedAt(Instant.now());
        r.setMessage(message == null ? null : message.length() > 1900 ? message.substring(message.length() - 1900) : message);
        return runs.save(r);
    }

    private Path pathOf(BackupRun r) {
        if (r.getFileName() == null) return null;
        // the current folder first, then folders used before the folder was changed
        List<Path> places = new ArrayList<>();
        places.add(dir);
        places.addAll(oldDirs);
        if (!places.contains(defaultDir)) places.add(defaultDir);
        for (Path d : places) {
            Path p = d.resolve(r.getFileName()).normalize();
            if (p.startsWith(d) && Files.isRegularFile(p)) return p;
        }
        Path p = dir.resolve(r.getFileName()).normalize();
        return p.startsWith(dir) ? p : null;
    }

    private record Result(int code, String output) {
        String tail() {
            String o = output == null ? "" : output.trim();
            return o.length() > 600 ? "..." + o.substring(o.length() - 600) : o.isEmpty() ? "exit code " + code : o;
        }
    }

    private Result exec(List<String> cmd, int timeoutMinutes) {
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd).redirectErrorStream(true);
            if (password != null && !password.isEmpty()) pb.environment().put("PGPASSWORD", password);
            Process p = pb.start();
            byte[] out = p.getInputStream().readAllBytes();
            if (!p.waitFor(timeoutMinutes, TimeUnit.MINUTES)) {
                p.destroyForcibly();
                return new Result(-1, "Timed out after " + timeoutMinutes + " minutes.");
            }
            return new Result(p.exitValue(), new String(out, StandardCharsets.UTF_8));
        } catch (IOException e) {
            return new Result(-1, "Could not run " + cmd.get(0) + " - is the PostgreSQL client installed on the server? (" + e.getMessage() + ")");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Result(-1, "Interrupted.");
        }
    }

    private boolean toolAvailable(String tool) {
        return exec(List.of(tool, "--version"), 1).code == 0;
    }

    private static String describeCron(String cron) {
        String[] f = cron.trim().split("\\s+");
        if (f.length == 6 && f[3].equals("*") && f[4].equals("*") && f[5].equals("*") && f[1].matches("\\d+") && f[2].matches("\\d+"))
            return String.format("Every day at %02d:%02d", Integer.parseInt(f[2]), Integer.parseInt(f[1]));
        return cron;
    }

    static Map<String, Object> view(BackupRun r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.getId());
        m.put("kind", r.getKind());
        m.put("status", r.getStatus());
        m.put("fileName", r.getFileName());
        m.put("fileSize", r.getFileSize());
        m.put("startedAt", r.getStartedAt());
        m.put("finishedAt", r.getFinishedAt());
        m.put("message", r.getMessage());
        m.put("by", r.getCreatedBy() == null ? (Objects.equals(r.getKind(), "SCHEDULED") ? "Schedule" : null) : r.getCreatedBy().getUsername());
        m.put("downloadable", "SUCCESS".equals(r.getStatus()) && r.getFileName() != null);
        return m;
    }

    private AppUser user(AuthenticatedUser me) { return me == null ? null : users.findById(me.getUserId()).orElse(null); }

    /** Backups contain every company's data, so only the platform administrator manages them. */
    private static void requireAdmin(AuthenticatedUser me) { requireAppAdmin(me); }

    private static void requireAppAdmin(AuthenticatedUser me) {
        if (me == null || !"APP_ADMIN".equals(me.getRole())) throw Errors.forbidden("Only the platform administrator can manage backups.");
    }
}
