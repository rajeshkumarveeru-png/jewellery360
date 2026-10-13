import {FormEvent, useEffect, useState} from 'react';
import {advances, closeDay, createExpense, dayClose, dayCloseHistory, deleteExpense, expenses, moneyPayments, refundAdvance} from '../api';
import {usePermissions} from '../shared/permissions';
import {confirmDialog} from '../shared/Notify';
import {Table} from '../shared/ui';
import {F, MONEY_MODES, Pill, Sheet, StateBox, Tabs, errText, label, money, num, today, useData} from '../shared/kit';
import type {PageProps} from './Dashboard';

/* ============================================================================================
   Money: every rupee in and out (bills, dues, advances, refunds, supplier and karigar payments, expenses), customer
   advances, expenses, and the end-of-day cash count that compares the drawer with what the books expect.
   ============================================================================================ */

type TabKey = 'payments' | 'advances' | 'expenses' | 'close';

export default function MoneyModule({ready, onNotice, focus}: PageProps) {
  const [tab, setTab] = useState<TabKey>('payments');
  const [addExpense, setAddExpense] = useState(0);
  useEffect(() => {
    if (focus?.type === 'DAY_CLOSE') setTab('close');
    if (focus?.type === 'EXPENSES') setTab('expenses');
    if (focus?.type === 'NEW_EXPENSE') { setTab('expenses'); setAddExpense(n => n + 1); }
  }, [focus?.n]);
  if (!ready) return <div className="panel"><StateBox empty emptyText="Choose a company and branch." /></div>;
  return <div>
    <Tabs value={tab} onChange={setTab} tabs={[{key: 'payments', label: 'Money in & out'}, {key: 'advances', label: 'Advances'}, {key: 'expenses', label: 'Expenses'}, {key: 'close', label: 'Day close'}]} />
    {tab === 'payments' && <Payments />}
    {tab === 'advances' && <Advances onNotice={onNotice} />}
    {tab === 'expenses' && <Expenses onNotice={onNotice} openAdd={addExpense} />}
    {tab === 'close' && <DayClose onNotice={onNotice} />}
  </div>;
}

function Payments() {
  const [from, setFrom] = useState(today());
  const [to, setTo] = useState(today());
  const [type, setType] = useState('');
  const [mode, setMode] = useState('');
  const p = useData(() => moneyPayments({from, to, type, mode}), [from, to, type, mode]);
  const d: any = p.data || {};
  const rows: any[] = d.payments || [];
  return <div className="panel">
    <div className="kRow">
      <input type="date" value={from} max={to} onChange={e => setFrom(e.target.value)} aria-label="From" />
      <input type="date" value={to} min={from} max={today()} onChange={e => setTo(e.target.value)} aria-label="To" />
      <select value={type} onChange={e => setType(e.target.value)} aria-label="Type"><option value="">All types</option><option value="RECEIPT">Received</option><option value="REFUND">Refunds</option><option value="PAYOUT">Paid out</option></select>
      <select value={mode} onChange={e => setMode(e.target.value)} aria-label="Mode"><option value="">All modes</option>{[...MONEY_MODES, 'ADVANCE', 'OLD_GOLD'].map(x => <option key={x} value={x}>{label(x)}</option>)}</select>
    </div>
    <div className="kStats" style={{marginTop: 12}}>
      <div className="kStat"><span>Money in</span><strong>{money(d.moneyIn)}</strong></div>
      <div className="kStat"><span>Money out</span><strong>{money(d.moneyOut)}</strong></div>
      <div className="kStat"><span>Net</span><strong>{money(d.net)}</strong></div>
      {Object.entries(d.byMode || {}).map(([k, v]) => <div className="kStat" key={k}><span>{label(k)} (net)</span><strong>{money(v)}</strong></div>)}
    </div>
    <StateBox loading={p.loading} error={p.error} onRetry={p.reload} empty={!rows.length} emptyText="No payments in this period">
      <Table><thead><tr><th>No.</th><th>Date</th><th>For</th><th>Type</th><th>Mode</th><th>Amount</th><th>By</th></tr></thead>
        <tbody>{rows.map(r => <tr key={r.id}><td><b>{r.paymentNo}</b><small>{r.reference}</small></td><td>{r.date}</td>
          <td>{r.customer?.name || r.supplier || r.karigar || '—'}<small>{r.invoiceNo || r.notes}</small></td><td>{label(r.type)}</td><td>{label(r.mode)}</td>
          <td><b className={r.direction < 0 ? 'kBad' : ''}>{r.direction < 0 ? '− ' : ''}{money(r.amount)}</b></td><td>{r.createdBy}</td></tr>)}</tbody></Table>
    </StateBox>
    <p className="kMuted">Advance and old-gold adjustments appear in the list but are not cash, so they are left out of the totals.</p>
  </div>;
}

function Advances({onNotice}: {onNotice: (s: string) => void}) {
  const perms = usePermissions();
  const [openOnly, setOpenOnly] = useState(true);
  const list = useData(() => advances(openOnly), [openOnly]);
  const rows: any[] = (list.data as any) || [];
  const [refund, setRefund] = useState<any>(null);
  const [f, setF] = useState({amount: '', mode: 'CASH', reference: ''});
  const save = async () => {
    try { await refundAdvance(refund.id, {amount: num(f.amount), mode: f.mode, reference: f.reference || undefined}); onNotice(`${money(f.amount)} refunded to ${refund.customer?.name}.`); setRefund(null); void list.reload(); }
    catch (e) { onNotice(errText(e)); }
  };
  return <div className="panel">
    <div className="kRow"><label className="kMuted"><input type="checkbox" checked={openOnly} onChange={e => setOpenOnly(e.target.checked)} /> Only advances with money left</label>
      <span style={{flex: 1}} /><span className="kMuted">Take a new advance from the customer's profile or an order.</span></div>
    <div className="kStats" style={{marginTop: 12}}><div className="kStat"><span>Held for customers</span><strong>{money(rows.reduce((s, r) => s + num(r.balance), 0))}</strong></div></div>
    <StateBox loading={list.loading} error={list.error} onRetry={list.reload} empty={!rows.length} emptyText="No advances">
      <Table><thead><tr><th>No.</th><th>Date</th><th>Customer</th><th>From</th><th>Amount</th><th>Used</th><th>Refunded</th><th>Left</th><th /></tr></thead>
        <tbody>{rows.map(a => <tr key={a.id}><td><b>{a.advanceNo}</b><small>{a.orderNo}</small></td><td>{a.date}</td><td>{a.customer?.name}<small>{a.customer?.phone}</small></td><td>{label(a.source)}</td>
          <td>{money(a.amount)}</td><td>{money(a.used)}</td><td>{money(a.refunded)}</td><td><b>{money(a.balance)}</b></td>
          <td>{perms.canCancel && num(a.balance) > 0 && <button type="button" className="ghost" onClick={() => { setRefund(a); setF({amount: String(num(a.balance)), mode: 'CASH', reference: ''}); }}>Refund</button>}</td></tr>)}</tbody></Table>
    </StateBox>
    <Sheet open={!!refund} onClose={() => setRefund(null)} kicker="REFUND ADVANCE" title={refund ? `${refund.advanceNo} · ${refund.customer?.name}` : ''}>
      {refund && <div className="kGrid">
        <F label={`Amount (up to ${money(refund.balance)})`}><input type="number" min="0" max={num(refund.balance)} step="0.01" value={f.amount} onChange={e => setF({...f, amount: e.target.value})} /></F>
        <F label="Paid by"><select value={f.mode} onChange={e => setF({...f, mode: e.target.value})}>{MONEY_MODES.map(x => <option key={x} value={x}>{label(x)}</option>)}</select></F>
        {f.mode !== 'CASH' && <F label="Reference"><input value={f.reference} onChange={e => setF({...f, reference: e.target.value})} /></F>}
        <div className="kActions kField wide"><button type="button" className="primary" disabled={!(num(f.amount) > 0)} onClick={save}>Refund</button></div>
      </div>}
    </Sheet>
  </div>;
}

function Expenses({onNotice, openAdd = 0}: {onNotice: (s: string) => void; openAdd?: number}) {
  const perms = usePermissions();
  const [from, setFrom] = useState(() => today().slice(0, 8) + '01');
  const [to, setTo] = useState(today());
  const list = useData(() => expenses({from, to}), [from, to]);
  const d: any = list.data || {};
  const rows: any[] = d.expenses || [];
  const [adding, setAdding] = useState(false);
  useEffect(() => { if (openAdd) setAdding(true); }, [openAdd]);
  const [f, setF] = useState<any>({date: today(), category: '', title: '', amount: '', mode: 'CASH', reference: '', notes: ''});
  const save = async (e: FormEvent) => {
    e.preventDefault();
    try { await createExpense({...f, amount: num(f.amount)}); onNotice('Expense saved.'); setAdding(false); setF({...f, title: '', amount: '', reference: '', notes: ''}); void list.reload(); }
    catch (err) { onNotice(errText(err)); }
  };
  const remove = async (x: any) => {
    if (!(await confirmDialog({title: `Delete expense “${x.title}”?`, message: money(x.amount), confirmLabel: 'Delete', tone: 'danger'}))) return;
    try { await deleteExpense(x.id); onNotice('Expense deleted.'); void list.reload(); } catch (err) { onNotice(errText(err)); }
  };
  return <div className="panel">
    <div className="kRow">
      <input type="date" value={from} max={to} onChange={e => setFrom(e.target.value)} aria-label="From" />
      <input type="date" value={to} min={from} max={today()} onChange={e => setTo(e.target.value)} aria-label="To" />
      <span style={{flex: 1}} />{!perms.isViewer && <button type="button" className="primary" onClick={() => setAdding(true)}>＋ Add expense</button>}
    </div>
    <div className="kStats" style={{marginTop: 12}}>
      <div className="kStat"><span>Total</span><strong>{money(d.total)}</strong></div>
      {Object.entries(d.byCategory || {}).slice(0, 5).map(([k, v]) => <div className="kStat" key={k}><span>{k}</span><strong>{money(v)}</strong></div>)}
    </div>
    <StateBox loading={list.loading} error={list.error} onRetry={list.reload} empty={!rows.length} emptyText="No expenses in this period">
      <Table><thead><tr><th>Date</th><th>Expense</th><th>Category</th><th>Mode</th><th>Amount</th><th>By</th><th /></tr></thead>
        <tbody>{rows.map(x => <tr key={x.id}><td>{x.date}</td><td><b>{x.title}</b><small>{x.notes}</small></td><td>{x.category}</td><td>{label(x.mode)}<small>{x.reference}</small></td><td>{money(x.amount)}</td><td>{x.createdBy}</td>
          <td>{perms.canManage && <button type="button" className="ghost danger" onClick={() => remove(x)} aria-label={`Delete ${x.title}`}>Delete</button>}</td></tr>)}</tbody></Table>
    </StateBox>
    <Sheet open={adding} onClose={() => setAdding(false)} kicker="EXPENSE" title="Add expense">
      <form className="kGrid" onSubmit={save}>
        <F label="Date"><input type="date" value={f.date} max={today()} onChange={e => setF({...f, date: e.target.value})} /></F>
        <F label="Category"><select value={f.category} onChange={e => setF({...f, category: e.target.value})}><option value="">Other</option>{(d.categories || []).map((c: string) => <option key={c} value={c}>{c}</option>)}</select></F>
        <F label="What for *" wide><input required value={f.title} onChange={e => setF({...f, title: e.target.value})} placeholder="e.g. October rent" /></F>
        <F label="Amount (₹) *"><input required type="number" min="0.01" step="0.01" value={f.amount} onChange={e => setF({...f, amount: e.target.value})} /></F>
        <F label="Paid by"><select value={f.mode} onChange={e => setF({...f, mode: e.target.value})}>{MONEY_MODES.map(x => <option key={x} value={x}>{label(x)}</option>)}</select></F>
        {f.mode !== 'CASH' && <F label="Reference"><input value={f.reference} onChange={e => setF({...f, reference: e.target.value})} /></F>}
        <F label="Notes" wide><input value={f.notes} onChange={e => setF({...f, notes: e.target.value})} /></F>
        <div className="kActions kField wide"><button className="primary">Save expense</button></div>
      </form>
    </Sheet>
  </div>;
}

function DayClose({onNotice}: {onNotice: (s: string) => void}) {
  const perms = usePermissions();
  const [date, setDate] = useState(today());
  const p = useData(() => dayClose(date), [date]);
  const h = useData(() => dayCloseHistory(), []);
  const d: any = p.data;
  const history: any[] = (h.data as any) || [];
  const [cash, setCash] = useState('');
  const [opening, setOpening] = useState('');
  const [notes, setNotes] = useState('');
  const [busy, setBusy] = useState(false);
  useEffect(() => { setCash(''); setNotes(''); }, [date]);
  const first = !history.length;
  const canReclose = perms.isAdmin;
  const diff = cash === '' ? null : num(cash) - num(d?.expectedCash) - (first && opening ? num(opening) - num(d?.openingCash) : 0);
  const save = async () => {
    if (cash === '') { onNotice('Count the cash in the drawer and enter it.'); return; }
    if (diff !== null && Math.abs(diff) > 0.5 && !(await confirmDialog({title: diff > 0 ? `Drawer has ${money(diff)} more than expected` : `Drawer is short by ${money(-diff)}`, message: 'Save the close with this difference? Add a note explaining it.', confirmLabel: 'Close the day'}))) return;
    setBusy(true);
    try { await closeDay({date, actualCash: num(cash), openingCash: first && opening ? num(opening) : undefined, notes: notes || undefined}); onNotice(`Day ${date} closed.`); void p.reload(); void h.reload(); }
    catch (e) { onNotice(errText(e)); }
    finally { setBusy(false); }
  };
  return <div className="kSplit">
    <div className="panel">
      <div className="panelHead"><div><span className="eyebrow">CASH COUNT</span><h2>{d?.closed ? `Closed${d.closedBy ? ` by ${d.closedBy}` : ''}` : 'Close the day'}</h2></div>
        <input type="date" value={date} max={today()} onChange={e => setDate(e.target.value)} aria-label="Day" /></div>
      <StateBox loading={p.loading} error={p.error} onRetry={p.reload} empty={!d}>
        {d && <>
          <div className="kTotals">
            <div><span>Opening cash</span><b>{money(d.openingCash)}</b></div>
            <div><span>Cash received</span><b>{money(d.cashIn)}</b></div>
            <div><span>Cash paid out</span><b>− {money(d.cashOut)}</b></div>
            <div><span>Cash expenses</span><b>− {money(d.cashExpenses)}</b></div>
            <div className="big"><span>Expected in drawer</span><b>{money(d.expectedCash)}</b></div>
            {d.closed && <><div><span>Counted</span><b>{money(d.actualCash)}</b></div><div><span>Difference</span><b className={num(d.difference) < 0 ? 'kBad' : ''}>{money(d.difference)}</b></div></>}
          </div>
          <h3 className="kSection">Other money today</h3>
          <div className="kStats">
            <div className="kStat"><span>UPI</span><strong>{money(d.upi)}</strong></div>
            <div className="kStat"><span>Card</span><strong>{money(d.card)}</strong></div>
            <div className="kStat"><span>Bank</span><strong>{money(d.bank)}</strong></div>
            <div className="kStat"><span>Sales</span><strong>{money(d.salesTotal)}</strong></div>
            <div className="kStat"><span>On credit</span><strong>{money(d.creditSales)}</strong></div>
            <div className="kStat"><span>Advances taken</span><strong>{money(d.advancesReceived)}</strong></div>
            <div className="kStat"><span>Refunds</span><strong>{money(d.refundsPaid)}</strong></div>
          </div>
          {(!d.closed || canReclose) && !perms.isViewer && <div className="kNote">
            <b>{d.closed ? 'Close again (owner)' : 'Count the drawer'}</b>
            <div className="kGrid" style={{marginTop: 8}}>
              {first && <F label="Opening cash (first close only)"><input type="number" min="0" step="0.01" value={opening} onChange={e => setOpening(e.target.value)} /></F>}
              <F label="Cash counted (₹) *"><input type="number" min="0" step="0.01" value={cash} onChange={e => setCash(e.target.value)} /></F>
              <F label="Notes"><input value={notes} onChange={e => setNotes(e.target.value)} placeholder="Explain any difference" /></F>
            </div>
            {diff !== null && <p className={Math.abs(diff) > 0.5 ? 'kBad' : 'kMuted'}>{Math.abs(diff) <= 0.5 ? 'Matches the books.' : diff > 0 ? `${money(diff)} more than expected.` : `Short by ${money(-diff)}.`}</p>}
            <div className="kActions"><button type="button" className="primary" disabled={busy} onClick={save}>{busy ? 'Saving…' : d.closed ? 'Close again' : 'Close the day'}</button></div>
          </div>}
          {d.closed && d.notes && <p className="kMuted">Note: {d.notes}</p>}
        </>}
      </StateBox>
    </div>
    <div className="panel">
      <div className="panelHead"><div><span className="eyebrow">EARLIER CLOSES</span><h2>{history.length} days</h2></div></div>
      <StateBox loading={h.loading} error={h.error} onRetry={h.reload} empty={!history.length} emptyText="No day closed yet">
        <Table bare><thead><tr><th>Date</th><th>Expected</th><th>Counted</th><th>Difference</th></tr></thead>
          <tbody>{history.map(c => <tr key={c.id} className="kClick" onClick={() => setDate(c.date)}><td><b>{c.date}</b><small>{c.closedBy}</small></td><td>{money(c.expectedCash)}</td><td>{money(c.actualCash)}</td>
            <td>{Math.abs(num(c.difference)) > 0.5 ? <Pill value={money(c.difference)} tone={num(c.difference) < 0 ? 'bad' : 'warn'} /> : '—'}</td></tr>)}</tbody></Table>
      </StateBox>
    </div>
  </div>;
}
