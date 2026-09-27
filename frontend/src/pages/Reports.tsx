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
import './Reports.css';
export default function ReportsModule(){const [type,setType]=useState('SALES');const [r,setR]=useState<any>(null);const load=()=>phase3Report(type).then(x=>setR(x.data)).catch(()=>setR(null));useEffect(()=>{ void load(); },[type]);return <div className="reportPage"><div className="reportTabs">{['SALES','STOCK','GOLD','PROFIT','CUSTOMERS'].map(x=><button key={x} className={type===x?'active':''} onClick={()=>setType(x)}>{x}</button>)}</div><div className="kpiGrid"><div className="kpi"><span>Records</span><strong>{r?.count||0}</strong></div><div className="kpi"><span>Amount</span><strong>₹{Number(r?.total||0).toLocaleString('en-IN')}</strong></div><div className="kpi"><span>In stock</span><strong>{r?.inStock??'—'}</strong></div></div><div className="panel"><div className="panelHead"><h2>{type} report</h2><button className="ghost" onClick={load}>Refresh</button></div><Table><thead><tr><th>Reference</th><th>Status / Customer</th><th>Date / Branch</th><th>Amount / Outstanding</th></tr></thead><tbody>{r?.rows?.map((x:any)=><tr key={x.id}><td><b>{x.invoiceNo||x.tag||x.customerName||x.itemId||x.id}</b></td><td>{x.status||x.customerName||''}</td><td>{x.date||x.branch||''}</td><td>₹{Number(x.total??x.outstanding??0).toLocaleString('en-IN')}</td></tr>)}</tbody></Table>{r?.note&&<p className="muted">{r.note}</p>}</div></div>}

