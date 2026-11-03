import {useEffect, useState} from 'react';
import {saveUpiSettings, testUpiKeys, upiSettings} from '../api';
import {errText} from './kit';
import {pushToast} from './Notify';
import {loadUpiSettings, type UpiSettings} from './UpiCollect';
import './upi.css';

/* Settings → UPI payments: the shop's own UPI QR and/or Razorpay. Secrets are write-only. */
export default function UpiSettingsPanel({companyId, canEdit}: {companyId?: number | null; canEdit: boolean}) {
  const [s, setS] = useState<UpiSettings | null>(null);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const [qrEnabled, setQrEnabled] = useState(false);
  const [vpa, setVpa] = useState('');
  const [payeeName, setPayeeName] = useState('');
  const [rzpEnabled, setRzpEnabled] = useState(false);
  const [keyId, setKeyId] = useState('');
  const [keySecret, setKeySecret] = useState('');
  const [webhookSecret, setWebhookSecret] = useState('');
  const [preferred, setPreferred] = useState<'RAZORPAY' | 'UPI_QR'>('RAZORPAY');
  const [minutes, setMinutes] = useState(15);

  const fill = (d: UpiSettings) => {
    setS(d); setQrEnabled(d.qrEnabled); setVpa(d.vpa || ''); setPayeeName(d.payeeName || '');
    setRzpEnabled(d.razorpayEnabled); setKeyId(d.razorpayKeyId || ''); setKeySecret(''); setWebhookSecret('');
    setPreferred(d.preferred === 'UPI_QR' ? 'UPI_QR' : 'RAZORPAY'); setMinutes(d.qrMinutes || 15);
  };
  useEffect(() => {
    if (!companyId) return;
    setError(''); setS(null);
    upiSettings().then(r => fill(r.data)).catch(e => setError(errText(e, 'Could not load the UPI settings.')));
  }, [companyId]);

  const save = async () => {
    setBusy(true); setError('');
    try {
      const r = await saveUpiSettings({
        qrEnabled, vpa: vpa.trim(), payeeName: payeeName.trim(), razorpayEnabled: rzpEnabled, razorpayKeyId: keyId.trim(),
        razorpayKeySecret: keySecret.trim() || undefined, razorpayWebhookSecret: webhookSecret.trim() || undefined, preferred, qrMinutes: minutes
      });
      fill(r.data);
      void loadUpiSettings(true);
      pushToast('UPI settings saved', 'success');
    } catch (e) { setError(errText(e, 'Could not save the UPI settings.')); }
    finally { setBusy(false); }
  };
  const test = async () => {
    setBusy(true); setError('');
    try { pushToast((await testUpiKeys()).data?.message || 'Razorpay accepted the keys', 'success'); }
    catch (e) { setError(errText(e, 'Razorpay did not accept the keys.')); }
    finally { setBusy(false); }
  };

  if (!companyId) return <div className="settingsNotice">Select a company context to set up UPI payments.</div>;
  if (!s && !error) return <div className="settingsNotice">Loading UPI settings…</div>;
  const webhookUrl = `${window.location.origin}${s?.webhookPath || '/api/webhooks/razorpay'}`;
  const mode = keyId.startsWith('rzp_live_') ? 'LIVE' : keyId.startsWith('rzp_test_') ? 'TEST' : null;
  const off = !canEdit;

  return <div className="upiSet">
    {error && <div className="settingsError" role="alert">{error}</div>}

    <section className="upiSetCard">
      <div className="upiSetHead">
        <span className="upiSetIco" aria-hidden="true">₹</span>
        <div><b>Shop UPI QR</b><small>Free. A QR for the exact bill amount to your own UPI ID; the cashier confirms when the money arrives.</small></div>
        <span className={`upiBadge ${s?.qrReady ? 'on' : 'off'}`}>{s?.qrReady ? 'On' : 'Off'}</span>
      </div>
      <label className="toggleField"><span><b>Use the shop UPI QR</b><small>Billing can show a QR for the amount being paid.</small></span><input type="checkbox" checked={qrEnabled} disabled={off} onChange={e => setQrEnabled(e.target.checked)} /></label>
      <div className="upiSetGrid">
        <label><span>Shop UPI ID</span><input value={vpa} disabled={off} onChange={e => setVpa(e.target.value.trim())} placeholder="e.g. myshop@okaxis or 9876543210@ybl" autoComplete="off" /></label>
        <label><span>Name customers see</span><input value={payeeName} disabled={off} onChange={e => setPayeeName(e.target.value)} maxLength={60} placeholder="Defaults to the company name" /></label>
      </div>
      <p className="upiSetNote">Use a merchant / business UPI ID where possible. The customer's app shows this name and the amount; Jewellery360 charges nothing.</p>
    </section>

    <section className="upiSetCard">
      <div className="upiSetHead">
        <span className="upiSetIco rzp" aria-hidden="true">R</span>
        <div><b>Razorpay</b><small>Dynamic UPI QR and payment links; the bill is marked paid automatically when the customer pays.</small></div>
        {mode && <span className={`upiBadge ${mode === 'LIVE' ? 'live' : 'test'}`}>{mode === 'LIVE' ? 'Live' : 'Test mode'}</span>}
        <span className={`upiBadge ${s?.razorpayReady ? 'on' : 'off'}`}>{s?.razorpayReady ? 'On' : 'Off'}</span>
      </div>
      <label className="toggleField"><span><b>Use Razorpay</b><small>Needs a Razorpay account with UPI QR / payment links enabled.</small></span><input type="checkbox" checked={rzpEnabled} disabled={off} onChange={e => setRzpEnabled(e.target.checked)} /></label>
      <div className="upiSetGrid">
        <label><span>Key ID</span><input value={keyId} disabled={off} onChange={e => setKeyId(e.target.value.trim())} placeholder="rzp_test_… or rzp_live_…" autoComplete="off" /></label>
        <label><span>Key Secret</span><input type="password" value={keySecret} disabled={off} onChange={e => setKeySecret(e.target.value)} autoComplete="new-password"
          placeholder={s?.razorpaySecretConfigured ? 'Saved - enter a new one to replace it' : 'From Razorpay Dashboard → API Keys'} /></label>
        <label><span>Webhook Secret (recommended)</span><input type="password" value={webhookSecret} disabled={off} onChange={e => setWebhookSecret(e.target.value)} autoComplete="new-password"
          placeholder={s?.webhookSecretConfigured ? 'Saved - enter a new one to replace it' : 'The secret you set on the webhook'} /></label>
      </div>
      <p className="upiSetNote">Webhook URL for Razorpay Dashboard → Webhooks (events <code>qr_code.credited</code>, <code>payment_link.paid</code>): <code>{webhookUrl}</code>.
        It must be reachable from the internet. Without it payments are still confirmed: the billing screen asks Razorpay every few seconds while the QR is shown.</p>
      {canEdit && <div className="upiSetActions"><button type="button" className="upiBtn" onClick={() => void test()} disabled={busy || !s?.razorpaySecretConfigured}>Check saved keys</button></div>}
    </section>

    <section className="upiSetCard">
      <div className="upiSetHead"><span className="upiSetIco pref" aria-hidden="true">⇄</span>
        <div><b>At the counter</b><small>What opens first when the cashier collects by UPI (both stay available).</small></div></div>
      <div className="upiPref" role="group" aria-label="Open first">
        <button type="button" disabled={off} className={preferred === 'RAZORPAY' ? 'on' : ''} onClick={() => setPreferred('RAZORPAY')}>Razorpay QR</button>
        <button type="button" disabled={off} className={preferred === 'UPI_QR' ? 'on' : ''} onClick={() => setPreferred('UPI_QR')}>Shop UPI QR</button>
      </div>
      <label style={{maxWidth: 260}}><span>QR valid for (minutes)</span><input type="number" min={2} max={120} value={minutes} disabled={off} onChange={e => setMinutes(Number(e.target.value) || 15)} /></label>
    </section>

    {canEdit ? <button type="button" className="settingsSave" onClick={() => void save()} disabled={busy}>{busy ? 'Saving…' : 'Save UPI settings'}</button>
      : <div className="settingsNotice">Only someone allowed to change settings can edit UPI payments.</div>}
  </div>;
}
