import {FormEvent, useState} from 'react';
import {saveMaster} from '../api';
import type {MasterKind} from '../api';
import {usePermissions} from './permissions';
import {Table} from './ui';
import {F, Pill, Sheet, StateBox, errText} from './kit';

/* One editor for the small reference lists (categories, designs, products, purities, suppliers, karigars).
   Each page shows the lists it owns; nothing is ever deleted - an entry is switched off instead so old bills keep it. */

export type MasterField = {key: string; label: string; type?: 'text' | 'number' | 'select' | 'textarea'; options?: {value: string; label: string}[]; required?: boolean; hint?: string};

export default function MasterEditor({kind, title, rows, fields, columns, onSaved, onNotice, addLabel}: {
  kind: MasterKind; title: string; rows: any[]; fields: MasterField[]; columns: {key: string; label: string; render?: (r: any) => any}[];
  onSaved: () => void; onNotice: (s: string) => void; addLabel: string;
}) {
  const perms = usePermissions();
  const [edit, setEdit] = useState<any>(null);
  const [busy, setBusy] = useState(false);
  const save = async (e: FormEvent) => {
    e.preventDefault();
    setBusy(true);
    try {
      const body: any = {};
      for (const f of fields) body[f.key] = edit[f.key] === '' ? null : edit[f.key];
      if (edit.id) body.active = edit.active !== false;
      await saveMaster(kind, body, edit.id);
      onNotice(`${title.replace(/s$/, '')} saved.`);
      setEdit(null);
      onSaved();
    } catch (err) { onNotice(errText(err)); }
    finally { setBusy(false); }
  };
  const toggle = async (r: any) => {
    try {
      const body: any = {};
      for (const f of fields) body[f.key] = r[f.key] ?? null;
      body.active = !r.active;
      await saveMaster(kind, body, r.id);
      onNotice(`${r.name} ${r.active ? 'switched off' : 'switched on'}.`);
      onSaved();
    } catch (err) { onNotice(errText(err)); }
  };
  return <div>
    <div className="panelHead"><div><span className="eyebrow">{title.toUpperCase()}</span><h2>{rows.length} {title.toLowerCase()}</h2></div>
      {perms.canManage && <button type="button" className="primary" onClick={() => setEdit({active: true})}>＋ {addLabel}</button>}</div>
    <StateBox empty={!rows.length} emptyText={`No ${title.toLowerCase()} yet`} emptyHint={perms.canManage ? `Use “${addLabel}” to create the first one.` : undefined}>
      <Table><thead><tr>{columns.map(c => <th key={c.key}>{c.label}</th>)}<th>Status</th>{perms.canManage && <th />}</tr></thead>
        <tbody>{rows.map(r => <tr key={r.id}>{columns.map(c => <td key={c.key}>{c.render ? c.render(r) : r[c.key] ?? '—'}</td>)}
          <td><Pill value={r.active === false ? 'Off' : 'Active'} tone={r.active === false ? 'bad' : 'success'} /></td>
          {perms.canManage && <td><button type="button" className="ghost" onClick={() => setEdit({...r})}>Edit</button><button type="button" className="ghost" onClick={() => toggle(r)}>{r.active === false ? 'Switch on' : 'Switch off'}</button></td>}
        </tr>)}</tbody></Table>
    </StateBox>
    <Sheet open={!!edit} onClose={() => setEdit(null)} kicker={edit?.id ? 'EDIT' : 'NEW'} title={edit?.id ? edit.name : addLabel}>
      {edit && <form className="kGrid" onSubmit={save}>
        {fields.map(f => <F key={f.key} label={f.label + (f.required ? ' *' : '')} hint={f.hint} wide={f.type === 'textarea'}>
          {f.type === 'select' ? <select required={f.required} value={edit[f.key] ?? ''} onChange={e => setEdit({...edit, [f.key]: e.target.value})}><option value="">—</option>{f.options?.map(o => <option key={o.value} value={o.value}>{o.label}</option>)}</select>
            : f.type === 'textarea' ? <textarea value={edit[f.key] ?? ''} onChange={e => setEdit({...edit, [f.key]: e.target.value})} />
            : <input required={f.required} type={f.type === 'number' ? 'number' : 'text'} step="any" value={edit[f.key] ?? ''} onChange={e => setEdit({...edit, [f.key]: e.target.value})} />}
        </F>)}
        <div className="kActions kField wide"><button className="primary" disabled={busy}>{busy ? 'Saving…' : 'Save'}</button><button type="button" className="ghost" onClick={() => setEdit(null)}>Cancel</button></div>
      </form>}
    </Sheet>
  </div>;
}
