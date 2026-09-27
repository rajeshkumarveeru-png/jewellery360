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
import './Overview.css';
export default function Overview({user,ready}:{user:User;ready:boolean}){
 const [d,setD]=useState<any>(null);
 useEffect(()=>{dashboard().then(r=>setD(r.data)).catch(()=>setD(null));},[ready]);
 if(user.role==='APP_ADMIN'&&!ready) return <div className="heroPanel"><span className="eyebrow">PLATFORM MODE</span><h2>Choose an operating company and branch</h2><p>APP_ADMIN can administer the platform without a tenant context. Select a company and branch above to test company workflows.</p></div>;
 if(!d) return <div className="heroPanel"><h2>Loading dashboard…</h2></div>;
 if(d.platform) return <div className="heroPanel"><h2>{d.message}</h2></div>;
 const cards=[
  ['Gold Sold',`${Number(d.goldSold||0).toFixed(3)} g`],
  ['Silver Sold',`${Number(d.silverSold||0).toFixed(3)} g`],
  ['Items Sold',d.itemsSold||0],
  ['New Customers',d.newCustomers||0],
  ['Outstanding',`₹${Number(d.outstanding||0).toLocaleString('en-IN')}`],
  ['Old Gold Purchased',`₹${Number(d.oldGoldPurchased||0).toLocaleString('en-IN')}`]
 ];
 return <>
  <div className="heroPanel"><div><span className="eyebrow">GOOD MORNING</span><h2>Today's business</h2><p>{user.companyName||'Platform'} · {user.branchName||'Operating branch'}</p></div><strong>₹{Number(d.todaySales||0).toLocaleString('en-IN')}</strong></div>
  <div className="kpiGrid">{cards.map(([a,b])=><div className="kpi" key={String(a)}><span>{String(a)}</span><strong>{String(b)}</strong></div>)}</div>
  <div className="panel"><div className="panelHead"><div><span className="eyebrow">ACTIVITY</span><h2>Recent transactions</h2></div></div>
   {d.recent?.length ? <table><thead><tr><th>Module</th><th>Reference</th><th>Date</th><th>Amount</th></tr></thead><tbody>{d.recent.map((x:any)=><tr key={x.id}><td>{x.module}</td><td><b>{x.title}</b></td><td>{x.date}</td><td>₹{Number(x.amount||0).toLocaleString('en-IN')}</td></tr>)}</tbody></table> : <Empty text="No transactions yet. Start with Billing, Customers or Inventory."/>}
  </div>
 </>;
}
