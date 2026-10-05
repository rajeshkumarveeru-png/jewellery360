import {useState} from 'react';
import {NAV_PATHS} from './ui';
import {useModuleStats} from './moduleStats';
import {User} from './types';

/* One banner per page: its own colour, pattern and icon, a one-line purpose, 2-3 live numbers and shortcuts to related pages.
   Collapsible (remembered), so power users can reclaim the height. */
type Hero = {tagline: string; pattern: string; actions: {label: string; tab: string}[]};

const HEROES: Record<string, Hero> = {
  Companies: {tagline: 'Every jewellery business on the platform, in one register.', pattern: 'p-grid', actions: [{label: 'Branches', tab: 'Branches'}, {label: 'Approvals', tab: 'Approvals'}]},
  Branches: {tagline: 'Showrooms, their GSTIN, contact details and invoice wording.', pattern: 'p-dots', actions: [{label: 'Users', tab: 'Users'}, {label: 'Settings', tab: 'Settings'}]},
  Users: {tagline: 'Who can sign in, with which role, at which branch.', pattern: 'p-rings', actions: [{label: 'Approvals', tab: 'Approvals'}, {label: 'Audit Logs', tab: 'Audit Logs'}]},
  Approvals: {tagline: 'New companies and users waiting for a decision.', pattern: 'p-stripes', actions: [{label: 'Users', tab: 'Users'}, {label: 'Companies', tab: 'Companies'}]},
  Jewellery: {tagline: 'Categories, designs, products, tags and every piece in stock.', pattern: 'p-facets', actions: [{label: 'Inventory', tab: 'Inventory'}, {label: 'New bill', tab: 'Billing'}]},
  'Gold & Rates': {tagline: 'Live market rates and the purity master that prices every bill.', pattern: 'p-coins', actions: [{label: 'Old Gold', tab: 'Old Gold'}, {label: 'New bill', tab: 'Billing'}]},
  Inventory: {tagline: 'Stock by branch, movements and transfers between showrooms.', pattern: 'p-grid', actions: [{label: 'Jewellery', tab: 'Jewellery'}, {label: 'Purchases', tab: 'Purchases'}]},
  Purchases: {tagline: 'Supplier purchases and returns that feed your stock.', pattern: 'p-waves', actions: [{label: 'Inventory', tab: 'Inventory'}, {label: 'Payments', tab: 'Payments'}]},
  'Old Gold': {tagline: 'Buy or exchange customers\u2019 old gold with a clear deduction trail.', pattern: 'p-coins', actions: [{label: 'Gold & Rates', tab: 'Gold & Rates'}, {label: 'Customers', tab: 'Customers'}]},
  Customers: {tagline: 'Your clientele: contacts, GSTIN and what they owe.', pattern: 'p-rings', actions: [{label: 'New bill', tab: 'Billing'}, {label: 'Payments', tab: 'Payments'}]},
  Services: {tagline: 'Repairs and custom orders, moved from received to delivered.', pattern: 'p-stripes', actions: [{label: 'Customers', tab: 'Customers'}, {label: 'Payments', tab: 'Payments'}]},
  Payments: {tagline: 'Receipts, advances and dues across all customers.', pattern: 'p-waves', actions: [{label: 'Customers', tab: 'Customers'}, {label: 'Reports', tab: 'Reports'}]},
  Reports: {tagline: 'Sales, stock, gold and customer reports for any date range.', pattern: 'p-grid', actions: [{label: 'Overview', tab: 'Overview'}, {label: 'Audit Logs', tab: 'Audit Logs'}]},
  WhatsApp: {tagline: 'Invoices and reminders sent to customers, with delivery status.', pattern: 'p-dots', actions: [{label: 'Customers', tab: 'Customers'}, {label: 'Settings', tab: 'Settings'}]},
  Settings: {tagline: 'Business profile, tax, print layout, menu and WhatsApp.', pattern: 'p-facets', actions: [{label: 'Branches', tab: 'Branches'}, {label: 'Users', tab: 'Users'}]},
  'Audit Logs': {tagline: 'A tamper-evident trail of who changed what, and when.', pattern: 'p-stripes', actions: [{label: 'Users', tab: 'Users'}, {label: 'Reports', tab: 'Reports'}]}
};

export default function PageHero({tab, ready, user, onGo}: {tab: string; ready: boolean; user: User; onGo: (tab: string) => void}) {
  const hero = HEROES[tab];
  const stats = useModuleStats(tab, ready, user);
  const [closed, setClosed] = useState(() => { try { return localStorage.getItem('j360_hero_closed') === '1'; } catch { return false; } });
  if (!hero) return null;
  const toggle = () => setClosed(c => { const n = !c; try { localStorage.setItem('j360_hero_closed', n ? '1' : '0'); } catch { /* ignore */ } return n; });
  const d = NAV_PATHS[tab] || 'M12 3l7 9-7 9-7-9z';
  return (
    <section className={`pageHero ${hero.pattern}${closed ? ' closed' : ''}`} aria-label={`${tab} overview`}>
      <span className="heroMedal" aria-hidden="true"><svg viewBox="0 0 24 24" width="26" height="26" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round"><path d={d} /></svg></span>
      <div className="heroText"><b>{tab}</b>{!closed && <small>{hero.tagline}</small>}</div>
      {!closed && <div className="heroStats">{stats.map(s => <span key={s.label}><small>{s.label}</small><b>{s.value}</b></span>)}</div>}
      {!closed && <div className="heroActions">{hero.actions.map(a => <button key={a.tab} type="button" onClick={() => onGo(a.tab)}>{a.label} →</button>)}</div>}
      <button type="button" className="heroToggle" onClick={toggle} aria-expanded={!closed} aria-label={closed ? 'Expand page banner' : 'Collapse page banner'} title={closed ? 'Expand banner' : 'Collapse banner'}>{closed ? '▾' : '▴'}</button>
    </section>
  );
}
