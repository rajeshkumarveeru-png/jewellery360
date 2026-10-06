import {FormEvent, useEffect, useMemo, useState} from 'react';
import {usePermissions} from '../shared/permissions';
import {
  api, login, registerCompany, adminReset, otpRequest, otpVerify, me, users, createUser, enableUser,
  approvals, approve, reject, companies, createCompany, branches, createBranch, records, createRecord,
  updateRecord, deleteRecord, dashboard, report, audit, calculateBilling, createBilling, createDomainBilling, invoicePdf,
  domainCustomers, createDomainCustomer, domainCategories, createDomainCategory, domainDesigns, createDomainDesign,
  domainProducts, createDomainProduct, domainTags, createDomainTag, domainItems, createDomainItem, domainPurities,
  domainGoldRates, createDomainGoldRate, domainStock, domainStockMovements, createDomainTransfer, createDomainStockMovement, domainSuppliers, domainPurchases, domainOldGold, domainRepairs, domainCustomOrders, domainPayments, domainAdvances, phase3Transfer, phase3CompleteTransfer, phase3Purchase, phase3OldGold, phase3RepairUpdate, phase3CustomOrderUpdate, phase3Advance, phase3Outstanding, phase3Report
} from '../api';
import {Role,User,ThemeKey,RecordItem,Company,Branch} from '../shared/types';
import {Table,Empty,Field} from '../shared/ui';
import FormDrawer from '../shared/FormDrawer';

async function downloadPdf(id:number,onNotice:(x:string)=>void){
 try {
  const response = await invoicePdf(id);
  const blob = new Blob([response.data], {type:'application/pdf'});
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a'); a.href=url; a.download=`invoice-${id}.pdf`; a.click();
  URL.revokeObjectURL(url);
 } catch(err:any) { onNotice(err?.response?.data?.message || 'Unable to download invoice PDF'); }
}
import {moduleConfig} from '../shared/config';
export default function GenericModule({module,ready,onNotice}:{module:string;ready:boolean;onNotice:(x:string)=>void}){
  const perms=usePermissions();
 const cfg=moduleConfig[module]||{subtypes:['RECORD'],fields:[{key:'title',label:'Title',required:true},{key:'amount',label:'Amount',type:'number'},{key:'quantity',label:'Quantity',type:'number'},{key:'notes',label:'Notes'}]};
 const [list,setList]=useState<RecordItem[]>([]);const [f,setF]=useState<any>({subtype:cfg.subtypes[0],status:'ACTIVE'});const [editing,setEditing]=useState<number|null>(null);
 const load=()=>{if(!ready){setList([]);return;}records(module).then(r=>setList(r.data)).catch(err=>onNotice(err?.response?.data?.message||`Unable to load ${module}`));};
 useEffect(load,[module,ready]);
 const save=async(e:FormEvent)=>{e.preventDefault();if(!ready){onNotice('Select company and branch context first.');return;}
  const amount=Number(f.amount||f.makingCharge||f.exchangeValue||f.charges||f.purchaseRate||0);
  const quantity=Number(f.quantity||f.netWeight||f.grossWeight||1);
  const title=f.tag||f.invoiceNo||f.purchaseNo||f.receiptNo||f.customerName||f.serviceNo||f.key||`${module}-${Date.now()}`;
  const payload={...f};
  try{
   const body={module,subtype:f.subtype,title,status:f.status||'ACTIVE',amount,quantity,date:f.date||f.rateDate||f.receivedDate||new Date().toISOString().slice(0,10),notes:f.notes||f.description||'',payload:JSON.stringify(payload)};
   if(editing) await updateRecord(editing,body); else await createRecord(body);
   setF({subtype:cfg.subtypes[0],status:'ACTIVE'});setEditing(null);await load();onNotice(`${module} saved successfully.`);
  }catch(err:any){onNotice(err?.response?.data?.message||`Unable to save ${module}`);}
 };
 const edit=(r:RecordItem)=>{setEditing(r.id);setF({...r.payload,subtype:r.subtype,status:r.status,amount:r.amount,quantity:r.quantity});};
 const remove=async(id:number)=>{try{await deleteRecord(id);await load();onNotice('Record cancelled.');}catch(err:any){onNotice(err?.response?.data?.message||'Unable to update record');}};
 return <div className="moduleGrid hasDrawer"><FormDrawer needsManage={!['Payments','Services','Old Gold','Customers','WhatsApp'].includes(module)} label="New record" title="Create record" editTitle="Update record" editing={!!editing} onCancel={()=>{setEditing(null);setF({subtype:cfg.subtypes[0],status:'ACTIVE'})}}><div className="panel"><div className="panelHead"><div><span className="eyebrow">{module.toUpperCase()}</span><h2>{editing?'Update':'Create'} {module}</h2></div><span className={ready?'pill success':'pill'}>{ready?'LIVE DATABASE':'CONTEXT REQUIRED'}</span></div>{ready&&<form className="formGrid" onSubmit={save}><select value={f.subtype} onChange={e=>setF({...f,subtype:e.target.value})}>{cfg.subtypes.map(s=><option key={s}>{s}</option>)}</select>{cfg.fields.map(x=><input key={x.key} required={x.required} type={x.type||'text'} placeholder={x.label} value={f[x.key]??''} onChange={e=>setF({...f,[x.key]:e.target.value})}/>)}<select value={f.status} onChange={e=>setF({...f,status:e.target.value})}><option>ACTIVE</option><option>PENDING</option><option>COMPLETED</option><option>DUE</option><option>CANCELLED</option></select><button className="primary">{editing?'Update':'Save'} {module}</button>{editing&&<button type="button" className="ghost" onClick={()=>{setEditing(null);setF({subtype:cfg.subtypes[0],status:'ACTIVE'})}}>Cancel edit</button>}</form>}</div></FormDrawer><div className="panel"><div className="panelHead"><div><span className="eyebrow">POSTGRESQL</span><h2>{list.length} records</h2></div><button className="ghost" onClick={load}>Refresh</button></div><Table><thead><tr><th>Reference</th><th>Status</th><th>Date</th><th>Qty</th><th>Amount</th><th/></tr></thead><tbody>{list.map(r=><tr key={r.id}><td><b>{r.title}</b><small>{r.notes}</small></td><td><span className="pill">{r.status}</span></td><td>{r.date}</td><td>{Number(r.quantity).toLocaleString()}</td><td>₹{Number(r.amount||0).toLocaleString('en-IN')}</td><td>{perms.canManage&&<><button className="ghost" onClick={()=>edit(r)}>Edit</button><button className="ghost danger" onClick={()=>remove(r.id)}>Cancel</button></>}{module==='Billing'&&<button className="ghost" onClick={()=>downloadPdf(r.id,onNotice)}>PDF</button>}</td></tr>)}</tbody></Table>{!list.length&&<Empty text={`No ${module.toLowerCase()} records yet.`}/>}</div></div>
}

