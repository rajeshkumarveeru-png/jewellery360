import {useCallback, useEffect, useMemo, useState} from 'react';
import {approvals, approve, reject, getFriendlyApiError, userRequestHistory} from '../api';
import {Ico} from '../shared/icons';
import {confirmDialog, promptDialog, pushToast, successDialog} from '../shared/Notify';
import './Approvals.css';

/* ============================================================================================
   APPROVALS (app admin): company registrations, requests for new users, password resets.
   - New user requests: Approve (the company admin can then create exactly one user) or Reject with a reason the owner sees.
   - Every decision uses the same confirm / success dialogs; approvals can tell the requester on WhatsApp.
   ============================================================================================ */
type Approval = {
  id: number; requestType: string; status: string; createdAt?: string; processedAt?: string | null; message?: string | null;
  targetUserId?: number | null; username?: string | null; email?: string | null; phone?: string | null; role?: string | null;
  companyId?: number | null; companyName?: string | null; branchId?: number | null; branchName?: string | null;
  requestedName?: string | null; decisionNote?: string | null; requestedByName?: string | null; requestedByPhone?: string | null; currentUsers?: number | null;
};
type Kind = 'ALL' | 'COMPANY' | 'USER' | 'RESET';

const ago = (iso?: string | null): string => {
  if (!iso) return '-';
  const t = new Date(iso).getTime();
  if (Number.isNaN(t)) return '-';
  const mins = Math.max(0, Math.round((Date.now() - t) / 60000));
  if (mins < 1) return 'just now';
  if (mins < 60) return `${mins} min ago`;
  if (mins < 1440) return `${Math.floor(mins / 60)} h ago`;
  const days = Math.floor(mins / 1440);
  return days < 31 ? `${days} day${days === 1 ? '' : 's'} ago` : new Date(iso).toLocaleDateString('en-IN', {day: '2-digit', month: 'short', year: 'numeric'});
};
const kindOf = (t: string): Exclude<Kind, 'ALL'> => (t === 'NEW_ACCOUNT' ? 'COMPANY' : t === 'PASSWORD_RESET' ? 'RESET' : 'USER');
const TITLE: Record<string, string> = {NEW_ACCOUNT: 'New company registration', NEW_USER: 'New company user', USER_SLOT: 'Request for a new user', PASSWORD_RESET: 'Password reset request'};
const ICON: Record<string, string> = {COMPANY: 'building', USER: 'user-plus', RESET: 'key'};
const STATUS_TEXT: Record<string, string> = {APPROVED: 'Approved - not used yet', REJECTED: 'Rejected', USED: 'User created', CANCELLED: 'Cancelled by owner'};

export default function ApprovalsModule({onNotice}: {onNotice: (x: string) => void}) {
  const [list, setList] = useState<Approval[]>([]);
  const [history, setHistory] = useState<Approval[]>([]);
  const [loading, setLoading] = useState(false);
  const [busy, setBusy] = useState<number | null>(null);
  const [kind, setKind] = useState<Kind>('ALL');

  const load = useCallback(async (quiet = false) => {
    if (!quiet) setLoading(true);
    try {
      const [p, h] = await Promise.all([approvals(), userRequestHistory().catch(() => ({data: []}))]);
      setList(Array.isArray(p.data) ? p.data : []);
      setHistory((Array.isArray(h.data) ? h.data : []).filter((x: Approval) => x.status !== 'PENDING').slice(0, 8));
    } catch (err: any) {
      setList([]);
      onNotice(getFriendlyApiError(err, 'Unable to load pending approval requests.'));
    } finally { setLoading(false); }
  }, [onNotice]);
  useEffect(() => { void load(); }, [load]);

  const counts = useMemo(() => ({
    ALL: list.length,
    COMPANY: list.filter(x => kindOf(x.requestType) === 'COMPANY').length,
    USER: list.filter(x => kindOf(x.requestType) === 'USER').length,
    RESET: list.filter(x => kindOf(x.requestType) === 'RESET').length
  }), [list]);
  const rows = list.filter(x => kind === 'ALL' || kindOf(x.requestType) === kind);

  const decide = async (r: Approval, ok: boolean) => {
    const who = r.requestType === 'USER_SLOT' ? (r.companyName || 'the company') : (r.username || 'this user');
    let note = '';
    if (ok) {
      const go = await confirmDialog({
        title: r.requestType === 'USER_SLOT' ? 'Approve a new user?' : 'Approve this request?',
        message: r.requestType === 'USER_SLOT'
          ? <><b>{r.companyName}</b> can then create one new user{r.requestedName ? <> for <b>{r.requestedName}</b></> : null}.</>
          : <>This activates the request for <b>{who}</b>.</>,
        details: [TITLE[r.requestType] || r.requestType, r.currentUsers != null ? `${r.currentUsers} user${r.currentUsers === 1 ? '' : 's'} today` : ''].filter(Boolean),
        confirmLabel: 'Approve', tone: 'success'
      });
      if (!go) return;
    } else if (r.requestType === 'USER_SLOT') {
      const reason = await promptDialog({title: 'Reject this request?', message: <>Tell <b>{r.requestedByName || r.companyName}</b> why, so they know what to do next.</>, label: 'Reason (shown to the owner)', placeholder: 'e.g. Please remove an unused user first', confirmLabel: 'Reject request'});
      if (reason === null) return;
      note = reason;
    } else {
      const go = await confirmDialog({title: 'Reject this request?', message: <>The request for <b>{who}</b> will be rejected.</>, confirmLabel: 'Reject', tone: 'danger'});
      if (!go) return;
    }
    setBusy(r.id);
    try {
      const res = await (ok ? approve(r.id) : reject(r.id, note || undefined));
      await load(true);
      if (ok) {
        const link: string | undefined = res.data?.notificationUrl;
        await successDialog({
          title: r.requestType === 'USER_SLOT' ? 'New user approved' : 'Request approved',
          message: res.data?.message || 'Request approved.',
          details: [r.companyName || '', r.requestedName ? `For: ${r.requestedName}` : ''].filter(Boolean),
          actions: link ? [{label: 'Tell owner on WhatsApp', tone: 'whatsapp', run: () => { window.open(link, '_blank', 'noopener,noreferrer'); }}] : []
        });
      } else pushToast(res.data?.message || 'Request rejected.', 'success');
    } catch (err: any) { pushToast(getFriendlyApiError(err, 'Approval action failed.'), 'error'); }
    finally { setBusy(null); }
  };

  return (
    <div className="panel page-approvals apPage">
      <div className="panelHead">
        <div>
          <span className="eyebrow">GOVERNANCE</span>
          <h2>Pending approvals</h2>
          <p className="approvalIntro">Company registrations, requests for new users and password resets. A new user can only be created after you approve the request.</p>
        </div>
        <button className="ghost" onClick={() => void load()} disabled={loading}>{loading ? 'Refreshing…' : 'Refresh'}</button>
      </div>
      <div className="apChips" role="group" aria-label="Request type">
        {([['ALL', 'All'], ['USER', 'New users'], ['COMPANY', 'Companies'], ['RESET', 'Password resets']] as [Kind, string][]).map(([k, label]) => (
          <button key={k} type="button" className={kind === k ? 'on' : ''} aria-pressed={kind === k} onClick={() => setKind(k)}>{label} <i>{counts[k]}</i></button>
        ))}
      </div>
      <div className="apList">
        {rows.map(r => {
          const k = kindOf(r.requestType);
          return (
            <article className={`apCard ${k.toLowerCase()}`} key={r.id}>
              <span className="apIcon"><Ico name={ICON[k]} size={20} /></span>
              <div className="apMain">
                <div className="apTop"><span className="apType">{TITLE[r.requestType] || r.requestType}</span><small>#{r.id} · {ago(r.createdAt)}</small></div>
                {r.requestType === 'USER_SLOT' ? (
                  <>
                    <h3>{r.companyName || 'Company'} <span className="apBadge">{r.currentUsers ?? 0} user{r.currentUsers === 1 ? '' : 's'}</span></h3>
                    <p className="apFor">For: <b>{r.requestedName}</b></p>
                    <p className="apSub">{r.message ? `“${r.message}”` : 'No reason given'} · by {r.requestedByName || '-'}{r.requestedByPhone ? ` · ${r.requestedByPhone}` : ''}</p>
                  </>
                ) : (
                  <>
                    <h3>{r.username || '—'}{r.companyName ? <span className="apBadge">{r.companyName}</span> : null}</h3>
                    <p className="apSub">{[r.email, r.phone, r.role?.replaceAll('_', ' '), r.branchName].filter(Boolean).join(' · ') || '—'}</p>
                    {r.message && <p className="apSub">{r.message}</p>}
                  </>
                )}
              </div>
              <div className="apActions">
                <button className="primary" disabled={busy === r.id} onClick={() => void decide(r, true)}><Ico name="check" size={14} /> Approve</button>
                <button className="ghost danger" disabled={busy === r.id} onClick={() => void decide(r, false)}><Ico name="ban" size={14} /> Reject</button>
              </div>
            </article>
          );
        })}
      </div>
      {!loading && !rows.length && <div className="apEmpty"><Ico name="check-circle" size={28} /><b>No pending approval requests.</b><small>New requests appear here and on refresh.</small></div>}
      {history.length > 0 && (
        <details className="apHistory"><summary><Ico name="users" size={14} /> Recent user requests <em>{history.length}</em></summary>
          <div className="apHistRows">{history.map(r => <div key={r.id}><b>{r.companyName || r.requestedByName}</b><span>{r.requestedName}</span><i className={`tmPill ${String(r.status).toLowerCase()}`}>{STATUS_TEXT[r.status] || r.status}</i><small>{ago(r.processedAt || r.createdAt)}</small></div>)}</div>
        </details>
      )}
    </div>
  );
}
