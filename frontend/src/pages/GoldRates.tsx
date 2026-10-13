import {useEffect, useMemo, useState} from 'react';
import {goldRateBoard, goldRateHistory, goldRateMarket, masters, saveGoldRates} from '../api';
import {usePermissions} from '../shared/permissions';
import {confirmDialog} from '../shared/Notify';
import {Table} from '../shared/ui';
import {StateBox, Tabs, errText, money, num, useData} from '../shared/kit';
import MasterEditor from '../shared/MasterEditor';
import type {PageProps} from './Dashboard';

/* ============================================================================================
   Gold rates: the shop's own selling rate per purity for today. Only a person changes them - the market feed is shown
   as a reference that can be copied in. Bills copy the rate into every line, so changing a rate never changes an old bill.
   ============================================================================================ */

export default function GoldRatesModule({ready, onNotice}: PageProps) {
  const perms = usePermissions();
  const [tab, setTab] = useState<'today' | 'history' | 'purities'>('today');
  const board = useData(() => goldRateBoard(), [], ready);
  const market = useData(() => goldRateMarket(), [], ready);
  const m = useData(() => masters(), [], ready && tab === 'purities');
  const [draft, setDraft] = useState<Record<number, string>>({});
  const [busy, setBusy] = useState(false);
  const [impact, setImpact] = useState<any>(null);
  const b: any = board.data;
  useEffect(() => { setDraft({}); }, [b]);
  if (!ready) return <div className="panel"><StateBox empty emptyText="Choose a company and branch to see its rates." /></div>;
  const rows: any[] = b?.rates || [];
  const md: any = market.data || {};
  const suggestions: Record<number, number> = Object.fromEntries((md.suggestions || []).map((s: any) => [s.purityId, num(s.ratePerGram)]));
  const changed = rows.filter(r => draft[r.purityId] !== undefined && draft[r.purityId] !== '' && num(draft[r.purityId]) !== num(r.ratePerGram));
  const save = async (all: boolean) => {
    const list = (all ? rows.filter(r => r.ratePerGram != null || draft[r.purityId]) : changed)
      .map(r => {
        const typed = draft[r.purityId];
        const value = num(typed ?? r.ratePerGram);
        return {purityId: r.purityId, ratePerGram: value, source: typed !== undefined && suggestions[r.purityId] === value ? 'MARKET' : 'MANUAL'};
      })
      .filter(r => r.ratePerGram > 0);
    if (!list.length) { onNotice('Enter at least one rate.'); return; }
    const big = list.filter(x => { const old = num(rows.find(r => r.purityId === x.purityId)?.ratePerGram); return old > 0 && Math.abs(x.ratePerGram - old) / old > 0.05; });
    if (big.length && !(await confirmDialog({title: 'Large change', message: `${big.length} rate${big.length === 1 ? '' : 's'} changed by more than 5%. Please check before saving.`, confirmLabel: 'Save anyway'}))) return;
    setBusy(true);
    try {
      const r = await saveGoldRates({rates: list});
      setImpact(r.data?.impact || null);
      onNotice(`${list.length} rate${list.length === 1 ? '' : 's'} saved for today.`);
      window.dispatchEvent(new CustomEvent('j360-rates-changed'));
      void board.reload();
    } catch (e) { onNotice(errText(e)); }
    finally { setBusy(false); }
  };
  const canEdit = perms.canManage;
  const missing = rows.filter(r => !r.setToday);
  return <div>
    <Tabs value={tab} onChange={setTab} tabs={[{key: 'today', label: 'Today'}, {key: 'history', label: 'History'}, {key: 'purities', label: 'Purities'}]} />
    {tab === 'today' && <div className="panel">
      <div className="panelHead"><div><span className="eyebrow">SELLING RATE PER GRAM · {b?.date || ''}</span><h2>{missing.length ? `${missing.length} of ${rows.length} not set today` : rows.length ? 'All rates set for today' : 'Rates'}</h2></div>
        {canEdit && rows.length > 0 && <div className="kRow">
          {missing.length > 0 && rows.some(r => r.ratePerGram != null) && <button type="button" className="secondary" disabled={busy} onClick={() => save(true)} title="Saves the last rates again for today">Keep last rates for today</button>}
          <button type="button" className="primary" disabled={busy || !changed.length} onClick={() => save(false)}>{busy ? 'Saving…' : `Save ${changed.length || ''} change${changed.length === 1 ? '' : 's'}`}</button>
        </div>}</div>
      {impact && (num(impact.ordersWithoutLockedRate) > 0 || num(impact.draftBills) > 0) && <div className="kNote warn">
        {num(impact.ordersWithoutLockedRate) > 0 && <>{impact.ordersWithoutLockedRate} open order{impact.ordersWithoutLockedRate === 1 ? '' : 's'} without a locked rate will be billed at the new rate. </>}
        {num(impact.draftBills) > 0 && <>{impact.draftBills} draft bill{impact.draftBills === 1 ? '' : 's'} will be priced at the new rate when completed.</>}
      </div>}
      <StateBox loading={board.loading} error={board.error} onRetry={board.reload} empty={!rows.length} emptyText="No purities yet" emptyHint="Add your purities (22K, 18K, silver …) on the Purities tab first.">
        <Table bare><thead><tr><th>Purity</th><th>Current rate / g</th><th>Change</th><th>Set on</th>{canEdit && <th>New rate / g</th>}<th>Market reference</th></tr></thead>
          <tbody>{rows.map(r => <tr key={r.purityId}>
            <td><b>{r.purity}</b><small>{String(r.metal || '').toLowerCase()}{num(r.fineness) ? ` · fineness ${num(r.fineness)}` : ''}</small></td>
            <td><b>{r.ratePerGram != null ? money(r.ratePerGram) : '—'}</b></td>
            <td>{r.change != null && num(r.change) !== 0 ? <span className={num(r.change) > 0 ? '' : 'kBad'}>{num(r.change) > 0 ? '▲' : '▼'} {money(Math.abs(num(r.change)))}</span> : '—'}</td>
            <td>{r.setToday ? <span className="pill success">today</span> : r.rateDate ? <span className="pill warn">{r.rateDate}</span> : <span className="pill bad">never</span>}<small>{r.updatedBy}</small></td>
            {canEdit && <td><input type="number" min="0" step="0.01" inputMode="decimal" value={draft[r.purityId] ?? ''} placeholder={r.ratePerGram != null ? String(r.ratePerGram) : 'Rate'} onChange={e => setDraft({...draft, [r.purityId]: e.target.value})} aria-label={`New rate for ${r.purity}`} style={{width: 120}} /></td>}
            <td>{suggestions[r.purityId] ? <>{money(suggestions[r.purityId])}{canEdit && <button type="button" className="ghost" onClick={() => setDraft({...draft, [r.purityId]: String(suggestions[r.purityId])})}>Use</button>}</> : '—'}</td>
          </tr>)}</tbody></Table>
      </StateBox>
      <p className="kMuted">{md.note || 'Market figures are an indicative reference only. Your saved rates are what bills use.'}{md.marketSource ? ` Source: ${md.marketSource}.` : ''}</p>
    </div>}
    {tab === 'history' && <History />}
    {tab === 'purities' && <div className="panel"><StateBox loading={m.loading} error={m.error} onRetry={m.reload} empty={!m.data}>
      <MasterEditor kind="purities" title="Purities" addLabel="Add purity" rows={(m.data as any)?.purities || []} onNotice={onNotice} onSaved={() => { void m.reload(); void board.reload(); }}
        fields={[{key: 'name', label: 'Name', required: true, hint: 'e.g. 22K (916)'}, {key: 'metal', label: 'Metal', type: 'select', required: true, options: [{value: 'GOLD', label: 'Gold'}, {value: 'SILVER', label: 'Silver'}, {value: 'PLATINUM', label: 'Platinum'}]},
          {key: 'karat', label: 'Karat', hint: 'e.g. 22K'}, {key: 'fineness', label: 'Fineness', type: 'number', required: true, hint: 'Pure metal share, e.g. 0.916'}, {key: 'description', label: 'Description', type: 'textarea'}]}
        columns={[{key: 'name', label: 'Purity'}, {key: 'metal', label: 'Metal'}, {key: 'karat', label: 'Karat'}, {key: 'fineness', label: 'Fineness'}]} />
    </StateBox></div>}
  </div>;
}

function History() {
  const [days, setDays] = useState(30);
  const h = useData(() => goldRateHistory(days), [days]);
  const rows: any[] = (h.data as any) || [];
  const purities = useMemo(() => [...new Set(rows.map(r => r.purity))], [rows]);
  // one row per day, one column per purity
  const byDay = useMemo(() => {
    const map = new Map<string, Record<string, any>>();
    rows.forEach(r => { const d = map.get(r.date) || {}; d[r.purity] = r; map.set(r.date, d); });
    return [...map.entries()];
  }, [rows]);
  return <div className="panel">
    <div className="panelHead"><div><span className="eyebrow">RATE HISTORY</span><h2>Last {days} days</h2></div>
      <select value={days} onChange={e => setDays(Number(e.target.value))} aria-label="Period"><option value={7}>7 days</option><option value={30}>30 days</option><option value={90}>90 days</option><option value={365}>1 year</option></select></div>
    <StateBox loading={h.loading} error={h.error} onRetry={h.reload} empty={!rows.length} emptyText="No rates saved in this period">
      <Table><thead><tr><th>Date</th>{purities.map(p => <th key={p}>{p}</th>)}</tr></thead>
        <tbody>{byDay.map(([d, r]) => <tr key={d}><td><b>{d}</b></td>{purities.map(p => <td key={p}>{r[p] ? <>{money(r[p].ratePerGram)}<small>{r[p].updatedBy}{r[p].source === 'MARKET' ? ' · from market' : ''}</small></> : '—'}</td>)}</tr>)}</tbody></Table>
    </StateBox>
  </div>;
}
