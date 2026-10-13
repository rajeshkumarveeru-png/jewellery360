import {permissionsFor} from './permissions';

/* ============================================================================================
   Navigation. Business pages use the server's page keys (PermissionService.PAGES); the company can rename, regroup, hide
   and restrict them per role on Settings → Menu (stored as the company MENU_LAYOUT). Access itself is always decided by
   the user's permissions - on the server for every request, and here only to hide what cannot be opened.
   ============================================================================================ */

/** Page key → tab name used inside the app. */
export const PAGE_TABS: Record<string, string> = {
  OVERVIEW: 'Overview', BILLING: 'Billing', CUSTOMERS: 'Customers', STOCK: 'Stock', GOLD_RATES: 'Gold Rates', OLD_GOLD: 'Old Gold',
  PURCHASES: 'Purchases', SERVICES: 'Services', MONEY: 'Money', REPORTS: 'Reports', SETTINGS: 'Settings', AUDIT_LOGS: 'Audit Logs'
};
/** Default menu names where the page name alone hides what is inside. */
export const DEFAULT_LABELS: Record<string, string> = {MONEY: 'Money & expenses'};

export const tabKey = (tab: string) => Object.keys(PAGE_TABS).find(k => PAGE_TABS[k] === tab) || tab.toUpperCase().replace(/ /g, '_');

/** Default menu (same as the server's default layout). */
export const DEFAULT_GROUPS: [string, string][] = [
  ['OVERVIEW', 'Daily'], ['BILLING', 'Daily'], ['CUSTOMERS', 'Daily'], ['GOLD_RATES', 'Daily'],
  ['STOCK', 'Stock'], ['OLD_GOLD', 'Stock'], ['PURCHASES', 'Stock'],
  ['SERVICES', 'Work'],
  ['MONEY', 'Accounts'], ['REPORTS', 'Accounts'],
  ['SETTINGS', 'Admin'], ['AUDIT_LOGS', 'Admin']
];

export type MenuLayoutItem = {key: string; label?: string; group?: string; hidden?: boolean; roles?: string[]};
export type MenuEntry = {tab: string; label: string; group: string};

/** Administration pages that are not company business pages. */
const adminPages = (role: string): MenuEntry[] => {
  const out: MenuEntry[] = [];
  if (role === 'APP_ADMIN') out.push({tab: 'Companies', label: 'Companies', group: 'Admin'});
  if (role === 'APP_ADMIN' || role === 'COMPANY_ADMIN') out.push({tab: 'Branches', label: 'Branches', group: 'Admin'}, {tab: 'Users', label: 'Users', group: 'Admin'});
  if (role === 'APP_ADMIN') out.push({tab: 'Approvals', label: 'Approvals', group: 'Admin'}, {tab: 'Backups', label: 'Backups', group: 'Admin'});
  return out;
};

/** The menu a user sees: the company layout (or the default), minus hidden pages, pages restricted to other roles and
 *  pages the user may not open, plus the administration pages of their role. */
export function buildMenu(user: {role: string; permissions?: string[] | null}, layout?: MenuLayoutItem[] | null): MenuEntry[] {
  const perms = permissionsFor(user);
  const items: MenuLayoutItem[] = layout && layout.length ? layout : DEFAULT_GROUPS.map(([key, group]) => ({key, group}));
  const known = items.filter(i => PAGE_TABS[i.key]);
  // pages added to the app after the layout was saved still appear (at the end of their default group)
  for (const [key, group] of DEFAULT_GROUPS) if (!known.some(i => i.key === key)) known.push({key, group});
  const out: MenuEntry[] = [];
  for (const i of known) {
    const tab = PAGE_TABS[i.key];
    if (i.hidden && i.key !== 'SETTINGS') continue;
    if (!perms.isAdmin && i.roles && i.roles.length && !i.roles.includes(user.role)) continue;
    if (!perms.canOpen(tab)) continue;
    out.push({tab, label: (i.label || '').trim() || DEFAULT_LABELS[i.key] || tab, group: (i.group || '').trim() || 'Pages'});
  }
  const admin = adminPages(user.role);
  if (user.role === 'APP_ADMIN') return [...admin, ...out];
  return [...out, ...admin];
}

export const themeMap: Record<string,{bg:string;ink:string}> = {
  LUXURY_GOLD:{bg:'#f5efe4',ink:'#211c18'},
  CLASSIC_IVORY:{bg:'#fbf8f1',ink:'#2b2722'},
  PREMIUM_DARK:{bg:'#151719',ink:'#f5f0e7'},
  MODERN_LIGHT:{bg:'#eef2f5',ink:'#20252b'}
};
