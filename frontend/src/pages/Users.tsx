import {CSSProperties, FormEvent, ReactNode, useCallback, useEffect, useMemo, useState} from 'react';
import {
  approvals, approve, reject, branches, cancelSlotRequest, createSlotRequest, createUser, disableUser, enableUser, getFriendlyApiError,
  setUserPermissions, updateUser, userSlotRequests, users
} from '../api';
import {Branch, Role, User} from '../shared/types';
import {Ico} from '../shared/icons';
import {confirmDialog, pushToast, successDialog} from '../shared/Notify';
import {
  FieldHint, FieldState, IDLE, PasswordInput, PasswordMeter, checkConfirm, checkEmail, checkPassword, checkPhone, checkUsername, generatePassword, normalizePhone
} from '../shared/AuthFields';
import {useAvailability} from '../shared/useAvailability';
import AccessEditor from '../shared/AccessEditor';
import {PAGE_DEFS, POWER_DEFS, ROLE_LABEL, STAFF_ROLES, presetOf, roleDefaults} from '../shared/permissions';
import './Users.css';

/* ============================================================================================
   USERS - the company admin manages the staff of the company.
   Every NEW user needs an approved request from the app administrator (no fixed number of users):
   request -> approval -> create the user. Per user: pages and powers, password, active / inactive, details.
   Password-reset requests of the staff are approved or disapproved here (the app admin can do it on the Approvals page too).
   ============================================================================================ */
type Req = {id: number; requestedName: string; note?: string | null; status: string; createdAt?: string | null; decidedAt?: string | null; decisionNote?: string | null; createdUserId?: number | null};
type Reset = {id: number; username?: string | null; phone?: string | null; email?: string | null; createdAt?: string; targetUserId?: number | null; requestType: string};
type Filter = 'ALL' | 'ACTIVE' | 'INACTIVE';

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
const hue = (name: string): number => { let h = 0; for (const ch of name) h = (h * 31 + ch.charCodeAt(0)) >>> 0; return h % 360; };
const waLink = (phone: string | null | undefined, text: string): string | null => {
  const d = String(phone || '').replace(/\D/g, '');
  if (!d) return null;
  return `https://wa.me/${d.length === 10 ? '91' + d : d}?text=${encodeURIComponent(text)}`;
};
const copyText = async (text: string, what: string) => {
  try { await navigator.clipboard.writeText(text); pushToast(`${what} copied`, 'success'); }
  catch { pushToast(`Could not copy ${what.toLowerCase()} - select it and copy manually`, 'warning'); }
};
const accessInfo = (u: {role: string; permissions?: string[]}) => {
  const keys = u.permissions && u.permissions.length ? u.permissions : roleDefaults(u.role);
  const id = presetOf(keys, u.role);
  return {id, label: id === 'CUSTOM' ? 'Custom access' : `${ROLE_LABEL[id] || id} access`, pages: PAGE_DEFS.filter(p => keys.includes(p.key)).length, keys};
};
const pagesText = (n: number) => `${n} page${n === 1 ? '' : 's'}`;
const errText = (e: any, fallback: string) => getFriendlyApiError(e, fallback);

/* ---------------- request a new user ---------------- */
function RequestModal({onClose, onSent}: {onClose: () => void; onSent: (name: string) => void}) {
  const [name, setName] = useState('');
  const [note, setNote] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const ok = name.trim().length >= 2;
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape') onClose(); };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [onClose]);
  const submit = async (e: FormEvent) => {
    e.preventDefault();
    if (!ok || busy) return;
    setBusy(true); setError('');
    try { await createSlotRequest({requestedName: name.trim(), note: note.trim()}); onSent(name.trim()); }
    catch (err: any) { setError(errText(err, 'Unable to send the request.')); }
    finally { setBusy(false); }
  };
  return (
    <div className="nt-overlay" onMouseDown={e => { if (e.target === e.currentTarget) onClose(); }}>
      <form className="nt-dialog primary tmModal" role="dialog" aria-modal="true" aria-label="Request a new user" onSubmit={submit}>
        <div className="nt-badge"><Ico name="user-plus" size={32} /></div>
        <h3>Request a new user</h3>
        <p>The app administrator reviews every new user. Once it is approved you can create the account.</p>
        <div className="tmFields">
          <label className="afField"><span className="afLabel">Who is the new user for?</span>
            <div className="afWrap"><Ico name="users" size={18} /><input value={name} onChange={e => setName(e.target.value)} placeholder="Name of the person" autoFocus maxLength={80} autoComplete="off" /></div></label>
          <label className="afField"><span className="afLabel">Reason / role (optional)</span>
            <textarea className="tmTextarea" value={note} onChange={e => setNote(e.target.value)} placeholder="e.g. Evening cashier for the second counter" maxLength={300} rows={3} /></label>
          {error && <div className="afMsg bad" role="alert">{error}</div>}
        </div>
        <div className="nt-actions">
          <button type="button" className="nt-btn ghost" onClick={onClose}>Cancel</button>
          <button type="submit" className="nt-btn primary" disabled={!ok || busy}>{busy ? 'Sending…' : 'Send request'}</button>
        </div>
      </form>
    </div>
  );
}

/* ---------------- set a new password ---------------- */
function PasswordModal({member, onClose, onSaved}: {member: User; onClose: () => void; onSaved: (password: string) => void}) {
  const [password, setPassword] = useState('');
  const [confirm, setConfirm] = useState('');
  const [busy, setBusy] = useState(false);
  const pw = checkPassword(password), match = checkConfirm(password, confirm);
  const ok = pw.state === 'ok' && match.state === 'ok';
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape') onClose(); };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [onClose]);
  const submit = async (e: FormEvent) => {
    e.preventDefault();
    if (!ok || busy) return;
    setBusy(true);
    try {
      await updateUser(member.id, {username: member.username, email: member.email, phone: member.phone || '', role: member.role, branchId: member.branchId, password});
      onSaved(password);
    } catch (err: any) { pushToast(errText(err, 'Unable to save the new password.'), 'error'); }
    finally { setBusy(false); }
  };
  return (
    <div className="nt-overlay" onMouseDown={e => { if (e.target === e.currentTarget) onClose(); }}>
      <form className="nt-dialog primary tmModal" role="dialog" aria-modal="true" aria-label="Set a new password" onSubmit={submit}>
        <div className="nt-badge"><Ico name="key" size={32} /></div>
        <h3>Set a new password</h3>
        <p>for <b>{member.username}</b>. They can sign in with it right away.</p>
        <div className="tmFields">
          <label className="afField"><span className="afLabel">New password</span>
            <PasswordInput value={password} onChange={setPassword} placeholder="At least 8 characters" autoFocus invalid={pw.state === 'bad'} />
            <PasswordMeter value={password} /><FieldHint s={pw} /></label>
          <label className="afField"><span className="afLabel">Confirm password</span>
            <PasswordInput value={confirm} onChange={setConfirm} placeholder="Re-enter the password" invalid={match.state === 'bad'} />
            <FieldHint s={match} /></label>
          <div className="tmGen">
            <button type="button" onClick={() => { const g = generatePassword(); setPassword(g); setConfirm(g); }}><Ico name="sparkles" size={14} /> Generate strong password</button>
            {password && <button type="button" onClick={() => void copyText(password, 'Password')}><Ico name="copy" size={14} /> Copy</button>}
          </div>
        </div>
        <div className="nt-actions">
          <button type="button" className="nt-btn ghost" onClick={onClose}>Cancel</button>
          <button type="submit" className="nt-btn primary" disabled={!ok || busy}>{busy ? 'Saving…' : 'Save password'}</button>
        </div>
      </form>
    </div>
  );
}

/* ---------------- slide-over shell ---------------- */
function Drawer({title, sub, icon, onClose, foot, children}: {title: string; sub?: string; icon: string; onClose: () => void; foot?: ReactNode; children: ReactNode}) {
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape') onClose(); };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [onClose]);
  return (
    <div className="tmOverlay" onMouseDown={e => { if (e.target === e.currentTarget) onClose(); }}>
      <div className="tmDrawer" role="dialog" aria-modal="true" aria-label={title}>
        <div className="tmDrawerHead"><span className="tmDrawerIcon"><Ico name={icon} size={22} /></span><div><b>{title}</b>{sub && <small>{sub}</small>}</div>
          <button type="button" className="tmX" aria-label="Close" onClick={onClose}><Ico name="x" size={18} /></button></div>
        <div className="tmDrawerBody">{children}</div>
        {foot && <div className="tmDrawerFoot">{foot}</div>}
      </div>
    </div>
  );
}

/* ---------------- create / edit ---------------- */
type DrawerState = {mode: 'create' | 'edit'; member: User | null; request?: Req | null};

function MemberDrawer({me, state, branchList, onClose, onSaved}: {me: User; state: DrawerState; branchList: Branch[]; onClose: () => void; onSaved: (saved: User, password?: string) => void}) {
  const editing = state.mode === 'edit' && !!state.member;
  const m = state.member;
  const [f, setF] = useState({
    username: m?.username ?? '', email: m?.email ?? '', phone: m?.phone ?? '', role: (m?.role ?? 'CASHIER') as Role, branchId: m?.branchId != null ? String(m.branchId) : '',
    password: editing ? '' : generatePassword()
  });
  const [confirm, setConfirm] = useState(editing ? '' : f.password);
  const [access, setAccess] = useState<string[]>(roleDefaults('CASHIER'));
  const [accessTouched, setAccessTouched] = useState(false);
  const [touched, setTouched] = useState<Record<string, boolean>>({});
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const username = f.username.trim(), email = f.email.trim().toLowerCase(), phone = normalizePhone(f.phone);
  const base: Record<string, FieldState> = {
    username: editing ? {state: 'ok', message: ''} : checkUsername(username),
    email: checkEmail(email),
    phone: f.phone.trim() ? checkPhone(f.phone) : {state: 'ok', message: ''}
  };
  const emailChanged = !editing || email !== String(m?.email || '').toLowerCase();
  const phoneChanged = !editing || phone !== String(m?.phone || '');
  const avail = useAvailability({
    username: !editing && base.username.state === 'ok' ? username : undefined,
    email: emailChanged && base.email.state === 'ok' ? email : undefined,
    phone: phoneChanged && f.phone.trim() && base.phone.state === 'ok' ? phone : undefined
  });
  const states: Record<string, FieldState> = {
    username: editing ? base.username : avail.state(base.username, 'username', username, 'username'),
    email: avail.state(base.email, 'email', email, 'email', emailChanged),
    phone: f.phone.trim() ? avail.state(base.phone, 'phone', phone, 'phone number', phoneChanged) : base.phone,
    ...(editing ? {} : {password: checkPassword(f.password), confirm: checkConfirm(f.password, confirm)})
  };
  const allOk = Object.values(states).every(s => s.state === 'ok');
  const shown = (k: string): FieldState => { const s = states[k]; if (!s) return IDLE; return s.state === 'ok' && !s.message ? IDLE : (k === 'username' && editing ? IDLE : s); };
  const wrap = (k: string) => `afWrap${shown(k).state === 'bad' ? ' invalid' : shown(k).state === 'ok' ? ' valid' : ''}`;
  const touch = (k: string) => setTouched(c => ({...c, [k]: true}));
  const info = accessInfo({role: f.role, permissions: access});
  const pickRole = (role: Role) => { setF(cur => ({...cur, role})); if (!accessTouched) setAccess(roleDefaults(role)); };

  const submit = async (e?: FormEvent) => {
    e?.preventDefault();
    setError('');
    setTouched({username: true, email: true, phone: true, password: true, confirm: true});
    if (!allOk) { setError('Please fix the highlighted fields.'); return; }
    setBusy(true);
    try {
      if (editing && m) {
        const r = await updateUser(m.id, {username: m.username, email, phone, role: f.role, branchId: f.branchId ? Number(f.branchId) : m.branchId});
        onSaved(r.data);
      } else {
        const r = await createUser({username, email, phone, password: f.password, role: f.role, branchId: me.branchId ?? undefined, permissions: access, requestId: state.request?.id});
        onSaved(r.data, f.password);
      }
    } catch (err: any) { setError(errText(err, editing ? 'Unable to save the changes.' : 'Unable to create the user.')); }
    finally { setBusy(false); }
  };
  const noteReq = state.request;
  return (
    <Drawer
      title={editing ? `Edit ${m?.username}` : 'Create user'} sub={editing ? `${ROLE_LABEL[m!.role] || m!.role}` : noteReq ? `Approved: ${noteReq.requestedName}` : 'Active immediately'}
      icon={editing ? 'edit' : 'user-plus'} onClose={onClose}
      foot={<><button type="button" className="tmBtn ghost" onClick={onClose}>Cancel</button>
        <button type="button" className="tmBtn solid" disabled={busy} onClick={() => void submit()}><Ico name={editing ? 'check' : 'user-plus'} size={14} /> {busy ? 'Saving…' : editing ? 'Save changes' : 'Create user'}</button></>}
    >
      <form className="tmForm" onSubmit={submit} noValidate autoComplete="off">
        {!editing && (
          <label className="afField"><span className="afLabel">Username (used to sign in)</span>
            <div className={wrap('username')}><Ico name="users" size={18} /><input name="tm-username" value={f.username} onChange={e => setF({...f, username: e.target.value.replace(/\s/g, '')})} onBlur={() => touch('username')} placeholder={noteReq ? noteReq.requestedName.toLowerCase().replace(/\s+/g, '.') : 'e.g. counter2.cashier'} autoComplete="off" maxLength={80} autoFocus /></div>
            <FieldHint s={shown('username')} /></label>
        )}
        <label className="afField"><span className="afLabel">Email</span>
          <div className={wrap('email')}><Ico name="mail" size={18} /><input name="tm-email" type="email" value={f.email} onChange={e => setF({...f, email: e.target.value})} onBlur={() => touch('email')} placeholder="user@business.com" autoComplete="off" /></div>
          <FieldHint s={shown('email')} /></label>
        <label className="afField"><span className="afLabel">WhatsApp phone - optional</span>
          <div className={wrap('phone')}><Ico name="phone" size={18} /><input name="tm-phone" value={f.phone} onChange={e => setF({...f, phone: e.target.value.replace(/[^\d+\s-]/g, '').slice(0, 20)})} onBlur={() => touch('phone')} placeholder="10-15 digit number" inputMode="tel" autoComplete="off" /></div>
          <FieldHint s={shown('phone')} /></label>
        <div className="tmRow2">
          <label className="afField"><span className="afLabel">Role</span>
            <select className="tmSelect" value={f.role} onChange={e => pickRole(e.target.value as Role)}>{STAFF_ROLES.map(r => <option key={r} value={r}>{ROLE_LABEL[r]}</option>)}</select></label>
          {editing && branchList.length > 0 && (
            <label className="afField"><span className="afLabel">Branch</span>
              <select className="tmSelect" value={f.branchId} onChange={e => setF({...f, branchId: e.target.value})}>{branchList.map(b => <option key={b.id} value={b.id}>{b.name}</option>)}</select></label>
          )}
        </div>
        {!editing && (
          <>
            <label className="afField"><span className="afLabel">Temporary password</span>
              <PasswordInput value={f.password} onChange={v => setF({...f, password: v})} onBlur={() => touch('password')} placeholder="At least 8 characters" invalid={shown('password').state === 'bad'} />
              <PasswordMeter value={f.password} /><FieldHint s={shown('password')} /></label>
            <label className="afField"><span className="afLabel">Confirm password</span>
              <PasswordInput value={confirm} onChange={setConfirm} onBlur={() => touch('confirm')} placeholder="Re-enter the password" invalid={shown('confirm').state === 'bad'} />
              <FieldHint s={shown('confirm')} /></label>
            <div className="tmGen">
              <button type="button" onClick={() => { const g = generatePassword(); setF(cur => ({...cur, password: g})); setConfirm(g); }}><Ico name="sparkles" size={14} /> Generate strong password</button>
              <button type="button" disabled={!f.password} onClick={() => void copyText(f.password, 'Password')}><Ico name="copy" size={14} /> Copy</button>
            </div>
            <details className="tmAccess">
              <summary><Ico name="shield" size={15} /> Access: <b>{info.label}</b> · {pagesText(info.pages)}<em>change</em></summary>
              <AccessEditor value={access} role={f.role} onChange={v => { setAccess(v); setAccessTouched(true); }} />
            </details>
            <p className="tmNote"><Ico name="shield" size={14} /> Share the password privately. The user can reset it with "Forgot password", and you approve that request here.</p>
          </>
        )}
        {error && <div className="afMsg bad" role="alert">{error}</div>}
      </form>
    </Drawer>
  );
}

/* ---------------- manage access ---------------- */
function AccessDrawer({member, onClose, onSaved}: {member: User; onClose: () => void; onSaved: (saved: User) => void}) {
  const start = member.permissions && member.permissions.length ? member.permissions : roleDefaults(member.role);
  const [access, setAccess] = useState<string[]>(start);
  const [busy, setBusy] = useState(false);
  const changed = JSON.stringify([...access].sort()) !== JSON.stringify([...start].sort());
  const save = async () => {
    if (!changed || busy) return;
    const labels = [...PAGE_DEFS, ...POWER_DEFS];
    const removed = start.filter(k => !access.includes(k)).map(k => labels.find(l => l.key === k)?.label).filter(Boolean) as string[];
    if (removed.length) {
      const ok = await confirmDialog({title: 'Take this access away?', message: <><b>{member.username}</b> loses it right away. You can give it back any time.</>, details: removed, confirmLabel: 'Save changes', tone: 'warning'});
      if (!ok) return;
    }
    setBusy(true);
    try { onSaved((await setUserPermissions(member.id, access)).data); }
    catch (err: any) { pushToast(errText(err, 'Unable to save the access.'), 'error'); }
    finally { setBusy(false); }
  };
  return (
    <Drawer title={`Access for ${member.username}`} sub="Applies immediately" icon="shield" onClose={onClose}
      foot={<><button type="button" className="tmBtn ghost" onClick={onClose}>Cancel</button>
        <button type="button" className="tmBtn solid" disabled={!changed || busy} onClick={() => void save()}><Ico name="check" size={14} /> {busy ? 'Saving…' : 'Save access'}</button></>}>
      <AccessEditor value={access} role={member.role} onChange={setAccess} />
    </Drawer>
  );
}

/* ---------------- page ---------------- */
export default function UsersModule({user, onNotice, focusCreate = 0}: {user: User; onNotice: (x: string) => void; focusCreate?: number}) {
  const isAppAdmin = user.role === 'APP_ADMIN';
  const isCompanyAdmin = user.role === 'COMPANY_ADMIN';
  const can = isAppAdmin || isCompanyAdmin;
  const [list, setList] = useState<User[]>([]);
  const [branchList, setBranchList] = useState<Branch[]>([]);
  const [requests, setRequests] = useState<Req[]>([]);
  const [resets, setResets] = useState<Reset[]>([]);
  const [loading, setLoading] = useState(true);
  const [search, setSearch] = useState('');
  const [filter, setFilter] = useState<Filter>('ALL');
  const [view, setView] = useState<'cards' | 'table'>(() => { try { return localStorage.getItem('j360_users_view') === 'table' ? 'table' : 'cards'; } catch { return 'cards'; } });
  const [drawer, setDrawer] = useState<DrawerState | null>(null);
  const [accessFor, setAccessFor] = useState<User | null>(null);
  const [pwFor, setPwFor] = useState<User | null>(null);
  const [requestOpen, setRequestOpen] = useState(false);
  const [busy, setBusy] = useState<number | null>(null);
  const [showAllReq, setShowAllReq] = useState(false);
  useEffect(() => { try { localStorage.setItem('j360_users_view', view); } catch { /* storage unavailable */ } }, [view]);

  const load = useCallback(async (quiet = false) => {
    if (!quiet) setLoading(true);
    try {
      const [u, r, a, b] = await Promise.all([
        users(search),
        isCompanyAdmin ? userSlotRequests().catch(() => ({data: []})) : Promise.resolve({data: []}),
        isCompanyAdmin ? approvals().catch(() => ({data: []})) : Promise.resolve({data: []}),
        can ? branches().catch(() => ({data: []})) : Promise.resolve({data: []})
      ]);
      setList(Array.isArray(u.data) ? u.data : []);
      setRequests(Array.isArray(r.data) ? r.data : []);
      setResets((Array.isArray(a.data) ? a.data : []).filter((x: Reset) => x.requestType === 'PASSWORD_RESET'));
      setBranchList((Array.isArray(b.data) ? b.data : []).filter((x: any) => x.active !== false));
    } catch (e: any) { setList([]); if (!quiet) onNotice(errText(e, 'Unable to load users')); }
    finally { setLoading(false); }
  }, [search, isCompanyAdmin, can, onNotice]);
  useEffect(() => { const t = window.setTimeout(() => void load(), 250); return () => window.clearTimeout(t); }, [load]);
  // the administrator's decision should show up without a manual refresh
  useEffect(() => {
    const refresh = () => { void load(true); };
    const timer = window.setInterval(refresh, 60000);
    window.addEventListener('focus', refresh);
    return () => { window.clearInterval(timer); window.removeEventListener('focus', refresh); };
  }, [load]);

  const approved = useMemo(() => requests.filter(r => r.status === 'APPROVED'), [requests]);
  const pending = useMemo(() => requests.filter(r => r.status === 'PENDING'), [requests]);
  const resetOf = (u: User) => resets.find(r => r.targetUserId === u.id);
  const counts = useMemo(() => ({ALL: list.length, ACTIVE: list.filter(u => u.enabled).length, INACTIVE: list.filter(u => !u.enabled).length}), [list]);
  const rows = useMemo(() => list.filter(u => filter === 'ALL' || (filter === 'ACTIVE' ? u.enabled : !u.enabled)), [list, filter]);
  const stats = [
    {label: 'Users', value: String(counts.ALL), hint: `${counts.ACTIVE} can sign in`, tone: 'gold'},
    ...(isCompanyAdmin ? [
      {label: 'Approved to create', value: String(approved.length), hint: approved.length ? 'ready - create the user' : 'request one to add a user', tone: 'green'},
      {label: 'Waiting for admin', value: String(pending.length), hint: pending.length ? 'pending approval' : 'no open requests', tone: 'amber'}
    ] : []),
    {label: 'Inactive', value: String(counts.INACTIVE), hint: 'access switched off', tone: 'rose'},
    ...(isCompanyAdmin ? [{label: 'Password requests', value: String(resets.length), hint: resets.length ? 'waiting for you' : 'none waiting', tone: 'teal'}] : [])
  ];

  const createFrom = (r: Req | null) => setDrawer({mode: 'create', member: null, request: r});
  /** App admin: create directly. Company admin: use an approved request, otherwise ask the app administrator first. */
  const openCreate = useCallback(() => {
    if (isAppAdmin) createFrom(null);
    else if (approved.length) createFrom(approved[0]);
    else setRequestOpen(true);
  }, [isAppAdmin, approved]);
  useEffect(() => { if (focusCreate > 0 && can && !loading) openCreate(); // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [focusCreate]);

  const credentials = async (u: User, password: string, title: string) => {
    const link = waLink(u.phone, `Hello ${u.username}! Your Jewellery360 login:\nUsername: ${u.username}\nPassword: ${password}\n\nPlease sign in and keep your password private.`);
    await successDialog({
      title, message: <><b>{u.username}</b> can sign in with these details. Share the password privately.</>,
      details: [`Username: ${u.username}`, `Password: ${password}`],
      actions: [
        {label: 'Copy login details', tone: 'ghost', run: () => { void copyText(`Username: ${u.username}\nPassword: ${password}`, 'Login details'); }},
        ...(link ? [{label: 'Send on WhatsApp', tone: 'whatsapp' as const, run: () => { window.open(link, '_blank', 'noopener,noreferrer'); }}] : [])
      ]
    });
  };
  const saved = async (u: User, password?: string) => {
    const wasCreate = drawer?.mode === 'create';
    setDrawer(null);
    await load(true);
    if (wasCreate && password) await credentials(u, password, 'User created');
    else pushToast(`${u.username} updated`, 'success');
  };
  const requestSent = async (name: string) => {
    setRequestOpen(false);
    await load(true);
    await successDialog({title: 'Request sent', message: <>The app administrator will review the request for <b>{name}</b>. You will see the decision here - and can create the user as soon as it is approved.</>});
  };
  const cancelReq = async (r: Req) => {
    if (!(await confirmDialog({title: 'Cancel this request?', message: <>The request for <b>{r.requestedName}</b> will be withdrawn.</>, confirmLabel: 'Cancel request', tone: 'danger'}))) return;
    try { await cancelSlotRequest(r.id); await load(true); pushToast('Request cancelled', 'success'); }
    catch (e: any) { pushToast(errText(e, 'Unable to cancel the request.'), 'error'); }
  };
  const setActive = async (u: User, next: boolean) => {
    const ok = await confirmDialog(next
      ? {title: 'Reactivate this user?', message: <><b>{u.username}</b> will be able to sign in again.</>, confirmLabel: 'Reactivate', tone: 'success'}
      : {title: 'Deactivate this user?', message: <><b>{u.username}</b> will no longer be able to sign in. You can reactivate them any time.</>, confirmLabel: 'Deactivate', tone: 'danger'});
    if (!ok) return;
    setBusy(u.id);
    try { await (next ? enableUser(u.id) : disableUser(u.id)); await load(true); pushToast(`${u.username} has been ${next ? 'reactivated' : 'deactivated'}.`, 'success'); }
    catch (e: any) { pushToast(errText(e, 'Unable to update this user.'), 'error'); }
    finally { setBusy(null); }
  };
  const decideReset = async (r: Reset, ok: boolean) => {
    const confirmed = await confirmDialog(ok
      ? {title: 'Approve this password reset?', message: <>The new password <b>{r.username}</b> chose becomes active immediately. Make sure it really was them.</>, confirmLabel: 'Approve reset', tone: 'success'}
      : {title: 'Disapprove this password reset?', message: <>The request from <b>{r.username}</b> will be discarded. Their current password keeps working.</>, confirmLabel: 'Disapprove', tone: 'danger'});
    if (!confirmed) return;
    setBusy(r.id);
    try {
      const res = await (ok ? approve(r.id) : reject(r.id));
      await load(true);
      if (ok) {
        const link = waLink(r.phone, `Hello ${r.username}! Your Jewellery360 password reset has been approved. You can sign in with your new password.`);
        await successDialog({title: 'Password reset approved', message: res.data?.message || `${r.username} can sign in with the new password.`,
          actions: link ? [{label: 'Tell user on WhatsApp', tone: 'whatsapp', run: () => { window.open(link, '_blank', 'noopener,noreferrer'); }}] : []});
      } else pushToast(res.data?.message || 'Reset request disapproved.', 'success');
    } catch (e: any) { pushToast(errText(e, 'Unable to process the request.'), 'error'); }
    finally { setBusy(null); }
  };

  const tone = (u: User) => ({['--h' as string]: hue(u.username || 'x')} as CSSProperties);
  const Actions = ({u}: {u: User}) => {
    if (!can || u.role === 'APP_ADMIN' || u.role === 'COMPANY_ADMIN') return <span className="tmMuted">Administrator</span>;
    const link = waLink(u.phone, `Hello ${u.username}!`);
    return (
      <div className="tmActions" onClick={e => e.stopPropagation()}>
        {u.enabled
          ? <button type="button" className="tmBtn danger" disabled={busy === u.id} onClick={() => void setActive(u, false)}><Ico name="user-x" size={14} /> Deactivate</button>
          : <button type="button" className="tmBtn solid" disabled={busy === u.id} onClick={() => void setActive(u, true)}><Ico name="user-check" size={14} /> Reactivate</button>}
        <button type="button" className="tmBtn ghost icon" aria-label={`Edit ${u.username}`} title="Edit details" onClick={() => setDrawer({mode: 'edit', member: u})}><Ico name="edit" size={14} /></button>
        <button type="button" className="tmBtn ghost icon" aria-label={`Manage access for ${u.username}`} title="Manage access (pages and powers)" onClick={() => setAccessFor(u)}><Ico name="shield" size={14} /></button>
        <button type="button" className="tmBtn ghost icon" aria-label={`Set a new password for ${u.username}`} title="Set a new password" onClick={() => setPwFor(u)}><Ico name="key" size={14} /></button>
        {link ? <a className="tmBtn wa icon" href={link} target="_blank" rel="noreferrer" aria-label={`WhatsApp ${u.username}`} title="Message on WhatsApp"><Ico name="whatsapp" size={15} /></a> : <span className="tmBtn wa icon off" title="No phone number saved"><Ico name="whatsapp" size={15} /></span>}
      </div>
    );
  };
  const AccessChip = ({u}: {u: User}) => {
    if (u.role === 'APP_ADMIN' || u.role === 'COMPANY_ADMIN') return <span className="tmChip full"><Ico name="shield" size={11} /> Full access</span>;
    const a = accessInfo(u);
    return <button type="button" className={`tmChip ${a.id === 'CUSTOM' ? 'custom' : 'role'}`} disabled={!can} onClick={e => { e.stopPropagation(); if (can) setAccessFor(u); }} title="Manage access"><Ico name="shield" size={11} /> {a.label} · {pagesText(a.pages)}</button>;
  };

  return (
    <div className="page-users tmPage">
      <div className="tmHero">
        <span className="tmHeroIcon"><Ico name="users" size={26} /></span>
        <div>
          <span className="eyebrow">TEAM ACCESS</span>
          <h2>Users of {user.companyName || 'your company'}</h2>
          <p>{isCompanyAdmin ? 'Every new user needs approval from the app administrator: send a request, and once it is approved you create the account. You decide which pages and powers each user has.' : 'Create and manage users of the selected company. Search by username, email, phone, role or branch.'}</p>
        </div>
        <div className="tmRing" aria-label={`${counts.ALL} users`}><b>{counts.ALL}</b><small>users</small></div>
        <div className="tmHeroActions">
          <button type="button" className="tmHeroBtn" onClick={() => void load()} disabled={loading}><Ico name="refresh" size={15} className={loading ? 'spin' : ''} /> Refresh</button>
          {can && isCompanyAdmin && approved.length > 0 && <button type="button" className="tmHeroBtn solid" onClick={openCreate}><Ico name="user-plus" size={15} /> Create user ({approved.length} approved)</button>}
          {can && isCompanyAdmin && <button type="button" className={`tmHeroBtn${approved.length ? '' : ' solid'}`} onClick={() => setRequestOpen(true)}><Ico name="plus" size={15} /> Request a new user</button>}
          {can && isAppAdmin && <button type="button" className="tmHeroBtn solid" onClick={openCreate}><Ico name="user-plus" size={15} /> Create user</button>}
        </div>
      </div>

      <div className="tmStats">{stats.map(s => <div key={s.label} className={`tmStat ${s.tone}`}><small>{s.label}</small><b>{s.value}</b><em>{s.hint}</em></div>)}</div>

      {isCompanyAdmin && requests.length > 0 && (
        <section className="tmRequests" aria-label="Requests for new users">
          <div className="tmReqHead"><b>Your requests to the administrator</b><small>{pending.length} waiting · {approved.length} approved</small>
            {requests.length > 4 && <button type="button" onClick={() => setShowAllReq(v => !v)}>{showAllReq ? 'Show fewer' : `Show all ${requests.length}`}</button>}</div>
          <div className="tmReqList">
            {(showAllReq ? requests : requests.slice(0, 4)).map(r => (
              <article className={`tmReq ${r.status.toLowerCase()}`} key={r.id}>
                <span className="tmReqDot" />
                <div className="tmReqCopy"><b>{r.requestedName}</b><small>{r.note || 'No note'} · sent {ago(r.createdAt)}</small>{r.decisionNote && <em>Administrator: {r.decisionNote}</em>}</div>
                <span className={`tmPill ${r.status.toLowerCase()}`}>{({PENDING: 'Waiting for approval', APPROVED: 'Approved', REJECTED: 'Rejected', USED: 'User created', CANCELLED: 'Cancelled'} as Record<string, string>)[r.status] || r.status}</span>
                {r.status === 'APPROVED' && <button type="button" className="tmBtn solid" onClick={() => createFrom(r)}><Ico name="user-plus" size={14} /> Create user</button>}
                {r.status === 'PENDING' && <button type="button" className="tmBtn danger" onClick={() => void cancelReq(r)}><Ico name="x" size={14} /> Cancel</button>}
              </article>
            ))}
          </div>
        </section>
      )}

      {isCompanyAdmin && resets.length > 0 && (
        <section className="tmResets" role="region" aria-label="Password reset requests">
          <div className="tmReqHead"><b>{resets.length} password reset request{resets.length === 1 ? '' : 's'} waiting</b><small>Approve only if you know the request came from your team member.</small></div>
          <div className="tmReqList">
            {resets.map(r => (
              <article className="tmReq pending" key={r.id}>
                <span className="tmAvatar" style={{['--h' as string]: hue(r.username || 'x')} as CSSProperties}>{(r.username || '?')[0].toUpperCase()}</span>
                <div className="tmReqCopy"><b>{r.username}</b><small>{r.phone || r.email} · requested {ago(r.createdAt)}</small></div>
                <button type="button" className="tmBtn solid" disabled={busy === r.id} onClick={() => void decideReset(r, true)}><Ico name="check" size={14} /> Approve</button>
                <button type="button" className="tmBtn danger" disabled={busy === r.id} onClick={() => void decideReset(r, false)}><Ico name="ban" size={14} /> Disapprove</button>
              </article>
            ))}
          </div>
        </section>
      )}

      <div className="tmToolbar">
        <label className="tmSearch"><Ico name="search" size={16} /><input value={search} onChange={e => setSearch(e.target.value)} placeholder="Search username, email, phone, role or branch…" aria-label="Search users" />{search && <button type="button" aria-label="Clear search" onClick={() => setSearch('')}><Ico name="x" size={14} /></button>}</label>
        <div className="tmChips" role="group" aria-label="Status">{(['ALL', 'ACTIVE', 'INACTIVE'] as Filter[]).map(k => <button key={k} type="button" className={filter === k ? 'on' : ''} aria-pressed={filter === k} onClick={() => setFilter(k)}>{k === 'ALL' ? 'All' : k === 'ACTIVE' ? 'Active' : 'Inactive'} <i>{counts[k]}</i></button>)}</div>
        <div className="tmViews" role="group" aria-label="View"><button type="button" className={view === 'table' ? 'on' : ''} aria-pressed={view === 'table'} onClick={() => setView('table')}><Ico name="list" size={15} /> Table</button><button type="button" className={view === 'cards' ? 'on' : ''} aria-pressed={view === 'cards'} onClick={() => setView('cards')}><Ico name="grid" size={15} /> Cards</button></div>
      </div>

      {loading && !list.length && <div className="tmEmpty"><Ico name="loader" size={24} className="spin" /><b>Loading users…</b></div>}
      {!loading && rows.length === 0 && <div className="tmEmpty"><Ico name="users" size={26} /><b>{search || filter !== 'ALL' ? 'No users match your filters' : 'No users yet'}</b>
        {!search && filter === 'ALL' && isCompanyAdmin && <><span>Request a user (cashier or manager) for your company. The app administrator approves it, then you create the account.</span><button type="button" className="tmBtn solid" onClick={openCreate}><Ico name="plus" size={14} /> {approved.length ? 'Create approved user' : 'Request a new user'}</button></>}</div>}

      {view === 'cards' && rows.length > 0 && (
        <div className="tmGrid">
          {rows.map(u => (
            <article key={u.id} className={`tmCard ${u.enabled ? 'on' : 'off'}`} style={tone(u)}>
              <div className="tmCardTop"><span className={`tmStatus ${u.enabled ? 'on' : 'off'}`}><i />{u.enabled ? 'Active' : 'Inactive'}</span><span className="tmRole">{ROLE_LABEL[u.role] || u.role.replaceAll('_', ' ')}</span></div>
              <span className="tmAvatar lg">{(u.username || '?')[0].toUpperCase()}</span>
              <b className="tmName">{u.username}</b>
              <div className="tmContact"><span title={u.email}><Ico name="mail" size={12} /> {u.email}</span><span><Ico name="phone" size={12} /> {u.phone || 'No phone number'}</span><span><Ico name="building" size={12} /> {u.branchName || '-'}{isAppAdmin && u.companyName ? ` · ${u.companyName}` : ''}</span></div>
              <AccessChip u={u} />
              {resetOf(u) && <span className="tmFlag"><Ico name="key" size={11} /> password request</span>}
              <Actions u={u} />
            </article>
          ))}
        </div>
      )}
      {view === 'table' && rows.length > 0 && (
        <div className="tmTableWrap"><table className="tmTable">
          <thead><tr><th>User</th><th>Email</th><th>Phone</th><th>Role</th><th>Branch</th><th>Access</th><th>Status</th><th>Actions</th></tr></thead>
          <tbody>{rows.map(u => (
            <tr key={u.id} style={tone(u)}>
              <td data-label="User"><div className="tmWho"><span className="tmAvatar">{(u.username || '?')[0].toUpperCase()}</span><b>{u.username}</b>{resetOf(u) && <span className="tmFlag"><Ico name="key" size={11} /> request</span>}</div></td>
              <td data-label="Email">{u.email}</td><td data-label="Phone">{u.phone || '—'}</td><td data-label="Role">{ROLE_LABEL[u.role] || u.role.replaceAll('_', ' ')}</td><td data-label="Branch">{u.branchName || '—'}</td>
              <td data-label="Access"><AccessChip u={u} /></td>
              <td data-label="Status"><span className={`tmStatus ${u.enabled ? 'on' : 'off'}`}><i />{u.enabled ? 'Active' : 'Inactive'}</span></td>
              <td data-label="Actions"><Actions u={u} /></td>
            </tr>))}
          </tbody></table></div>
      )}

      {requestOpen && <RequestModal onClose={() => setRequestOpen(false)} onSent={name => void requestSent(name)} />}
      {drawer && <MemberDrawer me={user} state={drawer} branchList={branchList} onClose={() => setDrawer(null)} onSaved={(u, pw) => void saved(u, pw)} />}
      {accessFor && <AccessDrawer member={accessFor} onClose={() => setAccessFor(null)} onSaved={u => { setAccessFor(null); setList(cur => cur.map(x => (x.id === u.id ? {...x, ...u} : x))); pushToast(`Access updated for ${u.username} - it applies right away.`, 'success'); }} />}
      {pwFor && <PasswordModal member={pwFor} onClose={() => setPwFor(null)} onSaved={async pw => { const u = pwFor; setPwFor(null); await credentials(u, pw, 'New password saved'); }} />}
    </div>
  );
}
