import {useEffect, useState} from 'react';
import {
  approvals, audit, branches, companies, domainCustomers, domainCustomOrders, domainItems, domainOldGold, domainPayments, domainPurchases,
  domainRepairs, domainStock, domainWhatsappLogs, getBusinessSettings, headerGoldRates, salesAnalytics, users
} from '../api';
import {User} from './types';

export type Stat = {label: string; value: string};

const list = (r: any): any[] => (Array.isArray(r?.data) ? r.data : []);
const num = (v: unknown) => Number(v || 0);
const inr = (v: number) => `₹${Math.round(v).toLocaleString('en-IN')}`;
const iso = (d: Date) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;

/** 2-3 live numbers for the hero of every page (each page loads only what it needs; failures just show no chips). */
export async function loadModuleStats(tab: string, user: User): Promise<Stat[]> {
  switch (tab) {
    case 'Customers': {
      const rows = list(await domainCustomers());
      return [{label: 'Customers', value: String(rows.length)}, {label: 'With GSTIN', value: String(rows.filter(x => x.gstin).length)}, {label: 'With email', value: String(rows.filter(x => x.email).length)}];
    }
    case 'Jewellery': {
      const rows = list(await domainItems());
      const inStock = rows.filter(x => x.status === 'IN_STOCK');
      return [{label: 'Items', value: String(rows.length)}, {label: 'In stock', value: String(inStock.length)}, {label: 'Net gold', value: `${inStock.reduce((s, x) => s + num(x.netWeight), 0).toFixed(1)} g`}];
    }
    case 'Inventory': {
      const rows = list(await domainStock());
      return [{label: 'Stock lines', value: String(rows.length)}, {label: 'Available', value: String(rows.filter(x => x.status === 'IN_STOCK').length)}, {label: 'Sold', value: String(rows.filter(x => x.status === 'SOLD').length)}];
    }
    case 'Purchases': {
      const rows = list(await domainPurchases());
      return [{label: 'Purchases', value: String(rows.length)}, {label: 'Value', value: inr(rows.reduce((s, x) => s + num(x.totalAmount ?? x.total ?? x.amount), 0))}];
    }
    case 'Old Gold': {
      const rows = list(await domainOldGold());
      return [{label: 'Transactions', value: String(rows.length)}, {label: 'Value', value: inr(rows.reduce((s, x) => s + num(x.totalValue ?? x.total ?? x.amount), 0))}];
    }
    case 'Services': {
      const [r, c] = await Promise.all([domainRepairs(), domainCustomOrders()]);
      const all = [...list(r), ...list(c)];
      return [{label: 'Repairs', value: String(list(r).length)}, {label: 'Custom orders', value: String(list(c).length)}, {label: 'Open', value: String(all.filter(x => !['DELIVERED', 'COMPLETED', 'CANCELLED'].includes(String(x.status).toUpperCase())).length)}];
    }
    case 'Payments': {
      const rows = list(await domainPayments());
      return [{label: 'Payments', value: String(rows.length)}, {label: 'Received', value: inr(rows.reduce((s, x) => s + num(x.amount), 0))}];
    }
    case 'Reports': {
      const to = new Date();
      const from = new Date(to.getFullYear(), to.getMonth(), to.getDate() - 29);
      const a = (await salesAnalytics({from: iso(from), to: iso(to), granularity: 'DAY'})).data;
      return a?.totals ? [{label: '30-day sales', value: inr(num(a.totals.sales))}, {label: 'Invoices', value: String(a.totals.invoices)}, {label: 'Average bill', value: inr(num(a.totals.average))}] : [];
    }
    case 'Users': {
      const rows = list(await users());
      return [{label: 'Users', value: String(rows.length)}, {label: 'Active', value: String(rows.filter(x => x.enabled).length)}, {label: 'Pending', value: String(rows.filter(x => !x.enabled).length)}];
    }
    case 'Branches': {
      const rows = list(await branches());
      return [{label: 'Branches', value: String(rows.length)}, {label: 'Active', value: String(rows.filter(x => x.active !== false).length)}];
    }
    case 'Companies': {
      const rows = list(await companies());
      return [{label: 'Companies', value: String(rows.length)}, {label: 'Active', value: String(rows.filter(x => x.active !== false).length)}];
    }
    case 'Approvals': {
      const rows = list(await approvals());
      return [{label: 'Waiting for approval', value: String(rows.length)}];
    }
    case 'Audit Logs': {
      const rows = list(await audit());
      return [{label: 'Events', value: String(rows.length)}, {label: 'Latest', value: rows[0]?.action ? String(rows[0].action).slice(0, 18) : '—'}];
    }
    case 'Gold & Rates': {
      const d: any = (await headerGoldRates()).data;
      const rates: any[] = Array.isArray(d?.marketRates) && d.marketRates.length ? d.marketRates : (Array.isArray(d?.rates) ? d.rates : []);
      return rates.slice(0, 3).map(x => ({label: String(x.karat || x.purity || ''), value: `₹${num(x.ratePerGram).toLocaleString('en-IN')}/g`}));
    }
    case 'WhatsApp': {
      const rows = list(await domainWhatsappLogs());
      return [{label: 'Messages', value: String(rows.length)}, {label: 'Sent', value: String(rows.filter(x => String(x.status).toUpperCase() === 'SENT').length)}];
    }
    case 'Settings': {
      if (user.role === 'APP_ADMIN' && !localStorage.getItem('j360_context_company')) return [];
      const d: any = (await getBusinessSettings(user.role === 'APP_ADMIN' ? Number(localStorage.getItem('j360_context_company')) || undefined : undefined)).data;
      return [{label: 'Print layout', value: String(d?.printFormat || 'A4')}, {label: 'GSTIN', value: d?.gstin ? 'Set' : 'Missing'}, {label: 'SKU prefix', value: d?.skuPrefix || 'none'}];
    }
    default:
      return [];
  }
}

export function useModuleStats(tab: string, ready: boolean, user: User): Stat[] {
  const [stats, setStats] = useState<Stat[]>([]);
  useEffect(() => {
    let off = false;
    setStats([]);
    const needsContext = user.role === 'APP_ADMIN' && !ready && !['Companies', 'Branches', 'Users', 'Approvals'].includes(tab);
    if (needsContext) return;
    loadModuleStats(tab, user).then(s => { if (!off) setStats(s); }).catch(() => { if (!off) setStats([]); });
    return () => { off = true; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [tab, ready, user.id]);
  return stats;
}
