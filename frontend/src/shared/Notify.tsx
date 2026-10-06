import {useCallback, useEffect, useRef, useState} from 'react';
import type {CSSProperties, ReactNode} from 'react';
import {Ico} from './icons';

/* ============================================================================================
   One notification system for the whole app: toasts, confirm / prompt dialogs and success popups.
   Use pushToast(), confirmDialog(), promptDialog() and successDialog() from anywhere - no native
   browser popups (alert / confirm / prompt) are used.
   ============================================================================================ */
export type ToastKind = 'success' | 'error' | 'warning' | 'info';
export type DialogTone = 'primary' | 'success' | 'danger' | 'warning';

type ToastItem = {id: number; kind: ToastKind; title: string; message: string; ms: number};
type DialogAction = {label: string; run: () => void; tone?: 'whatsapp' | 'primary' | 'ghost'};
type Dialog =
  | {id: number; type: 'confirm'; title: string; message: ReactNode; details?: string[]; confirmLabel: string; cancelLabel: string; tone: DialogTone; resolve: (v: boolean) => void}
  | {id: number; type: 'prompt'; title: string; message?: ReactNode; label: string; initial: string; placeholder: string; confirmLabel: string; resolve: (v: string | null) => void}
  | {id: number; type: 'success'; title: string; message: ReactNode; details?: string[]; actions: DialogAction[]; doneLabel: string; resolve: () => void};

let seq = 1;
const toastBus = new Set<(items: ToastItem[]) => void>();
const dialogBus = new Set<(items: Dialog[]) => void>();
let toasts: ToastItem[] = [];
let dialogs: Dialog[] = [];
const emitToasts = () => toastBus.forEach(f => f(toasts));
const emitDialogs = () => dialogBus.forEach(f => f(dialogs));

/** Plain text messages are classified so every existing onNotice('...') call gets the right look. */
export const inferKind = (message: string): ToastKind => {
  const m = message.toLowerCase();
  if (/(fail|error|unable|invalid|cannot|can't|couldn't|not found|denied|forbidden|required|please enter|enter a|must |already (in use|exists|registered)|expired|insufficient|not allowed|not available)/.test(m)) return 'error';
  if (/(allow pop-?ups|not configured|manually|warning|low stock|no matching|nothing to|skipped)/.test(m)) return 'warning';
  if (/(saved|success|added|updated|approved|activated|deactivated|created|refreshed|deleted|removed|cancelled|sent|imported|opened|exported|reactivated|applied|submitted)/.test(m)) return 'success';
  return 'info';
};
const TITLES: Record<ToastKind, string> = {success: 'Done', error: 'Something went wrong', warning: 'Heads up', info: 'Notice'};

export function pushToast(message: string, kind?: ToastKind, title?: string) {
  const text = String(message ?? '').trim();
  if (!text) return;
  const k = kind ?? inferKind(text);
  const item: ToastItem = {id: seq++, kind: k, title: title ?? TITLES[k], message: text, ms: k === 'error' ? 6200 : k === 'warning' ? 5200 : 3800};
  toasts = [...toasts.filter(t => t.message !== text), item].slice(-4);
  emitToasts();
}
const dropToast = (id: number) => { toasts = toasts.filter(t => t.id !== id); emitToasts(); };

export function confirmDialog(o: {title: string; message?: ReactNode; details?: string[]; confirmLabel?: string; cancelLabel?: string; tone?: DialogTone}): Promise<boolean> {
  return new Promise(resolve => {
    dialogs = [...dialogs, {id: seq++, type: 'confirm', title: o.title, message: o.message ?? '', details: o.details, confirmLabel: o.confirmLabel ?? 'Confirm', cancelLabel: o.cancelLabel ?? 'Cancel', tone: o.tone ?? 'primary', resolve}];
    emitDialogs();
  });
}
export function promptDialog(o: {title: string; message?: ReactNode; label?: string; initial?: string; placeholder?: string; confirmLabel?: string}): Promise<string | null> {
  return new Promise(resolve => {
    dialogs = [...dialogs, {id: seq++, type: 'prompt', title: o.title, message: o.message, label: o.label ?? '', initial: o.initial ?? '', placeholder: o.placeholder ?? '', confirmLabel: o.confirmLabel ?? 'Save', resolve}];
    emitDialogs();
  });
}
export function successDialog(o: {title: string; message?: ReactNode; details?: string[]; actions?: DialogAction[]; doneLabel?: string}): Promise<void> {
  return new Promise(resolve => {
    dialogs = [...dialogs, {id: seq++, type: 'success', title: o.title, message: o.message ?? '', details: o.details, actions: o.actions ?? [], doneLabel: o.doneLabel ?? 'Done', resolve}];
    emitDialogs();
  });
}
const closeDialog = (id: number) => { dialogs = dialogs.filter(d => d.id !== id); emitDialogs(); };

const ICON: Record<ToastKind, string> = {success: 'check-circle', error: 'x-circle', warning: 'triangle', info: 'info'};

function ToastCard({item}: {item: ToastItem}) {
  const [paused, setPaused] = useState(false);
  const left = useRef(item.ms);
  const started = useRef(Date.now());
  useEffect(() => {
    if (paused) { left.current -= Date.now() - started.current; return; }
    started.current = Date.now();
    const timer = window.setTimeout(() => dropToast(item.id), Math.max(300, left.current));
    return () => window.clearTimeout(timer);
  }, [paused, item.id]);
  return (
    <div className={`nt-toast ${item.kind}`} role={item.kind === 'error' ? 'alert' : 'status'} onMouseEnter={() => setPaused(true)} onMouseLeave={() => setPaused(false)}>
      <span className="nt-ico"><Ico name={ICON[item.kind]} size={20} /></span>
      <div className="nt-copy"><b>{item.title}</b><span>{item.message}</span></div>
      <button type="button" className="nt-x" aria-label="Dismiss" onClick={() => dropToast(item.id)}><Ico name="x" size={15} /></button>
      <i className="nt-bar" style={{animationDuration: `${item.ms}ms`, animationPlayState: paused ? 'paused' : 'running'}} />
    </div>
  );
}

function DialogView({d}: {d: Dialog}) {
  const [text, setText] = useState(d.type === 'prompt' ? d.initial : '');
  const primary = useRef<HTMLButtonElement | null>(null);
  const input = useRef<HTMLInputElement | null>(null);
  useEffect(() => { window.setTimeout(() => (d.type === 'prompt' ? input.current : primary.current)?.focus(), 40); }, [d.type]);
  const finish = useCallback((ok: boolean) => {
    if (d.type === 'confirm') d.resolve(ok);
    else if (d.type === 'prompt') d.resolve(ok ? text.trim() : null);
    else d.resolve();
    closeDialog(d.id);
  }, [d, text]);
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') { e.preventDefault(); finish(false); }
      else if (e.key === 'Enter' && d.type !== 'success' && !(e.target instanceof HTMLButtonElement)) { e.preventDefault(); finish(true); }
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [finish, d.type]);
  const tone = d.type === 'success' ? 'success' : d.type === 'prompt' ? 'primary' : d.tone;
  const icon = d.type === 'success' ? 'check-circle' : tone === 'danger' || tone === 'warning' ? 'triangle' : 'help';
  return (
    <div className="nt-overlay" onMouseDown={e => { if (e.target === e.currentTarget && d.type !== 'success') finish(false); }}>
      <div className={`nt-dialog ${tone}`} role="dialog" aria-modal="true" aria-label={d.title}>
        {d.type === 'success' && <div className="nt-confetti" aria-hidden="true">{Array.from({length: 16}).map((_, i) => <i key={i} style={{['--i' as string]: i} as CSSProperties} />)}</div>}
        <div className="nt-badge"><Ico name={icon} size={34} /></div>
        <h3>{d.title}</h3>
        {d.message ? <p>{d.message}</p> : null}
        {'details' in d && d.details && d.details.length > 0 && <div className="nt-details">{d.details.map(x => <span key={x}>{x}</span>)}</div>}
        {d.type === 'prompt' && (
          <label className="nt-field">
            {d.label && <span>{d.label}</span>}
            <input ref={input} value={text} placeholder={d.placeholder} onChange={e => setText(e.target.value)} />
          </label>
        )}
        <div className="nt-actions">
          {d.type === 'success' ? (
            <>
              {d.actions.map(a => <button key={a.label} type="button" className={`nt-btn ${a.tone ?? 'primary'}`} onClick={() => a.run()}>{a.label}</button>)}
              <button ref={primary} type="button" className="nt-btn done" onClick={() => finish(true)}>{d.doneLabel}</button>
            </>
          ) : (
            <>
              <button type="button" className="nt-btn ghost" onClick={() => finish(false)}>{d.type === 'confirm' ? d.cancelLabel : 'Cancel'}</button>
              <button ref={primary} type="button" className={`nt-btn ${tone}`} disabled={d.type === 'prompt' && !text.trim()} onClick={() => finish(true)}>{d.confirmLabel}</button>
            </>
          )}
        </div>
      </div>
    </div>
  );
}

export function NotifyHost() {
  const [ts, setTs] = useState<ToastItem[]>(toasts);
  const [ds, setDs] = useState<Dialog[]>(dialogs);
  useEffect(() => {
    toastBus.add(setTs); dialogBus.add(setDs);
    setTs(toasts); setDs(dialogs);
    return () => { toastBus.delete(setTs); dialogBus.delete(setDs); };
  }, []);
  return (
    <>
      <div className="nt-stack" aria-live="polite">{ts.map(t => <ToastCard key={t.id} item={t} />)}</div>
      {ds[0] && <DialogView key={ds[0].id} d={ds[0]} />}
    </>
  );
}
