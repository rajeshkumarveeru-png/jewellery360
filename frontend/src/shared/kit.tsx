import {useCallback, useEffect, useRef, useState} from 'react';
import {createPortal} from 'react-dom';
import type {ReactNode} from 'react';
import {customers as searchCustomers, getFriendlyApiError} from '../api';
import {inr} from './widgets';

/* ============================================================================================
   Small building blocks shared by every jewellery page, so they all load, fail, page and confirm the same way:
   useData (loading / error / reload), StateBox (loading, empty and error states with a next step), Tabs, Sheet (side panel
   using the existing drawer styles), CustomerPicker and PaymentRows (split payments).
   ============================================================================================ */

export const errText = (e: any, fallback = 'The request could not be completed.') => getFriendlyApiError(e, fallback);
export const money = (v: any) => inr(v, 2);
export const grams = (v: any) => `${Number(v || 0).toLocaleString('en-IN', {minimumFractionDigits: 3, maximumFractionDigits: 3})} g`;
export const num = (v: any) => { const n = Number(v); return Number.isFinite(n) ? n : 0; };
export const today = () => { const d = new Date(); return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`; };
export const daysAgo = (n: number) => { const d = new Date(); d.setDate(d.getDate() - n); return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`; };
// "OLD_GOLD" -> "Old gold"; short codes such as UPI stay as they are
const KEEP = ['UPI', 'GST', 'HUID', 'MRP', 'OTP'];
export const label = (s?: string | null) => KEEP.includes(String(s)) ? String(s) : (s || '').replace(/_/g, ' ').toLowerCase().replace(/^./, c => c.toUpperCase());

/** Loads data when the dependencies change; keeps the last good data while reloading. */
export function useData<T>(load: () => Promise<{data: T}>, deps: unknown[], enabled = true) {
  const [data, setData] = useState<T | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const seq = useRef(0);
  const reload = useCallback(async () => {
    if (!enabled) { setData(null); return; }
    const mine = ++seq.current;
    setLoading(true); setError('');
    try { const r = await load(); if (mine === seq.current) setData(r.data); }
    catch (e) { if (mine === seq.current) setError(errText(e)); }
    finally { if (mine === seq.current) setLoading(false); }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [enabled, ...deps]);
  useEffect(() => { void reload(); }, [reload]);
  return {data, loading, error, reload, setData};
}

/** Loading, error (with retry) and empty states. Renders children only when there is something to show. */
export function StateBox({loading, error, empty, emptyText, emptyHint, action, onRetry, children}: {
  loading?: boolean; error?: string; empty?: boolean; emptyText?: string; emptyHint?: string; action?: ReactNode; onRetry?: () => void; children?: ReactNode;
}) {
  if (error) return <div className="kState bad" role="alert"><b>Could not load this list</b><span>{error}</span>{onRetry && <button type="button" className="ghost" onClick={onRetry}>Try again</button>}</div>;
  if (loading && empty) return <div className="kState" aria-busy="true"><span className="kSpin" aria-hidden="true" /><span>Loading…</span></div>;
  if (empty) return <div className="kState"><b>{emptyText || 'Nothing here yet.'}</b>{emptyHint && <span>{emptyHint}</span>}{action}</div>;
  return <>{children}</>;
}

export function Tabs<T extends string>({value, tabs, onChange}: {value: T; tabs: {key: T; label: string; count?: number}[]; onChange: (t: T) => void}) {
  return <div className="kTabs" role="tablist">{tabs.map(t =>
    <button key={t.key} type="button" role="tab" aria-selected={value === t.key} className={value === t.key ? 'ghost on' : 'ghost'} onClick={() => onChange(t.key)}>
      {t.label}{t.count !== undefined && t.count > 0 && <span className="kCount">{t.count}</span>}
    </button>)}</div>;
}

/** Side panel for details and forms (same look as the entry drawer). Esc or the backdrop closes it. */
export function Sheet({open, title, kicker, onClose, children, wide}: {open: boolean; title: string; kicker?: string; onClose: () => void; children: ReactNode; wide?: boolean}) {
  useEffect(() => {
    if (!open) return;
    const k = (e: KeyboardEvent) => { if (e.key === 'Escape') onClose(); };
    window.addEventListener('keydown', k);
    return () => window.removeEventListener('keydown', k);
  }, [open, onClose]);
  return <Portal>
    <div className={`entryBackdrop${open ? ' open' : ''}`} onClick={onClose} aria-hidden="true" />
    <div className={`formDrawer${open ? ' open' : ''}${wide ? ' kWide' : ''}`} role="dialog" aria-modal="true" aria-hidden={!open} aria-label={title}>
      <div className="formDrawerHead">
        <div>{kicker && <small>{kicker}</small>}<h2>{title}</h2></div>
        <button type="button" className="formDrawerClose" onClick={onClose} aria-label="Close">✕</button>
      </div>
      <div className="formDrawerBody">{open && children}</div>
    </div>
  </Portal>;
}

/** Renders overlays at the app root (inside `.app`, so theme styles still apply). A panel with a blur or a hover lift
 *  would otherwise become the containing block and squeeze a fixed side panel into the panel's own box. */
export function Portal({children}: {children: ReactNode}) {
  const host = typeof document === 'undefined' ? null : document.querySelector('.app') || document.body;
  return host ? createPortal(children, host) : null;
}

/** Labelled form control. */
export function F({label: text, children, hint, wide}: {label: string; children: ReactNode; hint?: string; wide?: boolean}) {
  return <label className={`kField${wide ? ' wide' : ''}`}><span>{text}</span>{children}{hint && <small>{hint}</small>}</label>;
}

export function KV({rows}: {rows: [string, ReactNode][]}) {
  return <dl className="kv">{rows.filter(r => r[1] !== null && r[1] !== undefined && r[1] !== '').map(([k, v]) => <div key={k}><dt>{k}</dt><dd>{v}</dd></div>)}</dl>;
}

const TONE: Record<string, string> = {
  Paid: 'success', Settled: 'success', DELIVERED: 'success', CLOSED: 'success', READY: 'success', IN_STOCK: 'success', SETTLED: 'success',
  Unpaid: 'bad', Cancelled: 'bad', CANCELLED: 'bad', Returned: 'bad', REMOVED: 'bad', OPEN: 'warn', 'Partially paid': 'warn', Draft: 'warn', OVERDUE: 'bad'
};
export function Pill({value, tone}: {value?: string | null; tone?: string}) {
  if (!value) return null;
  return <span className={`pill ${tone || TONE[value] || ''}`}>{label(value)}</span>;
}

/* ------------------------------------------------------------------------------------------------ customers */

export type CustomerRef = {id?: number; name: string; phone?: string; owes?: number; advance?: number} | null;

/** Find a customer by name or phone, or type a new name + phone (created with the bill / order). Empty = walk-in. */
export function CustomerPicker({value, onChange, allowWalkIn = true}: {value: CustomerRef; onChange: (c: CustomerRef) => void; allowWalkIn?: boolean}) {
  const [q, setQ] = useState('');
  const [hits, setHits] = useState<any[]>([]);
  const [open, setOpen] = useState(false);
  const [adding, setAdding] = useState(false);
  const [draft, setDraft] = useState({name: '', phone: ''});
  useEffect(() => {
    const term = q.trim();
    if (term.length < 2) { setHits([]); return; }
    const t = window.setTimeout(() => { searchCustomers(term, 8).then(r => setHits((r.data || []).filter((c: any) => !c.walkIn))).catch(() => setHits([])); }, 220);
    return () => window.clearTimeout(t);
  }, [q]);
  if (value) return <div className="kPicked">
    <div><b>{value.name}</b><small>{value.phone || (value.id ? '' : 'new customer')}</small>
      {!!num(value.owes) && <span className="pill bad">owes {money(value.owes)}</span>}
      {!!num(value.advance) && <span className="pill success">advance {money(value.advance)}</span>}</div>
    <button type="button" className="ghost" onClick={() => { onChange(null); setQ(''); }}>Change</button>
  </div>;
  if (adding) return <div className="kRow">
    <input autoFocus placeholder="Customer name" value={draft.name} onChange={e => setDraft({...draft, name: e.target.value})} />
    <input placeholder="Phone" inputMode="tel" value={draft.phone} onChange={e => setDraft({...draft, phone: e.target.value})} />
    <button type="button" className="primary" disabled={!draft.name.trim()} onClick={() => { onChange({name: draft.name.trim(), phone: draft.phone.trim() || undefined}); setAdding(false); }}>Use</button>
    <button type="button" className="ghost" onClick={() => setAdding(false)}>Cancel</button>
  </div>;
  return <div className="kPick">
    <input type="search" placeholder={allowWalkIn ? 'Customer name or phone (leave empty for walk-in)' : 'Customer name or phone'} value={q}
           onChange={e => { setQ(e.target.value); setOpen(true); }} onFocus={() => setOpen(true)} onBlur={() => window.setTimeout(() => setOpen(false), 150)} />
    <button type="button" className="ghost" onClick={() => { setAdding(true); setDraft({name: /\d/.test(q) ? '' : q, phone: /\d/.test(q) ? q : ''}); }}>＋ New</button>
    {open && hits.length > 0 && <ul className="kHits" role="listbox">{hits.map(c =>
      <li key={c.id}><button type="button" className="ghost" onMouseDown={() => { onChange({id: c.id, name: c.name, phone: c.phone, owes: c.owes, advance: c.advance}); setQ(''); setOpen(false); }}>
        <b>{c.name}</b><small>{c.phone || '—'}{num(c.owes) > 0 ? ` · owes ${money(c.owes)}` : ''}{num(c.advance) > 0 ? ` · advance ${money(c.advance)}` : ''}</small>
      </button></li>)}</ul>}
  </div>;
}

/** Request body fields for a picked customer. */
export const customerBody = (c: CustomerRef) => c?.id ? {customerId: c.id} : c ? {customer: {name: c.name, phone: c.phone}} : {};

/* ------------------------------------------------------------------------------------------------ payments */

export const MONEY_MODES = ['CASH', 'UPI', 'CARD', 'BANK', 'CHEQUE'];
export type PayRow = {mode: string; amount: string; reference: string};
export const emptyPay = (amount = ''): PayRow => ({mode: 'CASH', amount, reference: ''});

/** Split payment editor: one row per mode. `extraModes` adds ADVANCE etc. */
export function PaymentRows({rows, onChange, extraModes = [], due}: {rows: PayRow[]; onChange: (r: PayRow[]) => void; extraModes?: string[]; due?: number}) {
  const set = (i: number, p: Partial<PayRow>) => onChange(rows.map((r, j) => j === i ? {...r, ...p} : r));
  const paid = rows.reduce((s, r) => s + num(r.amount), 0);
  return <div className="kPays">
    {rows.map((r, i) => <div className="kRow" key={i}>
      <select value={r.mode} onChange={e => set(i, {mode: e.target.value})} aria-label="Payment mode">{[...MONEY_MODES, ...extraModes].map(m => <option key={m} value={m}>{label(m)}</option>)}</select>
      <input type="number" min="0" step="0.01" inputMode="decimal" placeholder="Amount" value={r.amount} onChange={e => set(i, {amount: e.target.value})} aria-label="Amount" />
      {r.mode !== 'CASH' && r.mode !== 'ADVANCE' && <input placeholder="Reference / UTR" value={r.reference} onChange={e => set(i, {reference: e.target.value})} aria-label="Reference" />}
      {rows.length > 1 && <button type="button" className="ghost danger" onClick={() => onChange(rows.filter((_, j) => j !== i))} aria-label="Remove payment">✕</button>}
    </div>)}
    <div className="kRow kPayFoot">
      <button type="button" className="ghost" onClick={() => onChange([...rows, emptyPay(due !== undefined ? String(Math.max(0, +(due - paid).toFixed(2)) || '') : '')])}>＋ Split payment</button>
      {due !== undefined && <span className={paid > due + 0.005 ? 'kBad' : 'kMuted'}>{paid > due + 0.005 ? `${money(paid - due)} more than due` : due - paid > 0.005 ? `${money(due - paid)} left as credit` : 'Fully paid'}</span>}
    </div>
  </div>;
}
export const payBody = (rows: PayRow[]) => rows.filter(r => num(r.amount) > 0).map(r => ({mode: r.mode, amount: num(r.amount), reference: r.reference || undefined}));

/** Shared confirm-and-run helper: runs the action, toasts the result, returns true on success. */
export async function run(action: () => Promise<unknown>, ok: string, notify: (s: string) => void): Promise<boolean> {
  try { await action(); notify(ok); return true; }
  catch (e) { notify(errText(e)); return false; }
}
