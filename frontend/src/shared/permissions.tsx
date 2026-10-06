import {createContext, useContext} from 'react';

/* ============================================================================================
   What each user may open and do.
   APP_ADMIN / COMPANY_ADMIN: everything (in their scope).
   Every other role: exactly what the company admin granted on the Users page. Without a saved list the defaults of the role
   apply (same defaults as the server). The list arrives with the signed-in user and is refreshed every minute, so a page that
   was taken away disappears quickly - and the server checks the same permissions on every request.
   ============================================================================================ */

export const pageKey = (tab: string) => 'PAGE_' + tab.toUpperCase().replace(' & ', '_').replace(/ /g, '_');

export type PageDef = {key: string; tab: string; label: string; hint: string};
export const PAGE_DEFS: PageDef[] = ([
  ['Overview', 'Sales, today\'s gold rate and targets'],
  ['Billing', 'Create bills and take payments'],
  ['Jewellery', 'Items, tags, designs and categories'],
  ['Gold & Rates', 'Daily gold and silver rates, purities'],
  ['Inventory', 'Stock, transfers and audits'],
  ['Purchases', 'Suppliers and purchase entries'],
  ['Old Gold', 'Old gold purchase and exchange'],
  ['Customers', 'Find and add customers'],
  ['Services', 'Repairs and custom orders'],
  ['Payments', 'Receipts, advances and dues'],
  ['Reports', 'Sales, stock and gold reports'],
  ['WhatsApp', 'Send invoices and reminders'],
  ['Settings', 'View business, tax and bill settings'],
  ['Audit Logs', 'Who changed what, and when']
] as [string, string][]).map(([tab, hint]) => ({key: pageKey(tab), tab, label: tab, hint}));

export type PowerDef = {key: string; label: string; hint: string; sensitive?: boolean};
export const POWER_DEFS: PowerDef[] = [
  {key: 'MANAGE_RECORDS', label: 'Add, edit and delete records', hint: 'In the pages above. Billing, payments, customers, services and old gold can always be added.'},
  {key: 'SEE_PROFIT', label: 'See the profit report', hint: 'Reports - Profit tab', sensitive: true},
  {key: 'EDIT_SETTINGS', label: 'Change settings', hint: 'Save changes in Settings', sensitive: true}
];

const ROLE_PAGES: Record<string, string[]> = {
  MANAGER: ['Overview', 'Billing', 'Jewellery', 'Gold & Rates', 'Inventory', 'Purchases', 'Old Gold', 'Customers', 'Services', 'Payments', 'Reports', 'Audit Logs', 'WhatsApp'],
  CASHIER: ['Overview', 'Billing', 'Customers', 'Payments', 'Audit Logs'],
  SALESMAN: ['Overview', 'Billing', 'Customers', 'Services', 'Audit Logs'],
  INVENTORY_MANAGER: ['Overview', 'Jewellery', 'Inventory', 'Purchases', 'Old Gold', 'Reports', 'Audit Logs'],
  ACCOUNTANT: ['Overview', 'Customers', 'Payments', 'Reports', 'Audit Logs'],
  VIEWER: ['Overview', 'Customers', 'Inventory', 'Reports']
};
const ROLE_POWERS: Record<string, string[]> = {
  MANAGER: ['MANAGE_RECORDS', 'SEE_PROFIT'],
  INVENTORY_MANAGER: ['MANAGE_RECORDS'],
  ACCOUNTANT: ['MANAGE_RECORDS', 'SEE_PROFIT']
};
export const ROLE_LABEL: Record<string, string> = {
  MANAGER: 'Manager', CASHIER: 'Cashier', SALESMAN: 'Salesman', INVENTORY_MANAGER: 'Inventory manager', ACCOUNTANT: 'Accountant', VIEWER: 'Viewer'
};
export const STAFF_ROLES = Object.keys(ROLE_LABEL);

/** The defaults of a role as permission keys (same as the server). */
export const roleDefaults = (role: string): string[] =>
  [...(ROLE_PAGES[role] || ['Overview']).map(pageKey), ...(ROLE_POWERS[role] || [])];

const sameSet = (a: string[], b: string[]) => a.length === b.length && a.every(k => b.includes(k));
/** Which role's defaults the list equals (or 'CUSTOM'). */
export const presetOf = (keys: string[] | null | undefined, preferred?: string): string => {
  const list = keys && keys.length ? keys : [];
  if (preferred && sameSet(roleDefaults(preferred), list)) return preferred;
  return STAFF_ROLES.find(r => sameSet(roleDefaults(r), list)) ?? 'CUSTOM';
};

export type Permissions = {
  isAdmin: boolean;
  isStaff: boolean;
  allowed: string[];
  canOpen: (tab: string) => boolean;
  /** add / edit / delete records in the jewellery, gold rate, stock and purchase pages */
  canManage: boolean;
  canSeeProfit: boolean;
  canEditSettings: boolean;
};

const ALL_KEYS = [...PAGE_DEFS.map(p => p.key), ...POWER_DEFS.map(p => p.key)];
const FULL: Permissions = {isAdmin: true, isStaff: false, allowed: ALL_KEYS, canOpen: () => true, canManage: true, canSeeProfit: true, canEditSettings: true};

export const permissionsFor = (user?: {role?: string | null; permissions?: string[] | null} | null): Permissions => {
  const role = String(user?.role ?? '').toUpperCase();
  if (!user || role === 'APP_ADMIN' || role === 'COMPANY_ADMIN') return FULL;
  const allowed = user.permissions && user.permissions.length ? user.permissions : roleDefaults(role);
  const has = (k: string) => allowed.includes(k);
  return {
    isAdmin: false,
    isStaff: true,
    allowed,
    canOpen: (tab: string) => has(pageKey(tab)),
    canManage: has('MANAGE_RECORDS'),
    canSeeProfit: has('SEE_PROFIT'),
    canEditSettings: has('EDIT_SETTINGS')
  };
};

export const PermissionsContext = createContext<Permissions>(FULL);
export const usePermissions = (): Permissions => useContext(PermissionsContext);
