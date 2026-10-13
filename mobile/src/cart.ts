import {create} from 'zustand';

/* The bill being built on this phone. Prices are never computed here - the server preview prices every line. */
export type Piece = {id: number; tagNo: string; name: string; purity: string; net: number};
export type Cust = {id?: number; name: string; phone?: string} | null;
export type Pay = {mode: string; amount: string; reference: string};

const ref = () => `m-${Date.now()}-${Math.random().toString(36).slice(2, 10)}`;

type Cart = {
  pieces: Piece[]; customer: Cust; payments: Pay[]; clientRef: string;
  add: (p: Piece) => boolean; remove: (id: number) => void; setCustomer: (c: Cust) => void; setPayments: (p: Pay[]) => void; reset: () => void;
};

export const useCart = create<Cart>((set, get) => ({
  pieces: [], customer: null, payments: [{mode: 'CASH', amount: '', reference: ''}], clientRef: ref(),
  add: p => { if (get().pieces.some(x => x.id === p.id)) return false; set({pieces: [...get().pieces, p]}); return true; },
  remove: id => set({pieces: get().pieces.filter(x => x.id !== id)}),
  setCustomer: c => set({customer: c}),
  setPayments: p => set({payments: p}),
  reset: () => set({pieces: [], customer: null, payments: [{mode: 'CASH', amount: '', reference: ''}], clientRef: ref()}),
}));

export const pieceOf = (x: any): Piece => ({id: x.id, tagNo: x.tagNo, name: x.productName, purity: x.purity?.name || '', net: Number(x.netWeight || 0)});
