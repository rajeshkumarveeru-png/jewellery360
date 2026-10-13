import {create} from 'zustand';
import {J, setUnauthorizedHandler, tokenStore} from './api';

/* Signed-in user and what they may open (same permission keys as the server: PAGE_<PAGE> and powers). */

export type User = {id: number; username: string; role: string; companyName?: string; branchName?: string; branchId?: number | null; permissions?: string[]};

type Session = {
  token: string | null; user: User | null; ready: boolean;
  start: () => Promise<void>;
  signIn: (token: string) => Promise<void>;
  signOut: () => Promise<void>;
};

export const useSession = create<Session>((set) => ({
  token: null, user: null, ready: false,
  start: async () => {
    const token = await tokenStore.get();
    if (!token) { set({ready: true}); return; }
    try { const r = await J.me(); set({token, user: r.data, ready: true}); }
    catch { await tokenStore.remove(); set({token: null, user: null, ready: true}); }
  },
  signIn: async (token) => {
    await tokenStore.set(token);
    const r = await J.me();
    set({token, user: r.data});
  },
  signOut: async () => { await tokenStore.remove(); set({token: null, user: null}); },
}));

setUnauthorizedHandler(() => useSession.setState({token: null, user: null}));

const isAdmin = (u: User | null) => u?.role === 'COMPANY_ADMIN' || u?.role === 'APP_ADMIN';
/** Page access, e.g. can(user, 'BILLING'). Admins can open everything; the server checks again on every request. */
export const can = (u: User | null, page: string) => isAdmin(u) || !!u?.permissions?.includes('PAGE_' + page);
export const has = (u: User | null, power: string) => isAdmin(u) || !!u?.permissions?.includes(power);
