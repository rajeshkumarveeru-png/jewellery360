import {useState} from 'react';
import {audit} from '../api';
import {Table} from '../shared/ui';
import {Sheet, StateBox, label, useData} from '../shared/kit';
import './AuditLogs.css';

/* Who changed what, and when (latest 300 events of the company). Click a row to see the values before and after. */

const pretty = (v: any) => {
  if (v === null || v === undefined || v === '') return '—';
  try { return JSON.stringify(typeof v === 'string' ? JSON.parse(v) : v, null, 2); } catch { return String(v); }
};

export default function AuditModule() {
  const list = useData(() => audit(), []);
  const rows: any[] = (list.data as any) || [];
  const [open, setOpen] = useState<any>(null);
  return <div className="panel">
    <div className="panelHead"><div><span className="eyebrow">AUDIT LOG</span><h2>Latest {rows.length} changes</h2></div><button type="button" className="ghost" onClick={list.reload}>Refresh</button></div>
    <StateBox loading={list.loading} error={list.error} onRetry={list.reload} empty={!rows.length} emptyText="No changes recorded yet">
      <Table><thead><tr><th>Time</th><th>User</th><th>Action</th><th>Record</th><th>Company</th><th>Branch</th></tr></thead>
        <tbody>{rows.map(x => <tr key={x.id} className="kClick" tabIndex={0} onClick={() => setOpen(x)} onKeyDown={e => { if (e.key === 'Enter') setOpen(x); }}>
          <td>{new Date(x.createdAt).toLocaleString('en-IN')}</td><td>{x.user || '—'}</td><td><b>{label(x.action)}</b></td><td>{x.entityType} {x.entityId ? `#${x.entityId}` : ''}</td><td>{x.company || 'Platform'}</td><td>{x.branch || '—'}</td>
        </tr>)}</tbody></Table>
    </StateBox>
    <Sheet open={!!open} onClose={() => setOpen(null)} kicker="AUDIT EVENT" title={open ? label(open.action) : ''}>
      {open && <>
        <p className="kMuted">{new Date(open.createdAt).toLocaleString('en-IN')} · {open.user || 'system'} · {open.entityType} {open.entityId ? `#${open.entityId}` : ''}</p>
        <h3 className="kSection">Before</h3><pre className="kNote" style={{whiteSpace: 'pre-wrap', overflowWrap: 'anywhere'}}>{pretty(open.before)}</pre>
        <h3 className="kSection">After</h3><pre className="kNote" style={{whiteSpace: 'pre-wrap', overflowWrap: 'anywhere'}}>{pretty(open.after)}</pre>
      </>}
    </Sheet>
  </div>;
}
