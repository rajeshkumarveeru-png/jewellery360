import {useEffect, useMemo, useRef, useState} from 'react';
import {
  cancelSale, checkInvoiceNo, createSale, customers as customersApi, deleteDraftSale, goldRateBoard, masters, nextInvoiceNo, oldGoldList, order as loadOrder, paySale, previewSale,
  returnSale, sale as loadSale, sales, saveCustomer, scanStock, stockItems
} from '../api';
import {usePermissions} from '../shared/permissions';
import {confirmDialog, promptDialog, successDialog} from '../shared/Notify';
import {Table} from '../shared/ui';
import {
  CustomerRef, KV, PaymentRows, PayRow, Pill, Sheet, StateBox, Tabs, customerBody, daysAgo, emptyPay, errText, grams,
  label, money, num, payBody, today, useData
} from '../shared/kit';
import {printBill, shareBillOnWhatsApp} from '../shared/billActions';
import type {PageProps} from './Dashboard';
import './Billing.css';
import BillSummary from './BillSummary';

/* ============================================================================================
   Billing: one counter for every sale (scan or search → review → pay → complete) and the list of bills.
   Every price on screen comes from the server's preview, which prices the bill exactly as completing it would.
   ============================================================================================ */

type Line = {itemId: number; tagNo: string; name: string; purity: string; net: number; rate?: string; wastagePercent?: string; makingValue?: string; stoneCharge?: string; otherCharge?: string};
type OgRow = {description: string; purityId: string; grossWeight: string; stoneWeight: string; deductionPercent: string};
const emptyOg = (): OgRow => ({description: 'Old gold', purityId: '', grossWeight: '', stoneWeight: '', deductionPercent: '0'});
const newRef = () => (crypto?.randomUUID ? crypto.randomUUID() : `${Date.now()}-${Math.random().toString(36).slice(2)}`);

export default function BillingModule({ready, onNotice, focus, go}: PageProps) {
  const [tab, setTab] = useState<'counter' | 'bills'>('counter');
  const [openBill, setOpenBill] = useState<number | null>(null);
  const [draft, setDraft] = useState<any>(null);
  useEffect(() => {
    if (!focus) return;
    if (focus.type === 'BILL') { setTab('bills'); setOpenBill(focus.id); }
    else if (focus.type === 'NEW_BILL' || focus.type === 'ORDER') setTab('counter');
  }, [focus?.n]);
  if (!ready) return <div className="panel"><StateBox empty emptyText="Choose a company and branch to start billing." /></div>;
  return <div>
    <Tabs value={tab} onChange={setTab} tabs={[{key: 'counter', label: 'New bill'}, {key: 'bills', label: 'Bills'}]} />
    {tab === 'counter' && <Counter onNotice={onNotice} focus={focus} draft={draft} clearDraft={() => setDraft(null)} onDone={id => { setOpenBill(id); }} go={go} />}
    {tab === 'bills' && <Bills onNotice={onNotice} openId={openBill} setOpenId={setOpenBill} onContinueDraft={d => { setDraft(d); setTab('counter'); }} />}
  </div>;
}

/* ------------------------------------------------------------------------------------------------ counter */

function Counter({onNotice, focus, draft, clearDraft, onDone, go}: {onNotice: (s: string) => void; focus: PageProps['focus']; draft: any; clearDraft: () => void; onDone: (id: number) => void; go: PageProps['go']}) {
  const perms = usePermissions();
  const [lines, setLines] = useState<Line[]>([]);
  const [customer, setCustomer] = useState<CustomerRef>(null);
  const [discount, setDiscount] = useState('');
  const [notes, setNotes] = useState('');
  const [saleDate, setSaleDate] = useState(today());
  const [og, setOg] = useState<OgRow[]>([]);
  const [ogExcess, setOgExcess] = useState('ADVANCE');
  const [payAmount, setPayAmount] = useState('');
  const [payMode, setPayMode] = useState('CASH');
  const [payRef, setPayRef] = useState('');
  const [ogUse, setOgUse] = useState<{id: number; no: string; available: number; amount: string}[]>([]);
  const [openOg, setOpenOg] = useState<any[]>([]);
  const [orderRef, setOrderRef] = useState<{id: number; orderNo: string; advance: number} | null>(null);
  const [draftId, setDraftId] = useState<number | null>(null);
  const [preview, setPreview] = useState<any>(null);
  const [previewErr, setPreviewErr] = useState('');
  const [busy, setBusy] = useState(false);
  const [clock, setClock] = useState(() => new Date());
  // invoice number: the server's next number is shown up front; the counter may type its own, which the server validates
  const [invNo, setInvNo] = useState('');
  const [invSuggest, setInvSuggest] = useState('');
  const [invAuto, setInvAuto] = useState(true);
  const [invErr, setInvErr] = useState('');
  // customer picker
  const [custQ, setCustQ] = useState('');
  const [custOpen, setCustOpen] = useState(false);
  const [custList, setCustList] = useState<any[]>([]);
  const [newCustOpen, setNewCustOpen] = useState(false);
  const [newCust, setNewCust] = useState({name: '', phone: '', email: '', gstin: '', address: ''});
  const [custSaving, setCustSaving] = useState(false);
  // scan + item picker
  const [code, setCode] = useState('');
  const [itemQ, setItemQ] = useState('');
  const [itemOpen, setItemOpen] = useState(false);
  const [itemList, setItemList] = useState<any[]>([]);
  const clientRef = useRef(newRef());
  const scanRef = useRef<HTMLInputElement>(null);
  const {data: m} = useData(() => masters(), []);
  const {data: board} = useData(() => goldRateBoard(), []);
  const purities: any[] = (m as any)?.purities?.filter((p: any) => p.active !== false) || [];

  useEffect(() => { const t = window.setInterval(() => setClock(new Date()), 30000); return () => window.clearInterval(t); }, []);

  const reset = () => {
    setLines([]); setCustomer(null); setDiscount(''); setNotes(''); setSaleDate(today()); setOg([]); setPayAmount(''); setPayMode('CASH'); setPayRef('');
    setOgUse([]); setOrderRef(null); setDraftId(null); setPreview(null); setPreviewErr(''); setCustQ(''); setItemQ(''); setCode('');
    setInvAuto(true); setInvErr('');
    clientRef.current = newRef(); clearDraft();
    window.setTimeout(() => scanRef.current?.focus(), 50);
  };

  // next invoice number (refreshed every 30 s while the suggestion is kept, so another counter's bill moves it on)
  useEffect(() => {
    if (!invAuto) return;
    nextInvoiceNo(saleDate !== today() ? saleDate : undefined).then(r => { setInvSuggest(r.data.invoiceNo); setInvNo(r.data.invoiceNo); setInvErr(''); }).catch(() => setInvSuggest(''));
  }, [saleDate, invAuto, clock]);

  // a typed invoice number is checked by the server while typing
  useEffect(() => {
    if (invAuto) return;
    const v = invNo.trim();
    const t = window.setTimeout(() => checkInvoiceNo(v).then(r => setInvErr(r.data.ok ? '' : r.data.message || 'Invalid invoice number.')).catch(e => setInvErr(errText(e))), 350);
    return () => window.clearTimeout(t);
  }, [invNo, invAuto]);

  // a draft opened from the Bills tab
  useEffect(() => {
    if (!draft) return;
    setDraftId(draft.id);
    setLines((draft.lines || []).map((l: any) => ({itemId: l.itemId, tagNo: l.tagNo, name: l.description, purity: l.purity, net: num(l.netWeight)})));
    setCustomer(draft.walkIn ? null : draft.customer ? {id: draft.customer.id, name: draft.customer.name, phone: draft.customer.phone} : null);
    setDiscount(num(draft.totals?.discount) ? String(draft.totals.discount) : '');
    setNotes(draft.notes || '');
  }, [draft?.id]);

  // final bill for a customer order (from Services)
  useEffect(() => {
    if (focus?.type === 'NEW_BILL') reset();
    if (focus?.type !== 'ORDER') return;
    loadOrder(focus.id).then(r => {
      const o = r.data;
      reset();
      setOrderRef({id: o.id, orderNo: o.orderNo, advance: num(o.advanceAvailable)});
      setCustomer({id: o.customer.id, name: o.customer.name, phone: o.customer.phone});
    }).catch(e => onNotice(errText(e)));
  }, [focus?.n]);

  useEffect(() => { scanRef.current?.focus(); }, []);

  // open old gold receipts (exchange) of the customer can pay this bill
  useEffect(() => {
    setOgUse([]);
    if (!customer?.id) { setOpenOg([]); return; }
    oldGoldList({openOnly: true}).then(r => setOpenOg((r.data || []).filter((x: any) => x.customer?.id === customer.id && num(x.available) > 0))).catch(() => setOpenOg([]));
  }, [customer?.id]);

  // customer dropdown: recent customers on focus, server search while typing
  useEffect(() => {
    if (!custOpen) return;
    const t = window.setTimeout(() => customersApi(custQ.trim() || undefined, 30).then(r => setCustList((r.data || []).filter((c: any) => !c.walkIn))).catch(() => setCustList([])), custQ ? 220 : 0);
    return () => window.clearTimeout(t);
  }, [custQ, custOpen]);

  // jewellery dropdown: pieces in stock in this branch on focus, server search while typing
  useEffect(() => {
    if (!itemOpen) return;
    const t = window.setTimeout(() => stockItems({status: 'IN_STOCK', q: itemQ.trim() || undefined, limit: 40}).then(r => setItemList(r.data || [])).catch(() => setItemList([])), itemQ ? 220 : 0);
    return () => window.clearTimeout(t);
  }, [itemQ, itemOpen]);

  const body = (forCreate: boolean) => ({
    ...customerBody(customer),
    customOrderId: orderRef?.id,
    saleDate: saleDate !== today() ? saleDate : undefined,
    discount: num(discount) || undefined,
    notes: notes || undefined,
    items: lines.map(l => ({itemId: l.itemId, rate: l.rate || undefined, wastagePercent: l.wastagePercent || undefined, makingValue: l.makingValue || undefined,
      stoneCharge: l.stoneCharge || undefined, otherCharge: l.otherCharge || undefined})),
    oldGold: og.filter(o => num(o.grossWeight) > 0).map(o => ({description: o.description, purityId: o.purityId || undefined, grossWeight: num(o.grossWeight), stoneWeight: num(o.stoneWeight), deductionPercent: num(o.deductionPercent)})),
    oldGoldExcess: ogExcess,
    ...(forCreate ? {clientRef: clientRef.current, draftId: draftId || undefined, invoiceNoAuto: invAuto, invoiceNo: invAuto ? undefined : invNo.trim()} : {})
  });

  // server preview: debounced on every change
  const sig = JSON.stringify(body(false));
  useEffect(() => {
    if (!lines.length) { setPreview(null); setPreviewErr(''); return; }
    const t = window.setTimeout(() => previewSale(body(false)).then(r => { setPreview(r.data); setPreviewErr(''); }).catch(e => setPreviewErr(errText(e))), 300);
    return () => window.clearTimeout(t);
  }, [sig]);

  const addPiece = (p: any) => {
    if (lines.some(l => l.itemId === p.id)) { onNotice(`${p.tagNo} is already on the bill.`); return; }
    setLines(ls => [...ls, {itemId: p.id, tagNo: p.tagNo, name: p.productName, purity: p.purity?.name || '', net: num(p.netWeight)}]);
    setCode(''); setItemQ(''); setItemOpen(false);
  };
  const scan = async () => {
    const q = code.trim();
    if (!q) return;
    try {
      const r = await scanStock(q);
      if (r.data?.available === false) { onNotice(r.data?.message || 'This piece cannot be billed here.'); return; }
      addPiece(r.data?.item || r.data);
    } catch (err) { onNotice(errText(err, 'No piece found for that tag, barcode or HUID.')); }
    scanRef.current?.focus();
  };
  const setLine = (i: number, p: Partial<Line>) => setLines(ls => ls.map((l, j) => j === i ? {...l, ...p} : l));
  const saveNewCustomer = async () => {
    if (!newCust.name.trim()) { onNotice('Enter the customer name.'); return; }
    setCustSaving(true);
    try {
      const r = await saveCustomer({...newCust, name: newCust.name.trim(), phone: newCust.phone.trim() || undefined});
      setCustomer({id: r.data.id, name: r.data.name, phone: r.data.phone});
      setNewCustOpen(false); setNewCust({name: '', phone: '', email: '', gstin: '', address: ''});
      onNotice(`Customer ${r.data.name} saved and selected.`);
    } catch (e) { onNotice(errText(e)); }
    finally { setCustSaving(false); }
  };

  const payable = num(preview?.payable);
  const ogPays = ogUse.filter(o => num(o.amount) > 0).map(o => ({mode: 'OLD_GOLD', oldGoldId: o.id, amount: num(o.amount)}));
  const due = Math.max(0, payable - ogPays.reduce((s, p) => s + p.amount, 0));
  const advanceAvail = num(preview?.customerAdvance);
  const modes = ['CASH', 'UPI', 'CARD', 'BANK', ...(advanceAvail > 0 ? ['ADVANCE'] : []), ...(customer ? ['CREDIT'] : [])];
  const complete = async (asDraft: boolean) => {
    if (!lines.length) { onNotice('Scan or search a piece to start the bill.'); return; }
    if (!asDraft && !invAuto && invErr) { onNotice(invErr); return; }
    const shownNo = invAuto ? invNo : '';
    const amount = payMode === 'CREDIT' ? 0 : Math.min(num(payAmount), due);
    const payments = asDraft ? [] : [...ogPays, ...(amount > 0 ? [{mode: payMode, amount, reference: payRef || undefined}] : [])];
    const paid = payments.reduce((s, p) => s + p.amount, 0);
    if (!asDraft && !customer && paid + 0.005 < payable) { onNotice('A walk-in bill must be paid in full. Pick or add the customer to give credit.'); return; }
    if (!asDraft && paid + 0.005 < payable && !(await confirmDialog({title: 'Leave a balance on credit?', message: `${customer?.name} will owe ${money(payable - paid)} on this bill.`, confirmLabel: 'Complete with credit'}))) return;
    setBusy(true);
    try {
      const r = await createSale({...body(true), draft: asDraft || undefined, payments});
      const b = r.data;
      if (asDraft) { onNotice('Draft saved. Open it from Bills to continue.'); reset(); return; }
      reset();
      onDone(b.id);
      if (shownNo && b.invoiceNo !== shownNo) onNotice(`${shownNo} was just used by another counter, so this bill is ${b.invoiceNo}.`);
      void successDialog({
        title: `Bill ${b.invoiceNo} completed`,
        message: `${money(b.total)} · ${b.label}`,
        details: [num(b.balance) > 0 ? `Balance due: ${money(b.balance)}` : 'Fully paid', `Customer: ${b.customer?.name || 'Walk-in'}`],
        actions: [
          {label: 'Print', tone: 'primary', run: () => void printBill(b.id, onNotice)},
          ...(b.customer?.phone ? [{label: 'WhatsApp', tone: 'whatsapp' as const, run: () => void shareBillOnWhatsApp(b.id, onNotice)}] : [])
        ],
        doneLabel: 'New bill'
      });
    } catch (e) { onNotice(errText(e)); }
    finally { setBusy(false); }
  };

  const rates: any[] = ((board as any)?.rates || []).filter((r: any) => r.ratePerGram != null);
  const rate22 = rates.find(r => /22/.test(r.purity)) || rates[0];
  const rateLabel = rate22 ? `${rate22.purity} ₹${num(rate22.ratePerGram).toLocaleString('en-IN')}/g` : 'Rate not set';
  const rateMissing = (preview?.lines || []).some((l: any) => l.rateMissing);
  const ogTotal = num(preview?.oldGoldValue);
  const time = clock.toLocaleTimeString('en-IN', {hour: '2-digit', minute: '2-digit'});
  const editable = (p: any) => perms.canOverride && !p?.fixedPrice;
  const cell = (i: number, l: Line, p: any, k: 'wastagePercent' | 'makingValue' | 'stoneCharge' | 'otherCharge', labelText: string, server: number, show: string) =>
    editable(p) ? <label className="pc" data-l={labelText}><input type="number" min="0" step="0.01" value={(l as any)[k] ?? ''} placeholder={p ? String(server) : ''} onChange={e => setLine(i, {[k]: e.target.value} as any)} aria-label={`${labelText} for ${l.tagNo}`} /></label>
      : <span className="ro" data-l={labelText}>{show}</span>;

  return <div className="pos">
    <form className="posMain" onSubmit={e => { e.preventDefault(); void complete(false); }}>
      <div className="posBar">
        <div className="posTitle"><b>New Bill</b><span className={`pill ${draftId ? '' : 'success'}`}>{draftId ? 'DRAFT' : 'READY'}</span></div>
        <label className={`pf pfInv${invErr ? ' bad' : ''}`}><span>Invoice no{invAuto ? <em className="pfTag">next</em> : <button type="button" className="pfTag" onClick={() => setInvAuto(true)} title="Use the next number in sequence">↺ auto</button>}</span>
          <input value={invNo} maxLength={16} placeholder={invSuggest || 'INV/..'} onChange={e => { const v = e.target.value.toUpperCase(); setInvNo(v); setInvAuto(!!invSuggest && v.trim() === invSuggest); }} aria-label="Invoice number" aria-invalid={!!invErr} title={invErr || (invAuto ? 'Next number in sequence - type to use your own' : 'Your own invoice number')} />
          {invErr && <small className="pfErr" role="alert">{invErr}</small>}</label>
        <label className="pf narrow"><span>Date</span><input type="date" value={saleDate} max={today()} onChange={e => setSaleDate(e.target.value || today())} aria-label="Bill date" /></label>
        <label className="pf narrow"><span>Time</span><input readOnly value={time} aria-label="Time" /></label>
        <label className="pf rate"><span>Gold ₹/g <em>saved rate</em></span><div className="goldRateAutoField"><input readOnly value={rate22 ? num(rate22.ratePerGram) : ''} placeholder="Not set" aria-label="Today's gold rate" onClick={() => go('Gold Rates')} />
          <span className={`goldRateStatus ${rate22 && rate22.setToday ? 'live' : 'fallback'}`}>{rate22 ? (rate22.setToday ? `● ${rate22.purity}` : 'Old rate') : 'Set rate'}</span></div></label>
      </div>

      <div className="posFind">
        <div className="pf cust"><span>Customer <em>{customer ? '' : 'optional for paid bills'}</em></span>
          <div className="customerSelectRow">
            <div className="customerPicker" onBlur={e => { if (!e.currentTarget.contains(e.relatedTarget as Node)) setCustOpen(false); }}>
              <input aria-label="Search customer" role="combobox" aria-expanded={custOpen} value={customer ? `${customer.name} · ${customer.phone || 'no phone'}` : custQ} placeholder="Search customer name or phone"
                     onFocus={() => setCustOpen(true)} onClick={() => setCustOpen(true)} onChange={e => { setCustQ(e.target.value); setCustOpen(true); if (customer) { setCustomer(null); setOrderRef(null); } }}
                     onKeyDown={e => { if (e.key === 'Escape') setCustOpen(false); if (e.key === 'Enter' && custList[0] && !customer) { e.preventDefault(); const c = custList[0]; setCustomer({id: c.id, name: c.name, phone: c.phone}); setCustQ(''); setCustOpen(false); } }} />
              {custOpen && !customer && <div className="customerPickerMenu">{custList.length ? custList.map(c =>
                <button type="button" key={c.id} className="customerPickerOption" onMouseDown={e => e.preventDefault()} onClick={() => { setCustomer({id: c.id, name: c.name, phone: c.phone}); setCustQ(''); setCustOpen(false); }}>
                  <strong>{c.name}</strong><span>{c.phone || 'No phone'}{num(c.owes) > 0 ? ` · owes ${money(c.owes)}` : ''}{num(c.advance) > 0 ? ` · advance ${money(c.advance)}` : ''}</span></button>)
                : <div className="customerPickerEmpty">{custQ ? 'No customer matches this search.' : 'No customers yet - use “New customer”.'}</div>}</div>}
            </div>
            <button type="button" className="secondary customerAddButton" onClick={() => { setNewCustOpen(v => !v); setCustOpen(false); }}>{newCustOpen ? 'Use existing' : '＋ New customer'}</button>
          </div>
        </div>
        <div className="pf scan"><span>Scan barcode / tag</span><div className="barcodeScanRow"><div className="smartScanField billingSmartScanField">
          <input ref={scanRef} value={code} onChange={e => setCode(e.target.value)} onKeyDown={e => { if (e.key === 'Enter') { e.preventDefault(); void scan(); } }} placeholder="Scan or enter barcode / tag / HUID" aria-label="Scan or enter barcode, tag or HUID" />
          <button type="button" className="fieldScanButton" onClick={() => void scan()} aria-label="Add the scanned piece"><span className="scanFieldIcon" aria-hidden="true">▦</span><span className="scanFieldText">Scan</span></button>
        </div></div></div>
        <div className="pf item"><span>Add jewellery <em>required</em></span>
          <div className="itemPicker" onBlur={e => { if (!e.currentTarget.contains(e.relatedTarget as Node)) setItemOpen(false); }}>
            <input aria-label="Search jewellery item" role="combobox" aria-expanded={itemOpen} value={itemQ} placeholder="Search product, tag, barcode or HUID"
                   onFocus={() => setItemOpen(true)} onClick={() => setItemOpen(true)} onChange={e => { setItemQ(e.target.value); setItemOpen(true); }}
                   onKeyDown={e => { if (e.key === 'Escape') setItemOpen(false); if (e.key === 'Enter' && itemList[0]) { e.preventDefault(); addPiece(itemList[0]); } }} />
            {itemOpen && <div className="itemPickerMenu">{itemList.filter(x => !lines.some(l => l.itemId === x.id)).length ? itemList.filter(x => !lines.some(l => l.itemId === x.id)).map(x =>
              <button type="button" key={x.id} className="itemPickerOption" onMouseDown={e => e.preventDefault()} onClick={() => addPiece(x)}>
                <strong>{x.productName}</strong><span>Tag: {x.tagNo} · Barcode: {x.barcode || '—'}{x.huid ? ` · HUID ${x.huid}` : ''}</span><small>{x.purity?.name} · Net {grams(x.netWeight)}</small></button>)
              : <div className="itemPickerEmpty">{itemQ ? 'No piece in stock matches this search.' : 'No pieces in stock in this branch.'}</div>}</div>}
          </div>
        </div>
      </div>

      {orderRef && <div className="kNote" style={{margin: 0}}>Final bill for order <b>{orderRef.orderNo}</b>{orderRef.advance > 0 && <> · order advance {money(orderRef.advance)} can be used (mode ADVANCE)</>} <button type="button" className="ghost" onClick={() => setOrderRef(null)}>Unlink</button></div>}
      {newCustOpen && <div className="inlineCustomerCard"><div className="inlineCustomerGrid">
        <input placeholder="Customer name *" value={newCust.name} onChange={e => setNewCust({...newCust, name: e.target.value})} />
        <input placeholder="Phone number" inputMode="tel" value={newCust.phone} onChange={e => setNewCust({...newCust, phone: e.target.value})} />
        <input placeholder="Email" value={newCust.email} onChange={e => setNewCust({...newCust, email: e.target.value})} />
        <input placeholder="GSTIN" value={newCust.gstin} onChange={e => setNewCust({...newCust, gstin: e.target.value.toUpperCase()})} />
        <input className="wide" placeholder="Address" value={newCust.address} onChange={e => setNewCust({...newCust, address: e.target.value})} />
      </div><button type="button" className="primary saveCustomerButton" disabled={custSaving} onClick={saveNewCustomer}>{custSaving ? 'Saving customer…' : 'Save customer & use for bill'}</button></div>}

      <div className="posCart">
        <div className="posCartHead"><b>Bill items</b><span className="posCount">{lines.length}</span>
          <small>{preview ? `${preview.taxMode === 'CGST_SGST' ? 'CGST + SGST' : 'GST'} @ ${num(preview.taxRate).toFixed(2)}% · set in Settings` : 'GST from Settings is applied automatically'}</small></div>
        {lines.length === 0 ? <div className="posEmpty"><b>No jewellery added yet</b><small>Scan a barcode or search above — items appear here priced at today's saved gold rate.</small></div> :
          <div className="posLines" role="table" aria-label="Bill items">
            <div className="posLine head" role="row"><span>#</span><span>Item</span><span>Gross g</span><span>Stone g</span><span>Net g</span><span>Wst %</span><span>Making ₹</span><span>Stone ₹</span><span>Other ₹</span><span className="r">Amount</span><span /></div>
            {lines.map((l, i) => { const p = preview?.lines?.[i]; return <div className="posLine" role="row" key={l.itemId}>
              <span className="n">{i + 1}</span>
              <div className="it"><b title={l.name}>{l.name}</b><small>{l.purity} · Tag {l.tagNo}{p?.huid ? ` · HUID ${p.huid}` : ''}{p?.rateMissing ? ' · rate not set' : p ? ` · ${money(p.rate)}/g` : ''}</small></div>
              <span className="ro" data-l="Gross g">{p ? num(p.grossWeight).toFixed(3) : '…'}</span>
              <span className="ro" data-l="Stone g">{p ? num(p.stoneWeight).toFixed(3) : '…'}</span>
              <span className="ro" data-l="Net g">{num(l.net).toFixed(3)}</span>
              {cell(i, l, p, 'wastagePercent', 'Wastage %', num(p?.wastagePercent), `${num(p?.wastagePercent)}`)}
              {cell(i, l, p, 'makingValue', 'Making ₹', num(p?.makingValue), money(p?.makingCharge))}
              {cell(i, l, p, 'stoneCharge', 'Stone ₹', num(p?.stoneCharge), money(p?.stoneCharge))}
              {cell(i, l, p, 'otherCharge', 'Other ₹', num(p?.otherCharge), money(p?.otherCharge))}
              <b className="amt">{p ? money(p.total) : '…'}</b>
              <button type="button" className="removeItemButton posX" aria-label={`Remove ${l.name}`} title="Remove item" onClick={() => setLines(ls => ls.filter((_, j) => j !== i))}>✕</button>
            </div>; })}
          </div>}
      </div>

      <details className="posOg" open={og.length > 0}>
        <summary>Old gold exchange{ogTotal > 0 ? ` · ${money(ogTotal)} adjusted on this bill` : ''}</summary>
        {og.map((o, i) => <div className="kRow" key={i} style={{marginTop: 6}}>
          <input placeholder="Description" value={o.description} onChange={e => setOg(og.map((x, j) => j === i ? {...x, description: e.target.value} : x))} aria-label="Old gold description" />
          <select value={o.purityId} onChange={e => setOg(og.map((x, j) => j === i ? {...x, purityId: e.target.value} : x))} aria-label="Purity"><option value="">Purity…</option>{purities.map(p => <option key={p.id} value={p.id}>{p.name}</option>)}</select>
          <input type="number" min="0" step="0.001" placeholder="Gross g" value={o.grossWeight} onChange={e => setOg(og.map((x, j) => j === i ? {...x, grossWeight: e.target.value} : x))} aria-label="Gross weight" />
          <input type="number" min="0" step="0.001" placeholder="Stone g" value={o.stoneWeight} onChange={e => setOg(og.map((x, j) => j === i ? {...x, stoneWeight: e.target.value} : x))} aria-label="Stone weight" />
          <input type="number" min="0" step="0.01" placeholder="Deduction %" value={o.deductionPercent} onChange={e => setOg(og.map((x, j) => j === i ? {...x, deductionPercent: e.target.value} : x))} aria-label="Deduction percent" />
          <button type="button" className="ghost danger" onClick={() => setOg(og.filter((_, j) => j !== i))} aria-label="Remove old gold line">✕</button>
        </div>)}
        <div className="kRow" style={{marginTop: 6}}>
          <button type="button" className="ghost" onClick={() => setOg([...og, emptyOg()])}>＋ Add old gold</button>
          {num(preview?.oldGoldExcess) > 0 && <label className="kMuted">Old gold worth {money(preview.oldGoldExcess)} more than the bill:&nbsp;
            <select value={ogExcess} onChange={e => setOgExcess(e.target.value)}><option value="ADVANCE">keep as advance</option><option value="CASH">pay cash</option><option value="UPI">pay UPI</option><option value="BANK">bank transfer</option></select></label>}
          {openOg.map(o => { const u = ogUse.find(y => y.id === o.id); return <label key={o.id} className="kMuted"><input type="checkbox" checked={!!u} onChange={e => setOgUse(e.target.checked ? [...ogUse, {id: o.id, no: o.transactionNo, available: num(o.available), amount: String(Math.min(num(o.available), payable))}] : ogUse.filter(y => y.id !== o.id))} /> Use earlier old gold {o.transactionNo} ({money(o.available)})</label>; })}
        </div>
      </details>

      <div className="posPay">
        <div className="posTax"><b>{preview ? `${preview.taxMode === 'CGST_SGST' ? 'CGST + SGST' : 'GST'} ${num(preview.taxRate).toFixed(2)}%` : 'GST'}</b><small>applied automatically</small></div>
        <label className="pf"><span>Discount ₹</span><input value={discount} type="number" min="0" step="0.01" disabled={!perms.canDiscount} title={perms.canDiscount ? '' : 'Your account cannot give discounts'} onChange={e => setDiscount(e.target.value)} placeholder="0" /></label>
        <label className="pf"><span>Payment received ₹</span><input value={payMode === 'CREDIT' ? '' : payAmount} type="number" min="0" step="0.01" disabled={payMode === 'CREDIT'} onChange={e => setPayAmount(e.target.value)} placeholder={payMode === 'CREDIT' ? 'On credit' : '0'} /></label>
        <label className="pf"><span>Payment mode</span><select value={payMode} onChange={e => setPayMode(e.target.value)}>{modes.map(x => <option key={x} value={x}>{x}</option>)}</select></label>
        {payMode !== 'CASH' && payMode !== 'CREDIT' && payMode !== 'ADVANCE' && <label className="pf"><span>Reference / UTR</span><input value={payRef} onChange={e => setPayRef(e.target.value)} placeholder="Optional" /></label>}
      </div>
    </form>

    <BillSummary draft={!!draftId} saleDate={saleDate} saleTime={time} rateLabel={rateLabel} customer={customer} pieces={lines.length} preview={preview} previewErr={previewErr}
      due={due} paymentAmount={num(payAmount)} paymentMode={payMode} modes={modes} rateMissing={rateMissing} busy={busy}
      onPayment={v => { setPayAmount(v ? String(v) : ''); if (payMode === 'CREDIT' && v > 0) setPayMode('CASH'); }} onMode={setPayMode}
      onDraft={() => void complete(true)} onComplete={() => void complete(false)} onRates={() => go('Gold Rates')} />
  </div>;
}

/* ------------------------------------------------------------------------------------------------ bills */

const STATUSES = [['', 'All bills'], ['UNPAID', 'With balance due'], ['DRAFT', 'Drafts'], ['COMPLETED', 'Completed'], ['PARTIALLY_RETURNED', 'Part returned'], ['RETURNED', 'Returned'], ['CANCELLED', 'Cancelled']];

function Bills({onNotice, openId, setOpenId, onContinueDraft}: {onNotice: (s: string) => void; openId: number | null; setOpenId: (id: number | null) => void; onContinueDraft: (d: any) => void}) {
  const [from, setFrom] = useState(daysAgo(30));
  const [to, setTo] = useState(today());
  const [status, setStatus] = useState('');
  const [q, setQ] = useState('');
  const [query, setQuery] = useState('');
  const list = useData(() => sales({from, to, status, q: query, limit: 300}), [from, to, status, query]);
  const rows: any[] = (list.data as any) || [];
  const totals = useMemo(() => rows.reduce((t, r) => ({total: t.total + (r.status === 'CANCELLED' || r.status === 'DRAFT' ? 0 : num(r.total) - num(r.returned)), due: t.due + num(r.balance)}), {total: 0, due: 0}), [rows]);
  return <div className="panel">
    <form className="kRow" onSubmit={e => { e.preventDefault(); setQuery(q.trim()); }}>
      <input type="search" placeholder="Invoice no., customer or phone" value={q} onChange={e => setQ(e.target.value)} aria-label="Search bills" />
      <select value={status} onChange={e => setStatus(e.target.value)} aria-label="Status">{STATUSES.map(([v, t]) => <option key={v} value={v}>{t}</option>)}</select>
      {status !== 'UNPAID' && <><input type="date" value={from} max={to} onChange={e => setFrom(e.target.value)} aria-label="From" /><input type="date" value={to} min={from} max={today()} onChange={e => setTo(e.target.value)} aria-label="To" /></>}
      <button type="submit" className="ghost">Search</button>
    </form>
    <div className="kStats" style={{marginTop: 12}}>
      <div className="kStat"><span>Bills shown</span><strong>{rows.length}</strong></div>
      <div className="kStat"><span>Net sales</span><strong>{money(totals.total)}</strong></div>
      <div className="kStat"><span>Balance due</span><strong>{money(totals.due)}</strong></div>
    </div>
    <StateBox loading={list.loading} error={list.error} onRetry={list.reload} empty={!rows.length} emptyText="No bills match these filters" emptyHint="Change the dates or status, or start a new bill.">
      <Table><thead><tr><th>Invoice</th><th>Date</th><th>Customer</th><th>Total</th><th>Paid</th><th>Due</th><th>Status</th></tr></thead>
        <tbody>{rows.map(r => <tr key={r.id} className="kClick" onClick={() => setOpenId(r.id)} tabIndex={0} onKeyDown={e => { if (e.key === 'Enter') setOpenId(r.id); }}>
          <td><b>{r.invoiceNo}</b><small>{r.cashier}</small></td><td>{r.date}</td><td>{r.customer?.name}<small>{r.customer?.phone}</small></td>
          <td>{money(r.total)}</td><td>{money(r.paid)}</td><td>{num(r.balance) > 0 ? <b>{money(r.balance)}</b> : '—'}</td><td><Pill value={r.label} /></td>
        </tr>)}</tbody></Table>
    </StateBox>
    <BillSheet id={openId} onClose={() => setOpenId(null)} onNotice={onNotice} onChanged={list.reload} onContinueDraft={onContinueDraft} />
  </div>;
}

export function BillSheet({id, onClose, onNotice, onChanged, onContinueDraft}: {id: number | null; onClose: () => void; onNotice: (s: string) => void; onChanged?: () => void; onContinueDraft?: (d: any) => void}) {
  const perms = usePermissions();
  const bill = useData(() => loadSale(id!), [id], !!id);
  const b: any = bill.data;
  const [mode, setMode] = useState<'view' | 'pay' | 'return'>('view');
  const [pays, setPays] = useState<PayRow[]>([emptyPay()]);
  const [picked, setPicked] = useState<number[]>([]);
  const [refundMode, setRefundMode] = useState('CASH');
  const [busy, setBusy] = useState(false);
  useEffect(() => { setMode('view'); setPicked([]); setPays([emptyPay()]); }, [id]);
  const refresh = () => { void bill.reload(); onChanged?.(); };
  const act = async (fn: () => Promise<unknown>, ok: string) => {
    setBusy(true);
    try { await fn(); onNotice(ok); setMode('view'); refresh(); return true; }
    catch (e) { onNotice(errText(e)); return false; }
    finally { setBusy(false); }
  };
  const cancel = async () => {
    const reason = await promptDialog({title: `Cancel bill ${b.invoiceNo}?`, message: 'Pieces go back to stock and payments are reversed. Money received is refunded (or kept as advance).', label: 'Reason for cancelling', confirmLabel: 'Next'});
    if (!reason) return;
    const keep = num(b.paid) > 0 && b.customer && !b.walkIn && await confirmDialog({title: 'Refund or keep as advance?', message: `${money(b.paid)} was received on this bill.`, confirmLabel: 'Keep as advance', cancelLabel: 'Refund now'});
    await act(() => cancelSale(b.id, {reason, refundMode: keep ? 'ADVANCE' : refundMode}), `Bill ${b.invoiceNo} cancelled.`);
  };
  const open = !!id;
  const returnable = (b?.lines || []).filter((l: any) => !l.returned);
  return <Sheet open={open} wide onClose={onClose} kicker="BILL" title={b ? `${b.invoiceNo} · ${b.label}` : 'Bill'}>
    <StateBox loading={bill.loading} error={bill.error} onRetry={bill.reload} empty={!b}>
      {b && <>
        <KV rows={[['Date', b.date], ['Customer', b.walkIn ? 'Walk-in' : `${b.customer?.name}${b.customer?.phone ? ` · ${b.customer.phone}` : ''}`], ['Branch', b.branchName], ['Billed by', b.cashier],
          ['Order', b.customOrderNo], ['Total', money(b.total)], ['Paid', money(b.paid)], ['Returned', num(b.returned) ? money(b.returned) : null], ['Balance due', num(b.balance) ? money(b.balance) : null],
          ['Cancel reason', b.cancelReason], ['Notes', b.notes]]} />
        <div className="kActions">
          {b.status !== 'DRAFT' && <button type="button" className="secondary" onClick={() => printBill(b.id, onNotice)}>Print</button>}
          {b.status !== 'DRAFT' && b.customer?.phone && <button type="button" className="secondary" onClick={() => shareBillOnWhatsApp(b.id, onNotice)}>WhatsApp</button>}
          {num(b.balance) > 0 && !perms.isViewer && <button type="button" className="primary" onClick={() => { setMode('pay'); setPays([emptyPay(String(num(b.balance)))]); }}>Take payment</button>}
          {['COMPLETED', 'PARTIALLY_RETURNED'].includes(b.status) && perms.canCancel && <button type="button" className="secondary" onClick={() => setMode('return')}>Return pieces</button>}
          {b.status === 'COMPLETED' && perms.canCancel && <button type="button" className="ghost danger" disabled={busy} onClick={cancel}>Cancel bill</button>}
          {b.status === 'DRAFT' && onContinueDraft && <button type="button" className="primary" onClick={() => { onContinueDraft(b); onClose(); }}>Continue draft</button>}
          {b.status === 'DRAFT' && <button type="button" className="ghost danger" onClick={async () => { if (await confirmDialog({title: 'Delete this draft?', confirmLabel: 'Delete', tone: 'danger'}) && await act(() => deleteDraftSale(b.id), 'Draft deleted.')) onClose(); }}>Delete draft</button>}
        </div>

        {mode === 'pay' && <div className="kNote">
          <b>Take payment</b>
          <PaymentRows rows={pays} onChange={setPays} extraModes={['ADVANCE']} due={num(b.balance)} />
          <div className="kActions"><button type="button" className="primary" disabled={busy} onClick={() => act(() => paySale(b.id, payBody(pays)), 'Payment saved.')}>Save payment</button><button type="button" className="ghost" onClick={() => setMode('view')}>Cancel</button></div>
        </div>}

        {mode === 'return' && <div className="kNote">
          <b>Return pieces</b> - they go back to stock; anything paid above the new total is refunded.
          {returnable.map((l: any) => <label key={l.id} className="kRow"><input type="checkbox" checked={picked.includes(l.id)} onChange={e => setPicked(e.target.checked ? [...picked, l.id] : picked.filter(x => x !== l.id))} /> {l.tagNo} · {l.description} · {money(l.total)}</label>)}
          <div className="kRow"><span className="kMuted">Refund by</span><select value={refundMode} onChange={e => setRefundMode(e.target.value)} aria-label="Refund mode">{['CASH', 'UPI', 'CARD', 'BANK', ...(b.walkIn ? [] : ['ADVANCE'])].map(x => <option key={x} value={x}>{x === 'ADVANCE' ? 'Keep as advance' : label(x)}</option>)}</select></div>
          <div className="kActions"><button type="button" className="primary" disabled={busy || !picked.length} onClick={async () => {
            const reason = await promptDialog({title: 'Reason for the return', label: 'Reason', confirmLabel: 'Return pieces'});
            if (reason !== null) await act(() => returnSale(b.id, {saleItemIds: picked, reason: reason || undefined, refundMode}), 'Return saved. The pieces are back in stock.');
          }}>Return {picked.length || ''} piece{picked.length === 1 ? '' : 's'}</button><button type="button" className="ghost" onClick={() => setMode('view')}>Cancel</button></div>
        </div>}

        <h3 className="kSection">Pieces</h3>
        <div className="tableWrap"><table><thead><tr><th>Piece</th><th>Net wt</th><th>Rate</th><th>Making</th><th>GST</th><th>Total</th></tr></thead>
          <tbody>{(b.lines || []).map((l: any) => <tr key={l.id}><td><b>{l.tagNo}{l.returned && <> <Pill value="Returned" /></>}</b><small>{l.description} · {l.purity}{l.huid ? ` · HUID ${l.huid}` : ''}</small></td>
            <td>{grams(l.netWeight)}</td><td>{money(l.rate)}</td><td>{money(l.makingCharge)}</td><td>{money(l.gst)}</td><td><b>{money(l.total)}</b></td></tr>)}</tbody></table></div>
        <div className="kTotals" style={{marginTop: 10}}>
          <div><span>Taxable</span><b>{money(b.totals?.taxable)}</b></div>
          <div><span>GST</span><b>{money(b.totals?.gst)}</b></div>
          {num(b.totals?.discount) > 0 && <div><span>Discount</span><b>− {money(b.totals.discount)}</b></div>}
          <div className="big"><span>Total</span><b>{money(b.total)}</b></div>
        </div>
        {(b.payments || []).length > 0 && <><h3 className="kSection">Payments</h3>
          <div className="tableWrap"><table><thead><tr><th>No.</th><th>Date</th><th>Mode</th><th>Amount</th></tr></thead>
            <tbody>{b.payments.map((p: any) => <tr key={p.id}><td>{p.paymentNo}<small>{p.reference}</small></td><td>{p.date}</td><td>{label(p.type)} · {label(p.mode)}</td><td>{p.direction === 'OUT' ? '− ' : ''}{money(p.amount)}</td></tr>)}</tbody></table></div></>}
        {(b.returns || []).length > 0 && <><h3 className="kSection">Returns</h3>{b.returns.map((r: any) => <div key={r.id} className="kRow"><b>{r.returnNo}</b><span>{r.date}</span><span>{money(r.amount)}</span><span className="kMuted">{r.reason}</span></div>)}</>}
      </>}
    </StateBox>
  </Sheet>;
}
