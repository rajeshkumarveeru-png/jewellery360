import {useCallback, useEffect, useRef, useState} from 'react';
import QRCode from 'qrcode';
import {cancelUpiRequest, confirmUpiRequest, createUpiRequest, upiRequest, upiSettings} from '../api';
import {errText, money, Portal} from './kit';
import {pushToast} from './Notify';
import './upi.css';

/* ------------------------------------------------------------------------------------------------
   Collect a bill's money by UPI.
   - Shop UPI QR: the customer scans, pays the exact amount to the shop's UPI ID, the cashier confirms.
   - Razorpay QR / payment link: Razorpay confirms; this dialog checks every few seconds and closes the loop.
   The payment is recorded on the bill by the server (same rules as any payment).
   ------------------------------------------------------------------------------------------------ */

export type UpiSettings = {
  qrEnabled: boolean; qrReady: boolean; vpa: string; payeeName: string
  razorpayEnabled: boolean; razorpayReady: boolean; razorpayKeyId: string; razorpayMode: 'TEST' | 'LIVE' | null
  razorpaySecretConfigured: boolean; webhookSecretConfigured: boolean; preferred: 'RAZORPAY' | 'UPI_QR'; qrMinutes: number; webhookPath: string
};
type Mode = 'UPI_QR' | 'RAZORPAY_QR' | 'RAZORPAY_LINK';
type UpiReq = {
  id: number; saleId: number; invoiceNo: string; mode: Mode; amount: number
  status: 'PENDING' | 'PAID' | 'CANCELLED' | 'EXPIRED' | 'NEEDS_REVIEW'
  upiUri?: string | null; qrImageUrl?: string | null; shortUrl?: string | null; payeeName?: string; vpa?: string | null
  expiresAt?: string | null; paidAmount?: number | null; utr?: string | null; payerVpa?: string | null; note?: string | null
};

let cached: {at: number; data: UpiSettings | null} | null = null;
/** UPI settings for billing (cached a minute; null when they cannot be loaded). */
export async function loadUpiSettings(force = false): Promise<UpiSettings | null> {
  if (!force && cached && Date.now() - cached.at < 60000) return cached.data;
  try { cached = {at: Date.now(), data: (await upiSettings()).data}; } catch { cached = {at: Date.now(), data: null}; }
  return cached.data;
}
export const upiReady = (s: UpiSettings | null) => !!s && (s.qrReady || s.razorpayReady);

const MODE_LABEL: Record<Mode, string> = {UPI_QR: 'Shop UPI QR', RAZORPAY_QR: 'Razorpay QR', RAZORPAY_LINK: 'Payment link'};

function QrSvg({text, size = 232}: {text: string; size?: number}) {
  const [svg, setSvg] = useState('');
  useEffect(() => {
    let live = true;
    QRCode.toString(text, {type: 'svg', margin: 1, width: size, errorCorrectionLevel: 'M'})
      .then(s => { if (live) setSvg(s); }).catch(() => { if (live) setSvg(''); });
    return () => { live = false; };
  }, [text, size]);
  return svg ? <div className="upiQrSvg" role="img" aria-label="UPI QR code" style={{width: size, height: size}} dangerouslySetInnerHTML={{__html: svg}} />
    : <div className="upiQrSvg" style={{width: size, height: size}}>Drawing QR…</div>;
}

const waLink = (phone: string | null | undefined, text: string) => {
  const d = String(phone || '').replace(/\D/g, '');
  const to = d.length === 10 ? '91' + d : d.length === 12 && d.startsWith('91') ? d : '';
  return `https://wa.me/${to}?text=${encodeURIComponent(text)}`;
};

export default function UpiCollect({saleId, amount, invoiceNo, customerName, customerPhone, onClose, onPaid}: {
  saleId: number; amount: number; invoiceNo?: string; customerName?: string | null; customerPhone?: string | null
  onClose: (paid: boolean) => void; onPaid?: () => void
}) {
  const [cfg, setCfg] = useState<UpiSettings | null>(null);
  const [req, setReq] = useState<UpiReq | null>(null);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(true);
  const [utr, setUtr] = useState('');
  const [now, setNow] = useState(Date.now());
  const paidSeen = useRef(false);
  const started = useRef(false);   // one request per opening (React may run the first effect twice)

  const modes: Mode[] = cfg ? [
    ...(cfg.razorpayReady ? ['RAZORPAY_QR' as Mode] : []),
    ...(cfg.qrReady ? ['UPI_QR' as Mode] : []),
    ...(cfg.razorpayReady ? ['RAZORPAY_LINK' as Mode] : [])
  ] : [];

  const start = useCallback(async (mode?: Mode) => {
    setBusy(true); setError(''); setUtr('');
    try { setReq((await createUpiRequest({saleId, amount, mode})).data); }
    catch (e) { setError(errText(e, 'Could not start the UPI payment.')); }
    finally { setBusy(false); }
  }, [saleId, amount]);

  useEffect(() => {
    if (started.current) return;
    started.current = true;
    void (async () => {
      const s = await loadUpiSettings(true);
      setCfg(s);
      if (!upiReady(s)) { setBusy(false); setError('UPI collection is not set up. An administrator can add the shop UPI ID or Razorpay keys in Settings → UPI payments.'); return; }
      await start();
    })();
  }, [start]);

  // Razorpay confirms on its own: keep asking while the customer pays
  useEffect(() => {
    if (!req || req.status !== 'PENDING' || req.mode === 'UPI_QR') return;
    const t = window.setInterval(async () => { try { setReq((await upiRequest(req.id)).data); } catch { /* next tick */ } }, 3000);
    return () => window.clearInterval(t);
  }, [req?.id, req?.status, req?.mode]); // eslint-disable-line react-hooks/exhaustive-deps

  useEffect(() => { const t = window.setInterval(() => setNow(Date.now()), 1000); return () => window.clearInterval(t); }, []);

  useEffect(() => {
    if (req?.status === 'PAID' && !paidSeen.current) {
      paidSeen.current = true;
      pushToast(`${money(req.paidAmount ?? req.amount)} received by UPI for bill ${req.invoiceNo}`, 'success');
      onPaid?.();
    }
  }, [req?.status]); // eslint-disable-line react-hooks/exhaustive-deps

  const close = async () => {
    if (req && req.status === 'PENDING') {
      // stop the QR / link; the server checks once more in case it was just paid
      try {
        const r = await cancelUpiRequest(req.id);
        if (r.data.status === 'PAID') { setReq(r.data); return; }
      } catch { /* closing anyway */ }
    }
    onClose(req?.status === 'PAID');
  };
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape' && !busy) void close(); };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  });

  const confirm = async () => {
    if (!req) return;
    setBusy(true); setError('');
    try { setReq((await confirmUpiRequest(req.id, utr.trim() || undefined)).data); }
    catch (e) { setError(errText(e, 'Could not record the payment.')); }
    finally { setBusy(false); }
  };
  const checkNow = async () => {
    if (!req) return;
    try { const r = await upiRequest(req.id); setReq(r.data); if (r.data.status === 'PENDING') pushToast('Not paid yet', 'info'); }
    catch (e) { setError(errText(e, 'Could not check the payment.')); }
  };

  const left = req?.expiresAt ? Math.max(0, Math.floor((new Date(req.expiresAt).getTime() - now) / 1000)) : null;
  const clock = left == null ? '' : left >= 3600 ? `${Math.floor(left / 3600)} h ${Math.floor(left % 3600 / 60)} min` : `${Math.floor(left / 60)}:${String(left % 60).padStart(2, '0')}`;
  const linkText = req?.shortUrl ? `Please pay ${money(req.amount)} for bill ${req.invoiceNo}${req.payeeName ? ` at ${req.payeeName}` : ''}: ${req.shortUrl}` : '';

  return <Portal>
    <div className="upiOverlay" onMouseDown={e => { if (e.target === e.currentTarget && !busy) void close(); }}>
      <div className="upiCard" role="dialog" aria-modal="true" aria-label="Collect by UPI">
        <header className="upiHead">
          <span className="upiHeadIco" aria-hidden="true">₹</span>
          <div><h3>Collect by UPI</h3><small>Bill {invoiceNo || req?.invoiceNo}{customerName ? ` · ${customerName}` : ''}</small></div>
          <button type="button" className="upiX" aria-label="Close" onClick={() => void close()} disabled={busy}>✕</button>
        </header>

        <div className="upiAmount"><span>Amount</span><strong>{money(amount)}</strong></div>

        {modes.length > 1 && req?.status !== 'PAID' && <div className="upiModes" role="tablist" aria-label="How to collect">
          {modes.map(m => <button key={m} type="button" role="tab" aria-selected={req?.mode === m} className={req?.mode === m ? 'on' : ''} disabled={busy}
            onClick={() => { if (req?.mode !== m) void start(m); }}>{MODE_LABEL[m]}</button>)}
        </div>}

        {error && <div className="upiError" role="alert">{error}</div>}
        {busy && !req && !error && <div className="upiWait"><i className="upiSpin" aria-hidden="true" /> Preparing the payment…</div>}

        {req?.status === 'PAID' && <div className="upiDone">
          <span className="upiDoneIco" aria-hidden="true">✓</span>
          <b>{money(req.paidAmount ?? req.amount)} received</b>
          <small>Recorded on bill {req.invoiceNo}{req.utr ? ` · UPI ref ${req.utr}` : ''}{req.payerVpa ? ` · from ${req.payerVpa}` : ''}</small>
        </div>}

        {req?.status === 'NEEDS_REVIEW' && <div className="upiError" role="alert">{req.note || 'The money arrived but could not be put on the bill. Check the bill and record it by hand.'}</div>}

        {req && (req.status === 'CANCELLED' || req.status === 'EXPIRED') && <div className="upiWait">
          This {req.status === 'EXPIRED' ? 'QR has expired' : 'request was cancelled'}.
          <button type="button" className="upiBtn" onClick={() => void start(req.mode)}>Make a new one</button>
        </div>}

        {req?.status === 'PENDING' && <>
          {req.mode === 'UPI_QR' && req.upiUri && <>
            <div className="upiQr"><QrSvg text={req.upiUri} /></div>
            <div className="upiPayee"><b>{req.payeeName}</b><span>{req.vpa}</span></div>
            <p className="upiHint">Customer scans with any UPI app (GPay, PhonePe, Paytm, BHIM…). Check the money arrived in <b>your</b> UPI app or bank SMS before confirming.</p>
            <label className="upiField"><span>UPI reference / UTR <small>(optional, from your app)</small></span>
              <input value={utr} onChange={e => setUtr(e.target.value.replace(/\s/g, ''))} inputMode="numeric" maxLength={30} placeholder="e.g. 431245678901" /></label>
          </>}
          {req.mode === 'RAZORPAY_QR' && <>
            <div className="upiQr">{req.qrImageUrl ? <img src={req.qrImageUrl} alt="Razorpay UPI QR" /> : <span>QR not available</span>}</div>
            <div className="upiLive"><i /> Waiting for the payment - it is confirmed automatically</div>
          </>}
          {req.mode === 'RAZORPAY_LINK' && req.shortUrl && <>
            <div className="upiQr"><QrSvg text={req.shortUrl} size={200} /></div>
            <div className="upiLink"><code>{req.shortUrl}</code>
              <button type="button" className="upiBtn" onClick={() => { void navigator.clipboard?.writeText(req.shortUrl || ''); pushToast('Link copied', 'success'); }}>Copy</button></div>
            <a className="upiBtn wa" href={waLink(customerPhone, linkText)} target="_blank" rel="noopener noreferrer">Send link on WhatsApp</a>
            <div className="upiLive"><i /> Waiting for the payment - it is confirmed automatically</div>
          </>}
          {clock && <div className="upiTimer">{req.mode === 'UPI_QR' ? 'Suggested time' : 'Expires in'} {clock}</div>}
        </>}

        <footer className="upiFoot">
          {req?.status === 'PAID'
            ? <button type="button" className="upiBtn solid" onClick={() => onClose(true)}>Done</button>
            : <>
              <button type="button" className="upiBtn" onClick={() => void close()} disabled={busy}>{req?.status === 'PENDING' ? 'Cancel' : 'Close'}</button>
              {req?.status === 'PENDING' && req.mode === 'UPI_QR' && <button type="button" className="upiBtn solid" onClick={() => void confirm()} disabled={busy}>{busy ? 'Saving…' : 'Money received'}</button>}
              {req?.status === 'PENDING' && req.mode !== 'UPI_QR' && <button type="button" className="upiBtn" onClick={() => void checkNow()}>Check now</button>}
            </>}
        </footer>
      </div>
    </div>
  </Portal>;
}
