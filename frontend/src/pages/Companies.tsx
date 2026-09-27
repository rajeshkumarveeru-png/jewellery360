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
import './Companies.css';
export default function CompaniesModule({user,onNotice}:{user:User;onNotice:(x:string)=>void}){
 const [list,setList]=useState<Company[]>([]);const [f,setF]=useState<any>({});
 const load=()=>companies().then(r=>setList(r.data)).catch(()=>setList([]));useEffect(()=>{ void load(); },[]);
 const save=async(e:FormEvent)=>{e.preventDefault();try{await createCompany(f);setF({});await load();onNotice('Company created.');}catch(err:any){onNotice(err?.response?.data?.message||'Unable to create company');}};
 return <div className="moduleGrid"><div className="panel"><span className="eyebrow">PLATFORM ADMINISTRATION</span><h2>Companies</h2><form className="formGrid" onSubmit={save}><input placeholder="Company name" required value={f.name||''} onChange={e=>setF({...f,name:e.target.value})}/><input placeholder="Company code" value={f.code||''} onChange={e=>setF({...f,code:e.target.value})}/><input placeholder="GSTIN" value={f.gstin||''} onChange={e=>setF({...f,gstin:e.target.value})}/><input placeholder="Phone" value={f.phone||''} onChange={e=>setF({...f,phone:e.target.value})}/><input placeholder="Email" value={f.email||''} onChange={e=>setF({...f,email:e.target.value})}/><textarea placeholder="Address" value={f.address||''} onChange={e=>setF({...f,address:e.target.value})}/><button className="primary">Create company</button></form></div><div className="panel"><Table><thead><tr><th>Company</th><th>Code</th><th>GSTIN</th><th>Status</th></tr></thead><tbody>{list.map(x=><tr key={x.id}><td><b>{x.name}</b></td><td>{x.code}</td><td>{x.gstin||'—'}</td><td><span className="pill success">{x.active?'ACTIVE':'INACTIVE'}</span></td></tr>)}</tbody></Table>{!list.length&&<Empty text="No companies yet."/ >}</div></div>
}

