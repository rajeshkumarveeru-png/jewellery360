import {FormEvent, useEffect, useMemo, useState} from 'react';
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
import './Customers.css';
export default function CustomersDomainModule({ready,onNotice}:{ready:boolean;onNotice:(x:string)=>void}){
 const [list,setList]=useState<any[]>([]); const [f,setF]=useState<any>({});
 const load=()=>{if(!ready){setList([]);return;}domainCustomers().then(r=>setList(r.data)).catch(e=>onNotice(e?.response?.data?.message||'Unable to load customers'));};
 useEffect(load,[ready]);
 const save=async(e:FormEvent)=>{e.preventDefault();if(!ready){onNotice('Select company and branch context first.');return;}try{await createDomainCustomer(f);setF({});await load();onNotice('Customer created in PostgreSQL.');}catch(e:any){onNotice(e?.response?.data?.message||'Unable to create customer');}};
 return <div className="moduleGrid"><div className="panel"><span className="eyebrow">CUSTOMER MASTER</span><h2>Customers</h2><form className="formGrid" onSubmit={save}><input required placeholder="Customer name" value={f.name||''} onChange={e=>setF({...f,name:e.target.value})}/><input placeholder="Phone" value={f.phone||''} onChange={e=>setF({...f,phone:e.target.value})}/><input type="email" placeholder="Email" value={f.email||''} onChange={e=>setF({...f,email:e.target.value})}/><input placeholder="GSTIN" value={f.gstin||''} onChange={e=>setF({...f,gstin:e.target.value})}/><textarea placeholder="Address" value={f.address||''} onChange={e=>setF({...f,address:e.target.value})}/><textarea placeholder="Notes" value={f.notes||''} onChange={e=>setF({...f,notes:e.target.value})}/><button className="primary">Create customer</button></form></div><div className="panel"><div className="panelHead"><div><span className="eyebrow">LIVE DATABASE</span><h2>{list.length} customers</h2></div><button className="ghost" onClick={load}>Refresh</button></div><Table><thead><tr><th>Name</th><th>Phone</th><th>Email</th><th>GSTIN</th><th>Status</th></tr></thead><tbody>{list.map(x=><tr key={x.id}><td><b>{x.name}</b></td><td>{x.phone||'—'}</td><td>{x.email||'—'}</td><td>{x.gstin||'—'}</td><td><span className={x.active?'pill success':'pill'}>{x.active?'ACTIVE':'INACTIVE'}</span></td></tr>)}</tbody></Table>{!list.length&&<Empty text="No customers yet."/>}</div></div>
}

