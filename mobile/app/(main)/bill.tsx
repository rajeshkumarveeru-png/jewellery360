import React, {useEffect, useState} from 'react';
import {Alert, Pressable, Text, TextInput, View} from 'react-native';
import {router} from 'expo-router';
import {J, errText} from '@/src/api';
import {pieceOf, useCart} from '@/src/cart';
import type {Pay} from '@/src/cart';
import {Btn, C, Card, Chips, Field, Note, Row, Screen, Section, grams, label, money, num, s} from '@/src/ui';

/* Mobile counter: add pieces (search or camera scan) → customer → server-priced total → split payment → complete. */
const MODES = ['CASH', 'UPI', 'CARD', 'BANK'];

export default function Bill() {
  const cart = useCart();
  const [q, setQ] = useState('');
  const [hits, setHits] = useState<any[]>([]);
  const [preview, setPreview] = useState<any>(null);
  const [perr, setPerr] = useState('');
  const [cq, setCq] = useState('');
  const [cHits, setCHits] = useState<any[]>([]);
  const [newCust, setNewCust] = useState({name: '', phone: ''});
  const [busy, setBusy] = useState(false);

  // piece search
  useEffect(() => {
    const term = q.trim();
    if (term.length < 2) { setHits([]); return; }
    const t = setTimeout(() => J.stock({status: 'IN_STOCK', q: term, limit: 8}).then(r => setHits(r.data || [])).catch(() => setHits([])), 300);
    return () => clearTimeout(t);
  }, [q]);
  // customer search
  useEffect(() => {
    const term = cq.trim();
    if (term.length < 2) { setCHits([]); return; }
    const t = setTimeout(() => J.customers(term, 6).then(r => setCHits((r.data || []).filter((c: any) => !c.walkIn))).catch(() => setCHits([])), 300);
    return () => clearTimeout(t);
  }, [cq]);

  const body = () => ({
    ...(cart.customer?.id ? {customerId: cart.customer.id} : cart.customer ? {customer: {name: cart.customer.name, phone: cart.customer.phone}} : {}),
    items: cart.pieces.map(p => ({itemId: p.id})),
  });
  const sig = JSON.stringify(body());
  useEffect(() => {
    if (!cart.pieces.length) { setPreview(null); setPerr(''); return; }
    const t = setTimeout(() => J.preview(body()).then(r => { setPreview(r.data); setPerr(''); }).catch(e => setPerr(errText(e))), 300);
    return () => clearTimeout(t);
  }, [sig]);

  const add = async (code?: string) => {
    const term = (code ?? q).trim();
    if (!term) return;
    if (!code && hits.length === 1) { push(hits[0]); return; }
    try {
      const r = await J.scan(term);
      if (r.data?.available === false) { Alert.alert('Cannot bill this piece', r.data?.message || 'Not available here.'); return; }
      push(r.data);
    } catch (e) { Alert.alert('Not found', errText(e)); }
  };
  const push = (x: any) => { if (!cart.add(pieceOf(x))) Alert.alert('Already on the bill', `${x.tagNo} is already added.`); setQ(''); setHits([]); };

  const total = num(preview?.total);
  const paid = cart.payments.reduce((t, p) => t + num(p.amount), 0);
  const setPay = (i: number, p: Partial<Pay>) => cart.setPayments(cart.payments.map((x, j) => j === i ? {...x, ...p} : x));

  const complete = async () => {
    if (!cart.pieces.length) return;
    if (!cart.customer && paid + 0.005 < total) { Alert.alert('Walk-in bill', 'A walk-in bill must be paid in full. Add the customer to leave a balance.'); return; }
    if (paid > total + 0.005) { Alert.alert('Too much', `Payments are ${money(paid - total)} more than the bill. Give the change back instead.`); return; }
    const go = async () => {
      setBusy(true);
      try {
        const r = await J.createSale({...body(), clientRef: cart.clientRef, payments: cart.payments.filter(p => num(p.amount) > 0).map(p => ({mode: p.mode, amount: num(p.amount), reference: p.reference || undefined}))});
        cart.reset(); setPreview(null);
        router.push({pathname: '/(main)/sale', params: {id: String(r.data.id), done: '1'}});
      } catch (e) { Alert.alert('Bill not saved', errText(e)); }
      finally { setBusy(false); }
    };
    if (paid + 0.005 < total) Alert.alert('Leave a balance?', `${cart.customer?.name} will owe ${money(total - paid)}.`, [{text: 'Cancel', style: 'cancel'}, {text: 'Complete', onPress: go}]);
    else void go();
  };

  return <Screen title="New bill" right={cart.pieces.length ? <Btn title="Clear" kind="ghost" onPress={() => Alert.alert('Clear this bill?', 'The pieces stay in stock.', [{text: 'Keep'}, {text: 'Clear', style: 'destructive', onPress: () => cart.reset()}])} /> : null}>
    <View style={{flexDirection: 'row', gap: 8}}>
      <TextInput style={[s.input, {flex: 1}]} placeholder="Tag, barcode, HUID or name" placeholderTextColor="#9C9488" value={q} onChangeText={setQ} onSubmitEditing={() => add()} autoCapitalize="characters" returnKeyType="search" accessibilityLabel="Search piece" />
      <Btn title="Scan" kind="secondary" onPress={() => router.push('/(main)/scan')} />
    </View>
    {hits.map(h => <Card key={h.id} onPress={() => push(h)} style={{marginTop: 6}}><Text style={s.strong}>{h.tagNo} · {h.productName}</Text><Text style={s.sub}>{h.purity?.name} · {grams(h.netWeight)}{h.huid ? ` · HUID ${h.huid}` : ''}</Text></Card>)}

    <Section title={`Pieces (${cart.pieces.length})`} />
    {!cart.pieces.length && <Card><Text style={s.sub}>Search or scan a piece to start. Prices use today's saved gold rate.</Text></Card>}
    {cart.pieces.map((p, i) => { const l = preview?.lines?.[i]; return <Card key={p.id}>
      <View style={s.between}><Text style={s.strong}>{p.tagNo} · {p.name}</Text><Pressable onPress={() => cart.remove(p.id)} hitSlop={12} accessibilityLabel={`Remove ${p.tagNo}`}><Text style={{color: C.bad, fontWeight: '700'}}>Remove</Text></Pressable></View>
      <Text style={s.sub}>{p.purity} · {grams(p.net)} net{l ? ` · ${money(l.rate)}/g · making ${money(l.makingCharge)}` : ''}</Text>
      {l?.rateMissing && <Text style={{color: C.bad}}>Rate not set for today</Text>}
      <Text style={[s.strong, {marginTop: 4}]}>{l ? money(l.total) : '…'}</Text>
    </Card>; })}

    <Section title="Customer" />
    {cart.customer ? <Card><View style={s.between}><View><Text style={s.strong}>{cart.customer.name}</Text><Text style={s.sub}>{cart.customer.phone || 'new customer'}</Text></View><Btn title="Change" kind="ghost" onPress={() => cart.setCustomer(null)} /></View></Card>
      : <>
        <TextInput style={s.input} placeholder="Search name or phone (empty = walk-in)" placeholderTextColor="#9C9488" value={cq} onChangeText={setCq} accessibilityLabel="Search customer" />
        {cHits.map(c => <Card key={c.id} style={{marginTop: 6}} onPress={() => { cart.setCustomer({id: c.id, name: c.name, phone: c.phone}); setCq(''); }}><Text style={s.strong}>{c.name}</Text><Text style={s.sub}>{c.phone}{num(c.owes) > 0 ? ` · owes ${money(c.owes)}` : ''}</Text></Card>)}
        <View style={{flexDirection: 'row', gap: 8, marginTop: 8}}>
          <TextInput style={[s.input, {flex: 1}]} placeholder="New: name" placeholderTextColor="#9C9488" value={newCust.name} onChangeText={v => setNewCust({...newCust, name: v})} accessibilityLabel="New customer name" />
          <TextInput style={[s.input, {flex: 1}]} placeholder="Phone" keyboardType="phone-pad" placeholderTextColor="#9C9488" value={newCust.phone} onChangeText={v => setNewCust({...newCust, phone: v})} accessibilityLabel="New customer phone" />
        </View>
        {!!newCust.name.trim() && <Btn title={`Use ${newCust.name.trim()}`} kind="secondary" style={{marginTop: 8}} onPress={() => { cart.setCustomer({name: newCust.name.trim(), phone: newCust.phone.trim() || undefined}); setNewCust({name: '', phone: ''}); }} />}
      </>}

    {cart.pieces.length > 0 && <>
      <Section title="Total" />
      {perr ? <Note text={perr} tone="bad" /> : null}
      {(preview?.warnings || []).map((w: string) => <Note key={w} text={w} tone="warn" />)}
      <Card>
        <Row k="Metal" v={money(preview?.metalValue)} /><Row k="Wastage" v={money(preview?.wastage)} /><Row k="Making" v={money(preview?.making)} />
        {num(preview?.stone) > 0 && <Row k="Stones" v={money(preview?.stone)} />}
        <Row k="Taxable" v={money(preview?.taxable)} /><Row k="GST" v={money(preview?.gst)} />
        {num(preview?.roundOff) !== 0 && <Row k="Round off" v={money(preview?.roundOff)} />}
        <Row k="Total" v={money(total)} strong />
      </Card>
      <Section title="Payment" />
      {cart.payments.map((p, i) => <Card key={i}>
        <Chips value={p.mode} onChange={m => setPay(i, {mode: m})} options={MODES.map(m => ({value: m, label: label(m)}))} />
        <Field label="Amount (₹)" keyboardType="decimal-pad" value={p.amount} onChangeText={v => setPay(i, {amount: v})} />
        {p.mode !== 'CASH' && <Field label="Reference / UTR" value={p.reference} onChangeText={v => setPay(i, {reference: v})} />}
        {cart.payments.length > 1 && <Btn title="Remove payment" kind="ghost" onPress={() => cart.setPayments(cart.payments.filter((_, j) => j !== i))} />}
      </Card>)}
      <View style={{flexDirection: 'row', gap: 8}}>
        <Btn title="Fill full amount" kind="secondary" style={{flex: 1}} onPress={() => cart.setPayments([{...cart.payments[0], amount: String(Math.max(0, total - cart.payments.slice(1).reduce((t, x) => t + num(x.amount), 0)).toFixed(2))}, ...cart.payments.slice(1)])} />
        <Btn title="Split" kind="secondary" style={{flex: 1}} onPress={() => cart.setPayments([...cart.payments, {mode: 'UPI', amount: String(Math.max(0, total - paid).toFixed(2)), reference: ''}])} />
      </View>
      <Text style={[s.sub, {marginVertical: 8}]}>{paid > total + 0.005 ? `${money(paid - total)} more than the bill` : total - paid > 0.005 ? `${money(total - paid)} left as credit` : 'Fully paid'}</Text>
      <Btn title="Complete bill" onPress={complete} busy={busy} disabled={!!perr || !preview} />
    </>}
  </Screen>;
}
