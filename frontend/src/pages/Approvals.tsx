import {useEffect, useState} from 'react';
import {
  approvals, approve, reject, getFriendlyApiError
} from '../api';
import {Empty} from '../shared/ui';
import './Approvals.css';

type Approval = {
  id:number;
  requestType:string;
  status:string;
  createdAt?:string;
  processedAt?:string|null;
  message?:string|null;
  targetUserId?:number|null;
  username?:string|null;
  email?:string|null;
  phone?:string|null;
  role?:string|null;
  companyId?:number|null;
  companyName?:string|null;
  branchId?:number|null;
  branchName?:string|null;
};

export default function ApprovalsModule({onNotice}:{onNotice:(x:string)=>void}) {
  const [list,setList] = useState<Approval[]>([]);
  const [loading,setLoading] = useState(false);
  const [processing,setProcessing] = useState<number|null>(null);

  const load = async () => {
    setLoading(true);
    try {
      const r = await approvals();
      setList(Array.isArray(r.data) ? r.data : []);
    } catch (err:any) {
      setList([]);
      onNotice(getFriendlyApiError(err, 'Unable to load pending approval requests.'));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { void load(); }, []);

  const act = async (id:number, ok:boolean) => {
    setProcessing(id);
    try {
      const r = await (ok ? approve(id) : reject(id));
      await load();
      onNotice(r.data?.message || (ok ? 'Request approved.' : 'Request rejected.'));
    } catch (err:any) {
      onNotice(getFriendlyApiError(err, 'Approval action failed.'));
    } finally {
      setProcessing(null);
    }
  };

  return (
    <div className="panel page-approvals">
      <div className="panelHead">
        <div>
          <span className="eyebrow">GOVERNANCE</span>
          <h2>Pending approvals</h2>
          <p className="approvalIntro">
            APP_ADMIN can review company registrations and new company-user requests.
            Approval activates the requested account according to its request type.
          </p>
        </div>
        <button className="ghost" onClick={load} disabled={loading}>
          {loading ? 'Refreshing…' : 'Refresh'}
        </button>
      </div>

      <div className="approvalList">
        {list.map(r => (
          <article className="approvalCard" key={r.id}>
            <div className="approvalMain">
              <div className="approvalType">
                <span className="pill">{r.requestType}</span>
                <span className="approvalNumber">Request #{r.id}</span>
              </div>

              <h3>
                {r.requestType === 'NEW_ACCOUNT'
                  ? 'New company registration'
                  : r.requestType === 'NEW_USER'
                    ? 'New company user'
                    : r.requestType === 'PASSWORD_RESET'
                      ? 'Password reset request'
                      : r.requestType}
              </h3>

              <div className="approvalGrid">
                <div><small>User</small><strong>{r.username || '—'}</strong></div>
                <div><small>Email</small><strong>{r.email || '—'}</strong></div>
                <div><small>Phone</small><strong>{r.phone || '—'}</strong></div>
                <div><small>Role</small><strong>{r.role || '—'}</strong></div>
                <div><small>Company</small><strong>{r.companyName || '—'}</strong></div>
                <div><small>Branch</small><strong>{r.branchName || '—'}</strong></div>
                <div><small>Submitted</small><strong>{r.createdAt ? new Date(r.createdAt).toLocaleString() : '—'}</strong></div>
              </div>

              {r.message && <p className="approvalMessage">{r.message}</p>}
            </div>

            <div className="approvalActions">
              <button
                className="primary"
                disabled={processing===r.id}
                onClick={() => void act(r.id,true)}
              >
                {processing===r.id ? 'Processing…' : 'Approve'}
              </button>
              <button
                className="ghost danger"
                disabled={processing===r.id}
                onClick={() => void act(r.id,false)}
              >
                Reject
              </button>
            </div>
          </article>
        ))}
      </div>

      {!loading && !list.length && <Empty text="No pending approval requests." />}
    </div>
  );
}
