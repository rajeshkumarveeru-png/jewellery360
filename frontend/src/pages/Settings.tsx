import {useEffect, useState} from 'react';
import {usePermissions} from '../shared/permissions';
import {getWhatsAppSettings, saveWhatsAppSettings, getTaxSettings, saveTaxSettings, getUserMenuPreferences, saveUserMenuPreferences, getBusinessSettings, saveBusinessSettings, invoicePreview, getFriendlyApiError} from '../api';
import {rememberBrandName} from '../shared/brand';
import {Role,User,ThemeKey} from '../shared/types';
import {roleMenus,defaultPreferredMenu} from '../shared/config';
import './Settings.css';

type WhatsAppSettingsState = {
  enabled:boolean;
  accessToken:string;
  accessTokenPresent:boolean;
  phoneNumberId:string;
  graphApiVersion:string;
  webhookVerifyToken:string;
  webhookVerifyTokenPresent:boolean;
  invoiceTemplateName:string;
  templateLanguage:string;
};


type TaxSettingsState = {
  enabled:boolean;
  mode:'GST'|'CGST_SGST';
  rate:string;
  cgstRate:string;
  sgstRate:string;
};

type BusinessState={companyName:string;phone:string;email:string;gstin:string;printFormat:'A4'|'80MM'|'50MM';skuPrefix:string;invoiceTemplate:string};
const emptyBusiness:BusinessState={companyName:'',phone:'',email:'',gstin:'',printFormat:'A4',skuPrefix:'',invoiceTemplate:'CLASSIC'};
const STYLE_PACKS:{key:ThemeKey;label:string;style:string;note:string;thumb:string}[]=[
  {key:'LUXURY_GOLD',label:'Luxury Gold',style:'Glass',note:'Gold pill sidebar, glass cards, gradient buttons.',thumb:'pk-luxury'},
  {key:'CLASSIC_IVORY',label:'Classic Ivory',style:'Ledger',note:'Top menu bar, serif type, ruled tables, engraved buttons, underline fields.',thumb:'pk-classic'},
  {key:'PREMIUM_DARK',label:'Premium Dark',style:'Console',note:'Icon rail, neon outlines, grid tables, mono type, glow buttons.',thumb:'pk-dark'},
  {key:'MODERN_LIGHT',label:'Modern Light',style:'Studio',note:'Floating menu, huge radii, floating table rows, pill buttons.',thumb:'pk-modern'}
];
const INVOICE_DESIGNS:{key:string;label:string;note:string;best:string}[]=[
  {key:'CLASSIC',label:'Classic Gold',note:'Cream and gold boxes, rate board in the header.',best:'Everyday showroom bills'},
  {key:'MODERN',label:'Modern Minimal',note:'Clean white page, thin lines, teal accent, amount in words.',best:'Premium boutiques'},
  {key:'ROYAL',label:'Royal Border',note:'Double maroon-and-gold frame with serif lettering.',best:'Weddings and high-value sales'},
  {key:'COMPACT',label:'Compact Table',note:'Dense navy grid, 17 items per page, wastage column.',best:'Long bills with many items'},
  {key:'FORMAL',label:'GST Formal',note:'Black-and-white tax invoice with HSN, tax summary and declaration.',best:'B2B and audit copies'}
];
const GSTIN_RE=/^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][1-9A-Z]Z[0-9A-Z]$/;
const EMAIL_RE=/^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const PRINT_OPTIONS:{value:BusinessState['printFormat'];label:string;hint:string}[]=[
  {value:'A4',label:'A4 paper',hint:'Full tax invoice with price breakup, signatures and terms.'},
  {value:'80MM',label:'80 mm thermal',hint:'Compact counter receipt for 80 mm thermal printers.'},
  {value:'50MM',label:'50 mm thermal',hint:'Narrow receipt for 50 mm thermal printers.'}
];

const emptyTax:TaxSettingsState={
  enabled:true,
  mode:'GST',
  rate:'3.00',
  cgstRate:'1.50',
  sgstRate:'1.50'
};

const emptyWhatsApp:WhatsAppSettingsState={
  enabled:false,
  accessToken:'',
  accessTokenPresent:false,
  phoneNumberId:'',
  graphApiVersion:'v23.0',
  webhookVerifyToken:'',
  webhookVerifyTokenPresent:false,
  invoiceTemplateName:'smartbill_invoice',
  templateLanguage:'en_US'
};

export default function SettingsModule({user,theme,setTheme}:{user:User;theme:ThemeKey;setTheme:(x:ThemeKey)=>void}){
 const matrix:Record<Role,string[]>=roleMenus;
 const [wa,setWa]=useState<WhatsAppSettingsState>(emptyWhatsApp);
 const [tax,setTax]=useState<TaxSettingsState>(emptyTax);
 const [biz,setBiz]=useState<BusinessState>(emptyBusiness);
 const [bizLoading,setBizLoading]=useState(false);
 const [bizSaving,setBizSaving]=useState(false);
 const [bizMessage,setBizMessage]=useState('');
 const [bizError,setBizError]=useState('');
 const [taxLoading,setTaxLoading]=useState(false);
 const [taxSaving,setTaxSaving]=useState(false);
 const [taxMessage,setTaxMessage]=useState('');
 const [taxError,setTaxError]=useState('');
 const [loading,setLoading]=useState(false);
 const [saving,setSaving]=useState(false);
 const [message,setMessage]=useState('');
 const [error,setError]=useState('');
 const [secretVersion,setSecretVersion]=useState(0);
 const [menuDraft,setMenuDraft]=useState<string[]>(()=>defaultPreferredMenu(user.role));
 const [menuLoading,setMenuLoading]=useState(false);
 const [menuSaving,setMenuSaving]=useState(false);
 const [menuMessage,setMenuMessage]=useState('');
 const allowedMenu=roleMenus[user.role]||['Overview'];
 const companyIdForMenu=user.role==='APP_ADMIN' ? Number(localStorage.getItem('j360_context_company'))||null : (user.companyId??null);
 const normalizeMenu=(items:string[])=>{const ordered=items.filter(x=>allowedMenu.includes(x)); const unique=[...new Set(ordered)]; for(const locked of ['Overview','Settings']){if(allowedMenu.includes(locked)&&!unique.includes(locked))unique.push(locked);} return unique;};
 useEffect(()=>{
   setMenuDraft(defaultPreferredMenu(user.role));
   if(!companyIdForMenu) return;
   setMenuLoading(true);
   getUserMenuPreferences(companyIdForMenu).then(r=>{
     const saved=Array.isArray(r.data?.menu)?r.data.menu:[];
     setMenuDraft(saved.length?normalizeMenu(saved):defaultPreferredMenu(user.role));
   }).catch(()=>setMenuDraft(defaultPreferredMenu(user.role))).finally(()=>setMenuLoading(false));
 },[user.id,user.role,companyIdForMenu]);
 const moveMenu=(index:number,direction:-1|1)=>{
   const next=[...menuDraft]; const target=index+direction;
   if(target<0||target>=next.length)return;
   [next[index],next[target]]=[next[target],next[index]]; setMenuDraft(next);
 };
 const toggleMenu=(item:string)=>{
   if(item==='Overview'||item==='Settings')return;
   setMenuDraft(v=>v.includes(item)?v.filter(x=>x!==item):[...v,item]);
 };
 const orderedMenuRows=[
   ...menuDraft.filter(item=>allowedMenu.includes(item)),
   ...allowedMenu.filter(item=>!menuDraft.includes(item))
 ];
 const saveMenu=async()=>{
   if(!companyIdForMenu){setMenuMessage('Select a company context before saving user menu settings.');return;}
   const normalized=normalizeMenu(menuDraft);
   setMenuSaving(true);setMenuMessage('');
   try{await saveUserMenuPreferences(normalized,companyIdForMenu);setMenuDraft(normalized);setMenuMessage('Menu visibility and order saved to the database for this user.');window.dispatchEvent(new Event('j360-menu-preferences-changed'));}
   catch(e){setMenuMessage(getFriendlyApiError(e,'Unable to save menu settings.'));}
   finally{setMenuSaving(false);}
 };
 const resetMenu=async()=>{
   if(!companyIdForMenu){setMenuMessage('Select a company context before resetting user menu settings.');return;}
   const defaults=[...allowedMenu];
   try{await saveUserMenuPreferences(defaults,companyIdForMenu);setMenuDraft(defaults);setMenuMessage('Default menu restored and saved to the database.');window.dispatchEvent(new Event('j360-menu-preferences-changed'));}
   catch(e){setMenuMessage(getFriendlyApiError(e,'Unable to reset menu settings.'));}
 };
 const companyId=user.role==='APP_ADMIN'
   ? Number(localStorage.getItem('j360_context_company'))||null
   : (user.companyId??null);

 useEffect(()=>{
   setMessage(''); setError(''); setWa(emptyWhatsApp);
   if(!companyId) return;
   setLoading(true);
   getWhatsAppSettings(companyId)
     .then(r=>setWa({...emptyWhatsApp,...r.data,enabled:Boolean(r.data?.enabled)}))
     .catch(e=>setError(getFriendlyApiError(e,'Unable to load WhatsApp company properties.')))
     .finally(()=>setLoading(false));
 },[companyId]);

 useEffect(()=>{
   setTax(emptyTax); setTaxMessage(''); setTaxError('');
   if(!companyId) return;
   setTaxLoading(true);
   getTaxSettings(companyId)
     .then(r=>setTax({
       enabled:Boolean(r.data?.enabled ?? true),
       mode:r.data?.mode==='CGST_SGST'?'CGST_SGST':'GST',
       rate:String(r.data?.rate ?? '3.00'),
       cgstRate:String(r.data?.cgstRate ?? '1.50'),
       sgstRate:String(r.data?.sgstRate ?? '1.50')
     }))
     .catch(e=>setTaxError(getFriendlyApiError(e,'Unable to load tax settings.')))
     .finally(()=>setTaxLoading(false));
 },[companyId]);

 useEffect(()=>{
   setBiz(emptyBusiness); setBizMessage(''); setBizError('');
   if(!companyId) return;
   setBizLoading(true);
   getBusinessSettings(companyId)
     .then(r=>setBiz({
       companyName:String(r.data?.companyName||''),
       phone:String(r.data?.phone||''),
       email:String(r.data?.email||''),
       gstin:String(r.data?.gstin||''),
       printFormat:(['A4','80MM','50MM'].includes(r.data?.printFormat)?r.data.printFormat:'A4') as BusinessState['printFormat'],
       skuPrefix:String(r.data?.skuPrefix||''),
       invoiceTemplate:INVOICE_DESIGNS.some(d=>d.key===String(r.data?.invoiceTemplate||'').toUpperCase())?String(r.data.invoiceTemplate).toUpperCase():'CLASSIC'
     }))
     .catch(e=>setBizError(getFriendlyApiError(e,'Unable to load business settings.')))
     .finally(()=>setBizLoading(false));
 },[companyId]);

 const bizIssues=(()=>{
   const out:Record<string,string>={};
   if(biz.companyName.trim().length<2) out.companyName='Company name is required (at least 2 characters).';
   if(biz.phone.trim()&&!/^[+0-9][0-9 ()-]{6,19}$/.test(biz.phone.trim())) out.phone='Enter a valid phone number.';
   if(biz.email.trim()&&!EMAIL_RE.test(biz.email.trim())) out.email='Enter a valid e-mail address.';
   if(biz.gstin.trim()&&!GSTIN_RE.test(biz.gstin.trim().toUpperCase())) out.gstin='GSTIN must be 15 characters, e.g. 33ABCDE1234F1Z5.';
   if(biz.skuPrefix.trim()&&!/^[A-Za-z0-9][A-Za-z0-9-]{0,11}$/.test(biz.skuPrefix.trim())) out.skuPrefix='Use letters, digits or hyphen (max 12).';
   return out;
 })();
 const perms=usePermissions();
 const canEditBusiness=user.role==='APP_ADMIN'||user.role==='COMPANY_ADMIN'||perms.canEditSettings;


 const [previewBusy,setPreviewBusy]=useState('');
 const openPreview=async(key:string)=>{
   const popup=window.open('about:blank','_blank');
   setPreviewBusy(key);
   try{
     const r=await invoicePreview(key);
     const url=URL.createObjectURL(new Blob([r.data],{type:'application/pdf'}));
     if(popup){popup.location.href=url;popup.focus();}else{window.open(url,'_blank');}
     window.setTimeout(()=>URL.revokeObjectURL(url),60000);
   }catch(e){popup?.close();setBizError(getFriendlyApiError(e,'Unable to open the design preview.'));}
   finally{setPreviewBusy('');}
 };
 const saveBiz=async()=>{
   if(!companyId||Object.keys(bizIssues).length) return;
   setBizSaving(true); setBizMessage(''); setBizError('');
   try{
     const r=await saveBusinessSettings({companyName:biz.companyName.trim(),phone:biz.phone.trim(),email:biz.email.trim(),gstin:biz.gstin.trim().toUpperCase(),printFormat:biz.printFormat,skuPrefix:biz.skuPrefix.trim().toUpperCase(),invoiceTemplate:biz.invoiceTemplate},companyId);
     setBiz({companyName:String(r.data?.companyName||biz.companyName),phone:String(r.data?.phone||''),email:String(r.data?.email||''),gstin:String(r.data?.gstin||''),printFormat:(r.data?.printFormat||biz.printFormat) as BusinessState['printFormat'],skuPrefix:String(r.data?.skuPrefix||''),invoiceTemplate:String(r.data?.invoiceTemplate||biz.invoiceTemplate)});
     rememberBrandName(String(r.data?.companyName||biz.companyName));
     window.dispatchEvent(new Event('j360-brand-changed'));
     setBizMessage('Business settings saved. The company name, GSTIN, contact details and print layout now apply to new invoices and receipts.');
   }catch(e){setBizError(getFriendlyApiError(e,'Unable to save business settings.'));}
   finally{setBizSaving(false);}
 };

 const saveTax=async()=>{
   if(!companyId) return;
   setTaxSaving(true); setTaxMessage(''); setTaxError('');
   try{
     const payload={
       enabled:tax.enabled,
       mode:tax.mode,
       rate:Number(tax.rate||0),
       cgstRate:Number(tax.cgstRate||0),
       sgstRate:Number(tax.sgstRate||0)
     };
     const r=await saveTaxSettings(payload,companyId);
     setTax({
       enabled:Boolean(r.data?.enabled),
       mode:r.data?.mode==='CGST_SGST'?'CGST_SGST':'GST',
       rate:String(r.data?.rate ?? 0),
       cgstRate:String(r.data?.cgstRate ?? 0),
       sgstRate:String(r.data?.sgstRate ?? 0)
     });
     setTaxMessage('Tax settings saved. New invoices will use this configuration automatically.');
   }catch(e){setTaxError(getFriendlyApiError(e,'Unable to save tax settings.'));}
   finally{setTaxSaving(false);}
 };

 const save=async()=>{
   if(!companyId) return;
   setSaving(true);setMessage('');setError('');
   try{
     const payload={
       enabled:wa.enabled,
       accessToken:wa.accessToken.trim()|| (wa.accessTokenPresent?'__KEEP_EXISTING__':''),
       phoneNumberId:wa.phoneNumberId.trim(),
       graphApiVersion:wa.graphApiVersion.trim()||'v23.0',
       webhookVerifyToken:wa.webhookVerifyToken.trim()|| (wa.webhookVerifyTokenPresent?'__KEEP_EXISTING__':''),
       invoiceTemplateName:wa.invoiceTemplateName.trim()||'smartbill_invoice',
       templateLanguage:wa.templateLanguage.trim()||'en_US'
     };
     const r=await saveWhatsAppSettings(payload,companyId);
     setWa({...emptyWhatsApp,...r.data,enabled:Boolean(r.data?.enabled)});
     setMessage('WhatsApp company properties saved successfully.');
     setSecretVersion(x=>x+1);
   }catch(e){setError(getFriendlyApiError(e,'Unable to save WhatsApp company properties.'));}
   finally{setSaving(false);}
 };

 const secretPlaceholder=(present:boolean)=>present?'Saved — enter a new value to replace':'Enter value';

 return <div className="moduleGrid settingsGrid">
   <div className="panel">
    <span className="eyebrow">WORKSPACE</span><h2>Settings</h2>
    <div className="setting"><div><b>Theme</b><small>Stored as a UI preference in this browser.</small></div><select value={theme} onChange={e=>setTheme(e.target.value as ThemeKey)}><option value="LUXURY_GOLD">Luxury Gold · Glass</option><option value="CLASSIC_IVORY">Classic Ivory · Ledger</option><option value="PREMIUM_DARK">Premium Dark · Console</option><option value="MODERN_LIGHT">Modern Light · Studio</option></select></div>
    <div className="setting"><div><b>Business identity</b><small>{user.companyName||'Platform'} · {user.branchName||'No branch selected'}</small></div></div>
    <div className="setting"><div><b>Company property storage</b><small>WhatsApp configuration is stored per company in the <code>property</code> table. APP_ADMIN must select a company context before editing it.</small></div></div>
   </div>

   <div className="panel businessSettingsPanel domain-accent">
    <span className="eyebrow">BUSINESS PROFILE</span><h2>Company identity & documents</h2>
    <p className="settingsHelp">These details are printed on invoices and receipts. The company name is your brand: it appears in the sidebar, header, login portal and PDFs.</p>
    {!companyId&&<div className="settingsNotice">Select a company context to manage the business profile.</div>}
    {bizLoading&&<div className="settingsNotice">Loading business profile…</div>}
    {bizError&&<div className="settingsError" role="alert">{bizError}</div>}
    {bizMessage&&<div className="settingsSuccess" role="status">{bizMessage}</div>}
    {companyId&&!bizLoading&&<form className="singleColumnForm settingsForm" onSubmit={e=>{e.preventDefault();void saveBiz();}} noValidate>
      <label className={bizIssues.companyName?'hasIssue':''}><span>Company name</span><input value={biz.companyName} disabled={!canEditBusiness} onChange={e=>setBiz({...biz,companyName:e.target.value})} autoComplete="organization"/>{bizIssues.companyName&&<em>{bizIssues.companyName}</em>}</label>
      <label className={bizIssues.phone?'hasIssue':''}><span>Business phone</span><input type="tel" inputMode="tel" value={biz.phone} disabled={!canEditBusiness} onChange={e=>setBiz({...biz,phone:e.target.value})} placeholder="+91 98765 43210"/>{bizIssues.phone&&<em>{bizIssues.phone}</em>}</label>
      <label className={bizIssues.email?'hasIssue':''}><span>Corporate e-mail</span><input type="email" value={biz.email} disabled={!canEditBusiness} onChange={e=>setBiz({...biz,email:e.target.value})} placeholder="accounts@yourjewellers.in"/>{bizIssues.email&&<em>{bizIssues.email}</em>}</label>
      <label className={bizIssues.gstin?'hasIssue':''}><span>GSTIN</span><input value={biz.gstin} disabled={!canEditBusiness} maxLength={15} onChange={e=>setBiz({...biz,gstin:e.target.value.toUpperCase()})} placeholder="33ABCDE1234F1Z5"/>{bizIssues.gstin&&<em>{bizIssues.gstin}</em>}</label>
      <label><span>Print layout</span>
        <select value={biz.printFormat} disabled={!canEditBusiness} onChange={e=>setBiz({...biz,printFormat:e.target.value as BusinessState['printFormat']})}>{PRINT_OPTIONS.map(o=><option key={o.value} value={o.value}>{o.label}</option>)}</select>
        <small>{PRINT_OPTIONS.find(o=>o.value===biz.printFormat)?.hint}</small>
      </label>
      <label className={bizIssues.skuPrefix?'hasIssue':''}><span>SKU / tag prefix (optional)</span><input value={biz.skuPrefix} disabled={!canEditBusiness} maxLength={12} onChange={e=>setBiz({...biz,skuPrefix:e.target.value.toUpperCase()})} placeholder="e.g. GR- (leave blank to start at 01)"/>{bizIssues.skuPrefix?<em>{bizIssues.skuPrefix}</em>:<small>Your own sequence: next product would be <b>{(biz.skuPrefix||'').toUpperCase()}01</b>, then {(biz.skuPrefix||'').toUpperCase()}02… Other companies are never affected.</small>}</label>
      {canEditBusiness?<button className="settingsSave" type="submit" disabled={bizSaving||Object.keys(bizIssues).length>0}>{bizSaving?'Saving…':'Save business profile'}</button>:<div className="settingsNotice">Only a company administrator can change these details.</div>}
    </form>}
   </div>



   <div className="panel interfaceStylePanel">
    <span className="eyebrow">INTERFACE STYLE</span><h2>Pick the look of the whole app</h2>
    <p className="settingsHelp">Each style changes the menu, buttons, inputs, tables and cards - not just the colours. It is saved in this browser.</p>
    <div className="styleGrid" role="radiogroup" aria-label="Interface style">
      {STYLE_PACKS.map(k=><div key={k.key} className={`styleCard${theme===k.key?' on':''}`}>
        <button type="button" role="radio" aria-checked={theme===k.key} className="stylePick" onClick={()=>setTheme(k.key)}>
          <span className={`packThumb ${k.thumb}`} aria-hidden="true"><i/><i/><i/><i/></span>
          <b>{k.label} · {k.style}</b><small>{k.note}</small>
          {theme===k.key&&<span className="designTick">In use</span>}
        </button>
      </div>)}
    </div>
   </div>
   <div className="panel invoiceDesignPanel">
    <span className="eyebrow">INVOICE & RECEIPT DESIGN</span><h2>Choose how your invoices look</h2>
    <p className="settingsHelp">Pick the A4 invoice design. Open a sample to see it with your company name. Thermal receipts (50 / 80 mm) keep their compact layout; change the paper size in the business profile. A cashier can still print another design for one bill from the invoice-created card.</p>
    {!companyId&&<div className="settingsNotice">Select a company context to choose an invoice design.</div>}
    {companyId&&<div className="designGrid" role="radiogroup" aria-label="Invoice design">
      {INVOICE_DESIGNS.map(d=><div key={d.key} className={`designCard${biz.invoiceTemplate===d.key?' on':''}`}>
        <button type="button" role="radio" aria-checked={biz.invoiceTemplate===d.key} className="designPick" disabled={!canEditBusiness} onClick={()=>setBiz({...biz,invoiceTemplate:d.key})}>
          <span className={`invThumb t-${d.key.toLowerCase()}`} aria-hidden="true"><i/><i/><i/><i/><i/></span>
          <b>{d.label}</b><small>{d.note}</small><em>Best for: {d.best}</em>
          {biz.invoiceTemplate===d.key&&<span className="designTick">Selected</span>}
        </button>
        <button type="button" className="designPreview" disabled={previewBusy===d.key} onClick={()=>void openPreview(d.key)}>{previewBusy===d.key?'Opening…':'Preview PDF'}</button>
      </div>)}
    </div>}
    {companyId&&canEditBusiness&&<button className="settingsSave" type="button" disabled={bizSaving} onClick={()=>void saveBiz()}>{bizSaving?'Saving…':'Save invoice design'}</button>}
    {bizMessage&&<div className="settingsSuccess" role="status">{bizMessage}</div>}
   </div>
   <div className="panel taxSettingsPanel">
    <span className="eyebrow">TAX & GST</span><h2>Invoice tax configuration</h2>
    <p className="settingsHelp">Set this once for the company. Cashiers do not need to enter GST on every bill. The same configuration is used for invoice calculations and PDFs.</p>
    {!companyId&&<div className="settingsNotice">Select a company context to manage tax settings.</div>}
    {taxLoading&&<div className="settingsNotice">Loading tax configuration…</div>}
    {taxError&&<div className="settingsError">{taxError}</div>}
    {taxMessage&&<div className="settingsSuccess">{taxMessage}</div>}
    {companyId&&!taxLoading&&<div className="taxSettingsForm">
      <label className="toggleField"><span><b>Include GST in calculations</b><small>When on, GST lines are added automatically to live bill totals, payments, ledgers and printed invoices. When off, invoices are calculated without GST/CGST/SGST.</small></span><input type="checkbox" checked={tax.enabled} onChange={e=>setTax({...tax,enabled:e.target.checked})}/></label>
      <label><span>Tax mode</span><select value={tax.mode} disabled={!tax.enabled} onChange={e=>setTax({...tax,mode:e.target.value as TaxSettingsState['mode']})}><option value="GST">GST</option><option value="CGST_SGST">CGST + SGST</option></select></label>
      {tax.mode==='GST'?<label><span>GST rate (%)</span><input type="number" min="0" max="100" step="0.01" value={tax.rate} disabled={!tax.enabled} onChange={e=>setTax({...tax,rate:e.target.value})}/></label>:<div className="taxSplitGrid"><label><span>CGST rate (%)</span><input type="number" min="0" max="100" step="0.01" value={tax.cgstRate} disabled={!tax.enabled} onChange={e=>setTax({...tax,cgstRate:e.target.value})}/></label><label><span>SGST rate (%)</span><input type="number" min="0" max="100" step="0.01" value={tax.sgstRate} disabled={!tax.enabled} onChange={e=>setTax({...tax,sgstRate:e.target.value})}/></label></div>}
      <div className="taxPreview"><b>Invoice preview</b>{!tax.enabled?<span>No tax</span>:tax.mode==='GST'?<span>GST @ {Number(tax.rate||0).toFixed(2)}%</span>:<span>CGST @ {Number(tax.cgstRate||0).toFixed(2)}% + SGST @ {Number(tax.sgstRate||0).toFixed(2)}% = {(Number(tax.cgstRate||0)+Number(tax.sgstRate||0)).toFixed(2)}%</span>}</div>
      {canEditBusiness&&<button className="settingsSave" type="button" disabled={taxSaving} onClick={saveTax}>{taxSaving?'Saving…':'Save tax settings'}</button>}
    </div>}
   </div>

   <div className="panel menuPreferencesPanel">
    <span className="eyebrow">NAVIGATION</span><h2>Menu visibility & order</h2>
    <p className="settingsHelp">Choose which menu sections this user sees and set the order shown in the left navigation. Preferences are stored in the database against this company and user, so they follow the user across browsers and devices.</p>
    <div className="menuPreferenceList">
      {orderedMenuRows.map(item=>{const checked=menuDraft.includes(item); const index=menuDraft.indexOf(item); const locked=item==='Overview'||item==='Settings'; return <div className={`menuPreferenceRow ${checked?'selected':'muted'}`} key={item}>
        <label><input type="checkbox" checked={checked} disabled={locked} onChange={()=>toggleMenu(item)}/><span>{item}</span></label>
        <div className="menuMoveButtons">
          <button type="button" disabled={!checked||index<=0} onClick={()=>moveMenu(index,-1)} aria-label={`Move ${item} up`}>↑</button>
          <button type="button" disabled={!checked||index<0||index>=menuDraft.length-1} onClick={()=>moveMenu(index,1)} aria-label={`Move ${item} down`}>↓</button>
        </div>
      </div>})}
    </div>
    {menuLoading&&<div className="settingsNotice">Loading database menu preferences…</div>}{menuMessage&&<div className="settingsSuccess">{menuMessage}</div>}
    <div className="menuPreferenceActions"><button className="secondary" type="button" disabled={menuSaving||menuLoading} onClick={resetMenu}>Reset default</button><button className="settingsSave" type="button" disabled={menuSaving||menuLoading} onClick={saveMenu}>{menuSaving?'Saving…':'Save menu settings'}</button></div>
   </div>

   <div className="panel settingsRight">
    <span className="eyebrow">WHATSAPP BUSINESS</span><h2>WhatsApp configuration</h2>
    {!companyId&&<div className="settingsNotice">Select a company context to manage company WhatsApp properties.</div>}
    {loading&&<div className="settingsNotice">Loading company properties…</div>}
    {error&&<div className="settingsError">{error}</div>}
    {message&&<div className="settingsSuccess">{message}</div>}
    {companyId&&!loading&&<>
      <div className="settingsStatus"><span><i className={wa.enabled?'on':''}/>{wa.enabled?'Enabled':'Disabled'}</span><small>Company ID: {companyId}</small></div>
      <div className="waFormGrid">
       <label className="toggleField"><span><b>Enable WhatsApp</b><small>Approval notifications and WhatsApp operations use these company properties.</small></span><input type="checkbox" checked={wa.enabled} onChange={e=>setWa({...wa,enabled:e.target.checked})}/></label>
       <label><span>Access Token</span><input key={`token-${secretVersion}`} type="password" value={wa.accessToken} placeholder={secretPlaceholder(wa.accessTokenPresent)} onChange={e=>setWa({...wa,accessToken:e.target.value})}/><small>Stored in the company property table; never returned to the browser.</small></label>
       <label><span>Phone Number ID</span><input value={wa.phoneNumberId} onChange={e=>setWa({...wa,phoneNumberId:e.target.value})}/></label>
       <label><span>Graph API Version</span><input value={wa.graphApiVersion} onChange={e=>setWa({...wa,graphApiVersion:e.target.value})}/></label>
       <label><span>Webhook Verify Token</span><input key={`webhook-${secretVersion}`} type="password" value={wa.webhookVerifyToken} placeholder={secretPlaceholder(wa.webhookVerifyTokenPresent)} onChange={e=>setWa({...wa,webhookVerifyToken:e.target.value})}/><small>Stored securely as a company property; never returned to the browser.</small></label>
       <label><span>Invoice Template Name</span><input value={wa.invoiceTemplateName} onChange={e=>setWa({...wa,invoiceTemplateName:e.target.value})}/></label>
       <label><span>Template Language</span><input value={wa.templateLanguage} onChange={e=>setWa({...wa,templateLanguage:e.target.value})}/></label>
      </div>
      <div className="settingsDefaults"><b>Default properties created at company approval</b><span>Enabled: false</span><span>Graph API: v23.0</span><span>Invoice template: smartbill_invoice</span><span>Language: en_US</span><span>Access token / Phone Number ID / Webhook token: blank until configured</span></div>
      {canEditBusiness&&<button className="settingsSave" disabled={saving} onClick={save}>{saving?'Saving…':'Save WhatsApp company settings'}</button>}
    </>}
   </div>

   <div className="panel rolePanel"><span className="eyebrow">ROLE MATRIX</span><h2>Role access</h2>{Object.entries(matrix).map(([r,m])=><div className="roleRow" key={r}><b>{r}</b><span>{m.join(' · ')}</span></div>)}</div>
 </div>
}
