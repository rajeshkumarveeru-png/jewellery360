import {useEffect,useMemo,useState} from 'react';
import {companies,branches,getMenuLayout,globalSearch} from '../api';
import HeaderGadget from '../shared/HeaderGadget';
import AppFooter from '../shared/AppFooter';
import PageHero from '../shared/PageHero';
import CommandPalette from '../shared/CommandPalette';
import type {PaletteItem} from '../shared/CommandPalette';
import {User,ThemeKey,Company,Branch} from '../shared/types';
import {buildMenu,themeMap,PAGE_TABS} from '../shared/config';
import type {MenuLayoutItem} from '../shared/config';
import {PermissionsContext,permissionsFor} from '../shared/permissions';
import {NavIcon} from '../shared/ui';
import {brandInitials,getBrandName,rememberBrandName} from '../shared/brand';
import './Dashboard.css';

import Overview from './Overview';
import CompaniesModule from './Companies';
import BranchesModule from './Branches';
import UsersModule from './Users';
import ApprovalsModule from './Approvals';
import AuditModule from './AuditLogs';
import ReportsModule from './Reports';
import BillingModule from './Billing';
import StockModule from './Stock';
import CustomersModule from './Customers';
import GoldRatesModule from './GoldRates';
import PurchasesModule from './Purchases';
import OldGoldModule from './OldGold';
import ServicesModule from './Services';
import MoneyModule from './Money';
import SettingsModule from './Settings';
import BackupsModule from './Backups';

/** A record another page asked to open (global search, dashboard alerts, customer history ...). */
export type Focus={type:string;id:number;n:number}|null;
const NO_CONTEXT_TABS=['Companies','Branches','Users','Approvals','Backups'];

export default function Dashboard({user,theme,setTheme,logout}:{user:User;theme:ThemeKey;setTheme:(t:ThemeKey)=>void;logout:()=>void}){
 const perms=useMemo(()=>permissionsFor(user),[user.role,JSON.stringify(user.permissions??null)]);
 const [contextCompany,setContextCompany]=useState<number|null>(Number(localStorage.getItem('j360_context_company'))||null);
 const [contextBranch,setContextBranch]=useState<number|null>(Number(localStorage.getItem('j360_context_branch'))||null);
 const [layout,setLayout]=useState<MenuLayoutItem[]|null>(null);
 const menu=useMemo(()=>buildMenu(user,layout),[user.role,JSON.stringify(user.permissions??null),layout]);
 const tabs=menu.map(m=>m.tab);
 const [tab,setTab]=useState<string>(()=>{try{return sessionStorage.getItem('j360_tab')||'';}catch{return '';}});
 const [focus,setFocus]=useState<Focus>(null);
 const [companiesData,setCompaniesData]=useState<Company[]>([]);const [branchesData,setBranchesData]=useState<Branch[]>([]);
 const [notice,setNotice]=useState('');const c=themeMap[theme];
 const [usersFocus,setUsersFocus]=useState(0);
 const companyId=user.role==='APP_ADMIN'?contextCompany:(user.companyId??null);

 // company menu layout (Settings → Menu); without one the default menu is used
 const loadLayout=()=>{if(!companyId){setLayout(null);return;}getMenuLayout(companyId).then(r=>setLayout(Array.isArray(r.data?.items)?r.data.items:null)).catch(()=>setLayout(null));};
 useEffect(loadLayout,[companyId]);
 useEffect(()=>{const h=()=>loadLayout();window.addEventListener('j360-menu-preferences-changed',h);return()=>window.removeEventListener('j360-menu-preferences-changed',h);},[companyId]);
 useEffect(()=>{if(!tabs.includes(tab))setTab(tabs[0]||'Overview');},[tabs.join('|'),tab]);
 useEffect(()=>{try{sessionStorage.setItem('j360_tab',tab);}catch{/* ignore */}},[tab]);

 // Global dynamic branding: the company name from the signed-in account (and Settings) is the brand everywhere.
 const [brand,setBrand]=useState<string>(()=>user.companyName?rememberBrandName(user.companyName):getBrandName());
 useEffect(()=>{
   const name=user.role==='APP_ADMIN'?(companiesData.find(x=>x.id===contextCompany)?.name||user.companyName||''):(user.companyName||'');
   if(name) setBrand(rememberBrandName(name));
 },[user.role,user.companyName,contextCompany,companiesData]);
 const title=menu.find(m=>m.tab===tab)?.label||tab;
 useEffect(()=>{document.title=`${title} · ${brand}`;},[title,brand]);
 useEffect(()=>{const h=()=>setBrand(getBrandName());window.addEventListener('j360-brand-changed',h);return()=>window.removeEventListener('j360-brand-changed',h);},[]);

 // Any page can open another page (and a record on it): window.dispatchEvent(new CustomEvent('j360-navigate',{detail:{tab:'Customers',focus:{type:'CUSTOMER',id:5}}}))
 const go=(t:string,f?:{type:string;id:number})=>{
   if(!tabs.includes(t)){refreshNotice(`${t} is not available for your account.`);return;}
   setTab(t);setFocus(f?{...f,n:Date.now()}:null);
 };
 useEffect(()=>{
   const onNavigate=(e:Event)=>{
     const d=(e as CustomEvent).detail||{};
     if(typeof d.tab!=='string')return;
     go(d.tab,d.focus);
     if(d.tab==='Users'&&d.prefill?.focusCreate)setUsersFocus(n=>n+1);
   };
   window.addEventListener('j360-navigate',onNavigate);
   return()=>window.removeEventListener('j360-navigate',onNavigate);
 },[tabs.join('|')]);

 useEffect(()=>{if(user.role==='APP_ADMIN'){companies().then(r=>setCompaniesData(r.data)).catch(()=>{});}else{branches().then(r=>setBranchesData(r.data)).catch(()=>{});}},[user.role]);
 useEffect(()=>{if(user.role==='APP_ADMIN'&&contextCompany)branches().then(r=>setBranchesData(r.data.filter((b:Branch)=>b.companyId===contextCompany))).catch(()=>{});},[user.role,contextCompany]);
 const selectCompany=(id:number|null)=>{setContextCompany(id);setContextBranch(null);if(id)localStorage.setItem('j360_context_company',String(id));else localStorage.removeItem('j360_context_company');localStorage.removeItem('j360_context_branch');};
 const selectBranch=(id:number|null)=>{setContextBranch(id);if(id)localStorage.setItem('j360_context_branch',String(id));else localStorage.removeItem('j360_context_branch');};
 // the owner can look at one branch or all of them (X-Branch-Id); work that creates bills or stock needs a branch
 const ownerBranches=user.role==='COMPANY_ADMIN'&&branchesData.length>1;
 const readyContext=user.role!=='APP_ADMIN'||!!contextCompany&&!!contextBranch;
 const refreshNotice=(s:string)=>{setNotice(s);window.dispatchEvent(new CustomEvent('j360-notice',{detail:s}));window.setTimeout(()=>setNotice(''),3500);};
 // a different company/branch means different data: remount the page
 const contextKey=`${contextCompany??''}-${contextBranch??''}`;

 const moduleClass=`module-${tab.toLowerCase().replace(/[^a-z0-9]+/g,'-').replace(/^-|-$/g,'')}`;
 const [paletteOpen,setPaletteOpen]=useState(false);
 const [navCollapsed,setNavCollapsed]=useState<boolean>(()=>{try{const v=localStorage.getItem('j360_nav_collapsed');if(v==='1'||v==='0')return v==='1';}catch{/* storage unavailable */}return typeof window!=='undefined'&&window.innerWidth<1600;});
 const toggleNav=()=>setNavCollapsed(c=>{const n=!c;try{localStorage.setItem('j360_nav_collapsed',n?'1':'0');}catch{/* ignore */}return n;});
 useEffect(()=>{
   const onKey=(e:KeyboardEvent)=>{if((e.ctrlKey||e.metaKey)&&e.key.toLowerCase()==='k'){e.preventDefault();setPaletteOpen(o=>!o);}};
   window.addEventListener('keydown',onKey);return()=>window.removeEventListener('keydown',onKey);
 },[]);
 useEffect(()=>{document.querySelector('.workspaceBody')?.scrollTo({top:0});},[tab]);
 const themeOrder:ThemeKey[]=['LUXURY_GOLD','CLASSIC_IVORY','PREMIUM_DARK','MODERN_LIGHT'];
 const has=(t:string)=>tabs.includes(t);
 const paletteItems:PaletteItem[]=[
   ...menu.map(m=>({id:`go-${m.tab}`,label:m.label,hint:'Open page',group:'Go to',run:()=>{setFocus(null);setTab(m.tab);}})),
   ...(has('Billing')&&!perms.isViewer?[{id:'a-bill',label:'Start a new bill',hint:'Billing counter',group:'Action',run:()=>go('Billing',{type:'NEW_BILL',id:0})}]:[]),
   ...(has('Gold Rates')&&perms.canManage?[{id:'a-rate',label:'Update today\'s gold rates',hint:'Gold Rates',group:'Action',run:()=>setTab('Gold Rates')}]:[]),
   ...(has('Stock')&&perms.canManage?[{id:'a-piece',label:'Add a piece to stock',hint:'Stock',group:'Action',run:()=>go('Stock',{type:'NEW_ITEM',id:0})}]:[]),
   ...(has('Customers')&&!perms.isViewer?[{id:'a-cust',label:'Add a customer',hint:'Customers',group:'Action',run:()=>go('Customers',{type:'NEW_CUSTOMER',id:0})}]:[]),
   ...(has('Money')&&!perms.isViewer?[{id:'a-close',label:'Close the day (cash count)',hint:'Money',group:'Action',run:()=>go('Money',{type:'DAY_CLOSE',id:0})}]:[]),
   ...(has('Money')&&!perms.isViewer?[{id:'a-exp',label:'Add an expense',hint:'Money → Expenses',group:'Action',run:()=>go('Money',{type:'NEW_EXPENSE',id:0})}]:[]),
   ...(has('Users')?[{id:'a-user',label:'Create a user',hint:'Users',group:'Action',run:()=>{setTab('Users');setUsersFocus(n=>n+1);}}]:[]),
   {id:'a-theme',label:'Switch theme',hint:`Now: ${theme.replace('_',' ').toLowerCase()}`,group:'Action',run:()=>setTheme(themeOrder[(themeOrder.indexOf(theme)+1)%themeOrder.length])}
 ];
 // records: customers, pieces, bills, orders and repairs matching what was typed
 const searchRecords=async(q:string):Promise<PaletteItem[]>=>{
   if(!readyContext||q.trim().length<2)return[];
   try{
     const r=await globalSearch(q.trim());
     const rows:any[]=Array.isArray(r.data?.results)?r.data.results:[];
     return rows.map(h=>({h,t:PAGE_TABS[h.page]||h.page})).filter(x=>tabs.includes(x.t))
       .map(({h,t})=>({id:`s-${h.type}-${h.id}`,label:h.title,hint:h.subtitle||'',group:h.type.charAt(0)+h.type.slice(1).toLowerCase(),run:()=>go(t,{type:h.type,id:h.id})}));
   }catch{return[];}
 };
 const groups=menu.reduce<{group:string;items:typeof menu}[]>((acc,m)=>{const g=acc.find(x=>x.group===m.group);if(g)g.items.push(m);else acc.push({group:m.group,items:[m]});return acc;},[]);
 const needsContext=!readyContext&&user.role==='APP_ADMIN'&&!NO_CONTEXT_TABS.includes(tab);
 const props={ready:readyContext,onNotice:refreshNotice,focus,go};
 return <PermissionsContext.Provider value={perms}><div className={`app theme-${theme.toLowerCase()} ${moduleClass}${navCollapsed?' nav-collapsed':''}`} style={{background:c.bg,color:c.ink}}>
  <aside><div className="sideBrand"><div className="sideLogo" aria-hidden="true">{brandInitials(brand)}</div><div><b title={brand}>{brand}</b><small>{user.role.replaceAll('_',' ')}</small></div></div>
   <nav className="nav" aria-label="Main">{groups.map(g=><div key={g.group} className="navGroup"><span className="navGroupLabel">{g.group}</span>{g.items.map(x=><button key={x.tab} className={tab===x.tab?'active':''} onClick={()=>{setFocus(null);setTab(x.tab);}} title={x.label} aria-label={x.label} aria-current={tab===x.tab?'page':undefined}><NavIcon name={x.tab}/><span className="navText">{x.label}</span></button>)}</div>)}</nav>
   {(perms.isAdmin||perms.canEditSettings)&&has('Settings')&&<button className="signout" onClick={()=>{setTab('Settings');window.setTimeout(()=>document.querySelector('.menuPreferencesPanel')?.scrollIntoView({behavior:'smooth',block:'start'}),350);}} title="Arrange the menu (drag and drop)" aria-label="Arrange the menu"><span aria-hidden="true">⠿</span><span className="navText">Arrange menu</span></button>}
   <button className="signout" onClick={logout} style={{marginTop:(perms.isAdmin||perms.canEditSettings)&&has('Settings')?0:undefined}} title="Sign out" aria-label="Sign out"><span aria-hidden="true">↪</span><span className="navText">Sign out</span></button>
  </aside>
  <section className={`workspace ${moduleClass}`}>
   <header><button type="button" className="menuToggle" onClick={toggleNav} aria-pressed={navCollapsed} aria-label={navCollapsed?'Expand menu':'Collapse menu'} title={navCollapsed?'Expand menu':'Collapse menu'}><svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" aria-hidden="true"><path d="M4 6h16M4 12h16M4 18h10"/></svg></button><div className="headTitle"><span className="eyebrow">{brand.toUpperCase()}</span><h1>{title}</h1></div>
    <button type="button" className="headSearch" onClick={()=>setPaletteOpen(true)} aria-label="Search (Ctrl+K)" title="Search customers, pieces, bills… (Ctrl+K)"><svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" aria-hidden="true"><circle cx="11" cy="11" r="7"/><path d="M20 20l-3.5-3.5"/></svg></button>
    <div className="headerTools"><HeaderGadget ready={readyContext} canOpenRates={has('Gold Rates')} onOpenRates={()=>setTab('Gold Rates')}/>
      {user.role==='APP_ADMIN'&&<><select className="ctxSelect" aria-label="Company" value={contextCompany??''} onChange={e=>selectCompany(Number(e.target.value)||null)}><option value="">Select company</option>{companiesData.map(x=><option key={x.id} value={x.id}>{x.name}</option>)}</select><select className="ctxSelect" aria-label="Branch" value={contextBranch??''} disabled={!contextCompany} onChange={e=>selectBranch(Number(e.target.value)||null)}><option value="">Select branch</option>{branchesData.map(x=><option key={x.id} value={x.id}>{x.name}</option>)}</select></>}
      {ownerBranches&&<select className="ctxSelect" aria-label="Branch" value={contextBranch??''} onChange={e=>selectBranch(Number(e.target.value)||null)}><option value="">{user.branchName||'My branch'}</option>{branchesData.filter(b=>b.id!==user.branchId).map(x=><option key={x.id} value={x.id}>{x.name}</option>)}</select>}
      {user.role!=='APP_ADMIN'&&!ownerBranches&&<span className="contextBadge">{user.branchName||'All branches'}</span>}
      <select className="themeSelect" aria-label="Theme" value={theme} onChange={e=>setTheme(e.target.value as ThemeKey)}><option value="LUXURY_GOLD">Luxury Gold</option><option value="CLASSIC_IVORY">Classic Ivory</option><option value="PREMIUM_DARK">Premium Dark</option><option value="MODERN_LIGHT">Modern Light</option></select>
    </div>
   </header>
   <main className="workspaceBody" id="main">
   {notice&&<div className="toast" role="status">{notice}</div>}
   {needsContext&&<div className="contextRequired"><b>Choose a company and branch</b><span>Pick them at the top right to work with that shop's data.</span></div>}
   {tab!=='Overview'&&tab!=='Billing'&&tab!=='Users'&&<PageHero tab={tab} title={title} onGo={t=>go(t,t==='Billing'?{type:'NEW_BILL',id:0}:undefined)} canGo={has}/>}
   <div key={contextKey} className="pageBody">
   {tab==='Overview'&&<Overview user={user} {...props}/>}
   {tab==='Companies'&&<CompaniesModule user={user} onNotice={refreshNotice}/>}
   {tab==='Branches'&&<BranchesModule user={user} companiesData={companiesData} onNotice={refreshNotice}/>}
   {tab==='Users'&&<UsersModule user={user} onNotice={refreshNotice} focusCreate={usersFocus}/>}
   {tab==='Approvals'&&<ApprovalsModule onNotice={refreshNotice}/>}
   {tab==='Backups'&&<BackupsModule onNotice={refreshNotice}/>}
   {tab==='Audit Logs'&&<AuditModule/>}
   {!needsContext&&<>
   {tab==='Reports'&&<ReportsModule {...props}/>}
   {tab==='Billing'&&<BillingModule {...props}/>}
   {tab==='Stock'&&<StockModule {...props} branches={branchesData}/>}
   {tab==='Customers'&&<CustomersModule {...props}/>}
   {tab==='Gold Rates'&&<GoldRatesModule {...props}/>}
   {tab==='Purchases'&&<PurchasesModule {...props}/>}
   {tab==='Old Gold'&&<OldGoldModule {...props}/>}
   {tab==='Services'&&<ServicesModule {...props}/>}
   {tab==='Money'&&<MoneyModule {...props}/>}
   </>}
   {tab==='Settings'&&<SettingsModule user={user} theme={theme} setTheme={setTheme}/>}
   </div>
   </main>
   <AppFooter brand={brand} user={user} onPalette={()=>setPaletteOpen(true)}/>
  </section>
  <CommandPalette open={paletteOpen} items={paletteItems} search={searchRecords} onClose={()=>setPaletteOpen(false)}/>
 </div></PermissionsContext.Provider>
}

/** Props every business page receives from the shell. */
export type PageProps={ready:boolean;onNotice:(s:string)=>void;focus:Focus;go:(tab:string,focus?:{type:string;id:number})=>void};
