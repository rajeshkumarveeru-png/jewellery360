import {FormEvent, ReactNode, useEffect, useMemo, useState} from 'react';
import {login, registerCompany, getFriendlyApiError, adminReset, otpRequest, otpVerify, validateReset} from '../api';
import {brandInitials, getBrandName} from '../shared/brand';
import {Ico} from '../shared/icons';
import {
  FieldHint, FieldState, IDLE, PasswordInput, PasswordMeter, checkCompany, checkConfirm, checkEmail, checkPassword, checkPhone, checkUsername, normalizePhone
} from '../shared/AuthFields';
import {useAvailability} from '../shared/useAvailability';
import './Auth.css';

/* ============================================================================================
   Sign in / Create company / Forgot password.
   - Sign in with username, e-mail or mobile number (password with an eye button).
   - Create company: live rules and "already registered" checks, strength meter, progress, blocked submit while invalid.
   - Forgot password: 1) find the account  2) choose WhatsApp OTP or approval  3) new password (eye, meter, confirm).
   ============================================================================================ */
type Mode = 'login' | 'register' | 'reset';
type Step = 'identify' | 'method' | 'otp' | 'approval' | 'done';

export default function Auth({mode, setMode, onLogin}: {mode: string; setMode: (x: any) => void; onLogin: (t: string) => void}) {
  const brand = getBrandName();
  const m = (mode === 'register' || mode === 'reset' ? mode : 'login') as Mode;
  const title = m === 'login' ? 'Sign in' : m === 'register' ? 'Create company' : 'Reset password';
  return (
    <main className="auth">
      <section className="brand">
        <div className="logo" aria-hidden="true">{brandInitials(brand)}</div>
        <span className="eyebrow">JEWELLERY ERP PLATFORM</span>
        <h1>{brand}</h1>
        <p>Premium jewellery showroom management · Billing · Inventory · Customers · Operations</p>
        <div className="brandPills"><span>Multi-company</span><span>Branch control</span><span>JWT secured</span><span>Audit ready</span></div>
      </section>
      <div className="authCard af" key={m}>
        <span className="eyebrow dark">SECURE ACCESS</span>
        <h2>{title}</h2>
        {m === 'login' && <LoginForm setMode={setMode} onLogin={onLogin} />}
        {m === 'register' && <RegisterForm setMode={setMode} />}
        {m === 'reset' && <ResetForm setMode={setMode} />}
      </div>
    </main>
  );
}

/* ---------------- shared bits ---------------- */
function Field({label, children, hint}: {label: string; children: ReactNode; hint?: FieldState}) {
  return (
    <label className="afField">
      <span className="afLabel">{label}</span>
      {children}
      {hint && <FieldHint s={hint} />}
    </label>
  );
}
const wrapClass = (s?: FieldState) => `afWrap${s?.state === 'bad' ? ' invalid' : s?.state === 'ok' ? ' valid' : ''}`;

/* ---------------- sign in ---------------- */
function LoginForm({setMode, onLogin}: {setMode: (x: any) => void; onLogin: (t: string) => void}) {
  const [identifier, setIdentifier] = useState('');
  const [password, setPassword] = useState('');
  const [msg, setMsg] = useState('');
  const [busy, setBusy] = useState(false);
  const submit = async (e: FormEvent) => {
    e.preventDefault();
    setMsg('');
    if (!identifier.trim() || !password) { setMsg('Enter your username, email or mobile number and your password.'); return; }
    setBusy(true);
    try {
      const r = await login(identifier.trim(), password);
      onLogin(r.data.token);
    } catch (err: any) {
      setMsg(getFriendlyApiError(err));
    } finally { setBusy(false); }
  };
  return (
    <form className="afForm" onSubmit={submit} noValidate style={{display: 'contents'}}>
      <Field label="Username / Email / Mobile">
        <div className="afWrap"><Ico name="users" size={18} /><input name="identifier" value={identifier} onChange={e => setIdentifier(e.target.value)} placeholder="Username, email or mobile number" autoComplete="username" autoFocus /></div>
      </Field>
      <Field label="Password"><PasswordInput name="password" value={password} onChange={setPassword} placeholder="Your password" autoComplete="current-password" /></Field>
      {msg && <div className="afMsg bad" role="alert">{msg}</div>}
      <button className="primary afSubmit" type="submit" disabled={busy}>{busy ? 'Signing in…' : 'Sign in'}</button>
      <div className="afLinks"><button type="button" onClick={() => setMode('register')}>Create company</button><button type="button" onClick={() => setMode('reset')}>Forgot password?</button></div>
    </form>
  );
}

/* ---------------- create company ---------------- */
function RegisterForm({setMode}: {setMode: (x: any) => void}) {
  const [f, setF] = useState({companyName: '', username: '', email: '', phone: '', password: '', confirm: ''});
  const [touched, setTouched] = useState<Record<string, boolean>>({});
  const [msg, setMsg] = useState('');
  const [busy, setBusy] = useState(false);
  const [done, setDone] = useState('');
  const company = f.companyName.trim(), username = f.username.trim(), email = f.email.trim().toLowerCase(), phone = normalizePhone(f.phone);
  const base = {
    companyName: checkCompany(company),
    username: checkUsername(username),
    email: checkEmail(email),
    phone: checkPhone(f.phone),
    password: checkPassword(f.password),
    confirm: checkConfirm(f.password, f.confirm)
  };
  const avail = useAvailability({
    company: base.companyName.state === 'ok' ? company : undefined,
    username: base.username.state === 'ok' ? username : undefined,
    email: base.email.state === 'ok' ? email : undefined,
    phone: base.phone.state === 'ok' ? phone : undefined
  });
  const states: Record<string, FieldState> = {
    companyName: avail.state(base.companyName, 'company', company, 'company name'),
    username: avail.state(base.username, 'username', username, 'username'),
    email: avail.state(base.email, 'email', email, 'email'),
    phone: avail.state(base.phone, 'phone', phone, 'phone number'),
    password: base.password,
    confirm: base.confirm
  };
  const required = (v: string, key: string): FieldState => (!v.trim() && touched[key] ? {state: 'bad', message: 'This field is required.'} : IDLE);
  const shown = (k: keyof typeof f): FieldState => {
    const s = states[k === 'confirm' ? 'confirm' : k];
    if (s.state === 'idle') return required(f[k], k);
    return s;
  };
  const okCount = Object.values(states).filter(s => s.state === 'ok').length;
  const touch = (k: string) => setTouched(c => ({...c, [k]: true}));
  const set = (k: keyof typeof f, v: string) => setF(cur => ({...cur, [k]: v}));

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    setMsg('');
    setTouched({companyName: true, username: true, email: true, phone: true, password: true, confirm: true});
    if (okCount < 6) { setMsg('Please fix the highlighted fields.'); return; }
    setBusy(true);
    try {
      const r = await registerCompany({companyName: company, username, email, phone, password: f.password, confirmPassword: f.confirm});
      setDone(r.data?.message || 'Company registration submitted successfully. An App Admin must approve the request before you can sign in.');
    } catch (err: any) {
      setMsg(getFriendlyApiError(err));
    } finally { setBusy(false); }
  };

  if (done) {
    return (
      <div className="afDone" role="status">
        <span className="tick"><Ico name="check" size={32} /></span>
        <h3>Registration submitted</h3>
        <p>{done}</p>
        <button className="primary afSubmit" type="button" onClick={() => setMode('login')}>Back to sign in</button>
      </div>
    );
  }
  return (
    <form onSubmit={submit} noValidate style={{display: 'contents'}} autoComplete="off">
      <div className="afProgress" aria-label={`${okCount} of 6 fields complete`}><i><b style={{width: `${(okCount / 6) * 100}%`}} /></i><span>{okCount}/6</span></div>
      <Field label="Company name" hint={shown('companyName')}>
        <div className={wrapClass(states.companyName)}><Ico name="building" size={18} /><input value={f.companyName} onChange={e => set('companyName', e.target.value)} onBlur={() => touch('companyName')} placeholder="Your showroom / company" autoComplete="organization" autoFocus /></div>
      </Field>
      <Field label="Username" hint={shown('username')}>
        <div className={wrapClass(states.username)}><Ico name="users" size={18} /><input value={f.username} onChange={e => set('username', e.target.value.replace(/\s/g, ''))} onBlur={() => touch('username')} placeholder="e.g. srilakshmi.owner" autoComplete="username" maxLength={80} /></div>
      </Field>
      <Field label="Email" hint={shown('email')}>
        <div className={wrapClass(states.email)}><Ico name="mail" size={18} /><input type="email" value={f.email} onChange={e => set('email', e.target.value)} onBlur={() => touch('email')} placeholder="you@business.com" autoComplete="email" /></div>
      </Field>
      <Field label="WhatsApp phone" hint={shown('phone')}>
        <div className={wrapClass(states.phone)}><Ico name="phone" size={18} /><input type="tel" inputMode="tel" value={f.phone} onChange={e => set('phone', e.target.value.replace(/[^\d+\s-]/g, '').slice(0, 20))} onBlur={() => touch('phone')} placeholder="10-15 digit number, with country code if outside India" autoComplete="tel" /></div>
      </Field>
      <label className="afField"><span className="afLabel">Password</span>
        <PasswordInput value={f.password} onChange={v => set('password', v)} onBlur={() => touch('password')} placeholder="At least 8 characters" invalid={shown('password').state === 'bad'} />
        <PasswordMeter value={f.password} /><FieldHint s={shown('password')} />
      </label>
      <label className="afField"><span className="afLabel">Confirm password</span>
        <PasswordInput value={f.confirm} onChange={v => set('confirm', v)} onBlur={() => touch('confirm')} placeholder="Re-enter the password" invalid={shown('confirm').state === 'bad'} />
        <FieldHint s={shown('confirm')} />
      </label>
      {msg && <div className="afMsg bad" role="alert">{msg}</div>}
      <button className="primary afSubmit" type="submit" disabled={busy}>{busy ? 'Submitting…' : 'Submit registration'}</button>
      <div className="afLinks"><button type="button" onClick={() => setMode('login')}>Back to login</button></div>
    </form>
  );
}

/* ---------------- forgot / reset ---------------- */
function ResetForm({setMode}: {setMode: (x: any) => void}) {
  const [step, setStep] = useState<Step>('identify');
  const [identifier, setIdentifier] = useState('');
  const [hasPhone, setHasPhone] = useState(true);
  const [method, setMethod] = useState<'OTP' | 'ADMIN'>('OTP');
  const [challengeId, setChallengeId] = useState<number | null>(null);
  const [otp, setOtp] = useState('');
  const [password, setPassword] = useState('');
  const [confirm, setConfirm] = useState('');
  const [msg, setMsg] = useState<{kind: 'ok' | 'bad' | 'info'; text: string} | null>(null);
  const [busy, setBusy] = useState(false);
  const [doneText, setDoneText] = useState('');
  const pw = checkPassword(password), match = checkConfirm(password, confirm);
  const pwOk = pw.state === 'ok' && match.state === 'ok';
  const otpOk = /^\d{6}$/.test(otp);

  const run = async (fn: () => Promise<void>) => {
    setMsg(null); setBusy(true);
    try { await fn(); } catch (err: any) { setMsg({kind: 'bad', text: getFriendlyApiError(err)}); } finally { setBusy(false); }
  };
  const identify = (e: FormEvent) => {
    e.preventDefault();
    if (!identifier.trim()) { setMsg({kind: 'bad', text: 'Enter your username, email or mobile number.'}); return; }
    void run(async () => {
      const r = await validateReset(identifier.trim());
      setHasPhone(r.data?.hasPhone !== false);
      setMethod(r.data?.hasPhone === false ? 'ADMIN' : 'OTP');
      setStep('method');
    });
  };
  const chooseMethod = () => {
    if (method === 'OTP') {
      void run(async () => {
        const r = await otpRequest(identifier.trim());
        setChallengeId(r.data?.challengeId ?? null);
        setMsg({kind: 'info', text: r.data?.message || 'We sent a 6-digit OTP to your registered WhatsApp number.'});
        setStep('otp');
      });
    } else { setStep('approval'); }
  };
  const submitOtp = (e: FormEvent) => {
    e.preventDefault();
    if (!otpOk || !pwOk || challengeId == null) return;
    void run(async () => {
      const r = await otpVerify({challengeId, otp, newPassword: password});
      setDoneText(r.data?.message || 'Password reset successfully. You can sign in with the new password.');
      setStep('done');
    });
  };
  const submitApproval = (e: FormEvent) => {
    e.preventDefault();
    if (!pwOk) return;
    void run(async () => {
      const r = await adminReset({identifier: identifier.trim(), password, confirmPassword: confirm});
      setDoneText((r.data?.message || 'Password reset request submitted.') + ' Your company admin or the app administrator will approve it - your new password becomes active only after that.');
      setStep('done');
    });
  };

  if (step === 'done') {
    return (
      <div className="afDone" role="status">
        <span className="tick"><Ico name="check" size={32} /></span>
        <h3>{method === 'OTP' ? 'Password changed' : 'Request submitted'}</h3>
        <p>{doneText}</p>
        <button className="primary afSubmit" type="button" onClick={() => setMode('login')}>Back to sign in</button>
      </div>
    );
  }
  const back = () => { setMsg(null); setStep(step === 'otp' || step === 'approval' ? 'method' : 'identify'); };
  return (
    <>
      <div className="afSteps" aria-label="Reset steps">{['Find account', 'Method', 'New password'].map((x, i) => {
        const at = step === 'identify' ? 0 : step === 'method' ? 1 : 2;
        return <span key={x} className={i < at ? 'done' : i === at ? 'on' : ''}><b>{i < at ? <Ico name="check" size={12} /> : i + 1}</b>{x}</span>;
      })}</div>
      {step === 'identify' && (
        <form onSubmit={identify} noValidate style={{display: 'contents'}}>
          <p className="afLead">Enter the username, email or mobile number of your account.</p>
          <Field label="Username / Email / Mobile">
            <div className="afWrap"><Ico name="users" size={18} /><input value={identifier} onChange={e => setIdentifier(e.target.value)} placeholder="Username, email or mobile number" autoComplete="username" autoFocus /></div>
          </Field>
          {msg && <div className={`afMsg ${msg.kind}`} role="alert">{msg.text}</div>}
          <button className="primary afSubmit" type="submit" disabled={busy}>{busy ? 'Checking…' : 'Continue'}</button>
        </form>
      )}
      {step === 'method' && (
        <>
          <p className="afLead">Account found: <b>{identifier.trim()}</b>. How should we verify it is you?</p>
          <div className="afMethods">
            <button type="button" className={`afMethod${method === 'OTP' ? ' on' : ''}`} disabled={!hasPhone} onClick={() => setMethod('OTP')}><b><Ico name="whatsapp" size={16} /> WhatsApp OTP</b><small>{hasPhone ? 'A 6-digit code is sent to the registered number. Instant.' : 'No phone number is saved for this account.'}</small></button>
            <button type="button" className={`afMethod${method === 'ADMIN' ? ' on' : ''}`} onClick={() => setMethod('ADMIN')}><b><Ico name="shield" size={16} /> Admin approval</b><small>Your company admin or the app administrator approves the new password.</small></button>
          </div>
          {msg && <div className={`afMsg ${msg.kind}`} role="alert">{msg.text}</div>}
          <button className="primary afSubmit" type="button" disabled={busy} onClick={chooseMethod}>{busy ? 'Please wait…' : method === 'OTP' ? 'Send OTP' : 'Continue'}</button>
          <div className="afLinks"><button type="button" onClick={back}>Back</button></div>
        </>
      )}
      {(step === 'otp' || step === 'approval') && (
        <form onSubmit={step === 'otp' ? submitOtp : submitApproval} noValidate style={{display: 'contents'}} autoComplete="off">
          {step === 'otp' && (
            <Field label="6-digit OTP" hint={otp && !otpOk ? {state: 'bad', message: 'Enter the 6 digits from WhatsApp.'} : IDLE}>
              <div className={`afWrap${otp && !otpOk ? ' invalid' : otpOk ? ' valid' : ''}`}><Ico name="key" size={18} /><input value={otp} onChange={e => setOtp(e.target.value.replace(/\D/g, '').slice(0, 6))} placeholder="123456" inputMode="numeric" autoComplete="one-time-code" autoFocus /></div>
            </Field>
          )}
          <label className="afField"><span className="afLabel">New password</span>
            <PasswordInput value={password} onChange={setPassword} placeholder="At least 8 characters" invalid={pw.state === 'bad'} autoFocus={step === 'approval'} />
            <PasswordMeter value={password} /><FieldHint s={pw} />
          </label>
          <label className="afField"><span className="afLabel">Confirm password</span>
            <PasswordInput value={confirm} onChange={setConfirm} placeholder="Re-enter the password" invalid={match.state === 'bad'} />
            <FieldHint s={match} />
          </label>
          {step === 'approval' && <div className="afMsg info">Your new password is stored securely and becomes active only after your company admin or the app administrator approves the request.</div>}
          {msg && <div className={`afMsg ${msg.kind}`} role="alert">{msg.text}</div>}
          <button className="primary afSubmit" type="submit" disabled={busy || !pwOk || (step === 'otp' && !otpOk)}>{busy ? 'Please wait…' : step === 'otp' ? 'Reset password' : 'Submit for approval'}</button>
          <div className="afLinks"><button type="button" onClick={back}>Back</button></div>
        </form>
      )}
      {step !== 'identify' && step !== 'method' ? null : <div className="afLinks"><button type="button" onClick={() => setMode('login')}>Back to login</button></div>}
    </>
  );
}
