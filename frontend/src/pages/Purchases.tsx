import {useState} from 'react';
import {createPurchase, masters, payPurchase, purchase as loadPurchase, purchases, returnPurchase, supplierLedger} from '../api';
import {usePermissions} from '../shared/permissions';
import {promptDialog} from '../shared/Notify';
import {Table} from '../shared/ui';
import {F, KV, MONEY_MODES, Pill, Sheet, StateBox, Tabs, daysAgo, errText, grams, label, money, num, today, useData} from '../shared/kit';
import MasterEditor from '../shared/MasterEditor';
import type {PageProps} from './Dashboard';

/* ============================================================================================
   Purchases from suppliers. A line is either a tagged piece that goes straight into stock (cost = line amount) or plain
   metal / other goods. Supplier payments and returns reduce what the shop owes; the supplier ledger shows the balance.
   ============================================================================================ */

type Line = {description: string; purity: string; quantity: string; grossWeight: string; netWeight: string; rate: string; makingCharge: string; amount: string;
  addToStock: boolean; productName: string; categoryName: string; purityId: string; stoneWeight: string; huid: string; wastagePercent: string; makingType: string; makingValue: string; tagNo: string};
const emptyLine = (): Line => ({description: '', purity: '', quantity: '1', grossWeight: '', netWeight: '', rate: '', makingCharge: '', amount: '', addToStock: true,
  productName: '', categoryName: '', purityId: '', stoneWeight: '', huid: '', wastagePercent: '', makingType: 'PER_GRAM', makingValue: '', tagNo: ''});
const lineAmount = (l: Line) => l.amount !== '' ? num(l.amount) : num(l.netWeight || l.grossWeight) * num(l.rate) + num(l.makingCharge);

export default function PurchasesModule({ready, onNotice}: PageProps) {
  const perms = usePermissions();
  const [tab, setTab] = useState<'purchases' | 'suppliers'>('purchases');
  const [from, setFrom] = useState(daysAgo(90));
  const [to, setTo] = useState(today());
  const [creating, setCreating] = useState(false);
  const [openId, setOpenId] = useState<number | null>(null);
  const [ledgerId, setLedgerId] = useState<number | null>(null);
  const list = useData(() => purchases({from, to}), [from, to], ready);
  const m = useData(() => masters(), [], ready);
  if (!ready) return <div className="panel"><StateBox empty emptyText="Choose a company and branch." /></div>;
  const rows: any[] = (list.data as any) || [];
  const md: any = m.data || {};
  const due = rows.reduce((s, r) => s + num(r.balance), 0);
  return <div>
    <Tabs value={tab} onChange={setTab} tabs={[{key: 'purchases', label: 'Purchases'}, {key: 'suppliers', label: 'Suppliers'}]} />
    {tab === 'purchases' && <div className="panel">
      <div className="kRow">
        <input type="date" value={from} max={to} onChange={e => setFrom(e.target.value)} aria-label="From" />
        <input type="date" value={to} min={from} max={today()} onChange={e => setTo(e.target.value)} aria-label="To" />
        <span style={{flex: 1}} />
        {perms.canManage && <button type="button" className="primary" onClick={() => setCreating(true)} disabled={!(md.suppliers || []).length} title={(md.suppliers || []).length ? '' : 'Add a supplier first'}>＋ New purchase</button>}
      </div>
      {perms.canManage && m.data && !(md.suppliers || []).length && <div className="kNote">Add your first supplier on the Suppliers tab, then record purchases here.</div>}
      <div className="kStats" style={{marginTop: 12}}>
        <div className="kStat"><span>Purchases</span><strong>{rows.length}</strong></div>
        <div className="kStat"><span>Total</span><strong>{money(rows.reduce((s, r) => s + num(r.total), 0))}</strong></div>
        <div className="kStat"><span>You still owe</span><strong>{money(due)}</strong></div>
      </div>
      <StateBox loading={list.loading} error={list.error} onRetry={list.reload} empty={!rows.length} emptyText="No purchases in this period">
        <Table><thead><tr><th>No.</th><th>Date</th><th>Supplier</th><th>Their invoice</th><th>Total</th><th>Paid</th><th>Due</th><th>Status</th></tr></thead>
          <tbody>{rows.map(r => <tr key={r.id} className="kClick" tabIndex={0} onClick={() => setOpenId(r.id)} onKeyDown={e => { if (e.key === 'Enter') setOpenId(r.id); }}>
            <td><b>{r.purchaseNo}</b></td><td>{r.date}</td><td>{r.supplier}</td><td>{r.supplierInvoiceNo || '—'}</td><td>{money(r.total)}</td><td>{money(r.paid)}</td>
            <td>{num(r.balance) > 0 ? <b>{money(r.balance)}</b> : '—'}</td><td><Pill value={r.status} /></td></tr>)}</tbody></Table>
      </StateBox>
    </div>}
    {tab === 'suppliers' && <div className="panel">
      <MasterEditor kind="suppliers" title="Suppliers" addLabel="Add supplier" rows={md.suppliers || []} onSaved={m.reload} onNotice={onNotice}
        fields={[{key: 'name', label: 'Name', required: true}, {key: 'phone', label: 'Phone'}, {key: 'email', label: 'Email'}, {key: 'gstin', label: 'GSTIN'}, {key: 'address', label: 'Address', type: 'textarea'}]}
        columns={[{key: 'name', label: 'Supplier', render: r => <button type="button" className="ghost" onClick={() => setLedgerId(r.id)}>{r.name}</button>}, {key: 'phone', label: 'Phone'}, {key: 'gstin', label: 'GSTIN'}]} />
    </div>}
    <Sheet open={creating} wide onClose={() => setCreating(false)} kicker="PURCHASE" title="New purchase">
      {creating && <NewPurchase md={md} onNotice={onNotice} onDone={id => { setCreating(false); void list.reload(); setOpenId(id); }} />}
    </Sheet>
    <Detail id={openId} onClose={() => setOpenId(null)} onNotice={onNotice} onChanged={list.reload} onLedger={setLedgerId} />
    <Ledger id={ledgerId} onClose={() => setLedgerId(null)} />
  </div>;
}

function NewPurchase({md, onNotice, onDone}: {md: any; onNotice: (s: string) => void; onDone: (id: number) => void}) {
  const [supplierId, setSupplierId] = useState('');
  const [invoice, setInvoice] = useState('');
  const [date, setDate] = useState(today());
  const [gst, setGst] = useState('');
  const [lines, setLines] = useState<Line[]>([emptyLine()]);
  const [pay, setPay] = useState({mode: 'BANK', amount: '', reference: ''});
  const [notes, setNotes] = useState('');
  const [busy, setBusy] = useState(false);
  const purities = (md.purities || []).filter((p: any) => p.active !== false);
  const set = (i: number, p: Partial<Line>) => setLines(lines.map((l, j) => j === i ? {...l, ...p} : l));
  const subtotal = lines.reduce((s, l) => s + lineAmount(l), 0);
  const total = subtotal + num(gst);
  const save = async () => {
    if (!supplierId) { onNotice('Choose the supplier.'); return; }
    setBusy(true);
    try {
      const items = lines.filter(l => lineAmount(l) > 0).map(l => {
        const pur = purities.find((p: any) => String(p.id) === l.purityId);
        return {description: l.description || l.productName || 'Purchase', purity: pur?.name || l.purity || undefined, quantity: num(l.quantity) || 1,
          grossWeight: num(l.grossWeight), netWeight: num(l.netWeight || l.grossWeight), rate: num(l.rate), makingCharge: num(l.makingCharge), amount: l.amount !== '' ? num(l.amount) : undefined,
          addToStock: l.addToStock, stock: l.addToStock ? {productName: l.productName || l.description, categoryName: l.categoryName || undefined, purityId: l.purityId, grossWeight: num(l.grossWeight),
            stoneWeight: num(l.stoneWeight), netWeight: l.netWeight ? num(l.netWeight) : undefined, huid: l.huid || undefined, wastagePercent: num(l.wastagePercent), makingType: l.makingType, makingValue: num(l.makingValue), tagNo: l.tagNo || undefined, source: 'PURCHASE'} : undefined};
      });
      if (!items.length) { onNotice('Add at least one line with an amount.'); setBusy(false); return; }
      const r = await createPurchase({supplierId: Number(supplierId), supplierInvoiceNo: invoice || undefined, purchaseDate: date, gst: num(gst), notes: notes || undefined, items,
        payment: num(pay.amount) > 0 ? {mode: pay.mode, amount: num(pay.amount), reference: pay.reference || undefined} : undefined});
      onNotice(`Purchase ${r.data.purchaseNo} saved.`);
      onDone(r.data.id);
    } catch (e) { onNotice(errText(e)); }
    finally { setBusy(false); }
  };
  return <div>
    <div className="kGrid">
      <F label="Supplier *"><select value={supplierId} onChange={e => setSupplierId(e.target.value)}><option value="">Choose…</option>{(md.suppliers || []).filter((s: any) => s.active !== false).map((s: any) => <option key={s.id} value={s.id}>{s.name}</option>)}</select></F>
      <F label="Supplier invoice no."><input value={invoice} onChange={e => setInvoice(e.target.value)} /></F>
      <F label="Date"><input type="date" value={date} max={today()} onChange={e => setDate(e.target.value)} /></F>
    </div>
    <datalist id="j360-pcats">{(md.categories || []).map((c: any) => <option key={c.id} value={c.name} />)}</datalist>
    {lines.map((l, i) => <div className="kNote" key={i}>
      <div className="kRow"><b>Line {i + 1}</b><label className="kMuted"><input type="checkbox" checked={l.addToStock} onChange={e => set(i, {addToStock: e.target.checked})} /> Tagged piece - add to stock</label>
        <span style={{flex: 1}} />{lines.length > 1 && <button type="button" className="ghost danger" onClick={() => setLines(lines.filter((_, j) => j !== i))}>Remove</button>}</div>
      <div className="kGrid">
        {l.addToStock ? <>
          <F label="Product name *"><input value={l.productName} onChange={e => set(i, {productName: e.target.value})} placeholder="e.g. Gold chain" /></F>
          <F label="Category"><input list="j360-pcats" value={l.categoryName} onChange={e => set(i, {categoryName: e.target.value})} /></F>
          <F label="Purity *"><select value={l.purityId} onChange={e => set(i, {purityId: e.target.value})}><option value="">Choose…</option>{purities.map((p: any) => <option key={p.id} value={p.id}>{p.name}</option>)}</select></F>
        </> : <>
          <F label="Description *"><input value={l.description} onChange={e => set(i, {description: e.target.value})} placeholder="e.g. 24K bar, packing boxes" /></F>
          <F label="Purity"><input value={l.purity} onChange={e => set(i, {purity: e.target.value})} /></F>
          <F label="Quantity"><input type="number" min="0" step="any" value={l.quantity} onChange={e => set(i, {quantity: e.target.value})} /></F>
        </>}
        <F label="Gross wt (g)"><input type="number" min="0" step="0.001" value={l.grossWeight} onChange={e => set(i, {grossWeight: e.target.value})} /></F>
        {l.addToStock && <F label="Stone wt (g)"><input type="number" min="0" step="0.001" value={l.stoneWeight} onChange={e => set(i, {stoneWeight: e.target.value})} /></F>}
        <F label="Net wt (g)"><input type="number" min="0" step="0.001" value={l.netWeight} placeholder={l.grossWeight ? String(Math.max(0, num(l.grossWeight) - num(l.stoneWeight))) : ''} onChange={e => set(i, {netWeight: e.target.value})} /></F>
        <F label="Rate / g"><input type="number" min="0" step="0.01" value={l.rate} onChange={e => set(i, {rate: e.target.value})} /></F>
        <F label="Making (₹)"><input type="number" min="0" step="0.01" value={l.makingCharge} onChange={e => set(i, {makingCharge: e.target.value})} /></F>
        <F label="Amount (₹)" hint={l.amount ? undefined : `Net × rate + making = ${money(lineAmount(l))}`}><input type="number" min="0" step="0.01" value={l.amount} onChange={e => set(i, {amount: e.target.value})} /></F>
        {l.addToStock && <>
          <F label="HUID"><input maxLength={6} value={l.huid} onChange={e => set(i, {huid: e.target.value.toUpperCase()})} /></F>
          <F label="Selling wastage %"><input type="number" min="0" step="0.01" value={l.wastagePercent} onChange={e => set(i, {wastagePercent: e.target.value})} /></F>
          <F label="Selling making"><div className="kRow"><select value={l.makingType} onChange={e => set(i, {makingType: e.target.value})} aria-label="Making type"><option value="PER_GRAM">per g</option><option value="FIXED">fixed</option><option value="PERCENT">%</option></select><input type="number" min="0" step="0.01" value={l.makingValue} onChange={e => set(i, {makingValue: e.target.value})} aria-label="Making value" /></div></F>
          <F label="Tag no." hint="Empty = automatic"><input value={l.tagNo} onChange={e => set(i, {tagNo: e.target.value})} /></F>
        </>}
      </div>
    </div>)}
    <button type="button" className="ghost" onClick={() => setLines([...lines, {...emptyLine(), addToStock: lines[lines.length - 1]?.addToStock ?? true, purityId: lines[lines.length - 1]?.purityId || '', categoryName: lines[lines.length - 1]?.categoryName || ''}])}>＋ Add line</button>
    <div className="kGrid" style={{marginTop: 10}}>
      <F label="GST on the purchase (₹)"><input type="number" min="0" step="0.01" value={gst} onChange={e => setGst(e.target.value)} /></F>
      <F label="Paid now (₹)"><input type="number" min="0" step="0.01" value={pay.amount} onChange={e => setPay({...pay, amount: e.target.value})} /></F>
      <F label="Paid by"><select value={pay.mode} onChange={e => setPay({...pay, mode: e.target.value})}>{MONEY_MODES.map(x => <option key={x} value={x}>{label(x)}</option>)}</select></F>
      {pay.mode !== 'CASH' && <F label="Reference"><input value={pay.reference} onChange={e => setPay({...pay, reference: e.target.value})} /></F>}
      <F label="Notes" wide><input value={notes} onChange={e => setNotes(e.target.value)} /></F>
    </div>
    <div className="kTotals"><div><span>Subtotal</span><b>{money(subtotal)}</b></div><div><span>GST</span><b>{money(gst)}</b></div><div className="big"><span>Total</span><b>{money(total)}</b></div>
      {num(pay.amount) > 0 && <div><span>Still owed to supplier</span><b>{money(Math.max(0, total - num(pay.amount)))}</b></div>}</div>
    <div className="kActions"><button type="button" className="primary" disabled={busy} onClick={save}>{busy ? 'Saving…' : 'Save purchase'}</button></div>
  </div>;
}

function Detail({id, onClose, onNotice, onChanged, onLedger}: {id: number | null; onClose: () => void; onNotice: (s: string) => void; onChanged: () => void; onLedger: (id: number) => void}) {
  const perms = usePermissions();
  const d = useData(() => loadPurchase(id!), [id], !!id);
  const p: any = d.data;
  const [mode, setMode] = useState<'view' | 'pay' | 'return'>('view');
  const [pay, setPay] = useState({mode: 'BANK', amount: '', reference: ''});
  const [picked, setPicked] = useState<number[]>([]);
  const [busy, setBusy] = useState(false);
  const act = async (fn: () => Promise<unknown>, ok: string) => {
    setBusy(true);
    try { await fn(); onNotice(ok); setMode('view'); setPicked([]); void d.reload(); onChanged(); } catch (e) { onNotice(errText(e)); } finally { setBusy(false); }
  };
  return <Sheet open={!!id} wide onClose={() => { setMode('view'); onClose(); }} kicker="PURCHASE" title={p ? `${p.purchaseNo} · ${p.supplier}` : 'Purchase'}>
    <StateBox loading={d.loading} error={d.error} onRetry={d.reload} empty={!p}>
      {p && <>
        <KV rows={[['Date', p.date], ['Supplier invoice', p.supplierInvoiceNo], ['Subtotal', money(p.subtotal)], ['GST', money(p.gst)], ['Total', money(p.total)], ['Paid', money(p.paid)],
          ['Returned', num(p.returned) ? money(p.returned) : null], ['Due', money(p.balance)], ['Status', <Pill value={p.status} />], ['Notes', p.notes]]} />
        <div className="kActions">
          {perms.canManage && num(p.balance) > 0 && <button type="button" className="primary" onClick={() => { setMode('pay'); setPay({...pay, amount: String(num(p.balance))}); }}>Pay supplier</button>}
          {perms.canManage && (p.lines || []).some((l: any) => !l.returned) && <button type="button" className="secondary" onClick={() => setMode('return')}>Return to supplier</button>}
          <button type="button" className="ghost" onClick={() => onLedger(p.supplierId)}>Supplier ledger</button>
        </div>
        {mode === 'pay' && <div className="kNote"><div className="kRow">
          <input type="number" min="0" step="0.01" value={pay.amount} onChange={e => setPay({...pay, amount: e.target.value})} aria-label="Amount" />
          <select value={pay.mode} onChange={e => setPay({...pay, mode: e.target.value})} aria-label="Mode">{MONEY_MODES.map(x => <option key={x} value={x}>{label(x)}</option>)}</select>
          <input value={pay.reference} placeholder="Reference" onChange={e => setPay({...pay, reference: e.target.value})} aria-label="Reference" />
          <button type="button" className="primary" disabled={busy} onClick={() => act(() => payPurchase(p.id, {mode: pay.mode, amount: num(pay.amount), reference: pay.reference || undefined}), 'Supplier payment saved.')}>Save payment</button>
          <button type="button" className="ghost" onClick={() => setMode('view')}>Cancel</button></div></div>}
        {mode === 'return' && <div className="kNote"><b>Pick the lines going back.</b> Tagged pieces leave stock; the amount (with its GST) comes off what you owe.
          {(p.lines || []).filter((l: any) => !l.returned).map((l: any) => <label key={l.id} className="kRow"><input type="checkbox" checked={picked.includes(l.id)} onChange={e => setPicked(e.target.checked ? [...picked, l.id] : picked.filter(x => x !== l.id))} /> {l.tagNo ? `${l.tagNo} · ` : ''}{l.description} · {money(l.amount)}</label>)}
          <div className="kActions"><button type="button" className="primary" disabled={busy || !picked.length} onClick={async () => { const reason = await promptDialog({title: 'Reason for the return', label: 'Reason', confirmLabel: 'Return'}); if (reason !== null) await act(() => returnPurchase(p.id, {purchaseItemIds: picked, reason: reason || undefined}), 'Purchase return saved.'); }}>Return {picked.length || ''} line{picked.length === 1 ? '' : 's'}</button><button type="button" className="ghost" onClick={() => setMode('view')}>Cancel</button></div></div>}
        <div className="tableWrap"><table><thead><tr><th>Line</th><th>Purity</th><th>Gross</th><th>Net</th><th>Rate</th><th>Making</th><th>Amount</th></tr></thead>
          <tbody>{(p.lines || []).map((l: any) => <tr key={l.id}><td><b>{l.description}{l.returned && <> <Pill value="Returned" /></>}</b><small>{l.tagNo ? `Tag ${l.tagNo}` : `Qty ${num(l.quantity)}`}</small></td><td>{l.purity || '—'}</td>
            <td>{grams(l.grossWeight)}</td><td>{grams(l.netWeight)}</td><td>{money(l.rate)}</td><td>{money(l.makingCharge)}</td><td><b>{money(l.amount)}</b></td></tr>)}</tbody></table></div>
        {(p.payments || []).length > 0 && <><h3 className="kSection">Payments</h3>{p.payments.map((x: any) => <div className="kRow" key={x.id}><b>{x.paymentNo}</b><span>{x.date}</span><span>{label(x.mode)}</span><span>{money(x.amount)}</span><span className="kMuted">{x.reference}</span></div>)}</>}
      </>}
    </StateBox>
  </Sheet>;
}

function Ledger({id, onClose}: {id: number | null; onClose: () => void}) {
  const l = useData(() => supplierLedger(id!), [id], !!id);
  const d: any = l.data;
  return <Sheet open={!!id} wide onClose={onClose} kicker="SUPPLIER LEDGER" title={d?.supplier?.name || 'Supplier'}>
    <StateBox loading={l.loading} error={l.error} onRetry={l.reload} empty={!d}>
      {d && <>
        <div className={`kNote ${num(d.owed) > 0 ? 'warn' : ''}`}>{d.message}</div>
        <StateBox empty={!(d.ledger || []).length} emptyText="No entries yet">
          <div className="tableWrap"><table><thead><tr><th>Date</th><th>Entry</th><th>Reference</th><th>You owe +</th><th>Paid / returned −</th><th>Balance</th></tr></thead>
            <tbody>{d.ledger.map((r: any, i: number) => <tr key={i}><td>{r.date}</td><td>{r.type}</td><td>{r.reference}</td><td>{num(r.credit) ? money(r.credit) : ''}</td><td>{num(r.debit) ? money(r.debit) : ''}</td><td><b>{money(r.balance)}</b></td></tr>)}</tbody></table></div>
        </StateBox>
      </>}
    </StateBox>
  </Sheet>;
}
