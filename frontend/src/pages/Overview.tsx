import {useCallback, useEffect, useRef, useState} from 'react';
import type {DragEvent, ReactNode} from 'react';
import {
  advances, customers, dashboard, expenses, getDashboardLayout, goldRateBoard, karigarSummary, moneyPayments, orders, repairs, resetDashboardLayout,
  runReport, salesAnalytics, sales as salesList, saveDashboardLayout
} from '../api';
import type {Granularity} from '../api';
import {User} from '../shared/types';
import {NAV_PATHS} from '../shared/ui';
import {usePermissions} from '../shared/permissions';
import {DateRangePicker, DeltaChip, Segmented, TrendChart, defaultPresets, inr} from '../shared/widgets';
import type {TrendPoint} from '../shared/widgets';
import {PAGE_TABS} from '../shared/config';
import {KV, Pill, Sheet, StateBox, errText, grams, label, money, num, useData} from '../shared/kit';
import type {PageProps} from './Dashboard';
import './Overview.css';

/* ============================================================================================
   Owner dashboard. Key figures first; click any card for the detail behind it. Every figure comes from the server
   (dashboard, reports, payments, expenses, orders ...) - nothing is estimated. "Customize" lets each user drag the
   cards into their own order and hide the ones they do not need; the arrangement is saved for that user.
   ============================================================================================ */

type Period = {key: 'today' | '7d' | 'month' | 'custom'; from: string; to: string};
const iso = (d: Date) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
const periodOf = (key: Period['key'], from?: string, to?: string): Period => {
  const t = new Date();
  if (key === '7d') { const f = new Date(t); f.setDate(t.getDate() - 6); return {key, from: iso(f), to: iso(t)}; }
  if (key === 'month') return {key, from: iso(new Date(t.getFullYear(), t.getMonth(), 1)), to: iso(t)};
  if (key === 'custom') return {key, from: from || iso(t), to: to || iso(t)};
  return {key: 'today', from: iso(t), to: iso(t)};
};
const periodName = (p: Period) => p.key === 'today' ? 'today' : p.key === '7d' ? 'in the last 7 days' : p.key === 'month' ? 'this month' : `${p.from} – ${p.to}`;

type Ctx = {canPage: (tab: string) => boolean; profit: boolean};
/** size: grid width on a 12-column grid (hero tiles, KPI tiles, the trend panel ...). tone: colour of a KPI tile. */
type Size = 'heroL' | 'heroM' | 'heroS' | 'kpi' | 'trend' | 'side' | 'half';
type Widget = {id: string; title: string; icon: string; size: Size; tone?: string; def: string; can: (c: Ctx) => boolean; click?: boolean};

/** Every widget, in the default order. `def` is shown on the card so each number has a clear meaning. */
const WIDGETS: Widget[] = [
  {id: 'today', title: "Today's sales", icon: 'Billing', size: 'heroL', click: true, def: 'Bill totals of today including GST, minus returns; compared with the same figure for yesterday.', can: () => true},
  {id: 'target', title: 'Daily target', icon: 'Reports', size: 'heroM', def: "Today's sales against the target you set (saved in this browser).", can: () => true},
  {id: 'rates', title: "Today's rate", icon: 'Gold Rates', size: 'heroS', click: true, def: "The shop's own selling rate per gram that bills use.", can: () => true},
  {id: 'sales', title: 'Sales', icon: 'Billing', size: 'kpi', tone: 't-gold', click: true, def: 'Bill totals including GST, minus returns. Drafts and cancelled bills are not counted.', can: c => c.canPage('Billing') || c.canPage('Reports')},
  {id: 'collections', title: 'Collected', icon: 'Money', size: 'kpi', tone: 't-teal', click: true, def: 'Money received - cash, UPI, card, bank and cheque. Advance and old-gold adjustments are not new money and are left out.', can: c => c.canPage('Money')},
  {id: 'outstanding', title: 'Customers owe', icon: 'Customers', size: 'kpi', tone: 't-rose', click: true, def: 'Unpaid balance on all completed bills, whatever their date.', can: c => c.canPage('Customers')},
  {id: 'gold', title: 'Weight sold', icon: 'Gold Rates', size: 'kpi', tone: 't-champagne', click: true, def: 'Net weight of the pieces billed, all purities.', can: c => c.canPage('Reports')},
  {id: 'stock', title: 'Stock', icon: 'Stock', size: 'kpi', tone: 't-silver', click: true, def: 'Pieces in stock in this branch. Fine weight = net weight × fineness.', can: () => true},
  {id: 'expenses', title: 'Expenses', icon: 'Money', size: 'kpi', tone: 't-amber', click: true, def: 'Shop expenses entered on the Money page (rent, salary, electricity …).', can: c => c.canPage('Money')},
  {id: 'profit', title: 'Profit before expenses', icon: 'Reports', size: 'kpi', tone: 't-emerald', click: true, def: 'Sale value without GST minus the recorded cost of the pieces sold. Pieces without a cost price count as zero cost.', can: c => c.profit && c.canPage('Reports')},
  {id: 'orders', title: 'Open orders', icon: 'Services', size: 'kpi', tone: 't-indigo', click: true, def: 'Customer orders not yet delivered or cancelled.', can: c => c.canPage('Services')},
  {id: 'repairs', title: 'Open repairs', icon: 'Services', size: 'kpi', tone: 't-violet', click: true, def: 'Repairs received and not yet delivered.', can: c => c.canPage('Services')},
  {id: 'karigar', title: 'Karigar jobs', icon: 'Services', size: 'kpi', tone: 't-bronze', click: true, def: 'Open job work with karigars and fine gold still with them.', can: c => c.canPage('Services')},
  {id: 'advances', title: 'Advances held', icon: 'Money', size: 'kpi', tone: 't-blue', click: true, def: 'Customer advances not yet used or refunded.', can: c => c.canPage('Money') || c.canPage('Customers')},
  {id: 'suppliers', title: 'You owe suppliers', icon: 'Purchases', size: 'kpi', tone: 't-slate', click: true, def: 'Unpaid balance on purchases, after payments and returns.', can: c => c.canPage('Purchases')},
  {id: 'trend', title: 'Sales trend', icon: 'Reports', size: 'trend', def: 'Net sales (bill totals minus returns) per day, week or month in the chosen range.', can: () => true},
  {id: 'insights', title: 'Insights', icon: 'Reports', size: 'side', def: 'Worked out from the sales trend range: best period, average per day, growth (2nd half vs 1st half) and the busiest weekday.', can: () => true},
  {id: 'recent', title: 'Recent bills', icon: 'Billing', size: 'half', def: 'The latest bills of the last 30 days.', can: () => true},
  {id: 'celebrating', title: 'Celebrating today', icon: 'Customers', size: 'half', def: 'Customers with a birthday or anniversary today.', can: c => c.canPage('Customers')}
];
const DEFAULT = WIDGETS.map(w => ({id: w.id, hidden: false}));
type Item = {id: string; hidden: boolean};

/** Saved arrangement merged with the current widget list: unknown ids dropped, new widgets added at the end. */
const merge = (saved: Item[] | null): Item[] => {
  const out = (saved || []).filter(i => WIDGETS.some(w => w.id === i.id));
  // widgets added to the app after the layout was saved go to their default position
  DEFAULT.forEach((d, i) => { if (!out.some(k => k.id === d.id)) out.splice(Math.min(i, out.length), 0, d); });
  return out;
};

export default function Overview({user, ready, go}: PageProps & {user: User}) {
  const perms = usePermissions();
  const ctx: Ctx = {canPage: t => perms.canOpen(t), profit: perms.canSeeProfit};
  const companyId = user.role === 'APP_ADMIN' ? Number(localStorage.getItem('j360_context_company')) || null : user.companyId ?? null;
  const [period, setPeriod] = useState<Period>(() => periodOf('today'));
  const data = useDashboard(period, ctx, ready);
  const [open, setOpen] = useState<string | null>(null);
  /* sales trend panel: its own range, grouping and chart type (shared with the insight tiles) */
  const [tr, setTr] = useState<Trend>(() => { const r = defaultPresets().find(p => p.key === '30d')!.range(); return {from: r[0], to: r[1], gran: 'DAY', mode: 'line'}; });
  const trendQ = useData(() => salesAnalytics({from: tr.from, to: tr.to, granularity: tr.gran}), [tr.from, tr.to, tr.gran], ready);
  const [target, setTarget] = useState<number>(() => { try { return Number(localStorage.getItem('j360_daily_target')) || 100000; } catch { return 100000; } });
  const saveTarget = (n: number) => { setTarget(n); try { localStorage.setItem('j360_daily_target', String(n)); } catch { /* storage unavailable */ } };
  const x: Extra = {trend: trendQ.data as any, trendErr: trendQ.error, tr, setTr, target, saveTarget};

  /* layout and edit mode */
  const [layout, setLayout] = useState<Item[]>(DEFAULT);
  const [draft, setDraft] = useState<Item[] | null>(null);
  const [custom, setCustom] = useState(false);
  const [saving, setSaving] = useState(false);
  const [dragId, setDragId] = useState<string | null>(null);
  const [overId, setOverId] = useState<string | null>(null);
  const [notice, setNotice] = useState('');
  useEffect(() => {
    if (!ready || !companyId) return;
    getDashboardLayout(companyId).then(r => { setLayout(merge(r.data?.items)); setCustom(!!r.data?.custom); }).catch(() => setLayout(DEFAULT));
  }, [ready, companyId]);
  const editing = draft !== null;
  const items = (draft || layout).filter(i => WIDGETS.find(w => w.id === i.id)!.can(ctx));
  const move = (id: string, to: number) => setDraft(d => {
    const list = [...(d || layout)];
    const from = list.findIndex(i => i.id === id);
    if (from < 0 || to < 0 || to >= list.length || from === to) return list;
    const [x] = list.splice(from, 1);
    list.splice(to, 0, x);
    return list;
  });
  const moveBy = (id: string, delta: number) => {
    // move among the widgets this user can see, so a step is always visible
    const visible = items.map(i => i.id);
    const target = visible[visible.indexOf(id) + delta];
    if (!target) return;
    move(id, (draft || layout).findIndex(i => i.id === target));
  };
  const toggle = (id: string) => setDraft(d => (d || layout).map(i => i.id === id ? {...i, hidden: !i.hidden} : i));
  const save = async () => {
    if (!draft) return;
    setSaving(true);
    try { await saveDashboardLayout(draft, companyId); setLayout(draft); setCustom(true); setDraft(null); flash('Dashboard layout saved.'); }
    catch (e) { flash(errText(e)); }
    finally { setSaving(false); }
  };
  const reset = async () => {
    try { await resetDashboardLayout(companyId); setLayout(DEFAULT); setCustom(false); setDraft(null); flash('Default layout restored.'); }
    catch (e) { flash(errText(e)); }
  };
  const flash = (m: string) => { setNotice(m); window.setTimeout(() => setNotice(''), 3500); };
  const onDrop = (e: DragEvent, targetId: string) => {
    e.preventDefault();
    const id = dragId || e.dataTransfer.getData('text/plain');
    setDragId(null); setOverId(null);
    if (id && id !== targetId) move(id, (draft || layout).findIndex(i => i.id === targetId));
  };

  if (user.role === 'APP_ADMIN' && !ready) {
    return <div className="dx"><div className="dxEmpty"><h2>Choose a company and branch</h2><p>Pick them at the top right to see that shop's dashboard. Companies, branches, users, approvals and backups work without it.</p></div></div>;
  }
  const d: any = data.dash;
  const quick = [
    {label: 'New bill', tab: 'Billing', focus: {type: 'NEW_BILL', id: 0}, ok: perms.canOpen('Billing') && !perms.isViewer},
    {label: 'Add customer', tab: 'Customers', focus: {type: 'NEW_CUSTOMER', id: 0}, ok: perms.canOpen('Customers') && !perms.isViewer},
    {label: 'Add expense', tab: 'Money', focus: {type: 'NEW_EXPENSE', id: 0}, ok: perms.canOpen('Money') && !perms.isViewer},
    {label: 'Update rates', tab: 'Gold Rates', ok: perms.canOpen('Gold Rates') && perms.canManage},
    {label: 'Take old gold', tab: 'Old Gold', ok: perms.canOpen('Old Gold') && !perms.isViewer},
    {label: 'Close the day', tab: 'Money', focus: {type: 'DAY_CLOSE', id: 0}, ok: perms.canOpen('Money') && !perms.isViewer}
  ].filter(q => q.ok);

  return <div className={`dx${editing ? ' dxEditing' : ''}`}>
    <div className="dxHead">
      <div>
        <h2>{d?.branch ? `${d.branch} · ` : ''}{period.key === 'today' ? 'Today at a glance' : 'At a glance'}</h2>
        <p className="dxSub">{data.updatedAt ? `Updated ${data.updatedAt.toLocaleTimeString('en-IN', {hour: '2-digit', minute: '2-digit'})}` : 'Loading…'} · click any card for details</p>
      </div>
      <div className="dxTools">
        <div className="dxSeg" role="group" aria-label="Period">
          {(['today', '7d', 'month', 'custom'] as const).map(k => <button key={k} type="button" className={period.key === k ? 'on' : ''} aria-pressed={period.key === k}
            onClick={() => setPeriod(periodOf(k, period.from, period.to))}>{k === 'today' ? 'Today' : k === '7d' ? '7 days' : k === 'month' ? 'This month' : 'Custom'}</button>)}
        </div>
        {period.key === 'custom' && <span className="dxDates"><input type="date" value={period.from} max={period.to} onChange={e => setPeriod(periodOf('custom', e.target.value, period.to))} aria-label="From" />
          <input type="date" value={period.to} min={period.from} max={iso(new Date())} onChange={e => setPeriod(periodOf('custom', period.from, e.target.value))} aria-label="To" /></span>}
        <button type="button" className="dxBtn" onClick={data.reload} disabled={data.loading} aria-label="Refresh">{data.loading ? 'Refreshing…' : '↻ Refresh'}</button>
        {!editing && <button type="button" className="dxBtn" onClick={() => setDraft(layout)}>⠿ Customize</button>}
      </div>
    </div>

    {editing && <div className="dxEditBar" role="region" aria-label="Customize dashboard">
      <span><b>Customize:</b> drag cards by their handle (or use ◀ ▶), and hide what you do not need. Saved for you only.</span>
      <button type="button" className="dxBtn primary" disabled={saving} onClick={save}>{saving ? 'Saving…' : 'Save layout'}</button>
      <button type="button" className="dxBtn" onClick={() => setDraft(null)}>Cancel</button>
      {custom && <button type="button" className="dxBtn ghost" onClick={reset}>Reset to default</button>}
    </div>}
    {notice && <div className="dxNotice" role="status">{notice}</div>}
    {data.error && <div className="kNote bad" role="alert">{data.error} <button type="button" className="ghost" onClick={data.reload}>Try again</button></div>}

    {!editing && (d?.insights || []).length > 0 && <div className="dxAlerts" role="list" aria-label="Alerts">{d.insights.map((x: any, i: number) =>
      <button key={i} type="button" role="listitem" className={`dxAlert ${x.tone}`} onClick={() => x.page && go(PAGE_TABS[x.page] || x.page)}><i aria-hidden="true">{x.tone === 'warning' ? '!' : x.tone === 'good' ? '✓' : 'i'}</i><span>{x.text}</span>{x.page && <em>Open →</em>}</button>)}</div>}

    {!editing && quick.length > 0 && <div className="dxQuick" aria-label="Quick actions">{quick.map(q =>
      <button key={q.label} type="button" onClick={() => go(q.tab, q.focus)}>{q.label}</button>)}</div>}

    <div className="dxGrid">
      {items.filter(i => editing || !i.hidden).map((it, idx, arr) => {
        const w = WIDGETS.find(x => x.id === it.id)!;
        const body = <WidgetBody w={w} data={data} period={period} go={go} x={x} />;
        const cls = `dxCard s-${w.size}${w.tone ? ` ${w.tone}` : ''}`;
        if (editing) return <div key={w.id} className={`${cls} edit${it.hidden ? ' off' : ''}${dragId === w.id ? ' dragging' : ''}${overId === w.id && dragId !== w.id ? ' over' : ''}`}
          draggable onDragStart={e => { setDragId(w.id); e.dataTransfer.effectAllowed = 'move'; e.dataTransfer.setData('text/plain', w.id); }}
          onDragEnd={() => { setDragId(null); setOverId(null); }} onDragOver={e => { e.preventDefault(); if (overId !== w.id) setOverId(w.id); }} onDrop={e => onDrop(e, w.id)}>
          <div className="dxEditTop">
            <span className="dxHandle" aria-hidden="true" title="Drag to move">⠿</span><b>{w.title}</b>
            <span className="dxEditBtns">
              <button type="button" onClick={() => moveBy(w.id, -1)} disabled={idx === 0} aria-label={`Move ${w.title} earlier`}>◀</button>
              <button type="button" onClick={() => moveBy(w.id, 1)} disabled={idx === arr.length - 1} aria-label={`Move ${w.title} later`}>▶</button>
              <button type="button" onClick={() => toggle(w.id)} aria-pressed={!it.hidden} aria-label={it.hidden ? `Show ${w.title}` : `Hide ${w.title}`}>{it.hidden ? 'Show' : 'Hide'}</button>
            </span>
          </div>
          <div className="dxEditPreview" aria-hidden="true">{body}</div>
        </div>;
        if (!w.click) return <section key={w.id} className={cls} aria-label={w.title}>{body}</section>;
        // the whole tile is the button; the today tile opens today's sales
        return <button key={w.id} type="button" className={`${cls} dxClick`} aria-haspopup="dialog" title={w.def}
                       onClick={() => { if (w.id === 'today') { setPeriod(periodOf('today')); setOpen('sales'); } else setOpen(w.id); }}>{body}</button>;
      })}
    </div>

    <Detail id={open} onClose={() => setOpen(null)} period={period} data={data} go={go} />
  </div>;
}

/* ------------------------------------------------------------------------------------------------ data */

type Data = ReturnType<typeof useDashboard>;

/** Loads the dashboard and the figures for the chosen period from the endpoints the user may use. */
function useDashboard(period: Period, c: Ctx, ready: boolean) {
  const [state, setState] = useState<any>({});
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [updatedAt, setUpdatedAt] = useState<Date | null>(null);
  const seq = useRef(0);
  const reports = c.canPage('Reports'), money_ = c.canPage('Money'), billing = c.canPage('Billing'), services = c.canPage('Services');
  const load = useCallback(async () => {
    if (!ready) return;
    const mine = ++seq.current;
    setLoading(true); setError('');
    const {from, to} = period;
    const jobs: Record<string, Promise<any> | null> = {
      dash: dashboard(),
      salesRep: reports ? runReport('sales-daily', from, to) : null,
      salesList: !reports && billing ? salesList({from, to, limit: 1000}) : null,
      pay: money_ ? moneyPayments({from, to}) : null,
      gold: reports ? runReport('sales-purity', from, to) : null,
      exp: money_ ? expenses({from, to}) : null,
      profit: reports && c.profit ? runReport('profit', from, to) : null,
      karigar: services ? karigarSummary() : null
    };
    const keys = Object.keys(jobs);
    const res = await Promise.allSettled(keys.map(k => jobs[k] || Promise.resolve(null)));
    if (mine !== seq.current) return;
    const next: any = {};
    res.forEach((r, i) => { next[keys[i]] = r.status === 'fulfilled' ? r.value?.data ?? null : undefined; });
    const failed = res.findIndex((r, i) => r.status === 'rejected' && keys[i] === 'dash');
    if (failed >= 0) setError(errText((res[failed] as PromiseRejectedResult).reason, 'The dashboard could not be loaded.'));
    setState(next);
    setUpdatedAt(new Date());
    setLoading(false);
  }, [period.from, period.to, ready, reports, money_, billing, services, c.profit]);
  useEffect(() => { void load(); }, [load]);
  // keep figures fresh while the dashboard is open
  useEffect(() => { if (!ready) return; const id = window.setInterval(() => { void load(); }, 120000); return () => window.clearInterval(id); }, [load, ready]);

  // derived figures
  const s = state;
  let sales: {net: number; bills: number} | null = null;
  if (s.salesRep?.totals) sales = {net: num(s.salesRep.totals.net), bills: num(s.salesRep.totals.bills)};
  else if (Array.isArray(s.salesList)) {
    const ok = s.salesList.filter((b: any) => b.status !== 'CANCELLED' && b.status !== 'DRAFT');
    sales = {net: ok.reduce((t: number, b: any) => t + num(b.total) - num(b.returned), 0), bills: ok.length};
  } else if (period.key === 'today' && s.dash?.today) sales = {net: num(s.dash.today.sales), bills: num(s.dash.today.bills)};
  return {dash: s.dash, sales, pay: s.pay, gold: s.gold, exp: s.exp, profit: s.profit, karigar: s.karigar, salesRep: s.salesRep,
    loading, error, updatedAt, reload: load, ready: !!s.dash};
}

/* ------------------------------------------------------------------------------------------------ cards */

const Icon = ({name}: {name: string}) => <span className="dxIcon" aria-hidden="true"><svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round"><path d={NAV_PATHS[name] || 'M12 3l7 9-7 9-7-9z'} /></svg></span>;
const Skel = ({w = '60%'}: {w?: string}) => <span className="dxSkel" style={{width: w}} aria-hidden="true" />;

function Kpi({w, value, sub, tone}: {w: Widget; value: ReactNode | null; sub?: ReactNode; tone?: 'good' | 'bad' | 'warn'}) {
  return <>
    <div className="dxKpiTop"><Icon name={w.icon} /><span className="dxKpiLabel">{w.title}</span><span className="dxMore" aria-hidden="true">›</span></div>
    <div className={`dxValue${tone ? ` ${tone}` : ''}`}>{value === null ? <Skel /> : value}</div>
    <div className="dxKpiSub">{sub}</div>
  </>;
}

type Trend = {from: string; to: string; gran: Granularity; mode: 'line' | 'bar'};
type Extra = {trend: any; trendErr: string; tr: Trend; setTr: (t: Trend) => void; target: number; saveTarget: (n: number) => void};
const WEEKDAYS = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'];

function WidgetBody({w, data, period, go, x}: {w: Widget; data: Data; period: Period; go: PageProps['go']; x: Extra}) {
  const d: any = data.dash;
  const wait = !data.ready;
  switch (w.id) {
    case 'today': {
      const t = x.trend?.today;
      const sales = t ? num(t.sales) : d ? num(d.today?.sales) : null;
      return <>
        <span className="dxEyebrow">TODAY'S SALES · {d?.branch || ''}</span>
        <strong className="dxHeroValue">{sales === null ? <Skel w="50%" /> : inr(sales)}</strong>
        <div className="dxRow">{t && <DeltaChip value={num(t.changePercent)} />}<small>{data.updatedAt ? `Updated ${data.updatedAt.toLocaleTimeString('en-IN', {hour: '2-digit', minute: '2-digit'})}` : ''}</small></div>
        <div className="dxMini">
          <span><small>Bills</small><b>{t ? num(t.invoices) : num(d?.today?.bills)}</b></span>
          <span><small>GST</small><b>{inr(t?.tax)}</b></span>
          <span><small>Yesterday</small><b>{inr(t?.yesterdaySales)}</b></span>
          <span><small>Gold sold</small><b>{grams(d?.today?.goldSoldGrams)}</b></span>
        </div>
      </>;
    }
    case 'target': return <Target sales={num(x.trend?.today?.sales ?? d?.today?.sales)} target={x.target} save={x.saveTarget} />;
    case 'rates': {
      const r: any[] = (d?.rates || []).slice(0, 4);
      const missing = (d?.rates || []).filter((y: any) => !y.setToday && y.rate != null).length;
      return <>
        <span className="dxEyebrow">SELLING RATE · PER GRAM</span>
        {!d ? <Skel w="80%" /> : !r.length ? <p className="dxSub">No purities yet.</p> :
          <div className="dxRates">{r.map(y => <div key={y.purity}><span className="dxKarat">{y.purity}</span><b>{y.rate != null ? inr(y.rate) : '—'}</b>{!y.setToday && <em>{y.rate != null ? 'old' : 'not set'}</em>}</div>)}</div>}
        <span className="dxRatesFoot">{missing ? `${missing} not updated today · tap to update` : 'Tap for all rates →'}</span>
      </>;
    }
    case 'insights': return <Insights a={x.trend} />;
    case 'sales': return <Kpi w={w} value={data.sales ? inr(data.sales.net) : wait ? null : '—'} sub={data.sales ? `${data.sales.bills} bill${data.sales.bills === 1 ? '' : 's'} ${periodName(period)}` : 'Not available for your account'} />;
    case 'collections': {
      const modes = Object.entries(data.pay?.byMode || {}).sort((a: any, b: any) => b[1] - a[1]).slice(0, 2).map(([k, v]) => `${label(k)} ${inr(v as number)}`).join(' · ');
      return <Kpi w={w} value={data.pay ? inr(data.pay.moneyIn) : wait ? null : '—'} sub={data.pay ? modes || `nothing received ${periodName(period)}` : ''} />;
    }
    case 'outstanding': return <Kpi w={w} value={d ? inr(d.dues?.customersOwe) : null} tone={num(d?.dues?.customersOwe) > 0 ? 'bad' : undefined} sub="on bills, all dates" />;
    case 'gold': {
      const t = data.gold?.totals;
      return <Kpi w={w} value={t ? grams(t.netWeight) : wait ? null : '—'} sub={t ? `${num(t.pieces)} pieces ${periodName(period)}` : ''} />;
    }
    case 'stock': {
      const rows: any[] = d?.stock || [];
      const pieces = rows.reduce((s, r) => s + num(r.pieces), 0), fine = rows.filter(r => r.metal === 'GOLD').reduce((s, r) => s + num(r.fineWeight), 0);
      return <Kpi w={w} value={d ? `${pieces} pcs` : null} sub={d ? `${grams(fine)} fine gold` : ''} />;
    }
    case 'expenses': return <Kpi w={w} value={data.exp ? inr(data.exp.total) : wait ? null : '—'} sub={data.exp ? `${(data.exp.expenses || []).length} entries ${periodName(period)}` : ''} />;
    case 'profit': {
      const t = data.profit?.totals;
      const margin = t && num(t.sale) > 0 ? (num(t.profit) / num(t.sale) * 100).toFixed(1) : null;
      return <Kpi w={w} value={t ? inr(t.profit) : wait ? null : '—'} tone={t && num(t.profit) < 0 ? 'bad' : 'good'} sub={t ? `${margin ? `${margin}% margin · ` : ''}${periodName(period)}` : ''} />;
    }
    case 'orders': return <Kpi w={w} value={d ? String(num(d.work?.orders)) : null} sub={d ? `${num(d.work?.readyToDeliver)} ready to deliver (orders + repairs)` : ''} />;
    case 'repairs': return <Kpi w={w} value={d ? String(num(d.work?.repairs)) : null} tone={num(d?.work?.overdue) > 0 ? 'warn' : undefined} sub={d ? `${num(d.work?.overdue)} overdue (orders + repairs)` : ''} />;
    case 'karigar': {
      const k: any[] = data.karigar || [];
      const open = k.reduce((s, x) => s + num(x.openJobs), 0), short = k.reduce((s, x) => s + num(x.shortfallFine), 0);
      return <Kpi w={w} value={data.karigar ? `${open} open` : wait ? null : '—'} sub={data.karigar ? `${grams(k.reduce((s, x) => s + num(x.metalWithKarigarFine), 0))} fine with karigars${short > 0 ? ` · ${grams(short)} short` : ''}` : ''} />;
    }
    case 'advances': return <Kpi w={w} value={d ? inr(d.dues?.advancesHeld) : null} sub="to use on bills or refund" />;
    case 'suppliers': return <Kpi w={w} value={d ? (d.dues?.weOweSuppliers !== undefined ? inr(d.dues.weOweSuppliers) : '—') : null} sub="unpaid purchases" />;
    case 'trend': {
      const a = x.trend;
      const series: TrendPoint[] = (a?.series || []).map((p: any) => ({label: p.label, value: num(p.sales), secondary: `${p.invoices} bills`}));
      return <>
        <div className="dxTrendHead">
          <div><span className="dxEyebrow">SALES TREND</span><h3>{a ? inr(a.totals?.sales) : '…'} <small>in range</small></h3></div>
          <div className="dxTrendTools">
            <DateRangePicker from={x.tr.from} to={x.tr.to} maxDate={iso(new Date())} onChange={(f, t) => x.setTr({...x.tr, from: f, to: t})} label="From / To date" />
            <Segmented label="Group by" value={x.tr.gran} onChange={g => x.setTr({...x.tr, gran: g})} options={[{value: 'DAY', label: 'Daily'}, {value: 'WEEK', label: 'Weekly'}, {value: 'MONTH', label: 'Monthly'}]} />
            <Segmented label="Chart" value={x.tr.mode} onChange={m => x.setTr({...x.tr, mode: m})} options={[{value: 'line', label: 'Line'}, {value: 'bar', label: 'Bars'}]} />
          </div>
        </div>
        {x.trendErr && <div className="kNote bad" role="alert">{x.trendErr}</div>}
        <div className="dxRangeKpis"><div><span>Bills</span><b>{num(a?.totals?.invoices)}</b></div><div><span>Average bill</span><b>{inr(a?.totals?.average)}</b></div><div><span>GST collected</span><b>{inr(a?.totals?.tax)}</b></div></div>
        {a ? <TrendChart data={series} mode={x.tr.mode} height={260} /> : <Skel w="100%" />}
      </>;
    }
    case 'recent': return <><div className="dxKpiTop"><Icon name={w.icon} /><span className="dxKpiLabel">{w.title}</span>
        <button type="button" className="dxLink" onClick={() => go('Billing', {type: 'BILL', id: 0})}>All bills →</button></div>
      {!d ? <Skel w="100%" /> : !(d.recentBills || []).length ? <p className="dxSub">No bills in the last 30 days.</p> :
        <ul className="dxList">{d.recentBills.map((b: any) => <li key={b.id}><button type="button" onClick={() => go('Billing', {type: 'BILL', id: b.id})}>
          <b>{b.invoiceNo}</b><span>{b.customer?.name} · {b.date}</span><strong>{money(b.total)}</strong><Pill value={b.label} /></button></li>)}</ul>}</>;
    case 'celebrating': return <><div className="dxKpiTop"><Icon name={w.icon} /><span className="dxKpiLabel">{w.title}</span></div>
      {!d ? <Skel /> : !(d.celebrating || []).length ? <p className="dxSub">No birthdays or anniversaries today.</p> :
        <ul className="dxList">{d.celebrating.map((c: any) => <li key={c.id}><button type="button" onClick={() => go('Customers', {type: 'CUSTOMER', id: c.id})}><b>{c.name}</b><span>{c.occasion}{c.phone ? ` · ${c.phone}` : ''}</span></button></li>)}</ul>}</>;
    default: return null;
  }
}

/** Daily target ring. The target is a personal goal, kept in this browser. */
function Target({sales, target, save}: {sales: number; target: number; save: (n: number) => void}) {
  const [edit, setEdit] = useState(false);
  const [draft, setDraft] = useState('');
  const pct = target > 0 ? Math.min(1, sales / target) : 0;
  const C = 2 * Math.PI * 52;
  return <>
    <span className="dxEyebrow">DAILY TARGET</span>
    <div className="dxTarget">
      <svg viewBox="0 0 120 120" className="dxRing" role="img" aria-label={`${Math.round(pct * 100)} percent of the daily target`}>
        <circle cx="60" cy="60" r="52" className="trk" /><circle cx="60" cy="60" r="52" className="bar" style={{strokeDasharray: C, strokeDashoffset: C * (1 - pct)}} />
        <text x="60" y="58" textAnchor="middle" className="pc">{Math.round(pct * 100)}%</text><text x="60" y="76" textAnchor="middle" className="of">of target</text>
      </svg>
      <div>
        {edit ? <form onSubmit={e => { e.preventDefault(); const n = Math.round(Number(draft.replace(/[^0-9.]/g, ''))); if (n > 0) save(n); setEdit(false); }} className="dxTargetForm">
            <input autoFocus inputMode="numeric" aria-label="Daily target in rupees" value={draft} onChange={e => setDraft(e.target.value)} /><button className="dxBtn primary">Save</button></form>
          : <><small>Target</small><b className="dxTargetValue">{inr(target)}</b><small>{pct >= 1 ? 'Target reached' : `${inr(Math.max(0, target - sales))} to go`}</small>
            <button type="button" className="dxLink light" onClick={() => { setDraft(String(target)); setEdit(true); }}>Edit target</button></>}
      </div>
    </div>
  </>;
}

/** Facts worked out from the sales trend series (real bills only): best period, average per day, growth, busiest weekday. */
function Insights({a}: {a: any}) {
  const s: any[] = a?.series || [];
  if (!a) return <Skel w="100%" />;
  const best = s.reduce((m: any, p: any) => (!m || num(p.sales) > num(m.sales) ? p : m), null);
  const half = Math.floor(s.length / 2);
  const first = s.slice(0, half).reduce((t, p) => t + num(p.sales), 0), second = s.slice(half).reduce((t, p) => t + num(p.sales), 0);
  const growth = first > 0 ? ((second - first) / first) * 100 : null;
  const days = Math.max(1, Math.round((new Date(a.to).getTime() - new Date(a.from).getTime()) / 86400000) + 1);
  const byDay = [0, 0, 0, 0, 0, 0, 0];
  if (a.granularity === 'DAY') s.forEach(p => { const wd = (new Date(p.date).getDay() + 6) % 7; byDay[wd] += num(p.sales); });
  const max = Math.max(...byDay);
  return <div className="dxIns">
    <div className="i-best"><span>BEST {a.granularity === 'DAY' ? 'DAY' : a.granularity === 'WEEK' ? 'WEEK' : 'MONTH'}</span><b>{best && num(best.sales) > 0 ? inr(best.sales) : '—'}</b><small>{best && num(best.sales) > 0 ? best.label : 'no sales in range'}</small></div>
    <div className="i-avg"><span>AVERAGE / DAY</span><b>{inr(num(a.totals?.sales) / days)}</b><small>over {days} days</small></div>
    <div className="i-grow"><span>GROWTH</span><b>{growth === null ? '—' : `${growth >= 0 ? '▲' : '▼'} ${Math.abs(growth).toFixed(0)}%`}</b><small>2nd half vs 1st half</small></div>
    <div className="i-busy"><span>BUSIEST WEEKDAY</span><b>{max > 0 ? WEEKDAYS[byDay.indexOf(max)] : '—'}</b><small>by total sales</small></div>
    {a.granularity === 'DAY' && <div className="i-heat"><span>WEEKDAY HEAT</span><div>{WEEKDAYS.map((w, i) => <i key={w} title={`${w}: ${inr(byDay[i])}`} style={{opacity: max > 0 ? 0.15 + 0.85 * (byDay[i] / max) : 0.15}}><em>{w}</em></i>)}</div></div>}
  </div>;
}

/* ------------------------------------------------------------------------------------------------ details */

function Detail({id, onClose, period, data, go}: {id: string | null; onClose: () => void; period: Period; data: Data; go: PageProps['go']}) {
  const w = WIDGETS.find(x => x.id === id);
  const nav = (tab: string, focus?: {type: string; id: number}) => { onClose(); go(tab, focus); };
  return <Sheet open={!!w} wide onClose={onClose} kicker={period.key === 'today' ? 'TODAY' : periodName(period).toUpperCase()} title={w?.title || ''}>
    {w && <><p className="dxDef">{w.def}</p><DetailBody id={w.id} period={period} data={data} nav={nav} /></>}
  </Sheet>;
}

function Rows({head, rows, empty, onRow}: {head: string[]; rows: ReactNode[][]; empty: string; onRow?: (i: number) => void}) {
  if (!rows.length) return <StateBox empty emptyText={empty} />;
  return <div className="tableWrap"><table><thead><tr>{head.map(h => <th key={h}>{h}</th>)}</tr></thead>
    <tbody>{rows.map((r, i) => <tr key={i} className={onRow ? 'kClick' : ''} onClick={onRow ? () => onRow(i) : undefined} tabIndex={onRow ? 0 : undefined} onKeyDown={onRow ? e => { if (e.key === 'Enter') onRow(i); } : undefined}>{r.map((c, j) => <td key={j}>{c}</td>)}</tr>)}</tbody></table></div>;
}

function DetailBody({id, period, data, nav}: {id: string; period: Period; data: Data; nav: (tab: string, f?: {type: string; id: number}) => void}) {
  const perms = usePermissions();
  const {from, to} = period;
  const d: any = data.dash;
  const loaders: Record<string, (() => Promise<any>) | null> = {
    sales: perms.canOpen('Billing') ? () => salesList({from, to, limit: 200}) : null,
    collections: () => moneyPayments({from, to}),
    outstanding: () => customers(undefined, 1000),
    gold: () => runReport('sales-purity', from, to),
    stock: perms.canOpen('Reports') ? () => runReport('stock-ageing') : null,
    expenses: () => expenses({from, to}),
    profit: () => runReport('profit-category', from, to),
    orders: () => orders(),
    repairs: () => repairs(),
    karigar: () => karigarSummary(),
    advances: () => advances(true),
    suppliers: () => runReport('supplier-dues'),
    rates: () => goldRateBoard()
  };
  const loader = loaders[id];
  const q = useData(() => loader!(), [id, from, to], !!loader);
  const x: any = q.data;
  const box = (children: ReactNode, emptyIf = false, emptyText = 'Nothing to show') =>
    <StateBox loading={q.loading} error={q.error} onRetry={q.reload} empty={!x || emptyIf} emptyText={emptyText}>{children}</StateBox>;
  switch (id) {
    case 'sales': {
      const t = data.salesRep?.totals;
      const bills: any[] = (x || []).filter((b: any) => b.status !== 'DRAFT');
      return <>
        {t ? <KV rows={[['Bills', num(t.bills)], ['Before discount', money(t.subtotal)], ['Discount', money(t.discount)], ['Taxable', money(t.taxable)], ['GST', money(t.gst)],
          ['Bill total', money(t.total)], ['Returned', money(t.returned)], ['Net sales', <b>{money(t.net)}</b>], ['Still due', money(t.due)]]} />
          : data.sales && <KV rows={[['Bills', data.sales.bills], ['Net sales', money(data.sales.net)]]} />}
        {data.pay && <><h3 className="kSection">How it was paid (all money received in the period)</h3>
          <KV rows={Object.entries(data.pay.byMode || {}).map(([k, v]) => [label(k), money(v)] as [string, ReactNode])} /></>}
        {loader && <><h3 className="kSection">Bills</h3>{box(<Rows head={['Invoice', 'Date', 'Customer', 'Total', 'Due', 'Status']} empty="No bills in this period"
          rows={bills.map(b => [<b>{b.invoiceNo}</b>, b.date, b.customer?.name, money(b.total), num(b.balance) ? money(b.balance) : '—', <Pill value={b.label} />])}
          onRow={i => nav('Billing', {type: 'BILL', id: bills[i].id})} />)}</>}
        <div className="kActions"><button type="button" className="primary" onClick={() => nav('Billing', {type: 'NEW_BILL', id: 0})}>New bill</button>
          {perms.canOpen('Reports') && <button type="button" className="secondary" onClick={() => nav('Reports')}>Sales reports</button>}</div>
      </>;
    }
    case 'collections': return box(<>
      <KV rows={[['Money in', <b>{money(x?.moneyIn)}</b>], ['Money out', money(x?.moneyOut)], ['Net', money(x?.net)], ...Object.entries(x?.byMode || {}).map(([k, v]) => [`${label(k)} (net)`, money(v)] as [string, ReactNode])]} />
      <h3 className="kSection">Payments</h3>
      <Rows head={['No.', 'Date', 'For', 'Mode', 'Amount']} empty="No payments in this period"
        rows={(x?.payments || []).slice(0, 100).map((p: any) => [<b>{p.paymentNo}</b>, p.date, p.customer?.name || p.supplier || p.karigar || '—', label(p.mode), `${p.direction < 0 ? '− ' : ''}${money(p.amount)}`])} />
      <div className="kActions"><button type="button" className="secondary" onClick={() => nav('Money')}>Open Money</button></div>
    </>);
    case 'outstanding': {
      const owing = ((x || []) as any[]).filter(c => num(c.owes) > 0).sort((a, b) => num(b.owes) - num(a.owes));
      return box(<>
        <KV rows={[['Customers who owe', owing.length], ['Total due', <b>{money(owing.reduce((s, c) => s + num(c.owes), 0))}</b>]]} />
        <Rows head={['Customer', 'Phone', 'Owes', 'Advance', 'Last visit']} empty="Nobody owes you money."
          rows={owing.map(c => [<b>{c.name}</b>, c.phone || '—', <b className="kBad">{money(c.owes)}</b>, num(c.advance) ? money(c.advance) : '—', c.lastVisit || '—'])}
          onRow={i => nav('Customers', {type: 'CUSTOMER', id: owing[i].id})} />
        <p className="kMuted">Open a customer to collect a payment or send a reminder.</p>
      </>, !owing.length, 'Nobody owes you money.');
    }
    case 'gold': return box(<>
      <KV rows={[['Pieces', num(x?.totals?.pieces)], ['Gross weight', grams(x?.totals?.grossWeight)], ['Net weight', <b>{grams(x?.totals?.netWeight)}</b>], ['Value incl. GST', money(x?.totals?.total)]]} />
      <Rows head={['Purity', 'Pieces', 'Gross', 'Net', 'Making', 'Value']} empty="Nothing sold in this period"
        rows={(x?.rows || []).map((r: any) => [<b>{r.name}</b>, num(r.pieces), grams(r.grossWeight), grams(r.netWeight), money(r.making), money(r.total)])} />
    </>);
    case 'stock': {
      const rows: any[] = d?.stock || [];
      return <>
        <Rows head={['Purity', 'Pieces', 'Gross', 'Net', 'Fine']} empty="No stock yet" rows={rows.map(r => [<b>{r.purity}</b>, r.pieces, grams(r.grossWeight), grams(r.netWeight), grams(r.fineWeight)])} />
        {loader && <><h3 className="kSection">How long pieces have been in stock</h3>{box(<Rows head={['Age', 'Pieces', 'Net wt', '% of pieces']} empty="No stock"
          rows={(x?.rows || []).map((r: any) => [<b>{r.bucket}</b>, num(r.pieces), grams(r.netWeight), `${num(r.share).toFixed(1)} %`])} />)}</>}
        <div className="kActions"><button type="button" className="primary" onClick={() => nav('Stock')}>Open stock</button>
          {perms.canOpen('Reports') && <button type="button" className="secondary" onClick={() => nav('Reports')}>Stock value & dead stock reports</button>}</div>
      </>;
    }
    case 'expenses': return box(<>
      <KV rows={[['Total', <b>{money(x?.total)}</b>], ...Object.entries(x?.byCategory || {}).map(([k, v]) => [k, money(v)] as [string, ReactNode])]} />
      <h3 className="kSection">Entries</h3>
      <Rows head={['Date', 'Expense', 'Category', 'Mode', 'Amount']} empty="No expenses in this period"
        rows={(x?.expenses || []).map((e: any) => [e.date, <b>{e.title}</b>, e.category, label(e.mode), money(e.amount)])} />
      <div className="kActions"><button type="button" className="primary" onClick={() => nav('Money', {type: 'NEW_EXPENSE', id: 0})}>Add expense</button>
        <button type="button" className="secondary" onClick={() => nav('Money', {type: 'EXPENSES', id: 0})}>All expenses</button></div>
    </>);
    case 'profit': {
      const t = data.profit?.totals;
      return <>
        {t && <KV rows={[['Sale value (excl. GST)', money(t.sale)], ['Cost of pieces sold', money(t.cost)], ['Profit before expenses', <b>{money(t.profit)}</b>],
          ...(data.exp ? [['Expenses in the period', money(data.exp.total)] as [string, ReactNode], ['After expenses', <b>{money(num(t.profit) - num(data.exp.total))}</b>] as [string, ReactNode]] : [])]} />}
        <h3 className="kSection">By category</h3>
        {box(<Rows head={['Category', 'Pieces', 'Sale value', 'Cost', 'Profit']} empty="Nothing sold in this period"
          rows={(x?.rows || []).map((r: any) => [<b>{r.name}</b>, num(r.pieces), money(r.sale), money(r.cost), money(r.profit)])} />)}
        <p className="kMuted">Pieces without a cost price count as zero cost - add cost prices on Stock for an exact profit.</p>
      </>;
    }
    case 'orders': {
      const list = ((x || []) as any[]).filter(o => ['ORDERED', 'IN_PROGRESS', 'READY'].includes(o.status)).sort((a, b) => Number(b.overdue) - Number(a.overdue) || String(a.deliveryDate || '9').localeCompare(String(b.deliveryDate || '9')));
      return box(<Rows head={['Order', 'Customer', 'Item', 'Delivery', 'Advance', 'Status']} empty="No open orders"
        rows={list.map(o => [<b>{o.orderNo}</b>, o.customer?.name, o.description, <>{o.deliveryDate || '—'}{o.overdue && <> <Pill value="OVERDUE" /></>}</>, money(o.advancePaid), <Pill value={o.status} />])}
        onRow={i => nav('Services', {type: 'ORDER', id: list[i].id})} />);
    }
    case 'repairs': {
      const list = ((x || []) as any[]).filter(r => ['RECEIVED', 'IN_REPAIR', 'READY'].includes(r.status)).sort((a, b) => Number(b.overdue) - Number(a.overdue));
      return box(<Rows head={['Repair', 'Customer', 'Item', 'Expected', 'Due', 'Status']} empty="No open repairs"
        rows={list.map(r => [<b>{r.orderNo}</b>, r.customer?.name, r.itemDescription, <>{r.expectedDate || '—'}{r.overdue && <> <Pill value="OVERDUE" /></>}</>, num(r.balance) ? money(r.balance) : '—', <Pill value={r.status} />])}
        onRow={i => nav('Services', {type: 'REPAIR', id: list[i].id})} />);
    }
    case 'karigar': return box(<Rows head={['Karigar', 'Open jobs', 'Fine with them', 'Shortfall', 'Labour due']} empty="No karigars yet"
      rows={((x || []) as any[]).map(k => [<b>{k.name}</b>, k.openJobs, grams(k.metalWithKarigarFine), num(k.shortfallFine) ? <b className="kBad">{grams(k.shortfallFine)}</b> : '—', money(k.labourDue)])}
      onRow={() => nav('Services')} />);
    case 'advances': {
      const list = (x || []) as any[];
      return box(<Rows head={['No.', 'Customer', 'From', 'Amount', 'Left']} empty="No advances held"
        rows={list.map(a => [<b>{a.advanceNo}</b>, a.customer?.name, label(a.source), money(a.amount), <b>{money(a.balance)}</b>])}
        onRow={i => nav('Customers', {type: 'CUSTOMER', id: list[i].customer?.id})} />);
    }
    case 'suppliers': return box(<>
      <Rows head={(x?.columns || []).map((c: any) => c.label)} empty="You owe no supplier."
        rows={(x?.rows || []).map((r: any) => (x.columns || []).map((c: any) => c.type === 'money' ? money(r[c.key]) : String(r[c.key] ?? '—')))} />
      <div className="kActions"><button type="button" className="secondary" onClick={() => nav('Purchases')}>Open purchases</button></div>
    </>);
    case 'rates': return box(<>
      <Rows head={['Purity', 'Rate / g', 'Change', 'Set on']} empty="No purities yet"
        rows={(x?.rates || []).map((r: any) => [<b>{r.purity}</b>, r.ratePerGram != null ? money(r.ratePerGram) : '—', r.change != null && num(r.change) !== 0 ? `${num(r.change) > 0 ? '▲' : '▼'} ${money(Math.abs(num(r.change)))}` : '—', r.setToday ? 'today' : r.rateDate || 'never'])} />
      {perms.canManage && <div className="kActions"><button type="button" className="primary" onClick={() => nav('Gold Rates')}>Update rates</button></div>}
    </>);
    default: return null;
  }
}

