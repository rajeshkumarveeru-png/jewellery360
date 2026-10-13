import {useState} from 'react';
import {api, backupFolders, backupNow, backupSettings, backups, downloadBackup, restoreBackup} from '../api';
import {confirmDialog, promptDialog} from '../shared/Notify';
import {Table} from '../shared/ui';
import {F, KV, Pill, Sheet, StateBox, errText, useData} from '../shared/kit';

/* ============================================================================================
   Database backups (platform administrator only - the server enforces it). Restoring is deliberately slow to do:
   a typed confirmation word, and the server always takes a safety backup of the current data first.
   ============================================================================================ */

const size = (b: any) => { const n = Number(b || 0); return n > 1e9 ? `${(n / 1e9).toFixed(1)} GB` : n > 1e6 ? `${(n / 1e6).toFixed(1)} MB` : n > 1e3 ? `${(n / 1e3).toFixed(0)} KB` : `${n} B`; };
const when = (s: any) => (s ? new Date(s).toLocaleString('en-IN', {dateStyle: 'medium', timeStyle: 'short'}) : '—');

export default function BackupsModule({onNotice}: {onNotice: (s: string) => void}) {
  const st = useData(() => backups(), []);
  const s: any = st.data;
  const [busy, setBusy] = useState(false);
  const run = async () => {
    setBusy(true);
    try { const r = await backupNow(); onNotice(r.data?.message || 'Backup finished.'); }
    catch (e) { onNotice(errText(e)); }
    finally { setBusy(false); void st.reload(); }
  };
  const download = async (b: any) => {
    try {
      const r = await downloadBackup(b.id);
      const url = URL.createObjectURL(new Blob([r.data]));
      const a = document.createElement('a'); a.href = url; a.download = b.fileName; a.click();
      window.setTimeout(() => URL.revokeObjectURL(url), 60000);
    } catch (e) { onNotice(errText(e)); }
  };
  const upload = async (file: File) => {
    const form = new FormData(); form.append('file', file);
    setBusy(true);
    try { await api.post('/backups/upload', form); onNotice(`${file.name} uploaded. It can now be restored from the list.`); }
    catch (e) { onNotice(errText(e)); }
    finally { setBusy(false); void st.reload(); }
  };
  const restore = async (b: any) => {
    if (!(await confirmDialog({title: `Restore ${b.fileName}?`, message: 'Every company\'s current data is replaced by this backup. A safety backup of the current data is taken first. Everyone will need to sign in again.', confirmLabel: 'Continue', tone: 'danger'}))) return;
    const word = await promptDialog({title: 'Type the confirmation word', message: `Type ${s?.confirmWord || 'RESTORE'} to restore.`, label: 'Confirmation', confirmLabel: 'Restore now'});
    if (!word) return;
    setBusy(true);
    try { const r = await restoreBackup(b.id, word); onNotice(r.data?.message || 'Data restored.'); }
    catch (e) { onNotice(errText(e)); }
    finally { setBusy(false); void st.reload(); }
  };
  const rows: any[] = s?.history || [];
  return <div className="panel">
    <StateBox loading={st.loading} error={st.error} onRetry={st.reload} empty={!s}>
      {s && <>
        {!s.toolsAvailable && <div className="kNote bad">pg_dump / pg_restore were not found on the server. Install the PostgreSQL client tools (or set PG_DUMP / PG_RESTORE) - backups cannot run until then.</div>}
        {s.lastFailed && <div className="kNote warn">Last failed backup: {when(s.lastFailed.startedAt)} - {s.lastFailed.message}</div>}
        <KV rows={[['Last good backup', s.lastSuccess ? `${when(s.lastSuccess.finishedAt)} · ${size(s.lastSuccess.fileSize)}` : 'None yet'], ['Automatic backup', s.scheduleEnabled ? s.schedule : 'Off'],
          ['Copies kept', s.keep], ['Folder', s.directory], ['Free space', s.freeSpace != null ? size(s.freeSpace) : null]]} />
        <SettingsBox s={s} onNotice={onNotice} onSaved={st.reload} />
        <div className="kActions">
          <button type="button" className="primary" disabled={busy || s.running || !s.toolsAvailable} onClick={run}>{busy || s.running ? 'Working…' : 'Back up now'}</button>
          <label className="secondary" style={{cursor: 'pointer'}}>Upload a backup file<input type="file" accept=".dump,.backup,.sql" hidden onChange={e => { const f = e.target.files?.[0]; if (f) void upload(f); e.target.value = ''; }} /></label>
        </div>
        <h3 className="kSection">History</h3>
        <StateBox empty={!rows.length} emptyText="No backups yet">
          <Table><thead><tr><th>Started</th><th>Kind</th><th>File</th><th>Size</th><th>Status</th><th>By</th><th /></tr></thead>
            <tbody>{rows.map(b => <tr key={b.id}><td>{when(b.startedAt)}</td><td>{b.kind}</td><td>{b.fileName || '—'}<small>{b.message}</small></td><td>{b.fileSize ? size(b.fileSize) : '—'}</td>
              <td><Pill value={b.status} tone={b.status === 'SUCCESS' ? 'success' : b.status === 'FAILED' ? 'bad' : 'warn'} /></td><td>{b.by || '—'}</td>
              <td>{b.downloadable && <><button type="button" className="ghost" onClick={() => download(b)}>Download</button><button type="button" className="ghost danger" disabled={busy} onClick={() => restore(b)}>Restore</button></>}</td></tr>)}</tbody></Table>
        </StateBox>
      </>}
    </StateBox>
  </div>;
}

/** Backup folder and PostgreSQL tools - can be changed at any time; the server checks the folder is writable and the
 *  programs run. Backups taken in an earlier folder stay downloadable. */
function SettingsBox({s, onNotice, onSaved}: {s: any; onNotice: (m: string) => void; onSaved: () => void}) {
  const [open, setOpen] = useState(!s.toolsAvailable);
  const [dir, setDir] = useState<string>(s.directory || '');
  const [dump, setDump] = useState<string>(s.pgDump || '');
  const [restore, setRestore] = useState<string>(s.pgRestore || '');
  const [busy, setBusy] = useState(false);
  const [browse, setBrowse] = useState(false);
  const save = async () => {
    setBusy(true);
    try { await backupSettings({directory: dir, pgDump: dump || undefined, pgRestore: restore || undefined}); onNotice('Backup settings saved.'); onSaved(); }
    catch (e) { onNotice(errText(e)); }
    finally { setBusy(false); }
  };
  return <div className="kNote" style={{marginBottom: 12}}>
    <div className="kRow"><b style={{flex: 1}}>Backup folder &amp; PostgreSQL tools</b><button type="button" className="ghost" onClick={() => setOpen(!open)} aria-expanded={open}>{open ? 'Hide' : 'Change'}</button></div>
    {open && <>
      <div className="kGrid" style={{marginTop: 8}}>
        <F label="Backup folder on the server" wide hint={`Default: ${s.defaultDirectory}`}>
          <div className="kRow"><input value={dir} onChange={e => setDir(e.target.value)} placeholder="D:\Jewellery360\backups" /><button type="button" className="secondary" onClick={() => setBrowse(true)}>Browse…</button><button type="button" className="ghost" onClick={() => setDir(s.defaultDirectory)}>Use default</button></div>
        </F>
        <F label="pg_dump program" hint="Or the PostgreSQL bin folder. Leave as is when backups work."><input value={dump} onChange={e => setDump(e.target.value)} /></F>
        <F label="pg_restore program"><input value={restore} onChange={e => setRestore(e.target.value)} /></F>
      </div>
      <div className="kActions"><button type="button" className="primary" disabled={busy || !dir.trim()} onClick={save}>{busy ? 'Checking…' : 'Save settings'}</button></div>
    </>}
    <FolderBrowser open={browse} start={dir} onClose={() => setBrowse(false)} onPick={p => { setDir(p); setBrowse(false); }} />
  </div>;
}

function FolderBrowser({open, start, onClose, onPick}: {open: boolean; start: string; onClose: () => void; onPick: (p: string) => void}) {
  const [path, setPath] = useState(start);
  const f = useData(() => backupFolders(path || undefined), [path], open);
  const d: any = f.data;
  return <Sheet open={open} onClose={onClose} kicker="SERVER FOLDERS" title={d?.path || 'Drives'}>
    <div className="kActions" style={{marginTop: 0}}>
      {d?.parent !== null && d?.parent !== undefined && <button type="button" className="secondary" onClick={() => setPath(d.parent)}>↑ Up</button>}
      {d?.path && <button type="button" className="primary" onClick={() => onPick(d.path)}>Use this folder</button>}
    </div>
    <StateBox loading={f.loading} error={f.error} onRetry={f.reload} empty={!(d?.folders || []).length} emptyText="No sub-folders" emptyHint={d?.path ? 'Use this folder, or go up.' : undefined}>
      {(d?.folders || []).map((x: any) => <button key={x.path} type="button" className="ghost" style={{display: 'block', width: '100%', textAlign: 'left', padding: '10px 8px'}} onClick={() => setPath(x.path)}>📁 {x.name}</button>)}
    </StateBox>
  </Sheet>;
}
