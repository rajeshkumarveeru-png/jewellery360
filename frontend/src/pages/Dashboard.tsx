import {useEffect,useState} from 'react';
import {companies,branches} from '../api';
import {User,ThemeKey,Company,Branch} from '../shared/types';
import {roleMenus,themeMap} from '../shared/config';
import {NavIcon} from '../shared/ui';
import './Dashboard.css';
import Overview from './Overview';
import CompaniesModule from './Companies';
import BranchesModule from './Branches';
import UsersModule from './Users';
import ApprovalsModule from './Approvals';
import AuditModule from './AuditLogs';
import ReportsModule from './Reports';
import BillingModule from './Billing';
import JewelleryModule from './Jewellery';
import CustomersDomainModule from './Customers';
import GoldRatesModule from './GoldRates';
import InventoryModule from './Inventory';
import PurchasesModule from './Purchases';
import OldGoldModule from './OldGold';
import ServicesModule from './Services';
import PaymentsModule from './Payments';
import WhatsAppModule from './WhatsApp';
import SettingsModule from './Settings';
export default function Dashboard({user,theme,setTheme,logout}:{user:User;theme:ThemeKey;setTheme:(t:ThemeKey)=>void;logout:()=>void}){
 const menu=roleMenus[user.role]||['Overview'];const [tab,setTab]=useState(menu[0]);
 const [contextCompany,setContextCompany]=useState<number|null>(Number(localStorage.getItem('j360_context_company'))||null);
 const [contextBranch,setContextBranch]=useState<number|null>(Number(localStorage.getItem('j360_context_branch'))||null);
 const [companiesData,setCompaniesData]=useState<Company[]>([]);const [branchesData,setBranchesData]=useState<Branch[]>([]);
 const [notice,setNotice]=useState('');const c=themeMap[theme];

 useEffect(()=>{if(user.role==='APP_ADMIN'){companies().then(r=>setCompaniesData(r.data)).catch(()=>{});}else{branches().then(r=>setBranchesData(r.data)).catch(()=>{});}},[user.role]);
 useEffect(()=>{if(user.role==='APP_ADMIN'&&contextCompany)branches().then(r=>setBranchesData(r.data.filter((b:Branch)=>b.companyId===contextCompany))).catch(()=>{});},[user.role,contextCompany]);
 useEffect(()=>{if(!menu.includes(tab))setTab(menu[0]);},[menu,tab]);
 const selectCompany=(id:number|null)=>{setContextCompany(id);setContextBranch(null);if(id)localStorage.setItem('j360_context_company',String(id));else localStorage.removeItem('j360_context_company');localStorage.removeItem('j360_context_branch');};
 const selectBranch=(id:number|null)=>{setContextBranch(id);if(id)localStorage.setItem('j360_context_branch',String(id));else localStorage.removeItem('j360_context_branch');};
 const readyContext=user.role!=='APP_ADMIN'||!!contextCompany&&!!contextBranch;
 const refreshNotice=(s:string)=>{setNotice(s);window.setTimeout(()=>setNotice(''),3500);};

 return <div className="app" style={{background:c.bg,color:c.ink}}>
  <aside><div className="sideBrand"><div className="sideLogo">J360</div><div><b>Jewellery360</b><small>{user.role.replaceAll('_',' ')}</small></div></div>
   <div className="nav">{menu.map((x:string)=><button key={x} className={tab===x?'active':''} onClick={()=>setTab(x)}><NavIcon name={x}/>{x}</button>)}</div>
   <button className="signout" onClick={logout}>↪ Sign out</button>
  </aside>
  <section className="workspace">
   <header><div><span className="eyebrow">JEWELLERY360 / {user.role}</span><h1>{tab}</h1></div>
    <div className="headerTools">
      {user.role==='APP_ADMIN'&&<><select value={contextCompany??''} onChange={e=>selectCompany(Number(e.target.value)||null)}><option value="">Company context</option>{companiesData.map(x=><option key={x.id} value={x.id}>{x.name}</option>)}</select><select value={contextBranch??''} disabled={!contextCompany} onChange={e=>selectBranch(Number(e.target.value)||null)}><option value="">Branch context</option>{branchesData.map(x=><option key={x.id} value={x.id}>{x.name}</option>)}</select></>}
      {user.role!=='APP_ADMIN'&&<span className="contextBadge">{user.companyName||'Platform'} · {user.branchName||'All branches'}</span>}
      <select value={theme} onChange={e=>setTheme(e.target.value as ThemeKey)}><option value="LUXURY_GOLD">Luxury Gold</option><option value="CLASSIC_IVORY">Classic Ivory</option><option value="PREMIUM_DARK">Premium Dark</option><option value="MODERN_LIGHT">Modern Light</option></select>
    </div>
   </header>
   {notice&&<div className="toast">{notice}</div>}
   {!readyContext&&user.role==='APP_ADMIN'&&tab!=='Companies'&&tab!=='Branches'&&tab!=='Users'&&tab!=='Approvals'&&<div className="contextRequired"><b>Select Company + Branch context</b><span>APP_ADMIN must choose an operating company and branch before opening company-scoped workflows.</span></div>}
   {tab==='Overview'&&<Overview user={user} ready={readyContext}/>}
   {tab==='Companies'&&<CompaniesModule user={user} onNotice={refreshNotice}/>}
   {tab==='Branches'&&<BranchesModule user={user} companiesData={companiesData} onNotice={refreshNotice}/>}
   {tab==='Users'&&<UsersModule user={user} onNotice={refreshNotice}/>}
   {tab==='Approvals'&&<ApprovalsModule onNotice={refreshNotice}/>}
   {tab==='Audit Logs'&&<AuditModule/>}
   {tab==='Reports'&&<ReportsModule/>}
   {tab==='Billing'&&<BillingModule ready={readyContext} onNotice={refreshNotice}/>}
   {tab==='Jewellery'&&<JewelleryModule ready={readyContext} onNotice={refreshNotice}/>}
   {tab==='Customers'&&<CustomersDomainModule ready={readyContext} onNotice={refreshNotice}/>}
   {tab==='Gold & Rates'&&<GoldRatesModule ready={readyContext} onNotice={refreshNotice}/>}
   {tab==='Inventory'&&<InventoryModule ready={readyContext} onNotice={refreshNotice}/>}
   {tab==='Purchases'&&<PurchasesModule ready={readyContext} onNotice={refreshNotice}/>}
   {tab==='Old Gold'&&<OldGoldModule ready={readyContext} onNotice={refreshNotice}/>}
   {tab==='Services'&&<ServicesModule ready={readyContext} onNotice={refreshNotice}/>}
   {tab==='Payments'&&<PaymentsModule ready={readyContext} onNotice={refreshNotice}/>}
   {tab==='WhatsApp'&&<WhatsAppModule ready={readyContext} onNotice={refreshNotice}/>}
   {tab==='Settings'&&<SettingsModule user={user} theme={theme} setTheme={setTheme}/>}
  </section>
 </div>
}
