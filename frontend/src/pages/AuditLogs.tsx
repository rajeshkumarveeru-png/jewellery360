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
import './AuditLogs.css';
export default function AuditModule(){const [list,setList]=useState<any[]>([]);const load=()=>audit().then(r=>setList(r.data)).catch(()=>setList([]));useEffect(()=>{ void load(); },[]);return <div className="panel"><div className="panelHead"><div><span className="eyebrow">SECURITY</span><h2>Audit logs</h2></div><button className="ghost" onClick={load}>Refresh</button></div><Table><thead><tr><th>Time</th><th>User</th><th>Action</th><th>Entity</th><th>Company</th><th>Branch</th></tr></thead><tbody>{list.map(x=><tr key={x.id}><td>{new Date(x.createdAt).toLocaleString()}</td><td>{x.user}</td><td>{x.action}</td><td>{x.entityType} #{x.entityId}</td><td>{x.company||'Platform'}</td><td>{x.branch||'—'}</td></tr>)}</tbody></Table>{!list.length&&<Empty text="No audit events yet."/ >}</div>}

