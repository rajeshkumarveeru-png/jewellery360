import {useEffect, useState} from 'react';
import {getWhatsAppSettings, saveWhatsAppSettings, getTaxSettings, saveTaxSettings, getUserMenuPreferences, saveUserMenuPreferences, getFriendlyApiError} from '../api';
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
    <div className="setting"><div><b>Theme</b><small>Stored as a UI preference in this browser.</small></div><select value={theme} onChange={e=>setTheme(e.target.value as ThemeKey)}><option value="LUXURY_GOLD">Luxury Gold</option><option value="CLASSIC_IVORY">Classic Ivory</option><option value="PREMIUM_DARK">Premium Dark</option><option value="MODERN_LIGHT">Modern Light</option></select></div>
    <div className="setting"><div><b>Business identity</b><small>{user.companyName||'Platform'} · {user.branchName||'No branch selected'}</small></div></div>
    <div className="setting"><div><b>Company property storage</b><small>WhatsApp configuration is stored per company in the <code>property</code> table. APP_ADMIN must select a company context before editing it.</small></div></div>
   </div>

   <div className="panel taxSettingsPanel">
    <span className="eyebrow">TAX & GST</span><h2>Invoice tax configuration</h2>
    <p className="settingsHelp">Set this once for the company. Cashiers do not need to enter GST on every bill. The same configuration is used for invoice calculations and PDFs.</p>
    {!companyId&&<div className="settingsNotice">Select a company context to manage tax settings.</div>}
    {taxLoading&&<div className="settingsNotice">Loading tax configuration…</div>}
    {taxError&&<div className="settingsError">{taxError}</div>}
    {taxMessage&&<div className="settingsSuccess">{taxMessage}</div>}
    {companyId&&!taxLoading&&<div className="taxSettingsForm">
      <label className="toggleField"><span><b>Enable tax on invoices</b><small>When disabled, invoices are calculated without GST/CGST/SGST.</small></span><input type="checkbox" checked={tax.enabled} onChange={e=>setTax({...tax,enabled:e.target.checked})}/></label>
      <label><span>Tax mode</span><select value={tax.mode} disabled={!tax.enabled} onChange={e=>setTax({...tax,mode:e.target.value as TaxSettingsState['mode']})}><option value="GST">GST</option><option value="CGST_SGST">CGST + SGST</option></select></label>
      {tax.mode==='GST'?<label><span>GST rate (%)</span><input type="number" min="0" max="100" step="0.01" value={tax.rate} disabled={!tax.enabled} onChange={e=>setTax({...tax,rate:e.target.value})}/></label>:<div className="taxSplitGrid"><label><span>CGST rate (%)</span><input type="number" min="0" max="100" step="0.01" value={tax.cgstRate} disabled={!tax.enabled} onChange={e=>setTax({...tax,cgstRate:e.target.value})}/></label><label><span>SGST rate (%)</span><input type="number" min="0" max="100" step="0.01" value={tax.sgstRate} disabled={!tax.enabled} onChange={e=>setTax({...tax,sgstRate:e.target.value})}/></label></div>}
      <div className="taxPreview"><b>Invoice preview</b>{!tax.enabled?<span>No tax</span>:tax.mode==='GST'?<span>GST @ {Number(tax.rate||0).toFixed(2)}%</span>:<span>CGST @ {Number(tax.cgstRate||0).toFixed(2)}% + SGST @ {Number(tax.sgstRate||0).toFixed(2)}% = {(Number(tax.cgstRate||0)+Number(tax.sgstRate||0)).toFixed(2)}%</span>}</div>
      <button className="settingsSave" type="button" disabled={taxSaving} onClick={saveTax}>{taxSaving?'Saving…':'Save tax settings'}</button>
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
      <button className="settingsSave" disabled={saving} onClick={save}>{saving?'Saving…':'Save WhatsApp company settings'}</button>
    </>}
   </div>

   <div className="panel rolePanel"><span className="eyebrow">ROLE MATRIX</span><h2>Role access</h2>{Object.entries(matrix).map(([r,m])=><div className="roleRow" key={r}><b>{r}</b><span>{m.join(' · ')}</span></div>)}</div>
 </div>
}
