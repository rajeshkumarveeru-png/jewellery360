import {FormEvent, Suspense, lazy, useEffect, useMemo, useState} from 'react';
import {masters, removeStockItem, restoreStockItem, saveStockItem, scanStock, stockItems, stockTimeline, stockTransfers, transferStock} from '../api';
import {usePermissions} from '../shared/permissions';
import {confirmDialog, promptDialog} from '../shared/Notify';
import {Table} from '../shared/ui';
import {F, KV, Pill, Sheet, StateBox, Tabs, errText, grams, label, money, num, useData} from '../shared/kit';
import MasterEditor from '../shared/MasterEditor';
// the spreadsheet reader is large: load it only when the Import tab is opened
const BulkProductImport = lazy(() => import('./BulkProductImport'));
import type {Branch} from '../shared/types';
import type {PageProps} from './Dashboard';

/* ============================================================================================
   Stock: every tagged piece (weight, purity, HUID, where it is, its whole history), branch transfers, the catalogue
   (categories, designs, products) and spreadsheet import. One "Add piece" form creates products on the fly, so a
   new item never needs a trip to a master screen first.
   ============================================================================================ */

type TabKey = 'pieces' | 'transfers' | 'catalogue' | 'import';

export default function StockModule({ready, onNotice, focus, branches}: PageProps & {branches: Branch[]}) {
  const perms = usePermissions();
  const [tab, setTab] = useState<TabKey>('pieces');
  const [editing, setEditing] = useState<any>(null);
  const [openId, setOpenId] = useState<number | null>(null);
  const [rev, setRev] = useState(0);
  const m = useData(() => masters(), [], ready);
  useEffect(() => {
    if (focus?.type === 'NEW_ITEM') { setTab('pieces'); setEditing({}); }
    if (focus?.type === 'ITEM') { setTab('pieces'); setOpenId(focus.id); }
  }, [focus?.n]);
  if (!ready) return <div className="panel"><StateBox empty emptyText="Choose a company and branch to see stock." /></div>;
  const md: any = m.data || {};
  const tabs: {key: TabKey; label: string}[] = [{key: 'pieces', label: 'Pieces'}, {key: 'transfers', label: 'Transfers'}, {key: 'catalogue', label: 'Catalogue'}, ...(perms.canManage ? [{key: 'import' as TabKey, label: 'Import'}] : [])];
  return <div>
    <Tabs value={tab} onChange={setTab} tabs={tabs} />
    {tab === 'pieces' && <Pieces onNotice={onNotice} onAdd={() => setEditing({})} onOpen={setOpenId} key={rev} />}
    {tab === 'transfers' && <Transfers onNotice={onNotice} branches={branches} />}
    {tab === 'catalogue' && <div className="panel"><Catalogue md={md} reload={m.reload} onNotice={onNotice} /></div>}
    {tab === 'import' && <div className="panel"><Suspense fallback={<StateBox loading empty />}><BulkProductImport onClose={() => setTab('pieces')} onNotice={onNotice} /></Suspense></div>}
    <PieceSheet id={openId} onClose={() => setOpenId(null)} onNotice={onNotice} onEdit={p => { setOpenId(null); setEditing(p); }} />
    <Sheet open={editing !== null} wide onClose={() => setEditing(null)} kicker={editing?.id ? 'EDIT PIECE' : 'NEW PIECE'} title={editing?.id ? `${editing.tagNo} · ${editing.productName}` : 'Add a piece to stock'}>
      {editing && <PieceForm initial={editing} md={md} onNotice={onNotice} onSaved={() => { setEditing(null); setRev(r => r + 1); void m.reload(); }} onCancel={() => setEditing(null)} />}
    </Sheet>
  </div>;
}

/* ------------------------------------------------------------------------------------------------ pieces */

function Pieces({onNotice, onAdd, onOpen}: {onNotice: (s: string) => void; onAdd: () => void; onOpen: (id: number) => void}) {
  const perms = usePermissions();
  const [status, setStatus] = useState('IN_STOCK');
  const [q, setQ] = useState('');
  const [query, setQuery] = useState('');
  const [allBranches, setAllBranches] = useState(false);
  const list = useData(() => stockItems({status, q: query, allBranches, limit: 1000}), [status, query, allBranches]);
  const rows: any[] = (list.data as any) || [];
  const sum = useMemo(() => rows.reduce((t, r) => ({gross: t.gross + num(r.grossWeight), net: t.net + num(r.netWeight)}), {gross: 0, net: 0}), [rows]);
  const scan = async (e: FormEvent) => {
    e.preventDefault();
    const term = q.trim();
    setQuery(term);
    if (!term) return;
    // an exact tag / barcode / HUID opens the piece straight away
    try { const r = await scanStock(term); if (r.data?.id) onOpen(r.data.id); } catch { /* not an exact code: the list shows matches */ }
  };
  return <div className="panel">
    <form className="kRow" onSubmit={scan} role="search">
      <input type="search" placeholder="Scan or type tag, barcode, HUID or name" value={q} onChange={e => setQ(e.target.value)} aria-label="Search pieces" />
      <select value={status} onChange={e => setStatus(e.target.value)} aria-label="Status">
        <option value="IN_STOCK">In stock</option><option value="SOLD">Sold</option><option value="REMOVED">Removed</option><option value="RETURNED_TO_SUPPLIER">Returned to supplier</option><option value="ALL">All</option>
      </select>
      {perms.isAdmin && <label className="kMuted"><input type="checkbox" checked={allBranches} onChange={e => setAllBranches(e.target.checked)} /> All branches</label>}
      <button type="submit" className="ghost">Search</button>
      {perms.canManage && <button type="button" className="primary" onClick={onAdd}>＋ Add piece</button>}
    </form>
    <div className="kStats" style={{marginTop: 12}}>
      <div className="kStat"><span>Pieces</span><strong>{rows.length}</strong></div>
      <div className="kStat"><span>Gross weight</span><strong>{grams(sum.gross)}</strong></div>
      <div className="kStat"><span>Net weight</span><strong>{grams(sum.net)}</strong></div>
    </div>
    <StateBox loading={list.loading} error={list.error} onRetry={list.reload} empty={!rows.length}
              emptyText={query ? `Nothing matches “${query}”` : 'No pieces here yet'} emptyHint={perms.canManage ? 'Add a piece, record a purchase, or import your opening stock from a spreadsheet.' : undefined}>
      <Table><thead><tr><th>Tag</th><th>Item</th><th>Purity</th><th>Gross</th><th>Net</th><th>HUID</th><th>Where</th><th>Age</th><th>Status</th></tr></thead>
        <tbody>{rows.map(r => <tr key={r.id} className="kClick" tabIndex={0} onClick={() => onOpen(r.id)} onKeyDown={e => { if (e.key === 'Enter') onOpen(r.id); }}>
          <td><b>{r.tagNo}</b><small>{r.barcode}</small></td><td>{r.productName}<small>{r.category}{r.design ? ` · ${r.design}` : ''}</small></td><td>{r.purity?.name}</td>
          <td>{grams(r.grossWeight)}</td><td>{grams(r.netWeight)}</td><td>{r.huid || '—'}</td><td>{r.branchName}<small>{r.location}</small></td>
          <td>{r.ageDays} d</td><td><Pill value={r.status} /></td>
        </tr>)}</tbody></Table>
    </StateBox>
  </div>;
}

function PieceSheet({id, onClose, onNotice, onEdit}: {id: number | null; onClose: () => void; onNotice: (s: string) => void; onEdit: (p: any) => void}) {
  const perms = usePermissions();
  const t = useData(() => stockTimeline(id!), [id], !!id);
  const d: any = t.data;
  const p = d?.item;
  const remove = async () => {
    const reason = await promptDialog({title: `Take ${p.tagNo} out of stock?`, message: 'For damaged, lost or melted pieces, or pieces given for remaking. It can be put back later.', label: 'Reason', confirmLabel: 'Remove from stock'});
    if (!reason) return;
    try { await removeStockItem(p.id, reason); onNotice(`${p.tagNo} removed from stock.`); void t.reload(); } catch (e) { onNotice(errText(e)); }
  };
  const restore = async () => {
    if (!(await confirmDialog({title: `Put ${p.tagNo} back in stock?`, confirmLabel: 'Put back'}))) return;
    try { await restoreStockItem(p.id); onNotice(`${p.tagNo} is back in stock.`); void t.reload(); } catch (e) { onNotice(errText(e)); }
  };
  return <Sheet open={!!id} wide onClose={onClose} kicker="PIECE" title={p ? `${p.tagNo} · ${p.productName}` : 'Piece'}>
    <StateBox loading={t.loading} error={t.error} onRetry={t.reload} empty={!p}>
      {p && <>
        <KV rows={[['Status', <Pill value={p.status} />], ['Purity', p.purity?.name], ['Gross', grams(p.grossWeight)], ['Stone', grams(p.stoneWeight)], ['Net', grams(p.netWeight)],
          ['HUID', p.huid], ['Barcode', p.barcode], ['Certificate', p.certificateNo], ['Category', p.category], ['Design', p.design], ['HSN', p.hsnCode],
          ['Wastage', `${num(p.wastagePercent)} %`], ['Making', `${label(p.makingType)} ${num(p.makingValue)}`], ['Stones', num(p.stoneValue) ? `${money(p.stoneValue)}${p.stoneDetails ? ` · ${p.stoneDetails}` : ''}` : null],
          ['Fixed price (MRP)', num(p.mrp) ? money(p.mrp) : null], ['Cost price', perms.canSeeProfit && p.costPrice != null ? money(p.costPrice) : null],
          ['Branch', p.branchName], ['Location', p.location], ['In stock for', `${p.ageDays} days`], ['Source', label(p.source)], ['Notes', p.notes]]} />
        <div className="kActions">
          {perms.canManage && p.status === 'IN_STOCK' && <button type="button" className="primary" onClick={() => onEdit(p)}>Edit</button>}
          {perms.canManage && p.status === 'IN_STOCK' && <button type="button" className="ghost danger" onClick={remove}>Remove from stock</button>}
          {perms.canManage && p.status === 'REMOVED' && <button type="button" className="secondary" onClick={restore}>Put back in stock</button>}
        </div>
        <h3 className="kSection">History</h3>
        <StateBox empty={!(d.events || []).length} emptyText="No movements recorded">
          <div className="tableWrap"><table><thead><tr><th>Date</th><th>What</th><th>Branch</th><th>By</th></tr></thead>
            <tbody>{d.events.map((e: any, i: number) => <tr key={i}><td>{e.date}</td><td><b>{label(e.type)}</b><small>{e.notes}</small></td><td>{e.branch}</td><td>{e.by || '—'}</td></tr>)}</tbody></table></div>
        </StateBox>
      </>}
    </StateBox>
  </Sheet>;
}

const blank = {productId: '', productName: '', categoryId: '', categoryName: '', purityId: '', grossWeight: '', stoneWeight: '', netWeight: '', huid: '', tagNo: '', barcode: '',
  wastagePercent: '', makingType: 'PER_GRAM', makingValue: '', stoneValue: '', stoneCount: '', stoneDetails: '', otherCharge: '', costPrice: '', mrp: '', location: '', certificateNo: '', notes: ''};

function PieceForm({initial, md, onNotice, onSaved, onCancel}: {initial: any; md: any; onNotice: (s: string) => void; onSaved: () => void; onCancel: () => void}) {
  const perms = usePermissions();
  const editing = !!initial.id;
  const [f, setF] = useState<any>(() => editing ? Object.fromEntries(Object.keys(blank).map(k => [k, initial[k] ?? (k === 'purityId' ? initial.purity?.id ?? '' : '')])) : {...blank});
  const [newProduct, setNewProduct] = useState(false);
  const [more, setMore] = useState(false);
  const [busy, setBusy] = useState(false);
  const [again, setAgain] = useState(false);
  const set = (k: string, v: any) => setF((x: any) => ({...x, [k]: v}));
  const products = (md.products || []).filter((p: any) => p.active !== false);
  const purities = (md.purities || []).filter((p: any) => p.active !== false);
  const net = Math.max(0, num(f.grossWeight) - num(f.stoneWeight));
  const save = async (e: FormEvent) => {
    e.preventDefault();
    const body: any = {};
    for (const [k, v] of Object.entries(f)) if (v !== '' && v !== null) body[k] = v;
    if (newProduct || !body.productId) { delete body.productId; if (!body.productName) { onNotice('Pick a product or type a new product name.'); return; } }
    else { delete body.productName; delete body.categoryName; delete body.categoryId; }
    if (editing) { delete body.productName; delete body.categoryName; delete body.categoryId; }
    if (body.huid) body.huid = String(body.huid).toUpperCase();
    setBusy(true);
    try {
      const r = await saveStockItem(body, initial.id);
      onNotice(editing ? `${r.data.tagNo} saved.` : `${r.data.tagNo} added to stock.`);
      if (again && !editing) setF({...f, tagNo: '', barcode: '', huid: '', grossWeight: '', stoneWeight: '', netWeight: '', certificateNo: ''});
      else onSaved();
    } catch (err) { onNotice(errText(err)); }
    finally { setBusy(false); }
  };
  return <form onSubmit={save}>
    {!editing && <div className="kGrid">
      {newProduct || !products.length ? <>
        <F label="New product name *"><input required value={f.productName} onChange={e => set('productName', e.target.value)} placeholder="e.g. Gold bangle" /></F>
        <F label="Category"><input list="j360-categories" value={f.categoryName} onChange={e => set('categoryName', e.target.value)} placeholder="e.g. Bangles" /></F>
        <datalist id="j360-categories">{(md.categories || []).map((c: any) => <option key={c.id} value={c.name} />)}</datalist>
        {products.length > 0 && <button type="button" className="ghost" onClick={() => setNewProduct(false)}>Pick an existing product</button>}
      </> : <>
        <F label="Product *"><select required value={f.productId} onChange={e => set('productId', e.target.value)}><option value="">Choose…</option>{products.map((p: any) => <option key={p.id} value={p.id}>{p.name} · {p.category}</option>)}</select></F>
        <button type="button" className="ghost" onClick={() => setNewProduct(true)}>＋ New product</button>
      </>}
    </div>}
    <h3 className="kSection">Metal and weight</h3>
    <div className="kGrid">
      <F label="Purity *"><select required value={f.purityId} onChange={e => set('purityId', e.target.value)}><option value="">Choose…</option>{purities.map((p: any) => <option key={p.id} value={p.id}>{p.name} ({p.metal?.toLowerCase()})</option>)}</select></F>
      <F label="Gross weight (g) *"><input required type="number" min="0.001" step="0.001" inputMode="decimal" value={f.grossWeight} onChange={e => set('grossWeight', e.target.value)} /></F>
      <F label="Stone weight (g)"><input type="number" min="0" step="0.001" inputMode="decimal" value={f.stoneWeight} onChange={e => set('stoneWeight', e.target.value)} /></F>
      <F label="Net weight (g)" hint={f.netWeight ? undefined : `Gross − stone = ${net.toFixed(3)} g`}><input type="number" min="0" step="0.001" inputMode="decimal" value={f.netWeight} placeholder={net.toFixed(3)} onChange={e => set('netWeight', e.target.value)} /></F>
      <F label="HUID" hint="6 characters from the hallmark"><input maxLength={6} value={f.huid} onChange={e => set('huid', e.target.value.toUpperCase())} style={{textTransform: 'uppercase'}} /></F>
      <F label="Tag number" hint={editing ? undefined : 'Leave empty to number it automatically'}><input value={f.tagNo} onChange={e => set('tagNo', e.target.value)} /></F>
    </div>
    <h3 className="kSection">Price</h3>
    <div className="kGrid">
      <F label="Wastage %"><input type="number" min="0" step="0.01" value={f.wastagePercent} onChange={e => set('wastagePercent', e.target.value)} /></F>
      <F label="Making charge type"><select value={f.makingType} onChange={e => set('makingType', e.target.value)}><option value="PER_GRAM">Per gram (₹ × net weight)</option><option value="FIXED">Fixed (₹ per piece)</option><option value="PERCENT">Percent of metal value</option></select></F>
      <F label={f.makingType === 'PERCENT' ? 'Making %' : 'Making (₹)'}><input type="number" min="0" step="0.01" value={f.makingValue} onChange={e => set('makingValue', e.target.value)} /></F>
      <F label="Location in shop"><input value={f.location} onChange={e => set('location', e.target.value)} placeholder="e.g. Tray 3" /></F>
    </div>
    <button type="button" className="ghost" onClick={() => setMore(!more)} aria-expanded={more}>{more ? '− Fewer details' : '＋ Stones, fixed price, cost, certificate'}</button>
    {more && <div className="kGrid" style={{marginTop: 8}}>
      <F label="Stone value (₹)"><input type="number" min="0" step="0.01" value={f.stoneValue} onChange={e => set('stoneValue', e.target.value)} /></F>
      <F label="Stone count"><input type="number" min="0" step="1" value={f.stoneCount} onChange={e => set('stoneCount', e.target.value)} /></F>
      <F label="Stone details" wide><input value={f.stoneDetails} onChange={e => set('stoneDetails', e.target.value)} placeholder="e.g. 12 CZ, 1 ruby" /></F>
      <F label="Other charge (₹)"><input type="number" min="0" step="0.01" value={f.otherCharge} onChange={e => set('otherCharge', e.target.value)} /></F>
      <F label="Fixed price / MRP (₹)" hint="Only for pieces sold at a fixed price instead of by weight"><input type="number" min="0" step="0.01" value={f.mrp} onChange={e => set('mrp', e.target.value)} /></F>
      {perms.canSeeProfit && <F label="Cost price (₹)"><input type="number" min="0" step="0.01" value={f.costPrice} onChange={e => set('costPrice', e.target.value)} /></F>}
      <F label="Barcode" hint={editing ? undefined : 'Empty = same as tag'}><input value={f.barcode} onChange={e => set('barcode', e.target.value)} /></F>
      <F label="Certificate no."><input value={f.certificateNo} onChange={e => set('certificateNo', e.target.value)} /></F>
      <F label="Notes" wide><textarea value={f.notes} onChange={e => set('notes', e.target.value)} /></F>
    </div>}
    <div className="kActions">
      <button className="primary" disabled={busy}>{busy ? 'Saving…' : editing ? 'Save changes' : 'Add to stock'}</button>
      {!editing && <label className="kMuted"><input type="checkbox" checked={again} onChange={e => setAgain(e.target.checked)} /> Add another like this</label>}
      <button type="button" className="ghost" onClick={onCancel}>Cancel</button>
    </div>
  </form>;
}

/* ------------------------------------------------------------------------------------------------ transfers */

function Transfers({onNotice, branches}: {onNotice: (s: string) => void; branches: Branch[]}) {
  const perms = usePermissions();
  const list = useData(() => stockTransfers(), []);
  const [open, setOpen] = useState(false);
  const [to, setTo] = useState('');
  const [code, setCode] = useState('');
  const [picked, setPicked] = useState<any[]>([]);
  const [notes, setNotes] = useState('');
  const [busy, setBusy] = useState(false);
  const rows: any[] = (list.data as any) || [];
  const add = async (e: FormEvent) => {
    e.preventDefault();
    const c = code.trim();
    if (!c) return;
    try {
      const r = await scanStock(c);
      if (!r.data?.available) { onNotice(r.data?.message || 'That piece cannot be moved from this branch.'); return; }
      if (picked.some(p => p.id === r.data.id)) { onNotice(`${r.data.tagNo} is already in the list.`); return; }
      setPicked([...picked, r.data]); setCode('');
    } catch (err) { onNotice(errText(err)); }
  };
  const send = async () => {
    if (!to || !picked.length) return;
    setBusy(true);
    try {
      await transferStock({toBranchId: Number(to), itemIds: picked.map(p => p.id), notes: notes || undefined});
      onNotice(`${picked.length} piece${picked.length === 1 ? '' : 's'} moved.`);
      setPicked([]); setNotes(''); setOpen(false); void list.reload();
    } catch (err) { onNotice(errText(err)); }
    finally { setBusy(false); }
  };
  const others = branches.filter(b => b.active !== false);
  return <div className="panel">
    <div className="panelHead"><div><span className="eyebrow">BRANCH TRANSFERS</span><h2>{rows.length} transfers</h2></div>
      {perms.canManage && others.length > 1 && <button type="button" className="primary" onClick={() => setOpen(true)}>＋ Move pieces</button>}</div>
    {others.length <= 1 && <div className="kNote">Transfers need at least two branches. Add branches on the Branches page.</div>}
    <StateBox loading={list.loading} error={list.error} onRetry={list.reload} empty={!rows.length} emptyText="No transfers yet">
      <Table><thead><tr><th>No.</th><th>Date</th><th>From → To</th><th>Pieces</th><th>Net wt</th><th>By</th></tr></thead>
        <tbody>{rows.map(r => <tr key={r.id}><td><b>{r.transferNo}</b><small>{r.notes}</small></td><td>{r.date}</td><td>{r.from} → {r.to}</td><td>{r.pieces}<small>{(r.tags || []).join(', ')}</small></td><td>{grams(r.netWeight)}</td><td>{r.by}</td></tr>)}</tbody></Table>
    </StateBox>
    <Sheet open={open} onClose={() => setOpen(false)} kicker="TRANSFER" title="Move pieces to another branch">
      <F label="To branch *"><select value={to} onChange={e => setTo(e.target.value)}><option value="">Choose…</option>{others.map(b => <option key={b.id} value={b.id}>{b.name}</option>)}</select></F>
      <form className="kRow" onSubmit={add} style={{marginTop: 10}}><input placeholder="Scan or type tag / barcode" value={code} onChange={e => setCode(e.target.value)} aria-label="Piece to move" /><button className="secondary">Add</button></form>
      {picked.map(p => <div className="kRow" key={p.id}><b>{p.tagNo}</b><span>{p.productName} · {grams(p.netWeight)}</span><button type="button" className="ghost danger" onClick={() => setPicked(picked.filter(x => x.id !== p.id))} aria-label={`Remove ${p.tagNo}`}>✕</button></div>)}
      <F label="Notes"><input value={notes} onChange={e => setNotes(e.target.value)} /></F>
      <div className="kActions"><button type="button" className="primary" disabled={busy || !to || !picked.length} onClick={send}>Move {picked.length || ''} piece{picked.length === 1 ? '' : 's'}</button></div>
    </Sheet>
  </div>;
}

/* ------------------------------------------------------------------------------------------------ catalogue */

function Catalogue({md, reload, onNotice}: {md: any; reload: () => void; onNotice: (s: string) => void}) {
  const [which, setWhich] = useState<'products' | 'categories' | 'designs'>('products');
  const cats = (md.categories || []).map((c: any) => ({value: String(c.id), label: c.name}));
  const designs = (md.designs || []).map((d: any) => ({value: String(d.id), label: d.name}));
  return <>
    <Tabs value={which} onChange={setWhich} tabs={[{key: 'products', label: 'Products'}, {key: 'categories', label: 'Categories'}, {key: 'designs', label: 'Designs'}]} />
    {which === 'products' && <MasterEditor kind="products" title="Products" addLabel="Add product" rows={md.products || []} onSaved={reload} onNotice={onNotice}
      fields={[{key: 'name', label: 'Name', required: true}, {key: 'categoryId', label: 'Category', type: 'select', options: cats, required: true}, {key: 'designId', label: 'Design', type: 'select', options: designs},
        {key: 'sku', label: 'SKU', hint: 'Empty = made from the name'}, {key: 'hsnCode', label: 'HSN code', hint: '7113 for jewellery'}, {key: 'description', label: 'Description', type: 'textarea'}]}
      columns={[{key: 'name', label: 'Product'}, {key: 'sku', label: 'SKU'}, {key: 'category', label: 'Category'}, {key: 'design', label: 'Design'}, {key: 'hsnCode', label: 'HSN'}]} />}
    {which === 'categories' && <MasterEditor kind="categories" title="Categories" addLabel="Add category" rows={md.categories || []} onSaved={reload} onNotice={onNotice}
      fields={[{key: 'name', label: 'Name', required: true}, {key: 'code', label: 'Code', hint: 'Empty = made from the name'}]}
      columns={[{key: 'name', label: 'Category'}, {key: 'code', label: 'Code'}]} />}
    {which === 'designs' && <MasterEditor kind="designs" title="Designs" addLabel="Add design" rows={md.designs || []} onSaved={reload} onNotice={onNotice}
      fields={[{key: 'name', label: 'Name', required: true}, {key: 'categoryId', label: 'Category', type: 'select', options: cats, required: true}, {key: 'code', label: 'Code'}, {key: 'description', label: 'Description', type: 'textarea'}]}
      columns={[{key: 'name', label: 'Design'}, {key: 'code', label: 'Code'}, {key: 'category', label: 'Category'}]} />}
  </>;
}
