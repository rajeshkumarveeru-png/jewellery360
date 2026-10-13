# Current Task Progress

## Objective
Jewellery360 full audit + implementation (see the original task prompt): finish the backend consolidation, rebuild the
web frontend on the consolidated API, UI/UX simplification, security/business correctness, mobile app, cleanup.

## Project Root
D:\source_code\jewellery360 (original checkout, branch `main`, no worktree). Nothing committed yet.

## Status
IN_PROGRESS — Phases 1 (foundation), dev-DB upgrade, cleanup and mobile v1 DONE and verified. Remaining items below.

## Environment notes
- Build needs JDK 21: `JAVA_HOME=C:\Users\ASUS\.jdks\ms-21.0.12` (system default is Java 8).
- No working Python → `node backend/scripts/smoke_test.mjs <url> admin <password>` (EMPTY database only).
- PostgreSQL 18 client tools in `C:\Program Files\PostgreSQL\18\bin` (not on PATH; set PG_DUMP / PG_RESTORE for backups).
- Testing approach used: create a schema (e.g. `j360_test`) in the dev DB, start the jar with
  `SPRING_DATASOURCE_URL=...jewellery360?currentSchema=j360_test`, then `DROP SCHEMA j360_test CASCADE`.
- `.claude/launch.json` "web-test": Vite on 5174 proxied to a backend on 8090 (`J360_API_TARGET`).

## Completed
- [x] Backend consolidation finished; obsolete files and `remove-obsolete-files.bat` DELETED (user approved 2026-10-12).
- [x] Security fixes: AnalyticsController (TenantContext + OVERVIEW), sale detail page check, no default admin password
      (APP_BOOTSTRAP_ADMIN_PASSWORD or random one-time password in the log).
- [x] Dev DB (`jewellery360`, public) UPGRADED with 090-legacy-dev-upgrade (user approved). Backup first:
      `backups/jewellery360-before-consolidation-20261012-222759.dump` (pg_dump -Fc, git-ignored).
      First run failed and rolled back cleanly: legacy data re-used one tag on several pieces. Fixed the script
      (tag stays on the in-stock/newest piece, others get "-<id>"; bills keep their own tag copy), dry-ran it in a
      rolled-back transaction, then ran it: success; counts intact (4 companies, 5 users, 15 pieces, 6 bills,
      8 lines, 3 payments, 5 rates); Hibernate validate passed. 090 marked `validCheckSum: ANY`.
- [x] Smoke test (node) on empty schema: 59/59 passed. Test schema `j360_test` dropped afterwards.
- [x] Web frontend rebuilt on the consolidated API (Overview, Billing, Stock, Customers, Gold Rates, Old Gold,
      Purchases, Services, Money, Reports, Backups, Audit Logs, Settings menu editor). Browser-verified.
- [x] Mobile app v1 created in `mobile/` (Expo SDK 57, expo-router, SecureStore; versions from the user's existing
      skeleton lockfile): login, Home dashboard, Bill (search + camera scan, server preview, split payment,
      complete), Bills + bill detail (payment, WhatsApp share), Customers + profile (collect, new bill, call),
      Stock + piece history, Gold rates (view/update with >5% confirm), Orders & repairs status, Day close.
      Tabs follow permissions; APP_ADMIN told to use web. Verified: `tsc` clean, `expo-doctor` 21/21, web export
      driven in the browser at phone size as a cashier: login → dashboard → bill → ₹76,426 preview → paid →
      INV/26-27/0005; customers, more, day close render; deep links survive reload.

## Round 3 (user feedback 2026-10-12, screenshots + dashboard redesign spec)
- [x] Users: platform admin can edit / deactivate / reactivate / reset company owners (backend UserController rules:
      owners only by APP_ADMIN, APP_ADMIN account never, never yourself; UI Users.tsx actions + "Company owner" role).
- [x] Side panels squeezed inside panels (Add customer etc.): root cause = panels with backdrop-filter / hover transform
      become the containing block for position:fixed. Fix: kit `Portal` renders Sheet and FormDrawer overlays into `.app`.
- [x] Backups: folder + pg_dump/pg_restore changeable any time (PUT /api/backups/settings, GET /api/backups/folders
      server folder browser, APP_ADMIN only, folder must be writable, only pg_dump/pg_restore programs accepted);
      saved as platform properties (property table, company/user NULL - no migration); old folders still searched for
      downloads; tools auto-detected under C:\Program Files\PostgreSQL\<ver>\bin when not on PATH.
- [x] Expenses discoverable: menu default label "Money & expenses", dashboard Expenses card (drill-down + Add),
      "Add expense" quick action and Ctrl+K action (Money opens the Expenses tab / add form via focus).
- [x] Dashboard redesign (pages/Overview.tsx + Overview.css, dark-theme tokens, prefers-reduced-motion):
      period selector (Today / 7 days / This month / Custom), refresh + last updated, alerts, quick actions,
      16 widgets with a written definition each; 12 KPI cards open drill-down side panels with real data
      (sales bills + GST breakdown, collections by mode, customers owing, weight by purity, stock by purity + ageing,
      expenses, profit by category, open orders/repairs, karigar position, advances, supplier dues, rate board).
      Customize mode: HTML5 drag-and-drop + ◀ ▶ keyboard/touch alternative + hide/show + reset; saved per user per
      company in property table (user-level key DASHBOARD_LAYOUT) via GET/PUT/DELETE /api/properties/dashboard
      (validated ids, max 40). Not available: "low stock" (pieces are unique tagged items, no quantities).
- [x] Menu drag-and-drop: shared/MenuEditor.tsx rebuilt - grouped sections, drag within/between groups, rename
      groups, ↑↓ keyboard alternative, live preview, undo, restore default; "Arrange menu" link in the sidebar.
- Verification: rules API test 19/19 real checks (1 failure was the test sending a header instead of ?companyId);
  smoke 59/59; browser: Users owner actions, all 12 drill-downs full-height without errors, dashboard drag →
  save → reload persisted → reset, menu drag before a page across groups, Add customer panel full height,
  Backups folder browser; npm run build + gradlew clean build PASS.
- NOTE for the user: restart the backend (dev.bat) - the running server still has the previous build.

## Remaining (prioritised)
1. Mobile: test on a real phone with Expo Go (camera scanning not testable in the browser). Not verified on device.
2. Web: rework Users / Branches / Approvals / Auth onto the shared kit patterns (they work, older style).
3. CSS consolidation: 10 global CSS files (~277 KB) with overlapping theme packs.
4. Minor: gold-rate impact counts drafts company-wide; preview uses today's rate for back-dated bills;
   smoke_test.py still defaults to admin123 (pass the password as 3rd argument).
5. Optional: remove 090-legacy-dev-upgrade.yml + legacy guards once no other old database exists (e.g. desktop kit).
6. Commit the work (not done; user to decide).

## Files Changed (this session)
- backend: controller/AnalyticsController.java, service/SalesService.java, config/DataInitializer.java,
  resources/application.properties, db/changelog/090-legacy-dev-upgrade.yml (tag de-dup + validCheckSum),
  scripts/smoke_test.mjs (new); obsolete classes + old changelogs deleted
- frontend: src/api.ts, src/styles.css, vite.config.ts, src/shared/* (kit, billActions, MasterEditor, MenuEditor new;
  config, permissions, PageHero, HeaderGadget, CommandPalette, ui), src/pages/* (see Completed); obsolete pages deleted
- mobile/ (new project): app/**, src/{api,session,cart,ui}.ts(x), package.json, package-lock.json, app.json, README.md
- .claude/launch.json, remove-obsolete-files.bat (deleted), TASK_PROGRESS.md

## Last Verified Results (2026-10-12)
- `gradlew clean build`: BUILD SUCCESSFUL (0 unit tests in project).
- `npm run build` (frontend): PASS.
- mobile: `npx tsc --noEmit` clean; `npx expo-doctor` 21/21.
- Smoke test 59/59 (empty schema); browser click-through of web and mobile web build.

## Exact Next Steps
1. Run the mobile app on a phone: `cd mobile && cp .env.example .env` (LAN IP), `npx expo start`, scan with Expo Go.
2. Rework Users / Branches / Approvals / Auth screens; then CSS consolidation.

## Round 4 (2026-10-13: user compared old vs new dashboard/billing; log showed profit report crash)
- [x] ReportService.margin ClassCastException on empty period (totals are Integer 0) → numeric-safe dec().
- [x] Billing totals panel + MenuEditor preview used <aside>, which gets the global sidebar style `.app aside`
      (dark, 250px/88px, 100vh) → changed to <section>.
- [x] Dashboard visual redesign back towards the old look (kept real data, drill-downs, customize):
      hero row (gold Today's sales with vs-yesterday, daily target ring, dark rate board), coloured KPI tiles,
      dark trend panel (range picker, Daily/Weekly/Monthly, Line/Bars, range KPIs), insight tiles (best period,
      average/day, growth, busiest weekday + weekday heat - all from the analytics series), 12-column grid.
- [x] Stock filter checkbox: global rule keeps checkboxes/radios 18px (styles.css).
- [x] Billing redesign (Billing.tsx + new Billing.css): header (status, invoice no. on completion, bill date = backend
      saleDate max today, saved rate board), customer + scan fields, items with gross/net + GST, dark summary panel
      (4 steps, gross/stone/net, line totals, big Total payable, paid/balance bar, Full / 50% advance / On credit,
      mode chips, Save draft / Complete bill). Pricing still only from the server preview.
- [x] Dashboard tiles were white: the clickable class `kpi` collided with the legacy global `.kpi` glass rule
      (!important) - renamed to `dxClick`.
- Verification 2026-10-13: empty-period profit/profit-category reports 200 (were 500); smoke 59/59; browser:
  dashboard hero/KPI/trend/insights render with real data; billing Full → Complete bill → INV paid; summary
  panel 380px at 1280px wide, stacked on phone, no horizontal scroll; checkbox 18px. npm build + gradle build PASS.

## Round 5 (2026-10-13 afternoon)
- User's localhost:5173 still showed the previous dashboard/billing: verified Vite (frontend/node_modules/.bin/vite,
  this project) serves the current modules (dxClick / bxSum present) - the open tab held stale modules after HMR
  missed the file replacements. Fix for the user: hard refresh (Ctrl+Shift+R). Backend on 8080 restarted 13:48 OK.
- [x] Every page's stat tiles (kStat, 53 uses: Stock, Old Gold, Customers, Money, Purchases, Bills, Services sheets)
      now gradient tiles cycling gold / teal / indigo / rose / amber / slate with a ring accent and hover lift;
      tabs are a segmented pill bar; tables have a tinted header and accent row hover; side-panel headers tinted.
      All in styles.css (no new files), reduced-motion respected. Verified in the browser on Stock, Customers, Money.

## Round 6 (2026-10-13: plain drawers, incomplete menu, billing opened a stale drawer, empty dropdowns, responsiveness)
- [x] Billing opened the previous bill drawer: stale dashboard `focus` was re-applied on remount → menu, palette and `go()` without a focus now clear it (Dashboard.tsx).
- [x] Billing counter restored to the last committed look (Billing.tsx markup + BillSummary.tsx + Billing.css from HEAD) with today's features (server preview pricing, payment modes, old gold, rate overrides by permission); fits one screen at 1920x1000 and 1366x768 (no page scroll).
- [x] Customer dropdown lists existing customers on click (verified: Lakshmi R shown); input now also opens on click. Item dropdown lists in-stock pieces (verified). Walk-in bill completed in test.
- [x] Drawers styled across the app (styles.css: KV tiles, accent section headers, card tables, dark totals, gradient primary action) — verified on the dashboard Sales drill-down.
- [x] Sidebar: full-width menu buttons, gold small-caps group labels.
- [x] Responsive sweep at 375px over all 17 menu screens: only Reports overflowed (inline grid beat the media query) → `.kSplit.kSplitNav` class, stacks under 1000px. Drawers fit at 375px. Billing on phone uses the floating "View bill" bar.
- Tests: `npm run build` passed. Backend unchanged this round (no gradle run). Test schema j360_test dropped, test backend/preview stopped.
- Files: frontend/src/pages/Billing.tsx, BillSummary.tsx (new), Billing.css, Dashboard.tsx, Reports.tsx, styles.css.
- Next: user hard-refresh (Ctrl+Shift+R) to load the new frontend; report anything still off with a screenshot.

## Round 7 (2026-10-13: invoice number must show before billing, free text allowed, validated by backend)
- [x] Backend: `GET /api/sales/invoice-number?date=` (next number, not reserved) and `GET /api/sales/invoice-number/check?no=` ({ok, message}).
- [x] `POST /api/sales` accepts `invoiceNoAuto` (default true) + `invoiceNo`. A typed number must be 1-16 chars, letters/digits/-//, not start with DRAFT, unique per company (case-insensitive) — GST rule 46(b). Typed numbers don't consume the sequence; the sequence skips any number already used by hand (NumberingService.next/peek with a `taken` predicate).
- [x] Billing: Invoice no field shows the next number upfront (tag "next", refreshed every 30 s); typing switches to custom with live server check (red border + message), "↺ auto" restores the sequence; if another counter took the shown number the user is told the final number.
- Tests: smoke 59/59; new invoice-number API test 12/12 (scratchpad inv_test.mjs); browser: duplicate refused on screen, bill CNT-0500 completed, field returned to INV/26-27/0007; `npm run build` and `./gradlew build` passed. Test schema dropped.
- Files: backend NumberingService, SalesService, SalesController, SaleRepository, DocumentSequenceRepository; frontend api.ts, pages/Billing.tsx, pages/Billing.css.
- Next: restart the backend (new endpoints) and hard-refresh the browser.
