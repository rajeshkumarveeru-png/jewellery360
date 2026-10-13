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
/** Same pages, in the same order, as the server (PermissionService.PAGES). */
export const PAGE_DEFS: PageDef[] = ([
  ['Overview', 'Today\'s sales, dues, rates and alerts'],
  ['Billing', 'Create bills, take payments, returns'],
  ['Customers', 'Customers, dues, advances and history'],
  ['Stock', 'Tagged pieces, transfers, masters and import'],
  ['Gold Rates', 'Daily selling rates per purity'],
  ['Old Gold', 'Old gold purchase and exchange'],
  ['Purchases', 'Suppliers, purchases and supplier dues'],
  ['Services', 'Orders, repairs and karigar job work'],
  ['Money', 'Payments, advances, expenses and day close'],
  ['Reports', 'Sales, stock, GST and finance reports'],
  ['Settings', 'Business, tax, invoice and menu settings'],
  ['Audit Logs', 'Who changed what, and when']
] as [string, string][]).map(([tab, hint]) => ({key: pageKey(tab), tab, label: tab, hint}));

export type PowerDef = {key: string; label: string; hint: string; sensitive?: boolean};
/** Same powers as the server (PermissionService.POWERS). */
export const POWER_DEFS: PowerDef[] = [
  {key: 'MANAGE_RECORDS', label: 'Add and edit records', hint: 'Stock, masters, gold rates and purchases. Bills, customers, services and old gold never need it.'},
  {key: 'DISCOUNT', label: 'Give a discount on a bill', hint: 'Billing'},
  {key: 'PRICE_OVERRIDE', label: 'Change rate, making or wastage on a bill', hint: 'Billing - every change is written to the audit log', sensitive: true},
  {key: 'CANCEL_RETURN', label: 'Cancel bills, take returns and refund', hint: 'Billing and Money', sensitive: true},
  {key: 'SEE_PROFIT', label: 'See cost and profit', hint: 'Reports and stock cost price', sensitive: true},
  {key: 'EDIT_SETTINGS', label: 'Change settings', hint: 'Save changes in Settings', sensitive: true}
];

const ROLE_PAGES: Record<string, string[]> = {
  MANAGER: ['Overview', 'Billing', 'Customers', 'Stock', 'Gold Rates', 'Old Gold', 'Purchases', 'Services', 'Money', 'Reports', 'Audit Logs'],
  CASHIER: ['Overview', 'Billing', 'Customers', 'Old Gold', 'Money'],
  SALESMAN: ['Overview', 'Billing', 'Customers', 'Stock', 'Services'],
  INVENTORY_MANAGER: ['Overview', 'Stock', 'Gold Rates', 'Purchases', 'Services', 'Reports'],
  ACCOUNTANT: ['Overview', 'Customers', 'Purchases', 'Money', 'Reports', 'Audit Logs'],
  VIEWER: ['Overview', 'Customers', 'Stock', 'Reports']
};
const ROLE_POWERS: Record<string, string[]> = {
  MANAGER: ['MANAGE_RECORDS', 'DISCOUNT', 'PRICE_OVERRIDE', 'CANCEL_RETURN', 'SEE_PROFIT'],
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
  canDiscount: boolean;
  canOverride: boolean;
  canCancel: boolean;
  isViewer: boolean;
};

const ALL_KEYS = [...PAGE_DEFS.map(p => p.key), ...POWER_DEFS.map(p => p.key)];
const FULL: Permissions = {isAdmin: true, isStaff: false, allowed: ALL_KEYS, canOpen: () => true, canManage: true, canSeeProfit: true, canEditSettings: true, canDiscount: true, canOverride: true, canCancel: true, isViewer: false};

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
    canEditSettings: has('EDIT_SETTINGS'),
    canDiscount: has('DISCOUNT'),
    canOverride: has('PRICE_OVERRIDE'),
    canCancel: has('CANCEL_RETURN'),
    isViewer: role === 'VIEWER'
  };
};

export const PermissionsContext = createContext<Permissions>(FULL);
export const usePermissions = (): Permissions => useContext(PermissionsContext);
