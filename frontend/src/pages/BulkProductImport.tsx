import {useMemo, useState} from 'react';
import * as XLSX from 'xlsx';
import {bulkJewelleryImportCommit, bulkJewelleryImportPreview} from '../api';
import './BulkProductImport.css';

type ImportRow = Record<string, any> & {rowNumber?: number};

const HEADERS = [
  'Product Name','SKU','Category','Design','Purity','Tag No','Barcode',
  'Gross Weight','Stone Weight','Net Weight','HUID','Wastage %','Making Charge','Stone Value'
];

const aliases: Record<string,string> = {
  'product name':'productName','item name':'productName','product':'productName','name':'productName',
  'sku':'sku','item code':'sku','product code':'sku','code':'sku',
  'category':'category','category name':'category',
  'design':'design','design name':'design',
  'purity':'purity','purity name':'purity','karat':'purity',
  'tag no':'tagNo','tag number':'tagNo','tag':'tagNo',
  'barcode':'barcode','bar code':'barcode',
  'gross weight':'grossWeight','gross wt':'grossWeight','gross weight (g)':'grossWeight',
  'stone weight':'stoneWeight','stone wt':'stoneWeight','stone weight (g)':'stoneWeight',
  'net weight':'netWeight','net wt':'netWeight','net weight (g)':'netWeight',
  'huid':'huid','wastage %':'wastagePercent','wastage':'wastagePercent',
  'making charge':'makingCharge','making':'makingCharge','making charges':'makingCharge',
  'stone value':'stoneValue','stone amount':'stoneValue','description':'description'
};

const key = (x:string) => x.trim().toLowerCase().replace(/\s+/g,' ');

function normalizeRows(raw:any[]):ImportRow[] {
  return raw.map((r:any,index:number)=>{
    const out:ImportRow = {rowNumber:index+2};
    Object.entries(r || {}).forEach(([header,value])=>{
      const target = aliases[key(header)];
      if(target) out[target] = typeof value === 'number' ? value : String(value ?? '').trim();
    });
    return out;
  }).filter(r=>Object.values(r).some(v=>String(v ?? '').trim() !== ''));
}

export default function BulkProductImport({onClose,onNotice}:{onClose:()=>void;onNotice:(x:string)=>void}) {
  const [rows,setRows] = useState<ImportRow[]>([]);
  const [result,setResult] = useState<any>(null);
  const [busy,setBusy] = useState(false);
  const [fileName,setFileName] = useState('');

  const errors = useMemo(()=>result?.results?.filter((x:any)=>x.status==='ERROR') || [],[result]);
  const ready = Number(result?.ready || 0);
  const canCommit = rows.length > 0 && result?.success === true && ready === rows.length;

  const downloadTemplate = () => {
    const wb = XLSX.utils.book_new();
    const ws = XLSX.utils.aoa_to_sheet([
      HEADERS,
      ['Gold Ring','','Rings','','22K','','','4.250','0.000','4.250','','0','850','0'],
      ['Gold Chain','','Chains','','22K','','','18.500','0.000','18.500','','0','1200','0']
    ]);
    ws['!cols'] = HEADERS.map(h=>({wch:Math.max(12,h.length+2)}));
    XLSX.utils.book_append_sheet(wb,ws,'Products');
    XLSX.writeFile(wb,'Jewellery360_Product_Import_Template.xlsx');
  };

  const chooseFile = async(file:File) => {
    setBusy(true); setResult(null); setRows([]); setFileName(file.name);
    try {
      const buffer = await file.arrayBuffer();
      const wb = XLSX.read(buffer,{type:'array',cellDates:false});
      const first = wb.Sheets[wb.SheetNames[0]];
      const raw = XLSX.utils.sheet_to_json(first,{defval:''});
      const normalized = normalizeRows(raw);
      if(!normalized.length) throw new Error('No product rows found in the selected file.');
      setRows(normalized);
      const response = await bulkJewelleryImportPreview(normalized);
      setResult(response.data);
    } catch(e:any) {
      setResult({success:false,results:[{rowNumber:0,status:'ERROR',message:e?.response?.data?.message||e?.message||'Unable to read the file.'}],errors:1});
    } finally { setBusy(false); }
  };

  const commit = async() => {
    if(!canCommit) return;
    if(!window.confirm(`Import ${rows.length} jewellery items into the selected branch?`)) return;
    setBusy(true);
    try {
      const response = await bulkJewelleryImportCommit(rows);
      onNotice(`Bulk import completed: ${response.data?.created || 0} created${response.data?.skipped ? `, ${response.data.skipped} already existed` : ''}.`);
      onClose();
    } catch(e:any) {
      onNotice(e?.response?.data?.message || 'Bulk import failed. No partial import was committed.');
      setBusy(false);
    }
  };

  return <div className="bulkImportOverlay">
    <div className="bulkImportModal">
      <div className="bulkImportHead">
        <div><span className="eyebrow">JEWELLERY · BULK IMPORT</span><h2>Import your existing stock</h2><p>One Excel file. SmartBill creates the required master data, tags, items and opening stock automatically.</p></div>
        <button className="ghost" onClick={onClose}>Close</button>
      </div>

      <div className="bulkImportSteps">
        <div className="bulkStep"><b>1</b><span>Download template</span><button className="secondary" onClick={downloadTemplate}>Download Excel</button></div>
        <div className="bulkStep"><b>2</b><span>Fill your products</span><small>Category and Purity are matched automatically. SKU, Tag and Barcode can be left blank.</small></div>
        <div className="bulkStep"><b>3</b><span>Upload & check</span><small>SmartBill validates every row before saving anything.</small></div>
      </div>

      <label className="bulkDrop">
        <input type="file" accept=".xlsx,.xls,.csv" disabled={busy} onChange={e=>{const f=e.target.files?.[0];if(f)void chooseFile(f);}} />
        <strong>{busy ? 'Checking your file…' : fileName || 'Choose Excel / CSV file'}</strong>
        <span>Up to 10,000 rows · existing items are safely skipped</span>
      </label>

      {result && <div className="bulkSummary">
        <div><b>{rows.length}</b><span>Total rows</span></div>
        <div><b>{ready}</b><span>Ready</span></div>
        <div className={errors.length?'hasError':''}><b>{errors.length}</b><span>Need fixing</span></div>
      </div>}

      {errors.length>0 && <div className="bulkErrors">
        <strong>Fix these rows before importing</strong>
        <div className="bulkErrorList">{errors.slice(0,30).map((x:any)=><div key={x.rowNumber}><b>Row {x.rowNumber}</b><span>{x.message}</span></div>)}</div>
        {errors.length>30 && <small>Showing first 30 errors.</small>}
      </div>}

      {result?.success && ready>0 && errors.length===0 && <div className="bulkReady">✓ Everything is ready. No database changes have been made yet.</div>}

      <div className="bulkImportFoot">
        <button className="ghost" onClick={onClose} disabled={busy}>Cancel</button>
        <button className="primary" disabled={!canCommit || busy} onClick={()=>void commit()}>
          {busy ? 'Processing…' : `Import ${rows.length || ''} products`}
        </button>
      </div>
    </div>
  </div>;
}
