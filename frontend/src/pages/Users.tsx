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
import './Users.css';
export default function UsersModule({user,onNotice}:{user:User;onNotice:(x:string)=>void}){
 const [list,setList]=useState<User[]>([]);
 const [f,setF]=useState<any>({role:'CASHIER'});
 const [search,setSearch]=useState('');
 const [loading,setLoading]=useState(false);
 const load=async(q=search)=>{setLoading(true);try{const r=await users(q);setList(Array.isArray(r.data)?r.data:[]);}catch(err:any){setList([]);onNotice(err?.response?.data?.message||'Unable to load users');}finally{setLoading(false);}};
 useEffect(()=>{const t=window.setTimeout(()=>void load(search),250);return()=>window.clearTimeout(t);},[search]);
 const save=async(e:FormEvent)=>{e.preventDefault();try{await createUser({...f,branchId:Number(f.branchId)});setF({role:'CASHIER'});await load(search);onNotice('User created and sent for approval.');}catch(err:any){onNotice(err?.response?.data?.message||'Unable to create user');}};
 const can=user.role==='APP_ADMIN'||user.role==='COMPANY_ADMIN';
 return <div className="page-users moduleGrid">
   <div className="panel">
     <span className="eyebrow">IDENTITY</span><h2>Users</h2>
     <p className="usersIntro">All active users are shown here. Search by username, email, phone, role, company or branch.</p>
     {can&&<form className="formGrid" onSubmit={save}>
       <input placeholder="Username" required value={f.username||''} onChange={e=>setF({...f,username:e.target.value})}/>
       <input placeholder="Email" type="email" required value={f.email||''} onChange={e=>setF({...f,email:e.target.value})}/>
       <input placeholder="Phone" value={f.phone||''} onChange={e=>setF({...f,phone:e.target.value})}/>
       <input placeholder="Temporary password" type="password" required value={f.password||''} onChange={e=>setF({...f,password:e.target.value})}/>
       <select value={f.role} onChange={e=>setF({...f,role:e.target.value})}>{['MANAGER','CASHIER','SALESMAN','INVENTORY_MANAGER','ACCOUNTANT','VIEWER'].map(r=><option key={r}>{r}</option>)}</select>
       <input placeholder="Branch ID" required value={f.branchId||''} onChange={e=>setF({...f,branchId:e.target.value})}/>
       <button className="primary">Create user</button>
     </form>}
   </div>
   <div className="panel">
     <div className="panelHead usersListHead"><div><span className="eyebrow">DIRECTORY</span><h2>All users</h2></div><span className="userCount">{list.length} user{list.length===1?'':'s'}</span></div>
     <div className="userSearch"><input value={search} onChange={e=>setSearch(e.target.value)} placeholder="Search username, email, phone, role, company or branch…" aria-label="Search users"/><button className="ghost" type="button" onClick={()=>setSearch('')} disabled={!search}>Clear</button></div>
     {loading?<div className="empty"><div>◇</div><b>Loading users…</b></div>:<Table><thead><tr><th>User</th><th>Email</th><th>Phone</th><th>Role</th><th>Company</th><th>Branch</th><th>Status</th><th/></tr></thead><tbody>{list.map(x=><tr key={x.id}><td><b>{x.username}</b></td><td>{x.email||'—'}</td><td>{x.phone||'—'}</td><td>{x.role}</td><td>{x.companyName||'Platform'}</td><td>{x.branchName||'—'}</td><td><span className={x.enabled?'pill success':'pill'}>{x.enabled?'ACTIVE':'PENDING'}</span></td><td>{can&&!x.enabled&&x.role!=='APP_ADMIN'&&<button className="ghost" onClick={async()=>{try{await enableUser(x.id);await load(search);onNotice('User enabled.');}catch(err:any){onNotice(err?.response?.data?.message||'Unable to enable user')}}}>Enable</button>}</td></tr>)}</tbody></Table>}
     {!loading&&!list.length&&<Empty text={search?'No users match your search.':'No users found.'}/>}
   </div>
 </div>
}
