import {FormEvent, useEffect, useState} from 'react';
import {
  closeJob, createJob, createOrder, createRepair, job as loadJob, jobMetal, jobs, karigar as loadKarigar, karigarSummary, masters, order as loadOrder, orderAdvance,
  orders, payKarigar, repair as loadRepair, repairs, updateOrder, updateRepair
} from '../api';
import {usePermissions} from '../shared/permissions';
import {confirmDialog, promptDialog} from '../shared/Notify';
import {Table} from '../shared/ui';
import {CustomerPicker, CustomerRef, F, KV, MONEY_MODES, Pill, Sheet, StateBox, Tabs, customerBody, errText, grams, label, money, num, useData} from '../shared/kit';
import MasterEditor from '../shared/MasterEditor';
import type {PageProps} from './Dashboard';

/* ============================================================================================
   Services: customer orders (ORDERED → IN_PROGRESS → READY → final bill), repairs (RECEIVED → IN_REPAIR → READY →
   DELIVERED) and karigar job work, where metal issued and received is reconciled in fine grams.
   ============================================================================================ */

type TabKey = 'orders' | 'repairs' | 'jobs' | 'karigars';
const NEXT_ORDER: Record<string, string> = {ORDERED: 'IN_PROGRESS', IN_PROGRESS: 'READY'};
const NEXT_REPAIR: Record<string, string> = {RECEIVED: 'IN_REPAIR', IN_REPAIR: 'READY'};

export default function ServicesModule({ready, onNotice, focus, go}: PageProps) {
  const [tab, setTab] = useState<TabKey>('orders');
  const [open, setOpen] = useState<{kind: TabKey; id: number} | null>(null);
  useEffect(() => {
    if (focus?.type === 'ORDER') { setTab('orders'); setOpen({kind: 'orders', id: focus.id}); }
    if (focus?.type === 'REPAIR') { setTab('repairs'); setOpen({kind: 'repairs', id: focus.id}); }
  }, [focus?.n]);
  const m = useData(() => masters(), [], ready);
  if (!ready) return <div className="panel"><StateBox empty emptyText="Choose a company and branch." /></div>;
  const md: any = m.data || {};
  return <div>
    <Tabs value={tab} onChange={setTab} tabs={[{key: 'orders', label: 'Orders'}, {key: 'repairs', label: 'Repairs'}, {key: 'jobs', label: 'Karigar jobs'}, {key: 'karigars', label: 'Karigars'}]} />
    {tab === 'orders' && <Orders md={md} onNotice={onNotice} openId={open?.kind === 'orders' ? open.id : null} setOpen={id => setOpen(id ? {kind: 'orders', id} : null)} go={go} />}
    {tab === 'repairs' && <Repairs md={md} onNotice={onNotice} openId={open?.kind === 'repairs' ? open.id : null} setOpen={id => setOpen(id ? {kind: 'repairs', id} : null)} />}
    {tab === 'jobs' && <Jobs md={md} onNotice={onNotice} />}
    {tab === 'karigars' && <Karigars md={md} reload={m.reload} onNotice={onNotice} />}
  </div>;
}

function StatusFilter({value, onChange, flow}: {value: string; onChange: (v: string) => void; flow: string[]}) {
  return <select value={value} onChange={e => onChange(e.target.value)} aria-label="Status"><option value="OPEN">Open</option>{flow.map(s => <option key={s} value={s}>{label(s)}</option>)}<option value="">All</option></select>;
}

/* ------------------------------------------------------------------------------------------------ orders */

function Orders({md, onNotice, openId, setOpen, go}: {md: any; onNotice: (s: string) => void; openId: number | null; setOpen: (id: number | null) => void; go: PageProps['go']}) {
  const perms = usePermissions();
  const [status, setStatus] = useState('OPEN');
  const [creating, setCreating] = useState(false);
  const list = useData(() => orders(status === 'OPEN' ? undefined : status), [status]);
  const all: any[] = (list.data as any) || [];
  const rows = status === 'OPEN' ? all.filter(o => ['ORDERED', 'IN_PROGRESS', 'READY'].includes(o.status)) : all;
  return <div className="panel">
    <div className="kRow"><StatusFilter value={status} onChange={setStatus} flow={['ORDERED', 'IN_PROGRESS', 'READY', 'DELIVERED', 'CANCELLED']} /><span style={{flex: 1}} />
      {!perms.isViewer && <button type="button" className="primary" onClick={() => setCreating(true)}>＋ New order</button>}</div>
    <StateBox loading={list.loading} error={list.error} onRetry={list.reload} empty={!rows.length} emptyText="No orders here" emptyHint="Take a customer order with an advance; lock today's rate if the customer wants it.">
      <Table><thead><tr><th>Order</th><th>Customer</th><th>Item</th><th>Delivery</th><th>Estimate</th><th>Advance</th><th>Status</th></tr></thead>
        <tbody>{rows.map(o => <tr key={o.id} className="kClick" tabIndex={0} onClick={() => setOpen(o.id)} onKeyDown={e => { if (e.key === 'Enter') setOpen(o.id); }}>
          <td><b>{o.orderNo}</b><small>{o.date}</small></td><td>{o.customer?.name}<small>{o.customer?.phone}</small></td><td>{o.description}<small>{o.purity}{num(o.targetWeight) ? ` · ${grams(o.targetWeight)}` : ''}{num(o.lockedRate) ? ` · rate locked ${money(o.lockedRate)}` : ''}</small></td>
          <td>{o.deliveryDate || '—'}{o.overdue && <> <Pill value="OVERDUE" /></>}</td><td>{money(o.estimate)}</td><td>{money(o.advancePaid)}</td><td><Pill value={o.status} /><small>{o.karigar}</small></td>
        </tr>)}</tbody></Table>
    </StateBox>
    <Sheet open={creating} onClose={() => setCreating(false)} kicker="NEW ORDER" title="Customer order">
      {creating && <OrderForm md={md} onNotice={onNotice} onDone={id => { setCreating(false); void list.reload(); setOpen(id); }} />}
    </Sheet>
    <OrderSheet id={openId} md={md} onClose={() => setOpen(null)} onNotice={onNotice} onChanged={list.reload} go={go} />
  </div>;
}

function OrderForm({md, onNotice, onDone}: {md: any; onNotice: (s: string) => void; onDone: (id: number) => void}) {
  const [customer, setCustomer] = useState<CustomerRef>(null);
  const [f, setF] = useState<any>({description: '', deliveryDate: '', purityId: '', targetWeight: '', estimate: '', makingNotes: '', lockRate: false, karigarId: '', notes: '', advance: '', mode: 'CASH', reference: ''});
  const [busy, setBusy] = useState(false);
  const set = (k: string, v: any) => setF((x: any) => ({...x, [k]: v}));
  const save = async (e: FormEvent) => {
    e.preventDefault();
    if (!customer) { onNotice('Pick or add the customer.'); return; }
    setBusy(true);
    try {
      const r = await createOrder({...customerBody(customer), description: f.description, deliveryDate: f.deliveryDate || undefined, purityId: f.purityId || undefined, targetWeight: num(f.targetWeight),
        estimate: num(f.estimate), makingNotes: f.makingNotes || undefined, lockRate: f.lockRate, karigarId: f.karigarId || undefined, notes: f.notes || undefined,
        advance: num(f.advance) > 0 ? {amount: num(f.advance), mode: f.mode, reference: f.reference || undefined} : undefined});
      onNotice(`Order ${r.data.orderNo} saved.`);
      onDone(r.data.id);
    } catch (err) { onNotice(errText(err)); }
    finally { setBusy(false); }
  };
  return <form onSubmit={save}>
    <F label="Customer *"><CustomerPicker value={customer} onChange={setCustomer} allowWalkIn={false} /></F>
    <div className="kGrid" style={{marginTop: 10}}>
      <F label="What to make *" wide><input required value={f.description} onChange={e => set('description', e.target.value)} placeholder="e.g. 22K bangle pair, peacock design" /></F>
      <F label="Purity"><select value={f.purityId} onChange={e => set('purityId', e.target.value)}><option value="">—</option>{(md.purities || []).filter((p: any) => p.active !== false).map((p: any) => <option key={p.id} value={p.id}>{p.name}</option>)}</select></F>
      <F label="Approx. weight (g)"><input type="number" min="0" step="0.001" value={f.targetWeight} onChange={e => set('targetWeight', e.target.value)} /></F>
      <F label="Delivery date"><input type="date" value={f.deliveryDate} onChange={e => set('deliveryDate', e.target.value)} /></F>
      <F label="Estimate (₹)"><input type="number" min="0" step="0.01" value={f.estimate} onChange={e => set('estimate', e.target.value)} /></F>
      <F label="Karigar"><select value={f.karigarId} onChange={e => set('karigarId', e.target.value)}><option value="">Not yet</option>{(md.karigars || []).filter((k: any) => k.active !== false).map((k: any) => <option key={k.id} value={k.id}>{k.name}</option>)}</select></F>
      <F label="Making notes"><input value={f.makingNotes} onChange={e => set('makingNotes', e.target.value)} /></F>
    </div>
    <label className="kMuted" style={{display: 'block', margin: '10px 0'}}><input type="checkbox" checked={f.lockRate} disabled={!f.purityId} onChange={e => set('lockRate', e.target.checked)} /> Lock today's rate for this order (the final bill uses it)</label>
    <h3 className="kSection">Advance</h3>
    <div className="kRow"><input type="number" min="0" step="0.01" placeholder="Advance amount" value={f.advance} onChange={e => set('advance', e.target.value)} aria-label="Advance amount" />
      <select value={f.mode} onChange={e => set('mode', e.target.value)} aria-label="Mode">{MONEY_MODES.map(x => <option key={x} value={x}>{label(x)}</option>)}</select>
      {f.mode !== 'CASH' && <input placeholder="Reference" value={f.reference} onChange={e => set('reference', e.target.value)} aria-label="Reference" />}</div>
    <F label="Notes"><input value={f.notes} onChange={e => set('notes', e.target.value)} /></F>
    <div className="kActions"><button className="primary" disabled={busy}>{busy ? 'Saving…' : 'Save order'}</button></div>
  </form>;
}

function OrderSheet({id, md, onClose, onNotice, onChanged, go}: {id: number | null; md: any; onClose: () => void; onNotice: (s: string) => void; onChanged: () => void; go: PageProps['go']}) {
  const perms = usePermissions();
  const d = useData(() => loadOrder(id!), [id], !!id);
  const o: any = d.data;
  const [adv, setAdv] = useState({amount: '', mode: 'CASH'});
  const act = async (fn: () => Promise<unknown>, ok: string) => { try { await fn(); onNotice(ok); void d.reload(); onChanged(); } catch (e) { onNotice(errText(e)); } };
  const active = o && !['DELIVERED', 'CANCELLED'].includes(o.status);
  return <Sheet open={!!id} wide onClose={onClose} kicker="ORDER" title={o ? `${o.orderNo} · ${o.customer?.name}` : 'Order'}>
    <StateBox loading={d.loading} error={d.error} onRetry={d.reload} empty={!o}>
      {o && <>
        <KV rows={[['Status', <Pill value={o.status} />], ['Ordered', o.date], ['Delivery', o.deliveryDate], ['Item', o.description], ['Purity', o.purity], ['Weight', num(o.targetWeight) ? grams(o.targetWeight) : null],
          ['Locked rate', num(o.lockedRate) ? money(o.lockedRate) : 'Not locked - billed at the delivery-day rate'], ['Estimate', money(o.estimate)], ['Advance paid', money(o.advancePaid)], ['Advance left', money(o.advanceAvailable)],
          ['Balance (estimate)', money(o.balanceEstimate)], ['Karigar', o.karigar], ['Making notes', o.makingNotes], ['Notes', o.notes]]} />
        {active && !perms.isViewer && <div className="kActions">
          {NEXT_ORDER[o.status] && <button type="button" className="primary" onClick={() => act(() => updateOrder(o.id, {status: NEXT_ORDER[o.status]}), `Order marked ${label(NEXT_ORDER[o.status]).toLowerCase()}.`)}>Mark {label(NEXT_ORDER[o.status]).toLowerCase()}</button>}
          {o.status === 'READY' && <button type="button" className="primary" onClick={() => { onClose(); go('Billing', {type: 'ORDER', id: o.id}); }}>Make final bill</button>}
          <select value={o.karigarId ?? ''} onChange={e => act(() => updateOrder(o.id, {karigarId: e.target.value || null}), 'Karigar updated.')} aria-label="Karigar"><option value="">No karigar</option>{(md.karigars || []).map((k: any) => <option key={k.id} value={k.id}>{k.name}</option>)}</select>
          <button type="button" className="ghost danger" onClick={async () => { if (await confirmDialog({title: `Cancel order ${o.orderNo}?`, message: 'Any advance stays with the customer - use it on a bill or refund it from Money.', confirmLabel: 'Cancel order', tone: 'danger'})) await act(() => updateOrder(o.id, {status: 'CANCELLED'}), 'Order cancelled.'); }}>Cancel order</button>
        </div>}
        {active && !perms.isViewer && <div className="kNote"><b>Extra advance</b><div className="kRow">
          <input type="number" min="0" step="0.01" value={adv.amount} onChange={e => setAdv({...adv, amount: e.target.value})} placeholder="Amount" aria-label="Advance amount" />
          <select value={adv.mode} onChange={e => setAdv({...adv, mode: e.target.value})} aria-label="Mode">{MONEY_MODES.map(x => <option key={x} value={x}>{label(x)}</option>)}</select>
          <button type="button" className="secondary" disabled={!(num(adv.amount) > 0)} onClick={async () => { await act(() => orderAdvance(o.id, {amount: num(adv.amount), mode: adv.mode}), 'Advance saved.'); setAdv({amount: '', mode: 'CASH'}); }}>Save advance</button></div></div>}
        {(o.advances || []).length > 0 && <><h3 className="kSection">Advances</h3>{o.advances.map((a: any) => <div className="kRow" key={a.advanceNo}><b>{a.advanceNo}</b><span>{a.date}</span><span>{money(a.amount)}</span><span className="kMuted">{money(a.balance)} left</span></div>)}</>}
      </>}
    </StateBox>
  </Sheet>;
}

/* ------------------------------------------------------------------------------------------------ repairs */

function Repairs({md, onNotice, openId, setOpen}: {md: any; onNotice: (s: string) => void; openId: number | null; setOpen: (id: number | null) => void}) {
  const perms = usePermissions();
  const [status, setStatus] = useState('OPEN');
  const [creating, setCreating] = useState(false);
  const list = useData(() => repairs(status === 'OPEN' ? undefined : status), [status]);
  const all: any[] = (list.data as any) || [];
  const rows = status === 'OPEN' ? all.filter(r => ['RECEIVED', 'IN_REPAIR', 'READY'].includes(r.status)) : all;
  return <div className="panel">
    <div className="kRow"><StatusFilter value={status} onChange={setStatus} flow={['RECEIVED', 'IN_REPAIR', 'READY', 'DELIVERED', 'CANCELLED']} /><span style={{flex: 1}} />
      {!perms.isViewer && <button type="button" className="primary" onClick={() => setCreating(true)}>＋ Receive repair</button>}</div>
    <StateBox loading={list.loading} error={list.error} onRetry={list.reload} empty={!rows.length} emptyText="No repairs here">
      <Table><thead><tr><th>Repair</th><th>Customer</th><th>Item</th><th>Expected</th><th>Charge</th><th>Due</th><th>Status</th></tr></thead>
        <tbody>{rows.map(r => <tr key={r.id} className="kClick" tabIndex={0} onClick={() => setOpen(r.id)} onKeyDown={e => { if (e.key === 'Enter') setOpen(r.id); }}>
          <td><b>{r.orderNo}</b><small>{r.receivedDate}</small></td><td>{r.customer?.name}<small>{r.customer?.phone}</small></td><td>{r.itemDescription}<small>{r.problem}</small></td>
          <td>{r.expectedDate || '—'}{r.overdue && <> <Pill value="OVERDUE" /></>}</td><td>{money(num(r.finalAmount) || r.estimate)}</td><td>{num(r.balance) > 0 ? money(r.balance) : '—'}</td><td><Pill value={r.status} /><small>{r.karigar}</small></td>
        </tr>)}</tbody></Table>
    </StateBox>
    <Sheet open={creating} onClose={() => setCreating(false)} kicker="REPAIR" title="Receive a repair">
      {creating && <RepairForm md={md} onNotice={onNotice} onDone={id => { setCreating(false); void list.reload(); setOpen(id); }} />}
    </Sheet>
    <RepairSheet id={openId} md={md} onClose={() => setOpen(null)} onNotice={onNotice} onChanged={list.reload} />
  </div>;
}

function RepairForm({md, onNotice, onDone}: {md: any; onNotice: (s: string) => void; onDone: (id: number) => void}) {
  const [customer, setCustomer] = useState<CustomerRef>(null);
  const [f, setF] = useState<any>({itemDescription: '', itemTag: '', huid: '', grossWeight: '', problem: '', expectedDate: '', estimate: '', karigarId: '', notes: '', advance: '', mode: 'CASH'});
  const [busy, setBusy] = useState(false);
  const set = (k: string, v: any) => setF((x: any) => ({...x, [k]: v}));
  const save = async (e: FormEvent) => {
    e.preventDefault();
    if (!customer) { onNotice('Pick or add the customer.'); return; }
    setBusy(true);
    try {
      const r = await createRepair({...customerBody(customer), itemDescription: f.itemDescription, itemTag: f.itemTag || undefined, huid: f.huid || undefined, grossWeight: num(f.grossWeight), problem: f.problem,
        expectedDate: f.expectedDate || undefined, estimate: num(f.estimate), karigarId: f.karigarId || undefined, notes: f.notes || undefined,
        advance: num(f.advance) > 0 ? {amount: num(f.advance), mode: f.mode} : undefined});
      onNotice(`Repair ${r.data.orderNo} received.`);
      onDone(r.data.id);
    } catch (err) { onNotice(errText(err)); }
    finally { setBusy(false); }
  };
  return <form onSubmit={save}>
    <F label="Customer *"><CustomerPicker value={customer} onChange={setCustomer} allowWalkIn={false} /></F>
    <div className="kGrid" style={{marginTop: 10}}>
      <F label="Item *"><input required value={f.itemDescription} onChange={e => set('itemDescription', e.target.value)} placeholder="e.g. Gold chain" /></F>
      <F label="Weight received (g)" hint="Record it - it is checked on delivery"><input type="number" min="0" step="0.001" value={f.grossWeight} onChange={e => set('grossWeight', e.target.value)} /></F>
      <F label="Our tag (if bought here)"><input value={f.itemTag} onChange={e => set('itemTag', e.target.value)} /></F>
      <F label="HUID"><input maxLength={6} value={f.huid} onChange={e => set('huid', e.target.value.toUpperCase())} /></F>
      <F label="Problem / work *" wide><textarea required value={f.problem} onChange={e => set('problem', e.target.value)} /></F>
      <F label="Expected date"><input type="date" value={f.expectedDate} onChange={e => set('expectedDate', e.target.value)} /></F>
      <F label="Estimate (₹)"><input type="number" min="0" step="0.01" value={f.estimate} onChange={e => set('estimate', e.target.value)} /></F>
      <F label="Karigar"><select value={f.karigarId} onChange={e => set('karigarId', e.target.value)}><option value="">In-house / not yet</option>{(md.karigars || []).filter((k: any) => k.active !== false).map((k: any) => <option key={k.id} value={k.id}>{k.name}</option>)}</select></F>
      <F label="Advance (₹)"><div className="kRow"><input type="number" min="0" step="0.01" value={f.advance} onChange={e => set('advance', e.target.value)} aria-label="Advance" /><select value={f.mode} onChange={e => set('mode', e.target.value)} aria-label="Mode">{MONEY_MODES.map(x => <option key={x} value={x}>{label(x)}</option>)}</select></div></F>
    </div>
    <div className="kActions"><button className="primary" disabled={busy}>{busy ? 'Saving…' : 'Save repair'}</button></div>
  </form>;
}

function RepairSheet({id, md, onClose, onNotice, onChanged}: {id: number | null; md: any; onClose: () => void; onNotice: (s: string) => void; onChanged: () => void}) {
  const perms = usePermissions();
  const d = useData(() => loadRepair(id!), [id], !!id);
  const r: any = d.data;
  const [deliver, setDeliver] = useState(false);
  const [charge, setCharge] = useState('');
  const [pay, setPay] = useState({amount: '', mode: 'CASH'});
  useEffect(() => { setDeliver(false); }, [id]);
  const act = async (body: any, ok: string) => { try { await updateRepair(r.id, body); onNotice(ok); setDeliver(false); void d.reload(); onChanged(); } catch (e) { onNotice(errText(e)); } };
  const active = r && !['DELIVERED', 'CANCELLED'].includes(r.status);
  const finalCharge = charge !== '' ? num(charge) : num(r?.finalAmount) || num(r?.estimate);
  return <Sheet open={!!id} wide onClose={onClose} kicker="REPAIR" title={r ? `${r.orderNo} · ${r.customer?.name}` : 'Repair'}>
    <StateBox loading={d.loading} error={d.error} onRetry={d.reload} empty={!r}>
      {r && <>
        <KV rows={[['Status', <Pill value={r.status} />], ['Received', r.receivedDate], ['Expected', r.expectedDate], ['Delivered', r.deliveredDate], ['Item', r.itemDescription], ['Tag', r.itemTag], ['HUID', r.huid],
          ['Weight received', num(r.grossWeight) ? grams(r.grossWeight) : null], ['Work', r.problem], ['Estimate', money(r.estimate)], ['Final charge', num(r.finalAmount) ? money(r.finalAmount) : null],
          ['Paid', money(r.paid)], ['Due', money(r.balance)], ['Karigar', r.karigar], ['Notes', r.notes]]} />
        {active && !perms.isViewer && <div className="kActions">
          {NEXT_REPAIR[r.status] && <button type="button" className="primary" onClick={() => act({status: NEXT_REPAIR[r.status]}, `Repair marked ${label(NEXT_REPAIR[r.status]).toLowerCase()}.`)}>Mark {label(NEXT_REPAIR[r.status]).toLowerCase()}</button>}
          {r.status === 'READY' && <button type="button" className="primary" onClick={() => { setDeliver(true); setCharge(String(num(r.finalAmount) || num(r.estimate))); setPay({amount: String(Math.max(0, (num(r.finalAmount) || num(r.estimate)) - num(r.paid))), mode: 'CASH'}); }}>Deliver</button>}
          <select value={r.karigarId ?? ''} onChange={e => act({karigarId: e.target.value || null}, 'Karigar updated.')} aria-label="Karigar"><option value="">No karigar</option>{(md.karigars || []).map((k: any) => <option key={k.id} value={k.id}>{k.name}</option>)}</select>
          <button type="button" className="ghost danger" onClick={async () => { const why = await promptDialog({title: `Cancel repair ${r.orderNo}?`, label: 'Reason', confirmLabel: 'Cancel repair'}); if (why) await act({status: 'CANCELLED', notes: why}, 'Repair cancelled.'); }}>Cancel</button>
        </div>}
        {deliver && <div className="kNote"><b>Deliver to the customer</b>{num(r.grossWeight) > 0 && <> - check the weight: {grams(r.grossWeight)} received</>}
          <div className="kGrid" style={{marginTop: 8}}>
            <F label="Final charge (₹)"><input type="number" min="0" step="0.01" value={charge} onChange={e => setCharge(e.target.value)} /></F>
            <F label="Collect now (₹)"><input type="number" min="0" step="0.01" value={pay.amount} onChange={e => setPay({...pay, amount: e.target.value})} /></F>
            <F label="Mode"><select value={pay.mode} onChange={e => setPay({...pay, mode: e.target.value})}>{MONEY_MODES.map(x => <option key={x} value={x}>{label(x)}</option>)}</select></F>
          </div>
          {finalCharge - num(r.paid) - num(pay.amount) > 0.005 && <p className="kBad">Collect the full balance ({money(finalCharge - num(r.paid))}) to deliver.</p>}
          <div className="kActions"><button type="button" className="primary" disabled={finalCharge - num(r.paid) - num(pay.amount) > 0.005} onClick={() => act({status: 'DELIVERED', finalAmount: finalCharge, payment: num(pay.amount) > 0 ? {amount: num(pay.amount), mode: pay.mode} : undefined}, 'Repair delivered.')}>Confirm delivery</button><button type="button" className="ghost" onClick={() => setDeliver(false)}>Cancel</button></div>
        </div>}
        {(r.payments || []).length > 0 && <><h3 className="kSection">Payments</h3>{r.payments.map((p: any) => <div className="kRow" key={p.id}><b>{p.paymentNo}</b><span>{p.date}</span><span>{label(p.mode)}</span><span>{money(p.amount)}</span></div>)}</>}
      </>}
    </StateBox>
  </Sheet>;
}

/* ------------------------------------------------------------------------------------------------ karigar jobs */

function Jobs({md, onNotice}: {md: any; onNotice: (s: string) => void}) {
  const perms = usePermissions();
  const [status, setStatus] = useState('OPEN');
  const [creating, setCreating] = useState(false);
  const [openId, setOpenId] = useState<number | null>(null);
  const list = useData(() => jobs({status: status || undefined}), [status]);
  const rows: any[] = (list.data as any) || [];
  return <div className="panel">
    <div className="kRow"><select value={status} onChange={e => setStatus(e.target.value)} aria-label="Status"><option value="OPEN">Open</option><option value="CLOSED">Closed</option><option value="">All</option></select><span style={{flex: 1}} />
      {perms.canManage && <button type="button" className="primary" onClick={() => setCreating(true)} disabled={!(md.karigars || []).length} title={(md.karigars || []).length ? '' : 'Add a karigar first'}>＋ New job</button>}</div>
    <StateBox loading={list.loading} error={list.error} onRetry={list.reload} empty={!rows.length} emptyText="No karigar jobs here" emptyHint="Issue metal to a karigar for an order or repair, then record what comes back.">
      <Table><thead><tr><th>Job</th><th>Karigar</th><th>Work</th><th>Issued (fine)</th><th>Received (fine)</th><th>Difference</th><th>Due</th><th>Status</th></tr></thead>
        <tbody>{rows.map(j => <tr key={j.id} className="kClick" tabIndex={0} onClick={() => setOpenId(j.id)} onKeyDown={e => { if (e.key === 'Enter') setOpenId(j.id); }}>
          <td><b>{j.jobNo}</b><small>{j.date}{j.orderNo ? ` · ${j.orderNo}` : ''}</small></td><td>{j.karigar}</td><td>{j.description}<small>{j.purity}</small></td>
          <td>{grams(j.issuedFine)}</td><td>{grams(j.receivedFine)}</td><td>{j.differenceFine == null ? '—' : <b className={num(j.differenceFine) < 0 ? 'kBad' : ''}>{grams(j.differenceFine)}</b>}</td>
          <td>{j.dueDate || '—'}{j.overdue && <> <Pill value="OVERDUE" /></>}</td><td><Pill value={j.status} /></td></tr>)}</tbody></Table>
    </StateBox>
    <Sheet open={creating} onClose={() => setCreating(false)} kicker="KARIGAR JOB" title="Give work to a karigar">
      {creating && <JobForm md={md} onNotice={onNotice} onDone={id => { setCreating(false); void list.reload(); setOpenId(id); }} />}
    </Sheet>
    <JobSheet id={openId} onClose={() => setOpenId(null)} onNotice={onNotice} onChanged={list.reload} />
  </div>;
}

function JobForm({md, onNotice, onDone}: {md: any; onNotice: (s: string) => void; onDone: (id: number) => void}) {
  const [f, setF] = useState<any>({karigarId: '', description: '', purityId: '', dueDate: '', wastageAllowedPercent: '', labourCharge: '', issueWeight: '', customOrderId: '', repairOrderId: '', notes: ''});
  const openOrders = useData(() => orders(), []);
  const [busy, setBusy] = useState(false);
  const set = (k: string, v: any) => setF((x: any) => ({...x, [k]: v}));
  const save = async (e: FormEvent) => {
    e.preventDefault();
    setBusy(true);
    try {
      const r = await createJob({...f, karigarId: Number(f.karigarId), purityId: Number(f.purityId), wastageAllowedPercent: num(f.wastageAllowedPercent), labourCharge: num(f.labourCharge), issueWeight: num(f.issueWeight),
        customOrderId: f.customOrderId || undefined, repairOrderId: f.repairOrderId || undefined, dueDate: f.dueDate || undefined});
      onNotice(`Job ${r.data.jobNo} created.`);
      onDone(r.data.id);
    } catch (err) { onNotice(errText(err)); }
    finally { setBusy(false); }
  };
  return <form className="kGrid" onSubmit={save}>
    <F label="Karigar *"><select required value={f.karigarId} onChange={e => set('karigarId', e.target.value)}><option value="">Choose…</option>{(md.karigars || []).filter((k: any) => k.active !== false).map((k: any) => <option key={k.id} value={k.id}>{k.name}</option>)}</select></F>
    <F label="Purity *"><select required value={f.purityId} onChange={e => set('purityId', e.target.value)}><option value="">Choose…</option>{(md.purities || []).filter((p: any) => p.active !== false).map((p: any) => <option key={p.id} value={p.id}>{p.name}</option>)}</select></F>
    <F label="Work *" wide><input required value={f.description} onChange={e => set('description', e.target.value)} placeholder="e.g. Make 2 bangles 22K" /></F>
    <F label="For order"><select value={f.customOrderId} onChange={e => set('customOrderId', e.target.value)}><option value="">—</option>{((openOrders.data as any) || []).filter((o: any) => ['ORDERED', 'IN_PROGRESS'].includes(o.status)).map((o: any) => <option key={o.id} value={o.id}>{o.orderNo} · {o.customer?.name}</option>)}</select></F>
    <F label="Metal issued now (g)" hint="Gross grams of this purity"><input type="number" min="0" step="0.001" value={f.issueWeight} onChange={e => set('issueWeight', e.target.value)} /></F>
    <F label="Wastage allowed %"><input type="number" min="0" step="0.01" value={f.wastageAllowedPercent} onChange={e => set('wastageAllowedPercent', e.target.value)} /></F>
    <F label="Labour charge (₹)"><input type="number" min="0" step="0.01" value={f.labourCharge} onChange={e => set('labourCharge', e.target.value)} /></F>
    <F label="Due date"><input type="date" value={f.dueDate} onChange={e => set('dueDate', e.target.value)} /></F>
    <F label="Notes" wide><input value={f.notes} onChange={e => set('notes', e.target.value)} /></F>
    <div className="kActions kField wide"><button className="primary" disabled={busy}>{busy ? 'Saving…' : 'Create job'}</button></div>
  </form>;
}

function JobSheet({id, onClose, onNotice, onChanged}: {id: number | null; onClose: () => void; onNotice: (s: string) => void; onChanged: () => void}) {
  const perms = usePermissions();
  const d = useData(() => loadJob(id!), [id], !!id);
  const j: any = d.data;
  const [mv, setMv] = useState<{type: 'ISSUE' | 'RECEIPT'; weight: string; notes: string}>({type: 'RECEIPT', weight: '', notes: ''});
  const act = async (fn: () => Promise<unknown>, ok: string) => { try { await fn(); onNotice(ok); void d.reload(); onChanged(); } catch (e) { onNotice(errText(e)); } };
  const close = async () => {
    const short = num(j.differenceFine) < 0;
    if (!(await confirmDialog({title: `Close job ${j.jobNo}?`, message: short ? `The karigar still owes ${grams(Math.abs(num(j.differenceFine)))} fine gold beyond the allowed wastage. Close and accept the shortfall?` : 'The metal reconciles within the allowed wastage.', confirmLabel: short ? 'Accept shortfall and close' : 'Close job', tone: short ? 'danger' : 'primary'}))) return;
    await act(() => closeJob(j.id, {acceptShortfall: short}), 'Job closed.');
  };
  return <Sheet open={!!id} wide onClose={onClose} kicker="KARIGAR JOB" title={j ? `${j.jobNo} · ${j.karigar}` : 'Job'}>
    <StateBox loading={d.loading} error={d.error} onRetry={d.reload} empty={!j}>
      {j && <>
        <KV rows={[['Status', <Pill value={j.status} />], ['Work', j.description], ['Purity', j.purity], ['For', j.orderNo], ['Due', j.dueDate], ['Labour', money(j.labourCharge)], ['Wastage allowed', `${num(j.wastageAllowedPercent)} %`]]} />
        <div className="kStats">
          <div className="kStat"><span>Issued</span><strong>{grams(j.issued)}</strong><small>{grams(j.issuedFine)} fine</small></div>
          <div className="kStat"><span>Received back</span><strong>{grams(j.received)}</strong><small>{grams(j.receivedFine)} fine</small></div>
          <div className="kStat"><span>Expected back (fine)</span><strong>{grams(j.expectedFine)}</strong><small>allowed loss {grams(j.allowedLossFine)}</small></div>
          <div className="kStat"><span>Difference (fine)</span><strong className={num(j.differenceFine) < 0 ? 'kBad' : ''}>{j.differenceFine == null ? '—' : grams(j.differenceFine)}</strong><small>{num(j.differenceFine) < 0 ? 'karigar owes metal' : ''}</small></div>
        </div>
        {j.status === 'OPEN' && perms.canManage && <div className="kNote"><b>Record metal</b><div className="kRow">
          <select value={mv.type} onChange={e => setMv({...mv, type: e.target.value as any})} aria-label="Type"><option value="RECEIPT">Received back</option><option value="ISSUE">Issued more</option></select>
          <input type="number" min="0" step="0.001" placeholder="Grams" value={mv.weight} onChange={e => setMv({...mv, weight: e.target.value})} aria-label="Weight" />
          <input placeholder="Notes" value={mv.notes} onChange={e => setMv({...mv, notes: e.target.value})} aria-label="Notes" />
          <button type="button" className="secondary" disabled={!(num(mv.weight) > 0)} onClick={async () => { await act(() => jobMetal(j.id, {type: mv.type, weight: num(mv.weight), notes: mv.notes || undefined}), 'Metal recorded.'); setMv({...mv, weight: '', notes: ''}); }}>Save</button>
          <span style={{flex: 1}} /><button type="button" className="primary" onClick={close}>Close job</button></div></div>}
        <h3 className="kSection">Metal movements</h3>
        <StateBox empty={!(j.movements || []).length} emptyText="No metal recorded yet">
          <div className="tableWrap"><table><thead><tr><th>Date</th><th>Type</th><th>Weight</th><th>Fine</th><th>Notes</th></tr></thead>
            <tbody>{j.movements.map((x: any, i: number) => <tr key={i}><td>{x.date}</td><td>{label(x.type)}</td><td>{grams(x.weight)}</td><td>{grams(x.fineWeight)}</td><td>{x.notes}</td></tr>)}</tbody></table></div>
        </StateBox>
      </>}
    </StateBox>
  </Sheet>;
}

/* ------------------------------------------------------------------------------------------------ karigars */

function Karigars({md, reload, onNotice}: {md: any; reload: () => void; onNotice: (s: string) => void}) {
  const perms = usePermissions();
  const sum = useData(() => karigarSummary(), []);
  const [openId, setOpenId] = useState<number | null>(null);
  const rows: any[] = (sum.data as any) || [];
  return <div>
    <div className="panel">
      <div className="panelHead"><div><span className="eyebrow">KARIGAR POSITION</span><h2>Metal and labour</h2></div></div>
      <StateBox loading={sum.loading} error={sum.error} onRetry={sum.reload} empty={!rows.length} emptyText="No karigars yet">
        <Table><thead><tr><th>Karigar</th><th>Open jobs</th><th>Metal with them (fine)</th><th>Shortfall (fine)</th><th>Labour earned</th><th>Paid</th><th>Labour due</th></tr></thead>
          <tbody>{rows.map(k => <tr key={k.id} className="kClick" tabIndex={0} onClick={() => setOpenId(k.id)} onKeyDown={e => { if (e.key === 'Enter') setOpenId(k.id); }}><td><b>{k.name}</b><small>{k.phone}</small></td><td>{k.openJobs}</td><td>{grams(k.metalWithKarigarFine)}</td>
            <td>{num(k.shortfallFine) ? <b className="kBad">{grams(k.shortfallFine)}</b> : '—'}</td><td>{money(k.labourEarned)}</td><td>{money(k.labourPaid)}</td><td>{num(k.labourDue) > 0 ? <b>{money(k.labourDue)}</b> : '—'}</td></tr>)}</tbody></Table>
      </StateBox>
    </div>
    <div className="panel" style={{marginTop: 16}}>
      <MasterEditor kind="karigars" title="Karigars" addLabel="Add karigar" rows={md.karigars || []} onSaved={() => { reload(); void sum.reload(); }} onNotice={onNotice}
        fields={[{key: 'name', label: 'Name', required: true}, {key: 'phone', label: 'Phone'}, {key: 'address', label: 'Address', type: 'textarea'}, {key: 'notes', label: 'Notes', type: 'textarea'}]}
        columns={[{key: 'name', label: 'Karigar'}, {key: 'phone', label: 'Phone'}]} />
    </div>
    <KarigarSheet id={openId} onClose={() => setOpenId(null)} onNotice={onNotice} onChanged={sum.reload} canPay={perms.canManage} />
  </div>;
}

function KarigarSheet({id, onClose, onNotice, onChanged, canPay}: {id: number | null; onClose: () => void; onNotice: (s: string) => void; onChanged: () => void; canPay: boolean}) {
  const d = useData(() => loadKarigar(id!), [id], !!id);
  const k: any = d.data;
  const [pay, setPay] = useState({amount: '', mode: 'CASH', reference: ''});
  const save = async () => {
    try { await payKarigar(id!, {amount: num(pay.amount), mode: pay.mode, reference: pay.reference || undefined}); onNotice('Labour payment saved.'); setPay({amount: '', mode: 'CASH', reference: ''}); void d.reload(); onChanged(); }
    catch (e) { onNotice(errText(e)); }
  };
  return <Sheet open={!!id} wide onClose={onClose} kicker="KARIGAR" title={k?.name || 'Karigar'}>
    <StateBox loading={d.loading} error={d.error} onRetry={d.reload} empty={!k}>
      {k && <>
        <KV rows={[['Phone', k.phone], ['Open jobs', k.openJobs], ['Metal with karigar (fine)', grams(k.metalWithKarigarFine)], ['Shortfall (fine)', grams(k.shortfallFine)], ['Labour due', money(k.labourDue)]]} />
        {canPay && <div className="kNote"><b>Pay labour</b><div className="kRow">
          <input type="number" min="0" step="0.01" value={pay.amount} placeholder="Amount" onChange={e => setPay({...pay, amount: e.target.value})} aria-label="Amount" />
          <select value={pay.mode} onChange={e => setPay({...pay, mode: e.target.value})} aria-label="Mode">{MONEY_MODES.map(x => <option key={x} value={x}>{label(x)}</option>)}</select>
          {pay.mode !== 'CASH' && <input value={pay.reference} placeholder="Reference" onChange={e => setPay({...pay, reference: e.target.value})} aria-label="Reference" />}
          <button type="button" className="primary" disabled={!(num(pay.amount) > 0)} onClick={save}>Save payment</button></div></div>}
        <h3 className="kSection">Jobs</h3>
        <StateBox empty={!(k.jobs || []).length} emptyText="No jobs yet">
          <div className="tableWrap"><table><thead><tr><th>Job</th><th>Work</th><th>Issued fine</th><th>Received fine</th><th>Difference</th><th>Status</th></tr></thead>
            <tbody>{k.jobs.map((j: any) => <tr key={j.id}><td><b>{j.jobNo}</b><small>{j.date}</small></td><td>{j.description}</td><td>{grams(j.issuedFine)}</td><td>{grams(j.receivedFine)}</td><td>{j.differenceFine == null ? '—' : grams(j.differenceFine)}</td><td><Pill value={j.status} /></td></tr>)}</tbody></table></div>
        </StateBox>
        {(k.payments || []).length > 0 && <><h3 className="kSection">Payments</h3>{k.payments.map((p: any) => <div className="kRow" key={p.id}><b>{p.paymentNo}</b><span>{p.date}</span><span>{label(p.mode)}</span><span>{money(p.amount)}</span></div>)}</>}
      </>}
    </StateBox>
  </Sheet>;
}
