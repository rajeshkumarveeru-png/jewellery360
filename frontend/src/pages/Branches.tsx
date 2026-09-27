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
import './Branches.css';
export default function BranchesModule({user,companiesData,onNotice}:{user:User;companiesData:Company[];onNotice:(x:string)=>void}){
 const [list,setList]=useState<Branch[]>([]);const [f,setF]=useState<any>({companyId:user.companyId||companiesData[0]?.id});
 const load=()=>branches().then(r=>setList(r.data)).catch(()=>setList([]));useEffect(()=>{ void load(); },[user.companyId]);
 const save=async(e:FormEvent)=>{e.preventDefault();try{await createBranch({...f,companyId:user.role==='APP_ADMIN'?Number(f.companyId):undefined});setF({companyId:user.companyId||companiesData[0]?.id});await load();onNotice('Branch created.');}catch(err:any){onNotice(err?.response?.data?.message||'Unable to create branch');}};
 const can=user.role==='APP_ADMIN'||user.role==='COMPANY_ADMIN';
 return <div className="moduleGrid"><div className="panel"><span className="eyebrow">BRANCH CONTROL</span><h2>Branches</h2>{can&&<form className="formGrid" onSubmit={save}>{user.role==='APP_ADMIN'&&<select required value={f.companyId||''} onChange={e=>setF({...f,companyId:Number(e.target.value)})}><option value="">Company</option>{companiesData.map(c=><option key={c.id} value={c.id}>{c.name}</option>)}</select>}<input placeholder="Branch name" required value={f.name||''} onChange={e=>setF({...f,name:e.target.value})}/><input placeholder="Branch code" required value={f.code||''} onChange={e=>setF({...f,code:e.target.value})}/><input placeholder="Phone" value={f.phone||''} onChange={e=>setF({...f,phone:e.target.value})}/><input placeholder="Address" value={f.address||''} onChange={e=>setF({...f,address:e.target.value})}/><button className="primary">Create branch</button></form>}</div><div className="panel"><Table><thead><tr><th>Branch</th><th>Company</th><th>Code</th><th>Status</th></tr></thead><tbody>{list.map(x=><tr key={x.id}><td><b>{x.name}</b></td><td>{x.companyName}</td><td>{x.code}</td><td><span className="pill success">{x.active?'ACTIVE':'INACTIVE'}</span></td></tr>)}</tbody></Table></div></div>
}

