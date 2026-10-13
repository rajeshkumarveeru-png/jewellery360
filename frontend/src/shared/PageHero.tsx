import {useState} from 'react';
import {NAV_PATHS} from './ui';

/* One slim banner per page: a one-line purpose and shortcuts to related pages. Collapsible (remembered).
   Live figures are shown by the pages themselves, never twice. */
type Hero = {tagline: string; pattern: string; actions: {label: string; tab: string}[]};

const HEROES: Record<string, Hero> = {
  Companies: {tagline: 'Every jewellery business on the platform, in one register.', pattern: 'p-grid', actions: [{label: 'Branches', tab: 'Branches'}, {label: 'Approvals', tab: 'Approvals'}]},
  Branches: {tagline: 'Showrooms, their GSTIN, contact details and invoice wording.', pattern: 'p-dots', actions: [{label: 'Users', tab: 'Users'}, {label: 'Settings', tab: 'Settings'}]},
  Approvals: {tagline: 'New companies and users waiting for a decision.', pattern: 'p-stripes', actions: [{label: 'Users', tab: 'Users'}, {label: 'Companies', tab: 'Companies'}]},
  Backups: {tagline: 'Database backups: take one now, download it, or restore safely.', pattern: 'p-grid', actions: [{label: 'Companies', tab: 'Companies'}]},
  Stock: {tagline: 'Every tagged piece: weight, purity, HUID, where it is and its full history.', pattern: 'p-facets', actions: [{label: 'Purchases', tab: 'Purchases'}, {label: 'New bill', tab: 'Billing'}]},
  'Gold Rates': {tagline: 'Today\u2019s selling rate per purity. Bills copy the rate, so old bills never change.', pattern: 'p-coins', actions: [{label: 'Old Gold', tab: 'Old Gold'}, {label: 'New bill', tab: 'Billing'}]},
  Purchases: {tagline: 'Supplier purchases that feed your stock, payments and supplier dues.', pattern: 'p-waves', actions: [{label: 'Stock', tab: 'Stock'}, {label: 'Money', tab: 'Money'}]},
  'Old Gold': {tagline: 'Buy or exchange customers\u2019 old gold with a line-by-line valuation.', pattern: 'p-coins', actions: [{label: 'Gold Rates', tab: 'Gold Rates'}, {label: 'New bill', tab: 'Billing'}]},
  Customers: {tagline: 'Contacts, what each customer owes, their advances and full history.', pattern: 'p-rings', actions: [{label: 'New bill', tab: 'Billing'}, {label: 'Money', tab: 'Money'}]},
  Services: {tagline: 'Customer orders, repairs and karigar job work with metal reconciliation.', pattern: 'p-stripes', actions: [{label: 'Customers', tab: 'Customers'}, {label: 'Money', tab: 'Money'}]},
  Money: {tagline: 'Money in and out, customer advances, expenses and the end-of-day cash count.', pattern: 'p-waves', actions: [{label: 'Customers', tab: 'Customers'}, {label: 'Reports', tab: 'Reports'}]},
  Reports: {tagline: 'Sales, GST, stock and finance reports for any date range - export to CSV.', pattern: 'p-grid', actions: [{label: 'Overview', tab: 'Overview'}, {label: 'Audit Logs', tab: 'Audit Logs'}]},
  Settings: {tagline: 'Business profile, tax, invoice design, WhatsApp and the menu.', pattern: 'p-facets', actions: [{label: 'Branches', tab: 'Branches'}, {label: 'Users', tab: 'Users'}]},
  'Audit Logs': {tagline: 'Who changed what, and when.', pattern: 'p-stripes', actions: [{label: 'Users', tab: 'Users'}, {label: 'Reports', tab: 'Reports'}]}
};

export default function PageHero({tab, title, onGo, canGo}: {tab: string; title: string; onGo: (tab: string) => void; canGo: (tab: string) => boolean}) {
  const hero = HEROES[tab];
  const [closed, setClosed] = useState(() => { try { return localStorage.getItem('j360_hero_closed') === '1'; } catch { return false; } });
  if (!hero) return null;
  const toggle = () => setClosed(c => { const n = !c; try { localStorage.setItem('j360_hero_closed', n ? '1' : '0'); } catch { /* ignore */ } return n; });
  const d = NAV_PATHS[tab] || 'M12 3l7 9-7 9-7-9z';
  const actions = hero.actions.filter(a => canGo(a.tab));
  return (
    <section className={`pageHero ${hero.pattern}${closed ? ' closed' : ''}`} aria-label={`${title} overview`}>
      <span className="heroMedal" aria-hidden="true"><svg viewBox="0 0 24 24" width="26" height="26" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round"><path d={d} /></svg></span>
      <div className="heroText"><b>{title}</b>{!closed && <small>{hero.tagline}</small>}</div>
      {!closed && actions.length > 0 && <div className="heroActions">{actions.map(a => <button key={a.tab} type="button" onClick={() => onGo(a.tab)}>{a.label} →</button>)}</div>}
      <button type="button" className="heroToggle" onClick={toggle} aria-expanded={!closed} aria-label={closed ? 'Expand page banner' : 'Collapse page banner'} title={closed ? 'Expand banner' : 'Collapse banner'}>{closed ? '▾' : '▴'}</button>
    </section>
  );
}
