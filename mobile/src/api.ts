import axios from 'axios';
import {Platform} from 'react-native';
import * as SecureStore from 'expo-secure-store';

/* Jewellery360 API client. The server scopes every call to the signed-in user's company and branch, exactly as for the
   web app; the mobile app never sends company or branch headers. */

const TOKEN_KEY = 'j360.token';
// SecureStore (Keychain / Keystore) on phones; the browser build (expo start --web) has no secure store, so it uses
// localStorage there. A storage failure means "signed out", never a stuck start-up screen.
const web = Platform.OS === 'web';
export const tokenStore = {
  get: async () => { try { return web ? globalThis.localStorage?.getItem(TOKEN_KEY) ?? null : await SecureStore.getItemAsync(TOKEN_KEY); } catch { return null; } },
  set: async (t: string) => { if (web) globalThis.localStorage?.setItem(TOKEN_KEY, t); else await SecureStore.setItemAsync(TOKEN_KEY, t); },
  remove: async () => { try { if (web) globalThis.localStorage?.removeItem(TOKEN_KEY); else await SecureStore.deleteItemAsync(TOKEN_KEY); } catch { /* already gone */ } },
};

export const API_URL = process.env.EXPO_PUBLIC_API_URL || '';

export const api = axios.create({baseURL: API_URL, timeout: 20000, headers: {'Content-Type': 'application/json'}});

let onUnauthorized: (() => void) | null = null;
export const setUnauthorizedHandler = (fn: () => void) => { onUnauthorized = fn; };

api.interceptors.request.use(async config => {
  const token = await tokenStore.get();
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});
api.interceptors.response.use(r => r, async error => {
  if (error?.response?.status === 401 && !String(error?.config?.url || '').includes('/auth/login')) {
    await tokenStore.remove();
    onUnauthorized?.();
  }
  return Promise.reject(error);
});

/** The server's own message when it gave one; otherwise a plain explanation with what to do next. */
export function errText(e: any, fallback = 'Something went wrong. Please try again.') {
  const d = e?.response?.data;
  const msg = typeof d === 'string' ? d : d?.message || d?.error;
  if (typeof msg === 'string' && msg.trim() && !/^request failed/i.test(msg)) return msg.trim();
  const s = e?.response?.status;
  if (!e?.response) return API_URL ? `Cannot reach the server at ${API_URL}. Check Wi-Fi and that the server is running.` : 'The server address is not set (EXPO_PUBLIC_API_URL).';
  if (s === 401) return 'Your session has ended. Please sign in again.';
  if (s === 403) return 'Your account does not have permission for this.';
  if (s === 404) return 'Not found.';
  return fallback;
}

const clean = (p?: Record<string, any>) => p ? Object.fromEntries(Object.entries(p).filter(([, v]) => v !== undefined && v !== null && v !== '')) : undefined;

export const J = {
  login: (identifier: string, password: string) => api.post('/auth/login', {identifier, password}),
  me: () => api.get('/users/me'),
  dashboard: () => api.get('/dashboard'),
  search: (q: string) => api.get('/search', {params: {q}}),
  masters: () => api.get('/masters'),
  rates: () => api.get('/gold-rates'),
  saveRates: (rates: {purityId: number; ratePerGram: number}[]) => api.post('/gold-rates', {rates}),
  stock: (p?: {status?: string; q?: string; limit?: number}) => api.get('/stock/items', {params: clean(p)}),
  scan: (code: string) => api.get('/stock/scan', {params: {code}}),
  timeline: (id: number) => api.get(`/stock/items/${id}/timeline`),
  customers: (q?: string, limit = 100) => api.get('/customers', {params: clean({q, limit})}),
  customer: (id: number) => api.get(`/customers/${id}`),
  saveCustomer: (x: any) => api.post('/customers', x),
  collect: (id: number, x: any) => api.post(`/customers/${id}/collect`, x),
  sales: (p?: any) => api.get('/sales', {params: clean(p)}),
  sale: (id: number) => api.get(`/sales/${id}`),
  preview: (x: any) => api.post('/sales/preview', x),
  createSale: (x: any) => api.post('/sales', x),
  paySale: (id: number, payments: any[]) => api.post(`/sales/${id}/payments`, {payments}),
  shareSale: (id: number) => api.post(`/whatsapp/sales/${id}`),
  orders: () => api.get('/orders'),
  updateOrder: (id: number, x: any) => api.put(`/orders/${id}`, x),
  repairs: () => api.get('/repairs'),
  updateRepair: (id: number, x: any) => api.put(`/repairs/${id}`, x),
  dayClose: () => api.get('/money/day-close'),
  closeDay: (x: any) => api.post('/money/day-close', x),
};
