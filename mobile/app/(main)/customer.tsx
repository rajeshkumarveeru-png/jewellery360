import React, {useState} from 'react';
import {Alert, Linking, Text, View} from 'react-native';
import {router, useLocalSearchParams} from 'expo-router';
import {J, errText} from '@/src/api';
import {useCart} from '@/src/cart';
import {Btn, C, Card, Chips, Field, Note, Row, Screen, Section, State, label, money, num, s, useLoad} from '@/src/ui';

/* Customer profile: dues, advances, history; collect a payment or start a bill for them. */
export default function Customer() {
  const {id} = useLocalSearchParams<{id: string}>();
  const p = useLoad(() => J.customer(Number(id)), [id], !!id);
  const d: any = p.data;
  const setCustomer = useCart(c => c.setCustomer);
  const [collect, setCollect] = useState({open: false, amount: '', mode: 'CASH'});
  const [busy, setBusy] = useState(false);
  const c = d?.customer, sm = d?.summary || {};
  const save = async (toAdvance = false) => {
    setBusy(true);
    try {
      await J.collect(c.id, {amount: num(collect.amount), mode: collect.mode, excessToAdvance: toAdvance});
      setCollect({open: false, amount: '', mode: 'CASH'}); await p.reload(); Alert.alert('Payment saved');
    } catch (e) { Alert.alert('Not saved', errText(e)); }
    finally { setBusy(false); }
  };
  return <Screen title={c?.name || 'Customer'} subtitle={c?.phone} onRefresh={p.reload} refreshing={p.loading} right={<Btn title="Back" kind="ghost" onPress={() => router.back()} />}>
    <State loading={p.loading} error={p.error} onRetry={p.reload} empty={!c}>
      {c && <>
        <Note text={sm.message || ''} tone={num(sm.owes) > 0 ? 'bad' : undefined} />
        <View style={{flexDirection: 'row', gap: 8, marginBottom: 10}}>
          <Btn title="New bill" style={{flex: 1}} onPress={() => { setCustomer({id: c.id, name: c.name, phone: c.phone}); router.push('/(main)/bill'); }} />
          {num(sm.owes) > 0 && <Btn title="Collect" kind="secondary" style={{flex: 1}} onPress={() => setCollect({...collect, open: true, amount: String(num(sm.owes))})} />}
          {c.phone ? <Btn title="Call" kind="secondary" onPress={() => Linking.openURL(`tel:${c.phone}`)} /> : null}
        </View>
        {collect.open && <Card>
          <Chips value={collect.mode} onChange={m => setCollect({...collect, mode: m})} options={['CASH', 'UPI', 'CARD', 'BANK'].map(m => ({value: m, label: label(m)}))} />
          <Field label={`Amount (owes ${money(sm.owes)})`} keyboardType="decimal-pad" value={collect.amount} onChangeText={v => setCollect({...collect, amount: v})} />
          <Btn title="Save payment" busy={busy} disabled={!(num(collect.amount) > 0)} onPress={() => num(collect.amount) > num(sm.owes)
            ? Alert.alert('More than due', `Keep the extra ${money(num(collect.amount) - num(sm.owes))} as advance?`, [{text: 'Cancel'}, {text: 'Keep as advance', onPress: () => void save(true)}])
            : void save()} />
        </Card>}
        <Card><Row k="Bills" v={sm.bills} /><Row k="Bought" v={money(sm.purchases)} /><Row k="Owes" v={money(sm.owes)} /><Row k="Advance" v={money(sm.advance)} /><Row k="Last visit" v={sm.lastVisit || '—'} /></Card>
        {(d.unpaidBills || []).length > 0 && <><Section title="Bills with balance" />{d.unpaidBills.map((b: any) =>
          <Card key={b.id} onPress={() => router.push({pathname: '/(main)/sale', params: {id: String(b.id)}})}><Row k={`${b.invoiceNo} · ${b.date}`} v={money(b.balance)} /></Card>)}</>}
        <Section title="History" />
        {(d.timeline || []).slice(0, 30).map((t: any, i: number) => <Card key={i}><Text style={s.strong}>{t.kind}</Text><Text style={s.sub}>{t.date} · {t.title}{t.reference ? ` · ${t.reference}` : ''}</Text>{t.amount != null && <Text style={{color: C.ink, marginTop: 2}}>{money(t.amount)}</Text>}</Card>)}
      </>}
    </State>
  </Screen>;
}
