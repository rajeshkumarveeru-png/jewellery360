import {useEffect, useState} from 'react';
import {getWhatsAppSettings, saveWhatsAppSettings, getFriendlyApiError} from '../api';
import {Role,User,ThemeKey} from '../shared/types';
import {roleMenus} from '../shared/config';
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
 const [loading,setLoading]=useState(false);
 const [saving,setSaving]=useState(false);
 const [message,setMessage]=useState('');
 const [error,setError]=useState('');
 const [secretVersion,setSecretVersion]=useState(0);
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
