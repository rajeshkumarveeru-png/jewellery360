import {useEffect, useRef, useState} from 'react';
import type {CSSProperties} from 'react';

/* ------------------------------------------------------------------------------------------------
   Bill summary ("jewellery case") for the billing counter - same look as before (bsum* styles).
   - Always dark with its own colour tokens, so no theme can make its text unreadable.
   - Every amount comes from the server preview, which prices the bill exactly as completing it will.
   - Phones: the summary is a bottom sheet opened from a floating total bar.
   ------------------------------------------------------------------------------------------------ */

const inr = (v: unknown) => `₹${Number(v || 0).toLocaleString('en-IN', {minimumFractionDigits: 2, maximumFractionDigits: 2})}`;
const grams = (v: number) => `${v.toLocaleString('en-IN', {minimumFractionDigits: 3, maximumFractionDigits: 3})} g`;
const n = (v: unknown) => { const x = Number(v); return Number.isFinite(x) ? x : 0; };

/** smooth count-up for the headline amount (respects reduced motion) */
function useCountUp(target: number): number {
  const [shown, setShown] = useState(target);
  const from = useRef(target);
  useEffect(() => {
    const reduce = typeof window !== 'undefined' && window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    if (reduce || Math.abs(target - from.current) < 0.005) { setShown(target); from.current = target; return; }
    const start = performance.now();
    const origin = from.current;
    let raf = 0;
    const tick = (t: number) => {
      const k = Math.min(1, (t - start) / 450);
      setShown(origin + (target - origin) * (1 - Math.pow(1 - k, 3)));
      if (k < 1) raf = requestAnimationFrame(tick); else from.current = target;
    };
    raf = requestAnimationFrame(tick);
    return () => cancelAnimationFrame(raf);
  }, [target]);
  return shown;
}

type Props = {
  draft: boolean;
  saleDate: string;
  saleTime: string;
  rateLabel: string;
  customer: {name?: string; phone?: string} | null;
  pieces: number;
  preview: any | null;
  previewErr: string;
  due: number;
  paymentAmount: number;
  paymentMode: string;
  modes: string[];
  rateMissing: boolean;
  busy: boolean;
  onPayment: (amount: number) => void;
  onMode: (mode: string) => void;
  onDraft: () => void;
  onComplete: () => void;
  onRates: () => void;
};

export default function BillSummary(p: Props) {
  const [sheet, setSheet] = useState(false);
  const pr = p.preview;
  const hasItems = p.pieces > 0;
  const animated = useCountUp(p.due);
  const received = p.paymentMode === 'CREDIT' ? 0 : Math.min(Math.max(n(p.paymentAmount), 0), p.due);
  const balance = Math.max(p.due - received, 0);
  const paidPct = p.due > 0 ? Math.min(100, Math.round((received / p.due) * 100)) : 0;
  const status = !hasItems ? 'Draft' : p.previewErr ? 'Check bill' : pr ? 'Priced' : 'Pricing…';
  const w = (pr?.lines || []).reduce((t: any, l: any) => ({g: t.g + n(l.grossWeight), s: t.s + n(l.stoneWeight), net: t.net + n(l.netWeight)}), {g: 0, s: 0, net: 0});
  const walkInShort = !p.customer && hasItems && received + 0.005 < p.due;
  const steps = [
    {key: 'customer', label: 'Customer', done: !!p.customer || (hasItems && !walkInShort), hint: p.customer ? (p.customer.name || 'Selected') : 'Walk-in (pay in full)'},
    {key: 'items', label: 'Jewellery', done: hasItems, hint: hasItems ? `${p.pieces} item${p.pieces === 1 ? '' : 's'}` : 'Scan or search an item'},
    {key: 'rate', label: 'Gold rate', done: hasItems && !!pr && !p.rateMissing, hint: p.rateMissing ? 'Set today’s rate' : 'Saved rate'},
    {key: 'pay', label: 'Payment', done: hasItems && received + 0.005 >= p.due && p.due > 0, hint: !hasItems ? 'Add items first' : received + 0.005 >= p.due ? 'Paid in full' : received > 0 ? 'Part payment' : 'On credit'}
  ];
  const ready = hasItems && !!pr && !p.previewErr && !p.rateMissing && !walkInShort;
  const taxRows: [string, number][] = !pr ? [['GST', 0]] : pr.taxMode === 'CGST_SGST'
    ? [['CGST', n(pr.cgst)], ['SGST', n(pr.sgst)]]
    : [[n(pr.taxRate) ? `GST @ ${n(pr.taxRate).toFixed(2)}%` : 'Tax', n(pr.gst)]];
  const rows: [string, number, string][] = [
    ['Gold value', n(pr?.metalValue), '◈'],
    ['Wastage', n(pr?.wastage), '◇'],
    ['Making charges', n(pr?.making), '✦'],
    ['Stone & other', n(pr?.stone) + n(pr?.other), '◆']
  ];

  return (
    <>
    <button type="button" className="bsumFloat" onClick={() => setSheet(true)} aria-label="View bill">
      <span className="bsumFloatInfo"><small>{p.pieces} item{p.pieces === 1 ? '' : 's'} · {status}</small><b>{inr(p.due)}</b></span>
      <span className="bsumFloatBtn">View bill ▴</span>
    </button>
    {sheet && <div className="bsumBackdrop" onClick={() => setSheet(false)} aria-hidden="true" />}
    <section className={`bsum${hasItems ? '' : ' empty'}${sheet ? ' sheetOpen' : ''}`} aria-label="Bill summary">
      <button type="button" className="bsumClose" onClick={() => setSheet(false)} aria-label="Close bill">✕ Close</button>
      <div className="bsumGlow" aria-hidden="true" />

      <header className="bsumHead">
        <div>
          <span className="bsumEyebrow">BILL SUMMARY</span>
          <h2 className="bsumInvoice">{p.draft ? 'Draft bill' : 'New bill'}</h2>
        </div>
        <span className={`bsumStatus ${pr && !p.previewErr ? 'ok' : hasItems ? 'live' : 'idle'}`}><i aria-hidden="true" />{status}</span>
      </header>

      <div className="bsumMeta"><span>◷ {p.saleDate}{p.saleTime ? ` · ${p.saleTime}` : ''}</span><button type="button" onClick={p.onRates} className="bsumMetaBtn">◆ {p.rateLabel}</button></div>

      <div className="bsumScroll">
      <div className={`bsumCustomer${p.customer ? ' has' : ''}`}>
        <span className="bsumAvatar" aria-hidden="true">{p.customer ? String(p.customer.name || '?').trim().charAt(0).toUpperCase() : '＋'}</span>
        <div><b>{p.customer ? p.customer.name : 'Walk-in customer'}</b><small>{p.customer ? (p.customer.phone || 'New customer') : 'Pick a customer to give credit'}</small></div>
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
        <div><small>Gross</small><b>{grams(w.g)}</b></div>
        <div><small>Stone</small><b>{grams(w.s)}</b></div>
        <div className="net"><small>Net gold</small><b>{grams(w.net)}</b></div>
      </div>

      {p.previewErr && <div className="bsumAlert bad" role="alert">{p.previewErr}</div>}
      {(pr?.warnings || []).map((x: string) => <div className="bsumAlert" key={x}>{x}</div>)}

      <div className="bsumRows">
        {rows.map(([label, value, icon]) => (
          <div key={label}><span><i aria-hidden="true">{icon}</i>{label}</span><b>{inr(value)}</b></div>
        ))}
        <div className="sub"><span>Subtotal</span><b>{inr(pr?.subtotal)}</b></div>
        {n(pr?.discount) > 0 && <div className="disc"><span>Discount</span><b>− {inr(pr.discount)}</b></div>}
        {taxRows.map(([label, value]) => <div key={label}><span>{label}</span><b>{inr(value)}</b></div>)}
        {n(pr?.roundOff) !== 0 && <div><span>Round off</span><b>{inr(pr.roundOff)}</b></div>}
        {n(pr?.oldGoldUsed) > 0 && <div className="disc"><span>Old gold adjusted</span><b>− {inr(pr.oldGoldUsed)}</b></div>}
      </div>
      </div>

      <div className="bsumDock">
      <div className="bsumTotal">
        <span>Total payable</span>
        <strong aria-live="polite">{inr(animated)}</strong>
        <small>{!hasItems ? 'Add jewellery to begin' : pr ? 'Priced by the server at the saved rate' : 'Pricing…'}</small>
      </div>

      <div className="bsumPay">
        <div className="bsumPayHead"><span>Payment received</span><b>{inr(received)}</b></div>
        <div className="bsumBar" role="progressbar" aria-valuemin={0} aria-valuemax={100} aria-valuenow={paidPct} aria-label="Share of the bill received"><i style={{width: `${paidPct}%`}} /></div>
        <div className={`bsumBalance${balance > 0.004 ? ' due' : ' paid'}`}>
          <span>{balance > 0.004 ? 'Balance due' : p.due > 0 ? 'Paid in full' : 'Balance'}</span><b>{inr(balance)}</b>
        </div>
        <div className="bsumQuick" role="group" aria-label="Quick payment amount">
          <button type="button" disabled={!hasItems} onClick={() => p.onPayment(Number(p.due.toFixed(2)))}>Full</button>
          <button type="button" disabled={!hasItems} onClick={() => p.onPayment(Number((p.due / 2).toFixed(2)))}>50% advance</button>
          <button type="button" disabled={!hasItems} onClick={() => { p.onPayment(0); p.onMode('CREDIT'); }}>On credit</button>
        </div>
        <div className="bsumModes" role="radiogroup" aria-label="Payment mode">
          {p.modes.map(m => <button key={m} type="button" role="radio" aria-checked={p.paymentMode === m} className={p.paymentMode === m ? 'on' : ''} onClick={() => p.onMode(m)}>{m}</button>)}
        </div>
      </div>

      <div className="bsumActions">
        <button type="button" className="bsumBtn outline" onClick={p.onDraft} disabled={!hasItems || p.busy}>Save draft</button>
        <button type="button" className="bsumBtn gold" onClick={p.onComplete} disabled={!ready || p.busy}>{p.busy ? 'Saving…' : ready ? 'Create bill' : walkInShort ? 'Pay in full or add customer' : 'Complete checklist'}</button>
      </div>
      </div>
    </section>
    </>
  );
}
