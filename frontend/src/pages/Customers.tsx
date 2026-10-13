import {FormEvent, useEffect, useState} from 'react';
import {collectFromCustomer, createAdvance, customerProfile, customers, removeCustomer, saveCustomer} from '../api';
import {usePermissions} from '../shared/permissions';
import {confirmDialog} from '../shared/Notify';
import {Table} from '../shared/ui';
import {F, KV, MONEY_MODES, Pill, Sheet, StateBox, Tabs, errText, label, money, num, useData} from '../shared/kit';
import {BillSheet} from './Billing';
import type {PageProps} from './Dashboard';

/* ============================================================================================
   Customers: find anyone by name or phone, see at a glance who owes money or holds an advance, and open a profile with
   the bill ledger, advances and the full jewellery history. Collect dues or take an advance from the profile.
   ============================================================================================ */

export default function CustomersModule({ready, onNotice, focus, go}: PageProps) {
  const perms = usePermissions();
  const [q, setQ] = useState('');
  const [query, setQuery] = useState('');
  const [show, setShow] = useState<'all' | 'owes' | 'advance'>('all');
  const [openId, setOpenId] = useState<number | null>(null);
  const [edit, setEdit] = useState<any>(null);
  const list = useData(() => customers(query, 500), [query], ready);
  useEffect(() => {
    if (focus?.type === 'CUSTOMER') setOpenId(focus.id);
    if (focus?.type === 'NEW_CUSTOMER') setEdit({});
  }, [focus?.n]);
  if (!ready) return <div className="panel"><StateBox empty emptyText="Choose a company and branch to see customers." /></div>;
  const all: any[] = ((list.data as any) || []).filter((c: any) => !c.walkIn);
  const rows = all.filter(c => show === 'all' || (show === 'owes' ? num(c.owes) > 0 : num(c.advance) > 0));
  const owes = all.reduce((s, c) => s + num(c.owes), 0);
  const adv = all.reduce((s, c) => s + num(c.advance), 0);
  return <div className="panel">
    <form className="kRow" role="search" onSubmit={e => { e.preventDefault(); setQuery(q.trim()); }}>
      <input type="search" placeholder="Name, phone, GSTIN or PAN" value={q} onChange={e => setQ(e.target.value)} aria-label="Search customers" />
      <button className="ghost">Search</button>
      {!perms.isViewer && <button type="button" className="primary" onClick={() => setEdit({})}>＋ Add customer</button>}
    </form>
    <div className="kStats" style={{marginTop: 12}}>
      <div className="kStat"><span>Customers</span><strong>{all.length}</strong></div>
      <div className="kStat"><span>They owe you</span><strong>{money(owes)}</strong><button type="button" className="ghost" onClick={() => setShow(show === 'owes' ? 'all' : 'owes')}>{show === 'owes' ? 'Show all' : 'Show who owes'}</button></div>
      <div className="kStat"><span>Advances held</span><strong>{money(adv)}</strong><button type="button" className="ghost" onClick={() => setShow(show === 'advance' ? 'all' : 'advance')}>{show === 'advance' ? 'Show all' : 'Show with advance'}</button></div>
    </div>
    <StateBox loading={list.loading} error={list.error} onRetry={list.reload} empty={!rows.length}
              emptyText={query ? `No customer matches “${query}”` : show !== 'all' ? 'Nobody in this list' : 'No customers yet'} emptyHint={query ? 'Check the spelling or search by phone.' : 'Customers are added automatically when a bill has a name and phone.'}>
      <Table><thead><tr><th>Customer</th><th>Phone</th><th>Bills</th><th>Bought</th><th>Owes</th><th>Advance</th><th>Last visit</th></tr></thead>
        <tbody>{rows.map(c => <tr key={c.id} className="kClick" tabIndex={0} onClick={() => setOpenId(c.id)} onKeyDown={e => { if (e.key === 'Enter') setOpenId(c.id); }}>
          <td><b>{c.name}</b><small>{c.gstin}</small></td><td>{c.phone || '—'}</td><td>{c.bills}</td><td>{money(c.purchases)}</td>
          <td>{num(c.owes) > 0 ? <b className="kBad">{money(c.owes)}</b> : '—'}</td><td>{num(c.advance) > 0 ? money(c.advance) : '—'}</td><td>{c.lastVisit || '—'}</td>
        </tr>)}</tbody></Table>
    </StateBox>
    <Profile id={openId} onClose={() => setOpenId(null)} onNotice={onNotice} onEdit={c => setEdit(c)} onChanged={list.reload} go={go} />
    <Sheet open={!!edit} onClose={() => setEdit(null)} kicker={edit?.id ? 'EDIT CUSTOMER' : 'NEW CUSTOMER'} title={edit?.id ? edit.name : 'Add customer'}>
      {edit && <CustomerForm initial={edit} onNotice={onNotice} onSaved={id => { setEdit(null); void list.reload(); if (id) setOpenId(id); }} />}
    </Sheet>
  </div>;
}

function CustomerForm({initial, onNotice, onSaved}: {initial: any; onNotice: (s: string) => void; onSaved: (id?: number) => void}) {
  const [f, setF] = useState<any>({name: '', phone: '', email: '', address: '', gstin: '', pan: '', birthday: '', anniversary: '', notes: '', ...initial});
  const [busy, setBusy] = useState(false);
  const set = (k: string, v: string) => setF((x: any) => ({...x, [k]: v}));
  const save = async (e: FormEvent, allowDuplicate = false) => {
    e.preventDefault();
    setBusy(true);
    try {
      const body = {name: f.name, phone: f.phone, email: f.email, address: f.address, gstin: f.gstin, pan: f.pan, birthday: f.birthday || null, anniversary: f.anniversary || null, notes: f.notes, allowDuplicate};
      const r = await saveCustomer(body, f.id);
      onNotice(`${r.data.name} saved.`);
      onSaved(f.id ? undefined : r.data.id);
    } catch (err: any) {
      const msg = errText(err);
      // the same phone already belongs to someone: let the user decide
      if (err?.response?.status === 409 && !allowDuplicate && await confirmDialog({title: 'Phone number already used', message: msg, confirmLabel: 'Save anyway'})) { setBusy(false); return save(e, true); }
      onNotice(msg);
    } finally { setBusy(false); }
  };
  return <form className="kGrid" onSubmit={save}>
    <F label="Name *"><input required value={f.name} onChange={e => set('name', e.target.value)} /></F>
    <F label="Phone"><input inputMode="tel" value={f.phone || ''} onChange={e => set('phone', e.target.value)} /></F>
    <F label="Email"><input type="email" value={f.email || ''} onChange={e => set('email', e.target.value)} /></F>
    <F label="GSTIN"><input value={f.gstin || ''} onChange={e => set('gstin', e.target.value.toUpperCase())} /></F>
    <F label="PAN" hint="Needed for cash bills of ₹2 lakh or more"><input value={f.pan || ''} onChange={e => set('pan', e.target.value.toUpperCase())} /></F>
    <F label="Birthday"><input type="date" value={f.birthday || ''} onChange={e => set('birthday', e.target.value)} /></F>
    <F label="Anniversary"><input type="date" value={f.anniversary || ''} onChange={e => set('anniversary', e.target.value)} /></F>
    <F label="Address" wide><textarea value={f.address || ''} onChange={e => set('address', e.target.value)} /></F>
    <F label="Notes" wide><textarea value={f.notes || ''} onChange={e => set('notes', e.target.value)} /></F>
    <div className="kActions kField wide"><button className="primary" disabled={busy}>{busy ? 'Saving…' : 'Save customer'}</button></div>
  </form>;
}

function Profile({id, onClose, onNotice, onEdit, onChanged, go}: {id: number | null; onClose: () => void; onNotice: (s: string) => void; onEdit: (c: any) => void; onChanged: () => void; go: PageProps['go']}) {
  const perms = usePermissions();
  const p = useData(() => customerProfile(id!), [id], !!id);
  const d: any = p.data;
  const [tab, setTab] = useState<'history' | 'ledger' | 'advances'>('history');
  const [money_, setMoney] = useState<null | 'collect' | 'advance'>(null);
  const [amount, setAmount] = useState('');
  const [mode, setMode] = useState('CASH');
  const [ref, setRef] = useState('');
  const [toAdvance, setToAdvance] = useState(true);
  const [busy, setBusy] = useState(false);
  const [bill, setBill] = useState<number | null>(null);
  useEffect(() => { setTab('history'); setMoney(null); }, [id]);
  const refresh = () => { void p.reload(); onChanged(); };
  const submit = async () => {
    if (!(num(amount) > 0)) { onNotice('Enter the amount.'); return; }
    setBusy(true);
    try {
      if (money_ === 'collect') {
        const r = await collectFromCustomer(id!, {amount: num(amount), mode, reference: ref || undefined, excessToAdvance: toAdvance});
        const applied = (r.data?.applied || []).map((a: any) => `${a.invoiceNo}: ${money(a.amount)}`).join(', ');
        onNotice(`Payment saved${applied ? ` (${applied})` : ''}${num(r.data?.advanceAmount) > 0 ? `; ${money(r.data.advanceAmount)} kept as advance` : ''}.`);
      } else {
        await createAdvance({customerId: id!, amount: num(amount), mode, reference: ref || undefined});
        onNotice(`Advance of ${money(amount)} saved.`);
      }
      setMoney(null); setAmount(''); setRef(''); refresh();
    } catch (e) { onNotice(errText(e)); }
    finally { setBusy(false); }
  };
  const c = d?.customer;
  const s = d?.summary || {};
  return <Sheet open={!!id} wide onClose={onClose} kicker="CUSTOMER" title={c ? c.name : 'Customer'}>
    <StateBox loading={p.loading} error={p.error} onRetry={p.reload} empty={!c}>
      {c && <>
        <div className={`kNote ${num(s.owes) > 0 ? 'bad' : ''}`}>{s.message}</div>
        <KV rows={[['Phone', c.phone], ['Email', c.email], ['GSTIN', c.gstin], ['PAN', c.pan], ['Birthday', c.birthday], ['Anniversary', c.anniversary],
          ['Bills', s.bills], ['Bought', money(s.purchases)], ['Last visit', s.lastVisit], ['Address', c.address], ['Notes', c.notes]]} />
        {!perms.isViewer && <div className="kActions">
          {num(s.owes) > 0 && <button type="button" className="primary" onClick={() => { setMoney('collect'); setAmount(String(num(s.owes))); }}>Collect payment</button>}
          <button type="button" className="secondary" onClick={() => { setMoney('advance'); setAmount(''); }}>Take advance</button>
          <button type="button" className="secondary" onClick={() => { onClose(); go('Billing', {type: 'NEW_BILL', id: 0}); }}>New bill</button>
          <button type="button" className="ghost" onClick={() => onEdit(c)}>Edit details</button>
          {perms.canManage && num(s.owes) === 0 && num(s.advance) === 0 && <button type="button" className="ghost danger" onClick={async () => {
            if (!(await confirmDialog({title: `Remove ${c.name}?`, message: 'Their bills stay; the customer is hidden from lists.', confirmLabel: 'Remove', tone: 'danger'}))) return;
            try { await removeCustomer(c.id); onNotice(`${c.name} removed.`); onClose(); onChanged(); } catch (e) { onNotice(errText(e)); }
          }}>Remove</button>}
        </div>}
        {money_ && <div className="kNote">
          <b>{money_ === 'collect' ? 'Collect payment against dues (oldest bills first)' : 'Take an advance'}</b>
          <div className="kRow" style={{marginTop: 8}}>
            <input type="number" min="0" step="0.01" value={amount} onChange={e => setAmount(e.target.value)} placeholder="Amount" aria-label="Amount" />
            <select value={mode} onChange={e => setMode(e.target.value)} aria-label="Mode">{MONEY_MODES.map(m => <option key={m} value={m}>{label(m)}</option>)}</select>
            {mode !== 'CASH' && <input value={ref} onChange={e => setRef(e.target.value)} placeholder="Reference / UTR" aria-label="Reference" />}
          </div>
          {money_ === 'collect' && num(amount) > num(s.owes) && <label className="kMuted"><input type="checkbox" checked={toAdvance} onChange={e => setToAdvance(e.target.checked)} /> Keep the extra {money(num(amount) - num(s.owes))} as advance</label>}
          <div className="kActions"><button type="button" className="primary" disabled={busy} onClick={submit}>{busy ? 'Saving…' : 'Save'}</button><button type="button" className="ghost" onClick={() => setMoney(null)}>Cancel</button></div>
        </div>}

        <Tabs value={tab} onChange={setTab} tabs={[{key: 'history', label: 'History'}, {key: 'ledger', label: 'Bill ledger'}, {key: 'advances', label: 'Advances', count: (d.advances || []).length}]} />
        {tab === 'history' && <StateBox empty={!(d.timeline || []).length} emptyText="No history yet">
          <div className="tableWrap"><table><thead><tr><th>Date</th><th>What</th><th>Reference</th><th>Amount</th><th>Status</th></tr></thead>
            <tbody>{d.timeline.map((t: any, i: number) => <tr key={i}><td>{t.date}</td><td><b>{t.kind}</b><small>{t.title}</small></td><td>{t.reference}</td><td>{t.amount != null ? money(t.amount) : '—'}</td><td><Pill value={t.status} /></td></tr>)}</tbody></table></div>
        </StateBox>}
        {tab === 'ledger' && <StateBox empty={!(d.ledger || []).length} emptyText="No bills yet">
          <div className="tableWrap"><table><thead><tr><th>Date</th><th>Entry</th><th>Bill</th><th>Debit</th><th>Credit</th><th>Balance</th></tr></thead>
            <tbody>{d.ledger.map((l: any, i: number) => <tr key={i} className={l.saleId ? 'kClick' : ''} onClick={() => l.saleId && setBill(l.saleId)}><td>{l.date}</td><td>{l.type}</td><td>{l.reference}</td>
              <td>{num(l.debit) ? money(l.debit) : ''}</td><td>{num(l.credit) ? money(l.credit) : ''}</td><td><b>{money(l.balance)}</b></td></tr>)}</tbody></table></div>
        </StateBox>}
        {tab === 'advances' && <StateBox empty={!(d.advances || []).length} emptyText="No advances">
          <div className="tableWrap"><table><thead><tr><th>No.</th><th>Date</th><th>From</th><th>Amount</th><th>Used</th><th>Refunded</th><th>Left</th></tr></thead>
            <tbody>{d.advances.map((a: any) => <tr key={a.id}><td><b>{a.advanceNo}</b><small>{a.orderNo}</small></td><td>{a.date}</td><td>{label(a.source)}</td><td>{money(a.amount)}</td><td>{money(a.used)}</td><td>{money(a.refunded)}</td><td><b>{money(a.balance)}</b></td></tr>)}</tbody></table></div>
        </StateBox>}
      </>}
    </StateBox>
    <BillSheet id={bill} onClose={() => setBill(null)} onNotice={onNotice} onChanged={refresh} />
  </Sheet>;
}
