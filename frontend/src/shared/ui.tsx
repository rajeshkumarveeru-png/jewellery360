import {useEffect, useRef, useState} from 'react';
import type {FocusEvent, ReactNode} from 'react';
import {brandInitials, getBrandName} from './brand';
type FieldProps = {
  placeholder: string;
  value?: string;
  type?: string;
  onChange: (v:string)=>void;
  onBlur?: (e:FocusEvent<HTMLInputElement>)=>void;
  error?: string;
  autoComplete?: string;
  inputMode?: 'none'|'text'|'tel'|'url'|'email'|'numeric'|'decimal'|'search';
  name?: string;
};

export function Field({placeholder,value,type='text',onChange,onBlur,error,autoComplete,inputMode,name}:FieldProps){
  const fieldId = name || placeholder.toLowerCase().replace(/[^a-z0-9]+/g,'-');
  return <div className={`fieldWrap ${error ? 'hasError' : ''}`}>
    <input
      id={fieldId}
      name={name}
      required
      placeholder={placeholder}
      aria-label={placeholder}
      {...(value!==undefined?{value}:{})}
      type={type}
      autoComplete={autoComplete}
      inputMode={inputMode}
      aria-invalid={Boolean(error)}
      aria-describedby={error ? `${fieldId}-error` : undefined}
      onBlur={onBlur}
      onChange={e=>onChange(e.target.value)}
    />
    {error && <span id={`${fieldId}-error`} className="fieldError" role="alert">{error}</span>}
  </div>;
}
export function Loading(){const brand=getBrandName();return <main className="loading"><div className="loadingCard"><div className="logo">{brandInitials(brand)}</div><h2>Loading {brand}</h2><p>Preparing your workspace…</p></div></main>;}
/* ------------------------------------------------------------------------------------------------
   Table with a built-in toolbar (every list page gets it): live row filter, table / card view (remembered per page),
   comfortable / compact density and CSV export of the rows currently shown.
   The toolbar only reads and hides rows, it never reorders React-managed nodes.
   ------------------------------------------------------------------------------------------------ */
const moduleKey = () => (typeof document !== 'undefined' ? (document.querySelector('.workspace')?.className.match(/module-[a-z0-9-]+/)?.[0] || 'module-x') : 'module-x');

export function Table({children, cards = false, bare = false}: {children: ReactNode; cards?: boolean; bare?: boolean}) {
  const ref = useRef<HTMLDivElement>(null);
  const [key, setKey] = useState('module-x');
  const [q, setQ] = useState('');
  const [view, setView] = useState<'table' | 'cards'>(cards ? 'cards' : 'table');
  // the page class (module-reports ...) is only on the DOM after the first commit, so the per-page key is read in an effect
  useEffect(() => {
    const k = moduleKey();
    setKey(k);
    try { const v = localStorage.getItem('j360_view_' + k); if (v === 'table' || v === 'cards') setView(v); } catch { /* storage unavailable */ }
  }, []);
  const [dense, setDense] = useState(() => { try { return localStorage.getItem('j360_dense') === '1'; } catch { return false; } });
  const [stats, setStats] = useState({total: 0, shown: 0});

  useEffect(() => {
    const root: HTMLDivElement | null = ref.current;
    if (!root) return;
    const heads = Array.from(root.querySelectorAll<HTMLElement>('thead th')).map((th: HTMLElement) => (th.textContent || '').trim());
    const rows = Array.from(root.querySelectorAll<HTMLElement>('tbody tr'));
    const needle = q.trim().toLowerCase();
    let visible = 0;
    rows.forEach(tr => {
      const hit = !needle || (tr.textContent || '').toLowerCase().includes(needle);
      tr.style.display = hit ? '' : 'none';
      if (hit) visible++;
      Array.from(tr.children).forEach((td: Element, i: number) => td.setAttribute('data-label', heads[i] || ''));
      const first = (tr.children[0]?.textContent || '').trim();
      tr.setAttribute('data-initial', (first.match(/[A-Za-z0-9]/) || ['•'])[0].toUpperCase());
    });
    setStats(s => (s.total === rows.length && s.shown === visible ? s : {total: rows.length, shown: visible}));
  });

  const pick = (v: 'table' | 'cards') => { setView(v); try { localStorage.setItem('j360_view_' + key, v); } catch { /* ignore */ } };
  const toggleDense = () => setDense(d => { const n = !d; try { localStorage.setItem('j360_dense', n ? '1' : '0'); } catch { /* ignore */ } return n; });
  const exportCsv = () => {
    const root: HTMLDivElement | null = ref.current;
    if (!root) return;
    const esc = (v: string) => `"${v.replace(/"/g, '""').replace(/\s+/g, ' ').trim()}"`;
    const head = Array.from(root.querySelectorAll<HTMLElement>('thead th')).map((th: HTMLElement) => esc(th.textContent || ''));
    const body = (Array.from(root.querySelectorAll<HTMLElement>('tbody tr'))).filter(tr => tr.style.display !== 'none')
      .map(tr => Array.from(tr.children).map((td: Element) => esc(td.textContent || '')).join(','));
    const blob = new Blob(['\ufeff' + [head.join(','), ...body].join('\n')], {type: 'text/csv;charset=utf-8'});
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url; a.download = `${key.replace('module-', '')}-${new Date().toISOString().slice(0, 10)}.csv`; a.click();
    window.setTimeout(() => URL.revokeObjectURL(url), 2000);
  };

  return (
    <div className={`tableBox view-${view}${dense ? ' dense' : ''}`}>
      {!bare && stats.total > 0 && (
        <div className="tableTools" role="toolbar" aria-label="Table tools">
          <label className="tableSearch">
            <svg viewBox="0 0 24 24" width="15" height="15" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" aria-hidden="true"><circle cx="11" cy="11" r="7" /><path d="M20 20l-3.5-3.5" /></svg>
            <input type="search" value={q} onChange={e => setQ(e.target.value)} placeholder="Filter rows…" aria-label="Filter rows" />
          </label>
          <span className="tableCount" aria-live="polite">{stats.shown === stats.total ? `${stats.total} row${stats.total === 1 ? '' : 's'}` : `${stats.shown} of ${stats.total}`}</span>
          <div className="tableBtns">
            <button type="button" className={view === 'table' ? 'on' : ''} onClick={() => pick('table')} aria-pressed={view === 'table'} title="Table view">☰ Table</button>
            <button type="button" className={view === 'cards' ? 'on' : ''} onClick={() => pick('cards')} aria-pressed={view === 'cards'} title="Card view">▦ Cards</button>
            <button type="button" className={dense ? 'on' : ''} onClick={toggleDense} aria-pressed={dense} title="Compact rows">≡ Compact</button>
            <button type="button" onClick={exportCsv} title="Download the rows shown as CSV">⇩ CSV</button>
          </div>
        </div>
      )}
      <div className="tableWrap" ref={ref}><table>{children}</table></div>
      {q && stats.total > 0 && stats.shown === 0 && <div className="tableNoMatch">No rows match “{q}”.</div>}
    </div>
  );
}
export function Empty({text}:{text:string}){return <div className="empty"><div>◇</div><b>{text}</b><small>Use the create form to add a real PostgreSQL record.</small></div>}
/* Jewellery-style line icons for the sidebar (24x24, 1.7 stroke). Unknown names fall back to a diamond. */
export const NAV_PATHS: Record<string, string> = {
  Overview: 'M4 4h7v7H4z M13 4h7v4h-7z M13 10h7v10h-7z M4 13h7v7H4z',
  Companies: 'M4 20V6l8-3 8 3v14 M9 20v-5h6v5 M8 9h2 M14 9h2 M8 12h2 M14 12h2',
  Branches: 'M3 9l2-5h14l2 5 M4 9v11h16V9 M9 20v-6h6v6 M3 9a3 3 0 006 0 3 3 0 006 0 3 3 0 006 0',
  Users: 'M9 11a3.5 3.5 0 100-7 3.5 3.5 0 000 7z M2.5 20a6.5 6.5 0 0113 0 M16 4.5a3.3 3.3 0 010 6.3 M18 14.5a6 6 0 013.5 5.5',
  Approvals: 'M12 3l2.4 1.6 2.9-.2 1 2.7 2.4 1.6-.9 2.8.9 2.8-2.4 1.6-1 2.7-2.9-.2L12 21l-2.4-1.6-2.9.2-1-2.7L3.3 15.3l.9-2.8-.9-2.8 2.4-1.6 1-2.7 2.9.2z M8.5 12.2l2.3 2.3 4.7-4.9',
  Billing: 'M6 3h12v18l-3-2-3 2-3-2-3 2z M9 8h6 M9 12h6 M9 16h3',
  Jewellery: 'M7 3h10l4 6-9 12L3 9z M3 9h18 M9 3l3 6 3-6 M12 9v12',
  'Gold & Rates': 'M5 8c0-1.7 3.1-3 7-3s7 1.3 7 3-3.1 3-7 3-7-1.3-7-3z M5 8v4c0 1.7 3.1 3 7 3s7-1.3 7-3V8 M5 12v4c0 1.7 3.1 3 7 3s7-1.3 7-3v-4',
  Inventory: 'M3 7l9-4 9 4-9 4z M3 7v10l9 4 9-4V7 M12 11v10',
  Purchases: 'M3 4h2.5l2.2 11h10.6l2-8H7 M10 20a1 1 0 100-.01 M17 20a1 1 0 100-.01',
  'Old Gold': 'M20 12a8 8 0 01-14 5.3 M4 12a8 8 0 0114-5.3 M18 3v4h-4 M6 21v-4h4',
  Customers: 'M12 12a4 4 0 100-8 4 4 0 000 8z M4.5 21a7.5 7.5 0 0115 0',
  Services: 'M14.5 6.5a4 4 0 00-5 5L3.5 17.5l3 3 6-6a4 4 0 005-5l-2.5 2.5-2.5-.5-.5-2.5z',
  Payments: 'M3 7h16a2 2 0 012 2v9a2 2 0 01-2 2H5a2 2 0 01-2-2z M3 7l2-3h12l1 3 M16 14h3',
  Reports: 'M4 20V10 M10 20V4 M16 20v-7 M22 20H2',
  WhatsApp: 'M21 12a9 9 0 01-13.3 7.9L3 21l1.2-4.5A9 9 0 1121 12z M9 9.5c.3 2.6 2.4 4.7 5 5l1.2-1.4-1.9-1-.9.6a3 3 0 01-1.6-1.6l.6-.9-1-1.9z',
  Settings: 'M4 7h9 M17 7h3 M4 17h3 M11 17h9 M15 7a2 2 0 100-.01 M9 17a2 2 0 100-.01',
  'Audit Logs': 'M12 3l8 3v6c0 4.5-3.2 7.9-8 9-4.8-1.1-8-4.5-8-9V6z M9 12l2 2 4-4'
};

export function NavIcon({name}:{name:string}){
  const d = NAV_PATHS[name] || 'M12 3l7 9-7 9-7-9z';
  return <span className="navIcon" aria-hidden="true"><svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round"><path d={d}/></svg></span>;
}
export function toNum(f:any){return Object.fromEntries(Object.entries(f).map(([k,v])=>['grossWeight','stoneWeight','netWeight','goldRate','wastagePercent','makingCharge','stoneCharge','otherCharges','gstPercent'].includes(k)?[k,Number(v||0)]:[k,v]));}
