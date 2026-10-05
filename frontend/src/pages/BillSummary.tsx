import {useEffect, useMemo, useRef, useState} from 'react';
import type {CSSProperties} from 'react';

/* ------------------------------------------------------------------------------------------------
   Bill summary ("jewellery case") for the billing counter.
   - Always dark with its own colour tokens, so no theme can make its text unreadable.
   - Shows a LIVE estimate while the cashier types; "Calculate total" confirms it with the server.
   - The estimate mirrors the server rules in JewelleryBillingService.calculate (wastage and tax rounded to 3 places,
     discount capped at subtotal + tax, tax settings come from Settings), but the server value always wins.
   ------------------------------------------------------------------------------------------------ */

export type SummaryLine = {
  id: string; name: string; tag: string; purity: string;
  grossWeight: number; stoneWeight: number; netWeight: number; goldRate: number;
  wastagePercent: number; makingCharge: number; stoneCharge: number; otherCharge: number;
};

export type SummaryTax = {enabled: boolean; mode: 'GST' | 'CGST_SGST' | 'NONE'; rate: number; cgstRate: number; sgstRate: number; totalRate: number};

type Estimate = {
  items: number; gross: number; stone: number; net: number;
  goldValue: number; wastage: number; making: number; stoneCharge: number; other: number;
  subtotal: number; gst: number; cgst: number; sgst: number; discount: number; total: number;
};

const r3 = (n: number) => Math.round((n + Number.EPSILON) * 1000) / 1000;

export function estimateBill(lines: SummaryLine[], commonRate: number, tax: SummaryTax, discountInput: number): Estimate {
  let gross = 0, stone = 0, net = 0, goldValue = 0, wastage = 0, making = 0, stoneCharge = 0, other = 0;
  for (const l of lines) {
    const rate = commonRate > 0 ? commonRate : Number(l.goldRate || 0);
    const n = Number(l.netWeight || 0);
    const gold = n * rate;
    gross += Number(l.grossWeight || 0); stone += Number(l.stoneWeight || 0); net += n;
    goldValue += gold;
    wastage += r3((gold * Number(l.wastagePercent || 0)) / 100);
    making += Number(l.makingCharge || 0);
    stoneCharge += Number(l.stoneCharge || 0);
    other += Number(l.otherCharge || 0);
  }
  const subtotal = goldValue + wastage + making + stoneCharge + other;
  const totalRate = tax.enabled ? Number(tax.totalRate || 0) : 0;
  const gst = r3((subtotal * totalRate) / 100);
  const split = tax.enabled && tax.mode === 'CGST_SGST';
  const cgst = split ? r3((subtotal * Number(tax.cgstRate || 0)) / 100) : 0;
  const sgst = split ? r3((subtotal * Number(tax.sgstRate || 0)) / 100) : 0;
  const discount = Math.min(Math.max(Number(discountInput || 0), 0), subtotal + gst);
  const total = Math.max(subtotal + gst - discount, 0);
  return {items: lines.length, gross, stone, net, goldValue, wastage, making, stoneCharge, other, subtotal, gst, cgst, sgst, discount, total};
}

const inr = (v: unknown) => `₹${Number(v || 0).toLocaleString('en-IN', {minimumFractionDigits: 2, maximumFractionDigits: 2})}`;
const grams = (v: number) => `${v.toLocaleString('en-IN', {minimumFractionDigits: 3, maximumFractionDigits: 3})} g`;

/** smooth count-up for the headline amount (respects reduced motion) */
function useCountUp(target: number): number {
  const [shown, setShown] = useState(target);
  const from = useRef(target);
  useEffect(() => {
    const reduce = typeof window !== 'undefined' && window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    if (reduce || Math.abs(target - from.current) < 0.005) { setShown(target); from.current = target; return; }
    const start = performance.now();
    const origin = from.current;
    const duration = 450;
    let raf = 0;
    const tick = (t: number) => {
      const k = Math.min(1, (t - start) / duration);
      const eased = 1 - Math.pow(1 - k, 3);
      const v = origin + (target - origin) * eased;
      setShown(v);
      if (k < 1) raf = requestAnimationFrame(tick); else from.current = target;
    };
    raf = requestAnimationFrame(tick);
    return () => cancelAnimationFrame(raf);
  }, [target]);
  return shown;
}

type Props = {
  invoiceNo: string;
  saleDate: string;
  saleTime: string;
  customer: {name?: string; phone?: string} | null;
  lines: SummaryLine[];
  goldRate: number;
  tax: SummaryTax;
  discount: number;
  paymentAmount: number;
  paymentMode: string;
  calc: any | null;
  printFormat: string;
  onCalculate: () => void;
  onPayment: (amount: number) => void;
  onMode: (mode: string) => void;
  onRemove: (id: string) => void;
  formId: string;
  lastCreated: any | null;
  onPrint: () => void;
  onWhatsApp: () => void;
  onNewBill: () => void;
};

const MODES = ['CASH', 'UPI', 'CARD', 'CREDIT'];
const PRINT_LABEL: Record<string, string> = {A4: 'A4 invoice', '80MM': '80 mm receipt', '50MM': '50 mm receipt'};

export default function BillSummary(p: Props) {
  const est = useMemo(() => estimateBill(p.lines, p.goldRate, p.tax, p.discount), [p.lines, p.goldRate, p.tax, p.discount]);
  const verified = !!p.calc && Math.abs(Number(p.calc.total || 0) - est.total) < 0.01;
  const shownTotal = verified ? Number(p.calc.total) : est.total;
  const animated = useCountUp(shownTotal);
  const received = Math.min(Math.max(Number(p.paymentAmount || 0), 0), shownTotal);
  const balance = Math.max(shownTotal - received, 0);
  const paidPct = shownTotal > 0 ? Math.min(100, Math.round((received / shownTotal) * 100)) : 0;
  const hasItems = p.lines.length > 0;
  const status = !hasItems ? 'Draft' : verified ? 'Verified' : 'Live estimate';
  const cgst = verified && p.calc.cgst != null ? Number(p.calc.cgst) : est.cgst;
  const sgst = verified && p.calc.sgst != null ? Number(p.calc.sgst) : est.sgst;
  const gst = verified && p.calc.gst != null ? Number(p.calc.gst) : est.gst;

  const steps = [
    {key: 'customer', label: 'Customer', done: !!p.customer, hint: p.customer ? (p.customer.name || 'Selected') : 'Select a customer'},
    {key: 'items', label: 'Jewellery', done: hasItems, hint: hasItems ? `${p.lines.length} item${p.lines.length === 1 ? '' : 's'}` : 'Scan or search an item'},
    {key: 'rate', label: 'Gold rate', done: p.goldRate > 0, hint: p.goldRate > 0 ? `₹${p.goldRate.toLocaleString('en-IN')}/g` : 'Waiting for live rate'},
    {key: 'pay', label: 'Payment', done: hasItems && received >= shownTotal && shownTotal > 0, hint: !hasItems ? 'Add items first' : received >= shownTotal ? 'Paid in full' : received > 0 ? 'Part payment' : 'Nothing received'}
  ];
  const ready = steps.slice(0, 3).every(s => s.done);

  const taxRows: [string, number][] = !p.tax.enabled
    ? [['Tax (off in Settings)', 0]]
    : p.tax.mode === 'CGST_SGST'
      ? [[`CGST @ ${p.tax.cgstRate.toFixed(2)}%`, cgst], [`SGST @ ${p.tax.sgstRate.toFixed(2)}%`, sgst]]
      : [[`GST @ ${p.tax.rate.toFixed(2)}%`, gst]];

  const rows: [string, number, string][] = [
    ['Gold value', verified && p.calc.goldValue != null ? Number(p.calc.goldValue) : est.goldValue, '◈'],
    ['Wastage', verified && p.calc.wastageValue != null ? Number(p.calc.wastageValue) : est.wastage, '◇'],
    ['Making charges', est.making, '✦'],
    ['Stone & other', est.stoneCharge + est.other, '◆']
  ];

  /* ------------------------------------------------ after the bill has been created */
  if (p.lastCreated) {
    return (
      <section className="bsum bsumDone" aria-live="polite">
        <div className="bsumGlow" aria-hidden="true" />
        <div className="bsumCheck" aria-hidden="true"><svg viewBox="0 0 52 52"><circle cx="26" cy="26" r="23" /><path d="M15 27l8 8 15-17" /></svg></div>
        <span className="bsumEyebrow">INVOICE CREATED</span>
        <h2 className="bsumInvoice">{p.lastCreated.invoiceNo}</h2>
        <p className="bsumDoneText">{p.lastCreated.itemCount} jewellery item{p.lastCreated.itemCount === 1 ? '' : 's'} billed. Stock was reduced and the sale is saved.</p>
        <div className="bsumDoneTotal"><span>Amount billed</span><strong>{inr(p.lastCreated.total ?? shownTotal)}</strong></div>
        <div className="bsumChips"><span>Prints as {PRINT_LABEL[p.printFormat] || 'A4 invoice'}</span>{p.lastCreated.paymentStatus && <span>{String(p.lastCreated.paymentStatus).replace('_', ' ')}</span>}</div>
        <div className="bsumActions col">
          <button type="button" className="bsumBtn gold" onClick={p.onPrint}>🖨 Print invoice</button>
          <button type="button" className="bsumBtn outline" onClick={p.onWhatsApp}>◌ Send on WhatsApp</button>
          <button type="button" className="bsumBtn ghost" onClick={p.onNewBill}>＋ Start a new bill</button>
        </div>
      </section>
    );
  }

  return (
    <section className={`bsum${hasItems ? '' : ' empty'}`} aria-label="Bill summary">
      <div className="bsumGlow" aria-hidden="true" />

      <header className="bsumHead">
        <div>
          <span className="bsumEyebrow">BILL SUMMARY</span>
          <h2 className="bsumInvoice" title={p.invoiceNo}>{p.invoiceNo}</h2>
        </div>
        <span className={`bsumStatus ${verified ? 'ok' : hasItems ? 'live' : 'idle'}`}><i aria-hidden="true" />{status}</span>
      </header>

      <div className="bsumMeta"><span>◷ {p.saleDate}{p.saleTime ? ` · ${p.saleTime}` : ''}</span><span>◆ ₹{p.goldRate ? p.goldRate.toLocaleString('en-IN') : '—'}/g</span><span>🖨 {PRINT_LABEL[p.printFormat] || 'A4 invoice'}</span></div>

      <div className="bsumScroll">
      <div className={`bsumCustomer${p.customer ? ' has' : ''}`}>
        <span className="bsumAvatar" aria-hidden="true">{p.customer ? String(p.customer.name || '?').trim().charAt(0).toUpperCase() : '＋'}</span>
        <div><b>{p.customer ? p.customer.name : 'No customer selected'}</b><small>{p.customer ? (p.customer.phone || 'No phone on file') : 'Choose or add a customer to bill'}</small></div>
      </div>

      <ol className="bsumSteps" aria-label="Billing checklist">
        {steps.map((s, i) => (
          <li key={s.key} className={s.done ? 'done' : ''} style={{'--i': i} as CSSProperties}>
            <span className="bsumStepDot" aria-hidden="true">{s.done ? '✓' : i + 1}</span>
            <span className="bsumStepText"><b>{s.label}</b><small>{s.hint}</small></span>
          </li>
        ))}
      </ol>

      <div className="bsumWeights" aria-label="Weights">
        <div><small>Gross</small><b>{grams(est.gross)}</b></div>
        <div><small>Stone</small><b>{grams(est.stone)}</b></div>
        <div className="net"><small>Net gold</small><b>{grams(est.net)}</b></div>
      </div>

      <div className="bsumRows">
        {rows.map(([label, value, icon]) => (
          <div key={label}><span><i aria-hidden="true">{icon}</i>{label}</span><b>{inr(value)}</b></div>
        ))}
        <div className="sub"><span>Subtotal</span><b>{inr(verified && p.calc.subtotal != null ? p.calc.subtotal : est.subtotal)}</b></div>
        {taxRows.map(([label, value]) => <div key={label}><span>{label}</span><b>{inr(value)}</b></div>)}
        {est.discount > 0 && <div className="disc"><span>Discount</span><b>− {inr(verified && p.calc.discount != null ? p.calc.discount : est.discount)}</b></div>}
      </div>

      </div>

      <div className="bsumDock">
      <div className="bsumTotal">
        <span>Total payable</span>
        <strong aria-live="polite">{inr(animated)}</strong>
        <small>{verified ? 'Confirmed by the server' : hasItems ? 'Live estimate — press Calculate total to confirm' : 'Add jewellery to begin'}</small>
      </div>

      <div className="bsumPay">
        <div className="bsumPayHead"><span>Payment received</span><b>{inr(received)}</b></div>
        <div className="bsumBar" role="progressbar" aria-valuemin={0} aria-valuemax={100} aria-valuenow={paidPct} aria-label="Share of the bill received"><i style={{width: `${paidPct}%`}} /></div>
        <div className={`bsumBalance${balance > 0.004 ? ' due' : ' paid'}`}>
          <span>{balance > 0.004 ? 'Balance due' : shownTotal > 0 ? 'Paid in full' : 'Balance'}</span><b>{inr(balance)}</b>
        </div>
        <div className="bsumQuick" role="group" aria-label="Quick payment amount">
          <button type="button" disabled={!hasItems} onClick={() => p.onPayment(Number(shownTotal.toFixed(2)))}>Full</button>
          <button type="button" disabled={!hasItems} onClick={() => p.onPayment(Number((shownTotal / 2).toFixed(2)))}>50% advance</button>
          <button type="button" disabled={!hasItems} onClick={() => p.onPayment(0)}>On credit</button>
        </div>
        <div className="bsumModes" role="radiogroup" aria-label="Payment mode">
          {MODES.map(m => <button key={m} type="button" role="radio" aria-checked={p.paymentMode === m} className={p.paymentMode === m ? 'on' : ''} onClick={() => p.onMode(m)}>{m}</button>)}
        </div>
      </div>

      <div className="bsumActions">
        <button type="button" className="bsumBtn outline" onClick={p.onCalculate} disabled={!hasItems}>Calculate total</button>
        <button type="submit" form={p.formId} className="bsumBtn gold" disabled={!ready}>{ready ? 'Create bill' : 'Complete checklist'}</button>
      </div>
      <p className="bsumFoot">Tax follows Settings · Gold rate is the live market rate · Payment is capped at the bill total.</p>
      </div>
    </section>
  );
}
