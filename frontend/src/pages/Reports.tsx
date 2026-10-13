import {useEffect, useState} from 'react';
import {reportCatalogue, runReport} from '../api';
import {Table} from '../shared/ui';
import {StateBox, grams, money, useData} from '../shared/kit';
import type {PageProps} from './Dashboard';

/* ============================================================================================
   Reports: every report is a plain table from the server ({columns, rows, totals}) so all of them render, filter and
   export to CSV the same way. Figures come straight from bills, payments and stock - nothing is estimated.
   ============================================================================================ */

const firstOfMonth = () => { const d = new Date(); return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-01`; };
const todayIso = () => { const d = new Date(); return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`; };

const cell = (v: any, type: string) => {
  if (v === null || v === undefined || v === '') return '—';
  switch (type) {
    case 'money': return money(v);
    case 'weight': return grams(v);
    case 'int': return Number(v).toLocaleString('en-IN');
    case 'pct':
    case 'percent': return `${Number(v).toLocaleString('en-IN', {maximumFractionDigits: 2})} %`;
    default: return String(v);
  }
};

export default function ReportsModule({ready}: PageProps) {
  const cat = useData(() => reportCatalogue(), [], ready);
  const list: any[] = (cat.data as any) || [];
  const [key, setKey] = useState('');
  const [from, setFrom] = useState(firstOfMonth());
  const [to, setTo] = useState(todayIso());
  useEffect(() => { if (!key && list.length) setKey(list[0].key); }, [list.length]);
  const rep = useData(() => runReport(key, from, to), [key, from, to], ready && !!key);
  if (!ready) return <div className="panel"><StateBox empty emptyText="Choose a company and branch to run reports." /></div>;
  const r: any = rep.data;
  const groups = list.reduce<Record<string, any[]>>((g, x) => { (g[x.group] ||= []).push(x); return g; }, {});
  const cols: any[] = r?.columns || [];
  return <div className="kSplit kSplitNav">
    <nav className="panel" aria-label="Reports">
      <StateBox loading={cat.loading} error={cat.error} onRetry={cat.reload} empty={!list.length} emptyText="No reports available">
        {Object.entries(groups).map(([g, items]) => <div key={g}>
          <h3 className="kSection">{g}</h3>
          {items.map(x => <button key={x.key} type="button" className={`ghost${key === x.key ? ' on' : ''}`} style={{display: 'block', width: '100%', textAlign: 'left', fontWeight: key === x.key ? 800 : 500}} aria-current={key === x.key} onClick={() => setKey(x.key)}>{x.title}</button>)}
        </div>)}
      </StateBox>
    </nav>
    <section className="panel">
      <div className="panelHead"><div><span className="eyebrow">REPORT</span><h2>{r?.title || list.find(x => x.key === key)?.title || 'Report'}</h2></div>
        <div className="kRow">
          <input type="date" value={from} max={to} onChange={e => setFrom(e.target.value)} aria-label="From" />
          <input type="date" value={to} min={from} max={todayIso()} onChange={e => setTo(e.target.value)} aria-label="To" />
        </div></div>
      <StateBox loading={rep.loading} error={rep.error} onRetry={rep.reload} empty={!r || !(r.rows || []).length} emptyText="Nothing to show for this period" emptyHint="Pick another report or widen the dates.">
        {r && <Table>
          <thead><tr>{cols.map(c => <th key={c.key}>{c.label}</th>)}</tr></thead>
          <tbody>{r.rows.map((row: any, i: number) => <tr key={i}>{cols.map((c, j) => <td key={c.key}>{j === 0 ? <b>{cell(row[c.key], c.type)}</b> : cell(row[c.key], c.type)}</td>)}</tr>)}</tbody>
          {r.totals && Object.keys(r.totals).length > 0 && <tfoot><tr>{cols.map((c, j) => <td key={c.key}><b>{j === 0 ? 'Total' : r.totals[c.key] !== undefined ? cell(r.totals[c.key], c.type) : ''}</b></td>)}</tr></tfoot>}
        </Table>}
        {(r?.notes || []).map((n: string) => <p className="kMuted" key={n}>{n}</p>)}
      </StateBox>
    </section>
  </div>;
}
