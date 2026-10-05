import {FormEvent, useEffect, useMemo, useState} from 'react';
import {
  api, login, registerCompany, adminReset, otpRequest, otpVerify, me, users, createUser, enableUser,
  approvals, approve, reject, companies, createCompany, branches, createBranch, records, createRecord,
  updateRecord, deleteRecord, dashboard, report, audit, calculateBilling, createBilling, createDomainBilling, invoicePdf,
  domainCustomers, createDomainCustomer, domainCategories, createDomainCategory, domainDesigns, createDomainDesign, salePdf, sendInvoiceWhatsApp,
  domainProducts, createDomainProduct, domainTags, createDomainTag, domainItems, createDomainItem, domainPurities,
  domainGoldRates, createDomainGoldRate, domainStock, domainStockMovements, createDomainTransfer, createDomainStockMovement, domainSuppliers, domainPurchases, domainOldGold, domainRepairs, domainCustomOrders, domainPayments, domainAdvances, phase3Transfer, phase3CompleteTransfer, phase3Purchase, phase3OldGold, phase3RepairUpdate, phase3CustomOrderUpdate, phase3Advance, phase3Outstanding, phase3Report
} from '../api';
import {Role,User,ThemeKey,RecordItem,Company,Branch} from '../shared/types';
import {Table,Empty,Field} from '../shared/ui';
import {DateRangePicker,defaultPresets,toIso} from '../shared/widgets';
import './Reports.css';
export default function ReportsModule({onNotice}:{onNotice:(x:string)=>void}){
 const [type,setType]=useState('SALES');
 const initialRange=defaultPresets().find(p=>p.key==='30d')!.range();
 const [from,setFrom]=useState(initialRange[0]);
 const [to,setTo]=useState(initialRange[1]);
 const [r,setR]=useState<any>(null);
 const [busyId,setBusyId]=useState<number|null>(null);
 const load=()=>phase3Report(type,type==='SALES'?from:undefined,type==='SALES'?to:undefined).then(x=>setR(x.data)).catch(()=>setR(null));
 useEffect(()=>{ void load(); },[type,from,to]);
 const openSalePdf=async(id:number)=>{
   const popup=window.open('about:blank','_blank');
   try{
     setBusyId(id);
     const response=await salePdf(id);
     const url=URL.createObjectURL(new Blob([response.data],{type:'application/pdf'}));
     if(popup){
       popup.location.href=url;
       popup.focus();
     }else{
       const a=document.createElement('a');a.href=url;a.target='_blank';a.rel='noopener';a.download=`invoice-${id}.pdf`;a.click();
     }
     window.setTimeout(()=>URL.revokeObjectURL(url),60000);
   }catch(err:any){
     popup?.close();
     onNotice(err?.response?.data?.message||'Unable to open invoice PDF');
   }finally{setBusyId(null);}
 };
 const sendWhatsApp=async(id:number)=>{
   try{
     setBusyId(id);
     const response=await sendInvoiceWhatsApp(id);
     onNotice(response?.data?.status==='SENT'?'Invoice sent on WhatsApp.':(response?.data?.message||'WhatsApp message submitted.'));
   }catch(err:any){
     onNotice(err?.response?.data?.message||'Unable to send invoice on WhatsApp');
   }finally{setBusyId(null);}
 };
 const money=(v:any)=>`₹${Number(v||0).toLocaleString('en-IN',{minimumFractionDigits:2,maximumFractionDigits:2})}`;
 return <div className="reportPage">
   <div className="reportToolbar">{type==='SALES'&&<DateRangePicker from={from} to={to} maxDate={toIso(new Date())} onChange={(a,b)=>{setFrom(a);setTo(b);}} label="From / To date"/>}{type!=='SALES'&&<small className="muted">Date filtering applies to the Sales report.</small>}</div>
   <div className="reportTabs">{['SALES','STOCK','GOLD','PROFIT','CUSTOMERS'].map(x=><button key={x} className={type===x?'active':''} onClick={()=>setType(x)}>{x}</button>)}</div>
   <div className="kpiGrid"><div className="kpi"><span>Records</span><strong>{r?.count||0}</strong></div><div className="kpi"><span>Amount</span><strong>{money(r?.total)}</strong></div><div className="kpi"><span>In stock</span><strong>{r?.inStock??'—'}</strong></div></div>
   <div className="panel"><div className="panelHead"><div><span className="eyebrow">REPORTS</span><h2>{type} report</h2></div><button className="ghost" onClick={load}>Refresh</button></div>
     <Table><thead><tr><th>Reference</th><th>Status / Customer</th><th>Date / Branch</th><th>Amount / Outstanding</th>{type==='SALES'&&<th>Actions</th>}</tr></thead>
       <tbody>{r?.rows?.map((x:any)=><tr key={x.id}>
         <td><b>{x.invoiceNo||x.tag||x.customerName||x.itemId||x.id}</b>{type==='SALES'&&x.customer&&<small>{x.customer}</small>}</td>
         <td>{x.status||x.customerName||''}{type==='SALES'&&x.paymentStatus&&<small>{x.paymentStatus}</small>}</td>
         <td>{x.date||x.branch||''}{x.date&&x.branch&&<small>{x.branch}</small>}</td>
         <td>{money(x.total??x.outstanding)}</td>
         {type==='SALES'&&<td><div className="reportActions">
           <button className="ghost reportAction" disabled={busyId===x.id} onClick={()=>openSalePdf(Number(x.id))}>🖨 PDF / Print</button>
           <button className="ghost reportAction whatsappAction" disabled={busyId===x.id} onClick={()=>sendWhatsApp(Number(x.id))}>◌ WhatsApp</button>
         </div></td>}
       </tr>)}</tbody>
     </Table>
     {!r?.rows?.length&&<div className="reportEmpty">No records found for this report.</div>}
     {r?.note&&<p className="muted">{r.note}</p>}
   </div>
 </div>
}

