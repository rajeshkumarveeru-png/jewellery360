import {useEffect, useState} from 'react';
import {createOldGold, masters, oldGoldDetail, oldGoldList, previewOldGold, settleOldGold} from '../api';
import {usePermissions} from '../shared/permissions';
import {confirmDialog} from '../shared/Notify';
import {Table} from '../shared/ui';
import {CustomerPicker, CustomerRef, F, KV, MONEY_MODES, Pill, Sheet, StateBox, customerBody, daysAgo, errText, grams, label, money, num, today, useData} from '../shared/kit';
import type {PageProps} from './Dashboard';

/* ============================================================================================
   Old gold from customers, valued line by line (net = gross − stone; value = net × rate × (1 − deduction %)).
   Purchase pays the customer now; Exchange keeps the value open for a bill (today's or a later one).
   ============================================================================================ */

type Row = {description: string; purityId: string; grossWeight: string; stoneWeight: string; deductionPercent: string; rate: string};
const emptyRow = (): Row => ({description: '', purityId: '', grossWeight: '', stoneWeight: '', deductionPercent: '', rate: ''});

export default function OldGoldModule({ready, onNotice, go}: PageProps) {
  const perms = usePermissions();
  const [from, setFrom] = useState(daysAgo(30));
  const [to, setTo] = useState(today());
  const [openOnly, setOpenOnly] = useState(false);
  const [creating, setCreating] = useState(false);
  const [openId, setOpenId] = useState<number | null>(null);
  const list = useData(() => oldGoldList({from, to, openOnly}), [from, to, openOnly], ready);
  if (!ready) return <div className="panel"><StateBox empty emptyText="Choose a company and branch." /></div>;
  const rows: any[] = (list.data as any) || [];
  const totals = rows.reduce((t, r) => ({net: t.net + num(r.netWeight), amount: t.amount + num(r.amount), open: t.open + num(r.available)}), {net: 0, amount: 0, open: 0});
  return <div className="panel">
    <div className="kRow">
      <input type="date" value={from} max={to} onChange={e => setFrom(e.target.value)} aria-label="From" />
      <input type="date" value={to} min={from} max={today()} onChange={e => setTo(e.target.value)} aria-label="To" />
      <label className="kMuted"><input type="checkbox" checked={openOnly} onChange={e => setOpenOnly(e.target.checked)} /> Only open exchanges</label>
      <span className="grow" style={{flex: 1}} />
      {!perms.isViewer && <button type="button" className="primary" onClick={() => setCreating(true)}>＋ Take old gold</button>}
    </div>
    <div className="kStats" style={{marginTop: 12}}>
      <div className="kStat"><span>Net weight</span><strong>{grams(totals.net)}</strong></div>
      <div className="kStat"><span>Value</span><strong>{money(totals.amount)}</strong></div>
      <div className="kStat"><span>Open for a bill</span><strong>{money(totals.open)}</strong></div>
    </div>
    <StateBox loading={list.loading} error={list.error} onRetry={list.reload} empty={!rows.length} emptyText="No old gold in this period" emptyHint="Old gold exchanged on a bill also appears here.">
      <Table><thead><tr><th>Receipt</th><th>Date</th><th>Customer</th><th>Type</th><th>Net wt</th><th>Value</th><th>Open</th><th>Status</th></tr></thead>
        <tbody>{rows.map(r => <tr key={r.id} className="kClick" tabIndex={0} onClick={() => setOpenId(r.id)} onKeyDown={e => { if (e.key === 'Enter') setOpenId(r.id); }}>
          <td><b>{r.transactionNo}</b></td><td>{r.date}</td><td>{r.customer?.name}<small>{r.customer?.phone}</small></td><td>{label(r.type)}</td>
          <td>{grams(r.netWeight)}</td><td>{money(r.amount)}</td><td>{num(r.available) > 0 ? money(r.available) : '—'}</td><td><Pill value={r.status} /></td>
        </tr>)}</tbody></Table>
    </StateBox>
    <Sheet open={creating} wide onClose={() => setCreating(false)} kicker="OLD GOLD" title="Take old gold from a customer">
      {creating && <NewOldGold onNotice={onNotice} onDone={id => { setCreating(false); void list.reload(); setOpenId(id); }} />}
    </Sheet>
    <Detail id={openId} onClose={() => setOpenId(null)} onNotice={onNotice} onChanged={list.reload} onBill={() => go('Billing', {type: 'NEW_BILL', id: 0})} />
  </div>;
}

function NewOldGold({onNotice, onDone}: {onNotice: (s: string) => void; onDone: (id: number) => void}) {
  const [customer, setCustomer] = useState<CustomerRef>(null);
  const [type, setType] = useState<'PURCHASE' | 'EXCHANGE'>('EXCHANGE');
  const [rows, setRows] = useState<Row[]>([emptyRow()]);
  const [payout, setPayout] = useState('CASH');
  const [reference, setReference] = useState('');
  const [notes, setNotes] = useState('');
  const [val, setVal] = useState<any>(null);
  const [err, setErr] = useState('');
  const [busy, setBusy] = useState(false);
  const m = useData(() => masters(), []);
  const purities: any[] = ((m.data as any)?.purities || []).filter((p: any) => p.active !== false);
  const items = rows.filter(r => num(r.grossWeight) > 0).map(r => ({description: r.description || 'Old gold', purityId: r.purityId || undefined, grossWeight: num(r.grossWeight),
    stoneWeight: num(r.stoneWeight), deductionPercent: num(r.deductionPercent), rate: r.rate ? num(r.rate) : undefined}));
  const sig = JSON.stringify(items);
  useEffect(() => {
    if (!items.length || items.some(i => !i.purityId)) { setVal(null); setErr(''); return; }
    const t = window.setTimeout(() => previewOldGold(items).then(r => { setVal(r.data); setErr(''); }).catch(e => { setVal(null); setErr(errText(e)); }), 300);
    return () => window.clearTimeout(t);
  }, [sig]);
  const set = (i: number, p: Partial<Row>) => setRows(rows.map((r, j) => j === i ? {...r, ...p} : r));
  const save = async () => {
    if (!customer) { onNotice('Pick or add the customer.'); return; }
    if (!items.length) { onNotice('Enter the weight of the old gold.'); return; }
    if (type === 'PURCHASE' && !(await confirmDialog({title: `Pay ${money(val?.total)} to ${customer.name}?`, message: `Paid now by ${label(payout)}.`, confirmLabel: 'Pay and save'}))) return;
    setBusy(true);
    try {
      const r = await createOldGold({...customerBody(customer), type, items, payoutMode: type === 'PURCHASE' ? payout : undefined, reference: reference || undefined, notes: notes || undefined});
      onNotice(`${r.data.transactionNo} saved${type === 'EXCHANGE' ? ' - its value is ready to use on a bill' : ''}.`);
      onDone(r.data.id);
    } catch (e) { onNotice(errText(e)); }
    finally { setBusy(false); }
  };
  return <div>
    <F label="Customer *"><CustomerPicker value={customer} onChange={setCustomer} allowWalkIn={false} /></F>
    <div className="kRow" style={{margin: '12px 0'}} role="radiogroup" aria-label="Type">
      <label><input type="radio" checked={type === 'EXCHANGE'} onChange={() => setType('EXCHANGE')} /> Exchange - use the value on a bill</label>
      <label><input type="radio" checked={type === 'PURCHASE'} onChange={() => setType('PURCHASE')} /> Purchase - pay the customer now</label>
    </div>
    <div className="tableWrap"><table className="kLines"><thead><tr><th>Description</th><th>Purity</th><th>Gross g</th><th>Stone g</th><th>Deduction %</th><th>Rate/g</th><th>Value</th><th /></tr></thead>
      <tbody>{rows.map((r, i) => { const v = num(r.grossWeight) > 0 ? val?.lines?.[rows.slice(0, i).filter(x => num(x.grossWeight) > 0).length] : undefined; return <tr key={i}>
        <td><input value={r.description} placeholder="e.g. Old chain" onChange={e => set(i, {description: e.target.value})} aria-label="Description" style={{width: 140}} /></td>
        <td><select value={r.purityId} onChange={e => set(i, {purityId: e.target.value})} aria-label="Purity"><option value="">Choose…</option>{purities.map(p => <option key={p.id} value={p.id}>{p.name}</option>)}</select></td>
        <td><input type="number" min="0" step="0.001" value={r.grossWeight} onChange={e => set(i, {grossWeight: e.target.value})} aria-label="Gross weight" /></td>
        <td><input type="number" min="0" step="0.001" value={r.stoneWeight} onChange={e => set(i, {stoneWeight: e.target.value})} aria-label="Stone weight" /></td>
        <td><input type="number" min="0" step="0.01" value={r.deductionPercent} onChange={e => set(i, {deductionPercent: e.target.value})} aria-label="Deduction percent" /></td>
        <td><input type="number" min="0" step="0.01" value={r.rate} placeholder={v ? String(v.rate) : 'today'} onChange={e => set(i, {rate: e.target.value})} aria-label="Rate per gram" /></td>
        <td><b>{v ? money(v.amount) : '—'}</b>{v && <small>{grams(v.netWeight)} net</small>}</td>
        <td>{rows.length > 1 && <button type="button" className="ghost danger" onClick={() => setRows(rows.filter((_, j) => j !== i))} aria-label="Remove line">✕</button>}</td>
      </tr>; })}</tbody></table></div>
    <button type="button" className="ghost" onClick={() => setRows([...rows, emptyRow()])}>＋ Add another piece</button>
    {err && <div className="kNote bad">{err}</div>}
    <div className="kTotals" style={{marginTop: 10}}><div className="big"><span>Total value</span><b>{money(val?.total)}</b></div></div>
    <div className="kGrid" style={{marginTop: 10}}>
      {type === 'PURCHASE' && <F label="Pay by"><select value={payout} onChange={e => setPayout(e.target.value)}>{MONEY_MODES.map(x => <option key={x} value={x}>{label(x)}</option>)}</select></F>}
      {type === 'PURCHASE' && payout !== 'CASH' && <F label="Reference"><input value={reference} onChange={e => setReference(e.target.value)} /></F>}
      <F label="Notes" wide><input value={notes} onChange={e => setNotes(e.target.value)} /></F>
    </div>
    <div className="kActions"><button type="button" className="primary" disabled={busy || !val} onClick={save}>{busy ? 'Saving…' : type === 'PURCHASE' ? 'Pay and save' : 'Save exchange'}</button></div>
  </div>;
}

function Detail({id, onClose, onNotice, onChanged, onBill}: {id: number | null; onClose: () => void; onNotice: (s: string) => void; onChanged: () => void; onBill: () => void}) {
  const perms = usePermissions();
  const d = useData(() => oldGoldDetail(id!), [id], !!id);
  const t: any = d.data;
  const settle = async (mode: string) => {
    if (!(await confirmDialog({title: mode === 'ADVANCE' ? 'Keep the open value as advance?' : `Pay out ${money(t.available)}?`, message: mode === 'ADVANCE' ? 'It can then be used on any bill or refunded from Money.' : `Paid by ${label(mode)}.`, confirmLabel: 'Confirm'}))) return;
    try { await settleOldGold(t.id, mode); onNotice('Old gold settled.'); void d.reload(); onChanged(); } catch (e) { onNotice(errText(e)); }
  };
  return <Sheet open={!!id} wide onClose={onClose} kicker="OLD GOLD" title={t ? `${t.transactionNo} · ${label(t.type)}` : 'Old gold'}>
    <StateBox loading={d.loading} error={d.error} onRetry={d.reload} empty={!t}>
      {t && <>
        <KV rows={[['Date', t.date], ['Customer', t.customer?.name], ['Phone', t.customer?.phone], ['Net weight', grams(t.netWeight)], ['Value', money(t.amount)], ['Used', money(t.used)], ['Open', money(t.available)], ['Status', <Pill value={t.status} />], ['Notes', t.notes]]} />
        {num(t.available) > 0 && !perms.isViewer && <div className="kActions">
          <button type="button" className="primary" onClick={() => { onClose(); onBill(); }}>Use on a new bill</button>
          <button type="button" className="secondary" onClick={() => settle('ADVANCE')}>Keep as advance</button>
          <button type="button" className="ghost" onClick={() => settle('CASH')}>Pay out cash</button>
          <button type="button" className="ghost" onClick={() => settle('UPI')}>Pay out UPI</button>
        </div>}
        <div className="tableWrap"><table><thead><tr><th>Piece</th><th>Purity</th><th>Gross</th><th>Stone</th><th>Net</th><th>Rate</th><th>Deduction</th><th>Value</th></tr></thead>
          <tbody>{(t.items || []).map((x: any, i: number) => <tr key={i}><td>{x.description}</td><td>{x.purity}</td><td>{grams(x.grossWeight)}</td><td>{grams(x.stoneWeight)}</td><td>{grams(x.netWeight)}</td><td>{money(x.rate)}</td><td>{num(x.deductionPercent)} %</td><td><b>{money(x.amount)}</b></td></tr>)}</tbody></table></div>
      </>}
    </StateBox>
  </Sheet>;
}
