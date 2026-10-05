import {useCallback, useEffect, useMemo, useState} from 'react';
import type {CSSProperties} from 'react';
import {dashboard, getFriendlyApiError, headerGoldRates, salesAnalytics} from '../api';
import type {Granularity} from '../api';
import {User} from '../shared/types';
import {Empty, NAV_PATHS} from '../shared/ui';
import {DateRangePicker, DeltaChip, Segmented, TrendChart, defaultPresets, fromIso, inr, toIso} from '../shared/widgets';
import type {TrendPoint} from '../shared/widgets';
import './Overview.css';

type Analytics = {
  platform?: boolean; granularity: Granularity; from: string; to: string;
  today: {date: string; sales: number; invoices: number; tax: number; yesterdaySales: number; changePercent: number};
  totals: {sales: number; invoices: number; tax: number; average: number};
  series: {date: string; label: string; sales: number; invoices: number; tax: number}[];
};

const goTo = (tab: string, prefill?: Record<string, unknown>) => window.dispatchEvent(new CustomEvent('j360-navigate', {detail: {tab, prefill}}));
const Ico = ({name, size = 20}: {name: string; size?: number}) => (
  <svg viewBox="0 0 24 24" width={size} height={size} fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d={NAV_PATHS[name] || 'M12 3l7 9-7 9-7-9z'} /></svg>
);
const WEEKDAYS = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'];
const TARGET_KEY = 'j360_daily_target';

export default function Overview({user, ready}: {user: User; ready: boolean}) {
  const [d, setD] = useState<any>(null);
  const [legacyFailed, setLegacyFailed] = useState(false);
  const initial = useMemo(() => defaultPresets().find(p => p.key === '30d')!.range(), []);
  const [from, setFrom] = useState(initial[0]);
  const [to, setTo] = useState(initial[1]);
  const [granularity, setGranularity] = useState<Granularity>('DAY');
  const [mode, setMode] = useState<'line' | 'bar'>('line');
  const [analytics, setAnalytics] = useState<Analytics | null>(null);
  const [analyticsError, setAnalyticsError] = useState('');
  const [loading, setLoading] = useState(false);
  const [updatedAt, setUpdatedAt] = useState<Date | null>(null);
  const [rates, setRates] = useState<any[]>([]);
  const [target, setTarget] = useState<number>(() => { try { return Number(localStorage.getItem(TARGET_KEY)) || 100000; } catch { return 100000; } });
  const [editTarget, setEditTarget] = useState(false);
  const [draft, setDraft] = useState('');

  const isAdmin = user.role === 'APP_ADMIN' || user.role === 'COMPANY_ADMIN';
  const canUsePanel = user.role !== 'APP_ADMIN' || ready;

  useEffect(() => {
    if (!canUsePanel) return;
    dashboard().then(r => { setD(r.data); setLegacyFailed(false); }).catch(() => { setD(null); setLegacyFailed(true); });
    headerGoldRates().then(r => {
      const list: any[] = Array.isArray(r.data?.marketRates) && r.data.marketRates.length ? r.data.marketRates : (Array.isArray(r.data?.rates) ? r.data.rates : []);
      const uniq = new Map<string, any>();
      list.filter(x => x.active !== false).forEach(x => uniq.set(String(x.karat || x.purity || '').replace(/[^0-9]/g, ''), x));
      setRates([...uniq.values()].sort((a, b) => Number(String(b.karat || b.purity).replace(/[^0-9]/g, '')) - Number(String(a.karat || a.purity).replace(/[^0-9]/g, ''))).slice(0, 3));
    }).catch(() => setRates([]));
  }, [ready, canUsePanel]);

  const loadAnalytics = useCallback(async () => {
    if (!canUsePanel) return;
    setLoading(true);
    try {
      const r = await salesAnalytics({from, to, granularity});
      setAnalytics(r.data as Analytics); setAnalyticsError(''); setUpdatedAt(new Date());
    } catch (e) { setAnalyticsError(getFriendlyApiError(e, 'Unable to load sales analytics.')); }
    finally { setLoading(false); }
  }, [from, to, granularity, canUsePanel]);
  useEffect(() => { void loadAnalytics(); }, [loadAnalytics]);
  useEffect(() => {
    if (!canUsePanel) return;
    const id = window.setInterval(() => { void loadAnalytics(); }, 60000);
    return () => window.clearInterval(id);
  }, [loadAnalytics, canUsePanel]);

  const points: TrendPoint[] = useMemo(() => (analytics?.series || []).map(p => ({label: p.label, value: Number(p.sales || 0), secondary: `${p.invoices} invoice${p.invoices === 1 ? '' : 's'} · tax ${inr(p.tax)}`})), [analytics]);

  /* insights derived from the series already loaded (no extra API) */
  const insights = useMemo(() => {
    const s = analytics?.series || [];
    if (!s.length) return null;
    const best = s.reduce((m, x) => (x.sales > m.sales ? x : m), s[0]);
    const half = Math.floor(s.length / 2);
    const a = s.slice(0, half).reduce((t, x) => t + x.sales, 0);
    const b = s.slice(half).reduce((t, x) => t + x.sales, 0);
    const growth = a > 0 ? ((b - a) / a) * 100 : null;
    const byDay = Array(7).fill(0) as number[];
    if (analytics?.granularity === 'DAY') s.forEach(x => { const wd = (fromIso(x.date).getDay() + 6) % 7; byDay[wd] += x.sales; });
    const max = Math.max(...byDay, 0);
    const days = Math.max(1, Math.round((fromIso(analytics!.to).getTime() - fromIso(analytics!.from).getTime()) / 86400000) + 1);
    return {best, growth, byDay, max, busiest: max > 0 ? WEEKDAYS[byDay.indexOf(max)] : '—', perDay: (analytics?.totals.sales || 0) / days, days};
  }, [analytics]);

  if (user.role === 'APP_ADMIN' && !ready) {
    return <div className="heroPanel"><span className="eyebrow">PLATFORM MODE</span><h2>Choose an operating company and branch</h2><p>APP_ADMIN can administer the platform without a tenant context. Select a company and branch above to test company workflows.</p></div>;
  }

  const today = analytics && !analytics.platform ? analytics.today : null;
  const totals = analytics && !analytics.platform ? analytics.totals : null;
  const pct = target > 0 ? Math.min(1, (today?.sales || 0) / target) : 0;
  const CIRC = 2 * Math.PI * 52;
  const saveTarget = () => {
    const n = Math.round(Number(draft.replace(/[^0-9.]/g, '')));
    if (n > 0) { setTarget(n); try { localStorage.setItem(TARGET_KEY, String(n)); } catch { /* ignore */ } }
    setEditTarget(false);
  };

  const kpis: {cls: string; icon: string; label: string; value: string; note: string}[] = d && !d.platform ? [
    {cls: 'k-gold', icon: 'Gold & Rates', label: 'Gold sold', value: `${Number(d.goldSold || 0).toFixed(3)} g`, note: 'net weight billed'},
    {cls: 'k-silver', icon: 'Inventory', label: 'Silver sold', value: `${Number(d.silverSold || 0).toFixed(3)} g`, note: 'net weight billed'},
    {cls: 'k-items', icon: 'Jewellery', label: 'Items sold', value: String(d.itemsSold || 0), note: 'pieces on invoices'},
    {cls: 'k-cust', icon: 'Customers', label: 'New customers', value: String(d.newCustomers || 0), note: 'added recently'},
    {cls: 'k-due', icon: 'Payments', label: 'Outstanding', value: inr(d.outstanding), note: 'still to collect'},
    {cls: 'k-old', icon: 'Old Gold', label: 'Old gold bought', value: inr(d.oldGoldPurchased), note: 'exchange value'}
  ] : [];

  const quick = [
    {cls: 'q-bill', icon: 'Billing', label: 'New bill', tab: 'Billing'},
    {cls: 'q-prod', icon: 'Jewellery', label: 'Add product', tab: 'Jewellery'},
    {cls: 'q-cust', icon: 'Customers', label: 'Add customer', tab: 'Customers'},
    {cls: 'q-rate', icon: 'Gold & Rates', label: 'Gold rates', tab: 'Gold & Rates'},
    {cls: 'q-rep', icon: 'Reports', label: 'Reports', tab: 'Reports'},
    ...(isAdmin ? [{cls: 'q-user', icon: 'Users', label: 'Create user', tab: 'Users'}] : [])
  ];

  return <div className="ov">
    <div className="ovTop">
      <section className="ovTile ovToday" aria-label="Today's sales summary">
        <span className="ovEyebrow">TODAY'S SALES</span>
        <strong className="ovBig" aria-live="polite">{inr(today?.sales ?? 0)}</strong>
        <div className="ovRow">{today && <DeltaChip value={Number(today.changePercent || 0)} />}<small>{user.branchName || 'All branches'}</small></div>
        <div className="ovMini"><span><small>Invoices</small><b>{today?.invoices ?? 0}</b></span><span><small>Tax</small><b>{inr(today?.tax ?? 0)}</b></span><span><small>Yesterday</small><b>{inr(today?.yesterdaySales ?? 0)}</b></span></div>
        <small className="ovUpdated">{loading ? 'Refreshing…' : updatedAt ? `Updated ${updatedAt.toLocaleTimeString('en-IN', {hour: '2-digit', minute: '2-digit'})}` : ''}</small>
      </section>

      <section className="ovTile ovTarget" aria-label="Daily sales target">
        <span className="ovEyebrow">DAILY TARGET</span>
        <div className="ovRingWrap">
          <svg viewBox="0 0 120 120" className="ovRing" role="img" aria-label={`${Math.round(pct * 100)} percent of the daily target`}>
            <circle cx="60" cy="60" r="52" className="trk" /><circle cx="60" cy="60" r="52" className="bar" style={{strokeDasharray: CIRC, strokeDashoffset: CIRC * (1 - pct)} as CSSProperties} />
          </svg>
          <div className="ovRingText"><b>{Math.round(pct * 100)}%</b><small>of target</small></div>
        </div>
        <div className="ovTargetInfo">
          {editTarget
            ? <form onSubmit={e => { e.preventDefault(); saveTarget(); }} className="ovTargetForm"><input autoFocus inputMode="numeric" aria-label="Daily target in rupees" value={draft} onChange={e => setDraft(e.target.value)} placeholder="e.g. 150000" /><button type="submit">Save</button></form>
            : <><small>Target</small><b>{inr(target)}</b><small>{pct >= 1 ? 'Target reached 🎉' : `${inr(Math.max(0, target - (today?.sales || 0)))} to go`}</small><button type="button" className="ovLink" onClick={() => { setDraft(String(target)); setEditTarget(true); }}>Edit target</button></>}
        </div>
      </section>

      <section className="ovTile ovRates" aria-label="Today's gold rates">
        <span className="ovEyebrow">GOLD RATE · PER GRAM</span>
        <div className="ovRateList">
          {rates.length ? rates.map((x, i) => <div key={i}><b>{x.karat || x.purity}</b><span>₹{Number(x.ratePerGram || 0).toLocaleString('en-IN', {maximumFractionDigits: 2})}</span></div>) : <small>No market rate available.</small>}
        </div>
        <button type="button" className="ovLink" onClick={() => goTo('Gold & Rates')}>Manage rates →</button>
      </section>
    </div>

    {legacyFailed && <div className="settingsNotice">Operational numbers are temporarily unavailable.</div>}
    {kpis.length > 0 && <div className="ovKpis">{kpis.map((k, i) => (
      <div className={`ovKpi ${k.cls}`} key={k.label} style={{'--i': i} as CSSProperties}>
        <span className="ovKpiIcon"><Ico name={k.icon} /></span>
        <div><small>{k.label}</small><strong>{k.value}</strong><em>{k.note}</em></div>
        <i className="ovSpark" aria-hidden="true"><u /><u /><u /><u /><u /></i>
      </div>
    ))}</div>}

    <div className="ovMid">
      <section className="ovTile ovTrend" aria-label="Sales trend">
        <div className="ovTrendHead">
          <div><span className="ovEyebrow">SALES TREND</span><h2>{totals ? inr(totals.sales) : '₹0'} <small>in range</small></h2></div>
          <div className="trendControls">
            <DateRangePicker from={from} to={to} maxDate={toIso(new Date())} onChange={(a, b) => { setFrom(a); setTo(b); }} label="From / To date" />
            <Segmented<Granularity> label="Group by" value={granularity} onChange={setGranularity} options={[{value: 'DAY', label: 'Daily'}, {value: 'WEEK', label: 'Weekly'}, {value: 'MONTH', label: 'Monthly'}]} />
            <Segmented<'line' | 'bar'> label="Chart type" value={mode} onChange={setMode} options={[{value: 'line', label: 'Line'}, {value: 'bar', label: 'Bars'}]} />
          </div>
        </div>
        {analyticsError && <div className="settingsError" role="alert">{analyticsError}</div>}
        <div className="rangeKpis"><div><span>Invoices</span><b>{totals?.invoices ?? 0}</b></div><div><span>Average bill</span><b>{inr(totals?.average ?? 0)}</b></div><div><span>Tax collected</span><b>{inr(totals?.tax ?? 0)}</b></div></div>
        <TrendChart data={points} mode={mode} height={210} ariaLabel={`${granularity.toLowerCase()} sales trend`} />
      </section>

      <div className="ovSide" role="complementary" aria-label="Insights">
        <div className="ovTile ovIns i-best"><span className="ovEyebrow">BEST DAY</span><b>{insights ? inr(insights.best.sales) : '—'}</b><small>{insights ? insights.best.label : 'no sales yet'}</small></div>
        <div className="ovTile ovIns i-avg"><span className="ovEyebrow">AVERAGE / DAY</span><b>{insights ? inr(insights.perDay) : '—'}</b><small>over {insights?.days ?? 0} days</small></div>
        <div className="ovTile ovIns i-grow"><span className="ovEyebrow">GROWTH</span><b>{insights?.growth == null ? '—' : `${insights.growth >= 0 ? '▲' : '▼'} ${Math.abs(insights.growth).toFixed(1)}%`}</b><small>2nd half vs 1st half</small></div>
        <div className="ovTile ovIns i-busy"><span className="ovEyebrow">BUSIEST WEEKDAY</span><b>{insights?.busiest ?? '—'}</b><small>by total sales</small></div>
        <div className="ovTile ovWeek" aria-label="Sales by weekday">
          <span className="ovEyebrow">WEEKDAY HEAT</span>
          <div className="ovHeat">{WEEKDAYS.map((w, i) => { const v = insights?.byDay[i] || 0; const k = insights && insights.max > 0 ? v / insights.max : 0; return <span key={w} title={`${w}: ${inr(v)}`} style={{'--k': k.toFixed(2)} as CSSProperties}><i /><small>{w}</small></span>; })}</div>
        </div>
      </div>
    </div>

    <div className="ovBottom">
      <section className="ovTile ovQuick" aria-label="Quick actions">
        <span className="ovEyebrow">QUICK ACTIONS</span>
        <div className="ovQuickGrid">{quick.map(q => <button key={q.tab} type="button" className={`ovQ ${q.cls}`} onClick={() => goTo(q.tab, q.tab === 'Users' ? {focusCreate: true} : undefined)}><Ico name={q.icon} size={22} /><b>{q.label}</b></button>)}</div>
      </section>
      <section className="ovTile ovActivity" aria-label="Recent activity">
        <span className="ovEyebrow">RECENT ACTIVITY</span>
        {d && !d.platform && d.recent?.length
          ? <ol className="ovTimeline">{d.recent.slice(0, 8).map((x: any) => <li key={x.id} className={`m-${String(x.module || '').toLowerCase().replace(/[^a-z]+/g, '')}`}><i aria-hidden="true" /><div><b>{x.title}</b><small>{x.module} · {x.date}</small></div><span>{inr(x.amount)}</span></li>)}</ol>
          : <Empty text="No recent activity yet." />}
      </section>
    </div>
  </div>;
}
