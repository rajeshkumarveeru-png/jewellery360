import {FormEvent, useEffect, useState} from 'react';
import {companies,branches,createBranch,api} from '../api';
import {User,Company,Branch} from '../shared/types';
import {Table,Empty} from '../shared/ui';
import './Branches.css';

const emptyForm:any={companyId:'',name:'',code:'',phone:'',email:'',gstin:'',website:'',address:'',invoiceTitle:'',invoiceSubtitle:'',invoiceTerms:'',invoiceFooter:''};

export default function BranchesModule({user,companiesData,onNotice}:{user:User;companiesData:Company[];onNotice:(x:string)=>void}){
 const [list,setList]=useState<Branch[]>([]);
 const [f,setF]=useState<any>({...emptyForm,companyId:user.companyId||companiesData[0]?.id||''});
 const [editing,setEditing]=useState<number|null>(null);
 const [saving,setSaving]=useState(false);
 const canEdit=user.role==='APP_ADMIN'||user.role==='COMPANY_ADMIN';
 const load=()=>branches().then(r=>setList(r.data)).catch(()=>setList([]));
 useEffect(()=>{void load();},[user.companyId]);
 const reset=()=>{setEditing(null);setF({...emptyForm,companyId:user.companyId||companiesData[0]?.id||''});};
 const edit=(x:any)=>{setEditing(Number(x.id));setF({...emptyForm,...x,companyId:x.companyId});window.scrollTo({top:0,behavior:'smooth'});};
 const save=async(e:FormEvent)=>{
   e.preventDefault();
   if(!canEdit){onNotice('Only APP_ADMIN or COMPANY_ADMIN can configure branches.');return;}
   setSaving(true);
   try{
     const payload={...f,companyId:user.role==='APP_ADMIN'?Number(f.companyId):undefined};
     if(editing){await api.put(`/branches/${editing}`,payload);onNotice('Branch configuration updated.');}
     else{await createBranch(payload);onNotice('Branch created.');}
     reset();await load();
   }catch(err:any){onNotice(err?.response?.data?.message||`Unable to ${editing?'update':'create'} branch`);}
   finally{setSaving(false);}
 };
 return <div className="moduleGrid branchesPage">
   <div className="panel branchConfigPanel">
    <div className="panelHead"><div><span className="eyebrow">ADMIN-ONLY BRANCH CONFIGURATION</span><h2>{editing?'Edit branch':'Branches'}</h2></div>{editing&&<button type="button" className="ghost" onClick={reset}>Cancel edit</button>}</div>
    {!canEdit?<div className="settingsNotice">Branch business configuration is available only to APP_ADMIN and COMPANY_ADMIN.</div>:<form className="branchConfigForm" onSubmit={save}>
      {user.role==='APP_ADMIN'&&<label><span>Company</span><select required value={f.companyId||''} onChange={e=>setF({...f,companyId:Number(e.target.value)})}><option value="">Select company</option>{companiesData.map(c=><option key={c.id} value={c.id}>{c.name}</option>)}</select></label>}
      <label><span>Branch name *</span><input required value={f.name||''} onChange={e=>setF({...f,name:e.target.value})}/></label>
      <label><span>Branch code *</span><input required value={f.code||''} onChange={e=>setF({...f,code:e.target.value})}/></label>
      <label><span>GSTIN</span><input value={f.gstin||''} onChange={e=>setF({...f,gstin:e.target.value.toUpperCase()})}/></label>
      <label><span>Phone</span><input inputMode="tel" value={f.phone||''} onChange={e=>setF({...f,phone:e.target.value})}/></label>
      <label><span>Email</span><input type="email" value={f.email||''} onChange={e=>setF({...f,email:e.target.value})}/></label>
      <label><span>Website</span><input value={f.website||''} placeholder="www.example.com" onChange={e=>setF({...f,website:e.target.value})}/></label>
      <label className="wide"><span>Address</span><textarea rows={2} value={f.address||''} onChange={e=>setF({...f,address:e.target.value})}/></label>
      <div className="configSectionTitle wide">Invoice / PDF configuration</div>
      <label><span>Invoice title</span><input value={f.invoiceTitle||''} placeholder="TAX INVOICE" onChange={e=>setF({...f,invoiceTitle:e.target.value})}/></label>
      <label><span>Invoice subtitle</span><input value={f.invoiceSubtitle||''} placeholder="Transparent jewellery price breakup" onChange={e=>setF({...f,invoiceSubtitle:e.target.value})}/></label>
      <label className="wide"><span>Invoice terms</span><textarea rows={2} value={f.invoiceTerms||''} placeholder="Exchange / return / service terms" onChange={e=>setF({...f,invoiceTerms:e.target.value})}/></label>
      <label className="wide"><span>Invoice footer</span><textarea rows={2} value={f.invoiceFooter||''} placeholder="Thank you for choosing us..." onChange={e=>setF({...f,invoiceFooter:e.target.value})}/></label>
      <div className="wide branchConfigActions"><button type="button" className="secondary" onClick={reset}>Clear</button><button className="primary" disabled={saving}>{saving?(editing?'Updating…':'Creating…'):(editing?'Update branch configuration':'Create branch')}</button></div>
    </form>}
   </div>
   <div className="panel">
    <div className="panelHead"><div><span className="eyebrow">LIVE DATABASE</span><h2>{list.length} branches</h2></div><button className="ghost" onClick={load}>Refresh</button></div>
    <Table><thead><tr><th>Branch</th><th>Company</th><th>GSTIN</th><th>Phone</th><th>Status</th><th>Action</th></tr></thead><tbody>{list.map((x:any)=><tr key={x.id}><td><b>{x.name}</b><small className="tableSub">{x.code}</small></td><td>{x.companyName}</td><td>{x.gstin||'—'}</td><td>{x.phone||'—'}</td><td><span className="pill success">{x.active?'ACTIVE':'INACTIVE'}</span></td><td>{canEdit?<button className="ghost" type="button" onClick={()=>edit(x)}>Edit</button>:<span>—</span>}</td></tr>)}</tbody></Table>{!list.length&&<Empty text="No branches yet."/>}
    <div className="adminOnlyNote"><b>Admin only.</b> Branch GSTIN, contact details and invoice/PDF text are stored per branch and are used by billing, invoices and other branch-facing documents.</div>
   </div>
 </div>;
}
