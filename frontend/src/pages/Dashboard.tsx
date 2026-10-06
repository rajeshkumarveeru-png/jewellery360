import {useEffect,useMemo,useState} from 'react';
import {companies,branches,getUserMenuPreferences} from '../api';
import HeaderGadget from '../shared/HeaderGadget';
import AppFooter from '../shared/AppFooter';
import PageHero from '../shared/PageHero';
import CommandPalette from '../shared/CommandPalette';
import type {PaletteItem} from '../shared/CommandPalette';
import {User,ThemeKey,Company,Branch} from '../shared/types';
import {allowedMenu,themeMap} from '../shared/config';
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
 const perms=useMemo(()=>permissionsFor(user),[user.role,JSON.stringify(user.permissions??null)]);
 const [contextCompany,setContextCompany]=useState<number|null>(Number(localStorage.getItem('j360_context_company'))||null);
 const [menu,setMenu]=useState<string[]>(()=>allowedMenu(user)); const [tab,setTab]=useState(menu[0]);
 const loadUserMenu=()=>{
   const companyId=user.role==='APP_ADMIN'?contextCompany:(user.companyId??null);
   if(!companyId){setMenu(allowedMenu(user));return;}
   getUserMenuPreferences(companyId).then(r=>{
     const saved=Array.isArray(r.data?.menu)?r.data.menu:[];
     const allowed=allowedMenu(user);
     const ordered=saved.filter((x:string)=>allowed.includes(x));
     setMenu(ordered.length?ordered:allowed);
   }).catch(()=>setMenu(allowedMenu(user)));
 };
 const [contextBranch,setContextBranch]=useState<number|null>(Number(localStorage.getItem('j360_context_branch'))||null);
 const [companiesData,setCompaniesData]=useState<Company[]>([]);const [branchesData,setBranchesData]=useState<Branch[]>([]);
 const [notice,setNotice]=useState('');const c=themeMap[theme];
 const [usersFocus,setUsersFocus]=useState(0);
 // Global dynamic branding: the company name from the signed-in account (and Settings) is the brand everywhere.
 const [brand,setBrand]=useState<string>(()=>user.companyName?rememberBrandName(user.companyName):getBrandName());
 useEffect(()=>{
   const name=user.role==='APP_ADMIN'
     ? (companiesData.find(x=>x.id===contextCompany)?.name||user.companyName||'')
     : (user.companyName||'');
   if(name) setBrand(rememberBrandName(name));
 },[user.role,user.companyName,contextCompany,companiesData]);
 useEffect(()=>{document.title=`${tab} · ${brand}`;},[tab,brand]);
 useEffect(()=>{const h=()=>setBrand(getBrandName());window.addEventListener('j360-brand-changed',h);return()=>window.removeEventListener('j360-brand-changed',h);},[]);
 // Other pages (Overview quick actions) can ask the shell to open a tab: window.dispatchEvent(new CustomEvent('j360-navigate',{detail:{tab:'Users',prefill:{focusCreate:true}}}))
 useEffect(()=>{
   const onNavigate=(e:Event)=>{
     const d=(e as CustomEvent).detail||{};
     if(typeof d.tab==='string'&&menu.includes(d.tab)){setTab(d.tab);if(d.tab==='Users'&&d.prefill?.focusCreate)setUsersFocus(n=>n+1);}
   };
   window.addEventListener('j360-navigate',onNavigate);
   return()=>window.removeEventListener('j360-navigate',onNavigate);
 },[menu]);

 useEffect(()=>{if(user.role==='APP_ADMIN'){companies().then(r=>setCompaniesData(r.data)).catch(()=>{});}else{branches().then(r=>setBranchesData(r.data)).catch(()=>{});}},[user.role]);
 useEffect(()=>{if(user.role==='APP_ADMIN'&&contextCompany)branches().then(r=>setBranchesData(r.data.filter((b:Branch)=>b.companyId===contextCompany))).catch(()=>{});},[user.role,contextCompany]);
 useEffect(()=>{ loadUserMenu(); },[user.id,user.role,contextCompany,JSON.stringify(user.permissions??null)]);
 useEffect(()=>{
   const refreshMenu=()=>loadUserMenu();
   window.addEventListener('j360-menu-preferences-changed',refreshMenu);
   return()=>window.removeEventListener('j360-menu-preferences-changed',refreshMenu);
 },[user.id,user.role,contextCompany]);
 useEffect(()=>{if(!menu.includes(tab))setTab(menu[0]||'Overview');},[menu,tab]);
 useEffect(()=>{
   const id=window.requestAnimationFrame(()=>{
     const root=document.querySelector('.workspace');
     const active=root?.querySelector<HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement>('input:not([type=hidden]):not([disabled]), select:not([disabled]), textarea:not([disabled])');
     if(active && active.offsetParent!==null && document.activeElement===document.body) active.focus();
   });
   return()=>window.cancelAnimationFrame(id);
 },[tab]);
 const selectCompany=(id:number|null)=>{setContextCompany(id);setContextBranch(null);if(id)localStorage.setItem('j360_context_company',String(id));else localStorage.removeItem('j360_context_company');localStorage.removeItem('j360_context_branch');};
 const selectBranch=(id:number|null)=>{setContextBranch(id);if(id)localStorage.setItem('j360_context_branch',String(id));else localStorage.removeItem('j360_context_branch');};
 const readyContext=user.role!=='APP_ADMIN'||!!contextCompany&&!!contextBranch;
 const refreshNotice=(s:string)=>{setNotice(s);window.dispatchEvent(new CustomEvent('j360-notice',{detail:s}));window.setTimeout(()=>setNotice(''),3500);};

 const moduleClass=`module-${tab.toLowerCase().replace(/[^a-z0-9]+/g,'-').replace(/^-|-$/g,'')}`;
 const [paletteOpen,setPaletteOpen]=useState(false);
 const [navCollapsed,setNavCollapsed]=useState<boolean>(()=>{try{const v=localStorage.getItem('j360_nav_collapsed');if(v==='1'||v==='0')return v==='1';}catch{/* storage unavailable */}return typeof window!=='undefined'&&window.innerWidth<1600;});
 const toggleNav=()=>setNavCollapsed(c=>{const n=!c;try{localStorage.setItem('j360_nav_collapsed',n?'1':'0');}catch{/* ignore */}return n;});
 const go=(t:string)=>{if(menu.includes(t)){setTab(t);}else{refreshNotice(`${t} is not available for your role.`);}};
 useEffect(()=>{
   const onKey=(e:KeyboardEvent)=>{if((e.ctrlKey||e.metaKey)&&e.key.toLowerCase()==='k'){e.preventDefault();setPaletteOpen(o=>!o);}};
   window.addEventListener('keydown',onKey);return()=>window.removeEventListener('keydown',onKey);
 },[]);
 useEffect(()=>{document.querySelector('.workspaceBody')?.scrollTo({top:0});},[tab]);
 const themeOrder:ThemeKey[]=['LUXURY_GOLD','CLASSIC_IVORY','PREMIUM_DARK','MODERN_LIGHT'];
 const paletteItems:PaletteItem[]=[
   ...menu.map((m:string)=>({id:`go-${m}`,label:m,hint:'Open page',group:'Go to',run:()=>setTab(m)})),
   ...(menu.includes('Billing')?[{id:'a-bill',label:'Start a new bill',hint:'Billing counter',group:'Action',run:()=>setTab('Billing')}]:[]),
   ...(menu.includes('Jewellery')&&perms.canManage?[{id:'a-prod',label:'Add a product or tag',hint:'Jewellery',group:'Action',run:()=>setTab('Jewellery')}]:[]),
   ...(menu.includes('Customers')?[{id:'a-cust',label:'Add a customer',hint:'Customers',group:'Action',run:()=>setTab('Customers')}]:[]),
   ...(menu.includes('Users')&&(user.role==='APP_ADMIN'||user.role==='COMPANY_ADMIN')?[{id:'a-user',label:'Create a user',hint:'Users',group:'Action',run:()=>{setTab('Users');setUsersFocus(n=>n+1);}}]:[]),
   {id:'a-theme',label:'Switch theme',hint:`Now: ${theme.replace('_',' ').toLowerCase()}`,group:'Action',run:()=>setTheme(themeOrder[(themeOrder.indexOf(theme)+1)%themeOrder.length])}
 ];
 return <PermissionsContext.Provider value={perms}><div className={`app theme-${theme.toLowerCase()} ${moduleClass}${navCollapsed?' nav-collapsed':''}`} style={{background:c.bg,color:c.ink}}>
  <aside><div className="sideBrand"><div className="sideLogo" aria-hidden="true">{brandInitials(brand)}</div><div><b title={brand}>{brand}</b><small>{user.role.replaceAll('_',' ')}</small></div></div>
   <div className="nav">{menu.map((x:string)=><button key={x} className={tab===x?'active':''} onClick={()=>setTab(x)} title={x} aria-label={x} aria-current={tab===x?'page':undefined}><NavIcon name={x}/><span className="navText">{x}</span></button>)}</div>
   <button className="signout" onClick={logout} title="Sign out" aria-label="Sign out"><span aria-hidden="true">↪</span><span className="navText">Sign out</span></button>
  </aside>
  <section className={`workspace ${moduleClass}`}>
   <header><button type="button" className="menuToggle" onClick={toggleNav} aria-pressed={navCollapsed} aria-label={navCollapsed?'Expand menu':'Collapse menu'} title={navCollapsed?'Expand menu':'Collapse menu'}><svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" aria-hidden="true"><path d="M4 6h16M4 12h16M4 18h10"/></svg></button><div className="headTitle"><span className="eyebrow">{brand.toUpperCase()} / {user.role}</span><h1>{tab}</h1></div>
    <button type="button" className="headSearch" onClick={()=>setPaletteOpen(true)} aria-label="Quick jump" title="Quick jump"><svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" aria-hidden="true"><circle cx="11" cy="11" r="7"/><path d="M20 20l-3.5-3.5"/></svg></button>
    <div className="headerTools"><HeaderGadget ready={readyContext}/>
      {user.role==='APP_ADMIN'&&<><select className="ctxSelect" value={contextCompany??''} onChange={e=>selectCompany(Number(e.target.value)||null)}><option value="">Company context</option>{companiesData.map(x=><option key={x.id} value={x.id}>{x.name}</option>)}</select><select className="ctxSelect" value={contextBranch??''} disabled={!contextCompany} onChange={e=>selectBranch(Number(e.target.value)||null)}><option value="">Branch context</option>{branchesData.map(x=><option key={x.id} value={x.id}>{x.name}</option>)}</select></>}
      {user.role!=='APP_ADMIN'&&<span className="contextBadge">{user.companyName||'Platform'} · {user.branchName||'All branches'}</span>}
      <select className="themeSelect" value={theme} onChange={e=>setTheme(e.target.value as ThemeKey)}><option value="LUXURY_GOLD">Luxury Gold · Glass</option><option value="CLASSIC_IVORY">Classic Ivory · Ledger</option><option value="PREMIUM_DARK">Premium Dark · Console</option><option value="MODERN_LIGHT">Modern Light · Studio</option></select>
    </div>
   </header>
   <main className="workspaceBody" id="main">
   {notice&&<div className="toast">{notice}</div>}
   {!readyContext&&user.role==='APP_ADMIN'&&tab!=='Companies'&&tab!=='Branches'&&tab!=='Users'&&tab!=='Approvals'&&<div className="contextRequired"><b>Select Company + Branch context</b><span>APP_ADMIN must choose an operating company and branch before opening company-scoped workflows.</span></div>}
   {tab!=='Overview'&&tab!=='Billing'&&tab!=='Users'&&<PageHero tab={tab} ready={readyContext} user={user} onGo={go}/>}
   {tab==='Overview'&&<Overview user={user} ready={readyContext}/>}
   {tab==='Companies'&&<CompaniesModule user={user} onNotice={refreshNotice}/>}
   {tab==='Branches'&&<BranchesModule user={user} companiesData={companiesData} onNotice={refreshNotice}/>}
   {tab==='Users'&&<UsersModule user={user} onNotice={refreshNotice} focusCreate={usersFocus}/>}
   {tab==='Approvals'&&<ApprovalsModule onNotice={refreshNotice}/>}
   {tab==='Audit Logs'&&<AuditModule/>}
   {tab==='Reports'&&<ReportsModule onNotice={refreshNotice}/>}
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
   </main>
   <AppFooter brand={brand} user={user} onPalette={()=>setPaletteOpen(true)}/>
  </section>
  <CommandPalette open={paletteOpen} items={paletteItems} onClose={()=>setPaletteOpen(false)}/>
 </div></PermissionsContext.Provider>
}
