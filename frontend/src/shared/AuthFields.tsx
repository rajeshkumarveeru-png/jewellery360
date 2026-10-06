import {useState} from 'react';
import type {ReactNode} from 'react';
import {Ico} from './icons';

/* ============================================================================================
   Shared building blocks for the sign-in family of screens (login, create company, forgot, reset) and the
   Users page: password fields with an eye button, a strength meter, live hints and the same validation rules
   the server uses.
   ============================================================================================ */
export type FieldState = {state: 'idle' | 'ok' | 'bad' | 'wait'; message: string};
export const IDLE: FieldState = {state: 'idle', message: ''};

export function PasswordInput(props: {
  value: string;
  onChange: (v: string) => void;
  placeholder?: string;
  autoComplete?: string;
  disabled?: boolean;
  autoFocus?: boolean;
  invalid?: boolean;
  ariaLabel?: string;
  onBlur?: () => void;
  name?: string;
}) {
  const [show, setShow] = useState(false);
  return (
    <div className={`afWrap pw-input${props.invalid ? ' invalid' : ''}`}>
      <Ico name="lock" size={18} />
      <input
        name={props.name}
        type={show ? 'text' : 'password'}
        value={props.value}
        onChange={e => props.onChange(e.target.value)}
        onBlur={props.onBlur}
        placeholder={props.placeholder}
        autoComplete={props.autoComplete ?? 'new-password'}
        disabled={props.disabled}
        autoFocus={props.autoFocus}
        aria-label={props.ariaLabel}
        aria-invalid={props.invalid ? true : undefined}
      />
      <button type="button" className="afEye" onClick={() => setShow(v => !v)} aria-label={show ? 'Hide password' : 'Show password'} aria-pressed={show} title={show ? 'Hide password' : 'Show password'}>
        <Ico name={show ? 'eye-off' : 'eye'} size={18} />
      </button>
    </div>
  );
}

export function passwordScore(value: string): number {
  return (value.length >= 8 ? 1 : 0)
    + (/[A-Z]/.test(value) && /[a-z]/.test(value) ? 1 : 0)
    + (/\d/.test(value) ? 1 : 0)
    + (/[^A-Za-z0-9]/.test(value) ? 1 : 0);
}

export function PasswordMeter({value}: {value: string}) {
  if (!value) return null;
  const score = value.length < 8 ? 0 : Math.max(1, passwordScore(value));
  const label = ['Too short', 'Weak', 'Fair', 'Good', 'Strong'][score];
  return (
    <div className={`pwm s${score}`} aria-live="polite">
      <span className="pwm-bars" aria-hidden="true">{[0, 1, 2, 3].map(i => <i key={i} className={i < score ? 'on' : ''} />)}</span>
      <small>{label}</small>
    </div>
  );
}

export function FieldHint({s, children}: {s: FieldState; children?: ReactNode}) {
  // an idle hint keeps its line (invisible) so the fields below do not jump when a message appears - a layout shift between
  // mouse-down and mouse-up would make the click on the next button miss
  if (s.state === 'idle' && !children) return <small className="fv idle" aria-hidden="true">&nbsp;</small>;
  const icon = s.state === 'ok' ? 'check-circle' : s.state === 'bad' ? 'alert-circle' : s.state === 'wait' ? 'loader' : null;
  return (
    <small className={`fv ${s.state}`} role={s.state === 'bad' ? 'alert' : undefined}>
      {icon ? <Ico name={icon} size={13} className={s.state === 'wait' ? 'spin' : ''} /> : null}
      <span>{s.message}</span>
      {children}
    </small>
  );
}

/* ---------------- validation (same rules as the server) ---------------- */
/** Phone numbers are stored as digits only (10-15 digits; a leading + or spaces are ignored). */
export const normalizePhone = (raw: string): string => String(raw ?? '').replace(/\D/g, '').slice(0, 15);

export const checkCompany = (v: string): FieldState => {
  const value = v.trim();
  if (!value) return IDLE;
  if (value.length < 2) return {state: 'bad', message: 'Use at least 2 characters.'};
  return {state: 'ok', message: 'Looks good.'};
};
export const checkUsername = (v: string): FieldState => {
  const value = v.trim();
  if (!value) return IDLE;
  if (value.length < 3) return {state: 'bad', message: 'Use at least 3 characters.'};
  if (value.length > 80) return {state: 'bad', message: 'Use at most 80 characters.'};
  if (!/^[A-Za-z0-9._-]+$/.test(value)) return {state: 'bad', message: 'Only letters, numbers, dot, dash and underscore.'};
  if (!/[A-Za-z]/.test(value)) return {state: 'bad', message: 'Include at least one letter (numbers alone look like a phone number).'};
  return {state: 'ok', message: 'Looks good.'};
};
export const checkEmail = (v: string): FieldState => {
  const value = v.trim();
  if (!value) return IDLE;
  return /^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$/.test(value)
    ? {state: 'ok', message: 'Valid email address.'}
    : {state: 'bad', message: 'Enter a valid email, like you@business.com.'};
};
export const checkPhone = (v: string): FieldState => {
  const digits = normalizePhone(v);
  if (!String(v ?? '').trim()) return IDLE;
  if (digits.length < 10) return {state: 'bad', message: `${10 - digits.length} more digit${10 - digits.length === 1 ? '' : 's'} needed.`};
  return {state: 'ok', message: 'Valid phone number.'};
};
export const checkPassword = (v: string): FieldState => {
  if (!v) return IDLE;
  if (v.length < 8) return {state: 'bad', message: 'Use at least 8 characters.'};
  return {state: 'ok', message: passwordScore(v) >= 3 ? 'Strong password.' : 'Accepted - add capitals, numbers or symbols to make it stronger.'};
};
export const checkConfirm = (password: string, confirm: string): FieldState => {
  if (!confirm) return IDLE;
  return password === confirm
    ? {state: 'ok', message: 'Passwords match.'}
    : {state: 'bad', message: 'Passwords do not match yet.'};
};

/** A readable random password (no look-alike characters) with capitals, small letters, digits and a symbol. */
export const generatePassword = (): string => {
  const upper = 'ABCDEFGHJKLMNPQRSTUVWXYZ', lower = 'abcdefghijkmnpqrstuvwxyz', digits = '23456789', sym = '@#$%&*!?';
  const all = upper + lower + digits;
  const rnd = (n: number) => { const a = new Uint32Array(1); crypto.getRandomValues(a); return a[0] % n; };
  const chars = [upper[rnd(upper.length)], lower[rnd(lower.length)], digits[rnd(digits.length)], sym[rnd(sym.length)]];
  while (chars.length < 10) chars.push(all[rnd(all.length)]);
  for (let i = chars.length - 1; i > 0; i--) { const j = rnd(i + 1); [chars[i], chars[j]] = [chars[j], chars[i]]; }
  return chars.join('');
};
