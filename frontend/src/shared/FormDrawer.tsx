import {useEffect, useRef, useState} from 'react';
import {usePermissions} from './permissions';
import type {ReactNode} from 'react';

/* Slide-over entry form (same idea as BIZ360's EntryDrawer). The page keeps ALL of its form state and handlers;
   this component only decides when the form is visible: after "+ New ..." is pressed or while a record is being edited.
   A successful save closes it: the shell broadcasts every toast as a "j360-notice" event, and a notice that does not look
   like an error, arriving after the form was submitted, closes the drawer. On an error the drawer stays open with the typed values. */
const ERROR_WORDS = /unable|fail|error|invalid|required|select company|not available|already exists|cannot|denied|forbidden|not found|too long|must /i;

export default function FormDrawer({label, title, editTitle, hint, editing = false, onCancel, needsManage = false, children}: {
  label: string; title: string; editTitle?: string; hint?: string; editing?: boolean; onCancel?: () => void; needsManage?: boolean; children: ReactNode;
}) {
  const perms = usePermissions();
  const [manual, setManual] = useState(false);
  const submitted = useRef(false);
  const body = useRef<HTMLDivElement>(null);
  const open = manual || editing;

  const dismiss = () => {
    if (editing && onCancel) onCancel();
    setManual(false);
    submitted.current = false;
  };

  useEffect(() => {
    const onNotice = (e: Event) => {
      if (!submitted.current) return;
      submitted.current = false;
      const message = String((e as CustomEvent).detail || '');
      if (!ERROR_WORDS.test(message)) setManual(false);
    };
    window.addEventListener('j360-notice', onNotice);
    return () => window.removeEventListener('j360-notice', onNotice);
  }, []);

  useEffect(() => {
    if (!open) return;
    const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape') dismiss(); };
    window.addEventListener('keydown', onKey);
    const t = window.setTimeout(() => {
      body.current?.querySelector<HTMLElement>('input:not([disabled]):not([readonly]):not([type="checkbox"]), textarea:not([disabled]), select:not([disabled])')?.focus();
    }, 330);
    return () => { window.removeEventListener('keydown', onKey); window.clearTimeout(t); };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open]);

  // a user without the "add / edit / delete records" power never sees the create form of these pages
  if (needsManage && !perms.canManage) return null;
  return (
    <>
      <div className="entryTrigger">
        {hint && <p>{hint}</p>}
        <button type="button" className="entryNew" onClick={() => setManual(true)}><span aria-hidden="true">＋</span> {label}</button>
      </div>
      <div className={`entryBackdrop${open ? ' open' : ''}`} onClick={dismiss} aria-hidden="true" />
      <div className={`formDrawer${open ? ' open' : ''}`} role="dialog" aria-modal="true" aria-hidden={!open} aria-label={editing ? (editTitle || title) : title}>
        <div className="formDrawerHead">
          <div><small>{editing ? 'EDITING' : 'NEW ENTRY'}</small><h2>{editing ? (editTitle || title) : title}</h2></div>
          <button type="button" className="formDrawerClose" onClick={dismiss} aria-label="Close form">✕</button>
        </div>
        <div className="formDrawerBody" ref={body} onSubmit={() => { submitted.current = true; }}>{children}</div>
        <div className="formDrawerHint" aria-hidden="true"><kbd>Esc</kbd> to close</div>
      </div>
    </>
  );
}
