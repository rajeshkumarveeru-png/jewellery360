import type {FocusEvent, ReactNode} from 'react';
type FieldProps = {
  placeholder: string;
  type?: string;
  onChange: (v:string)=>void;
  onBlur?: (e:FocusEvent<HTMLInputElement>)=>void;
  error?: string;
  autoComplete?: string;
  inputMode?: 'none'|'text'|'tel'|'url'|'email'|'numeric'|'decimal'|'search';
  name?: string;
};

export function Field({placeholder,type='text',onChange,onBlur,error,autoComplete,inputMode,name}:FieldProps){
  const fieldId = name || placeholder.toLowerCase().replace(/[^a-z0-9]+/g,'-');
  return <div className={`fieldWrap ${error ? 'hasError' : ''}`}>
    <input
      id={fieldId}
      name={name}
      required
      placeholder={placeholder}
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
export function Loading(){return <main className="loading"><div className="loadingCard"><div className="logo">J360</div><h2>Loading Jewellery360</h2><p>Preparing your workspace…</p></div></main>;}
export function Table({children}:{children:ReactNode}){return <div className="tableWrap"><table>{children}</table></div>}
export function Empty({text}:{text:string}){return <div className="empty"><div>◇</div><b>{text}</b><small>Use the create form to add a real PostgreSQL record.</small></div>}
export function NavIcon({name}:{name:string}){const icons:Record<string,string>={Overview:'⌂',Companies:'▦',Branches:'⌘',Billing:'₹',Jewellery:'◇','Gold & Rates':'◈',Inventory:'▤',Purchases:'🛒','Old Gold':'↻',Customers:'♙',Services:'⚒',Payments:'◉',Reports:'▥',WhatsApp:'◌',Users:'♟',Approvals:'✓',Settings:'⚙','Audit Logs':'◍'};return <span className="navIcon">{icons[name]||'•'}</span>}
export function toNum(f:any){return Object.fromEntries(Object.entries(f).map(([k,v])=>['grossWeight','stoneWeight','netWeight','goldRate','wastagePercent','makingCharge','stoneCharge','otherCharges','gstPercent'].includes(k)?[k,Number(v||0)]:[k,v]));}
