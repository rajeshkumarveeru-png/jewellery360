import React, {useState} from 'react';
import {Alert, Linking, Text, View} from 'react-native';
import {router, useLocalSearchParams} from 'expo-router';
import {J, errText} from '@/src/api';
import {Btn, C, Card, Chips, Field, Note, Pill, Row, Screen, Section, State, grams, label, money, num, s, useLoad} from '@/src/ui';

/* One bill: lines, totals, payments; take a further payment or share it on WhatsApp. */
export default function Sale() {
  const {id, done} = useLocalSearchParams<{id: string; done?: string}>();
  const b = useLoad(() => J.sale(Number(id)), [id], !!id);
  const x: any = b.data;
  const [pay, setPay] = useState({mode: 'CASH', amount: ''});
  const [busy, setBusy] = useState(false);
  const takePayment = async () => {
    setBusy(true);
    try { await J.paySale(x.id, [{mode: pay.mode, amount: num(pay.amount)}]); setPay({mode: 'CASH', amount: ''}); await b.reload(); Alert.alert('Payment saved'); }
    catch (e) { Alert.alert('Payment not saved', errText(e)); }
    finally { setBusy(false); }
  };
  const share = async () => {
    try {
      const r = await J.shareSale(x.id);
      if (r.data?.status === 'SENT') Alert.alert('WhatsApp', r.data.message || 'Invoice sent.');
      else if (r.data?.shareUrl) await Linking.openURL(r.data.shareUrl);   // WhatsApp Business not set up: open WhatsApp with the message ready
    } catch (e) { Alert.alert('WhatsApp', errText(e)); }
  };
  return <Screen title={x ? x.invoiceNo : 'Bill'} subtitle={x ? `${x.date} · ${x.walkIn ? 'Walk-in' : x.customer?.name}` : undefined} onRefresh={b.reload} refreshing={b.loading}
                 right={<Btn title={done ? 'New bill' : 'Back'} kind="ghost" onPress={() => done ? router.replace('/(main)/bill') : router.back()} />}>
    <State loading={b.loading} error={b.error} onRetry={b.reload} empty={!x}>
      {x && <>
        {done ? <Note text="Bill completed." tone="good" /> : null}
        <Card>
          <View style={s.between}><Text style={s.strong}>{money(x.total)}</Text><Pill text={x.label} tone={x.label === 'Paid' ? 'good' : x.label === 'Cancelled' ? 'bad' : 'warn'} /></View>
          <Row k="Paid" v={money(x.paid)} />{num(x.returned) > 0 && <Row k="Returned" v={money(x.returned)} />}<Row k="Balance due" v={money(x.balance)} strong />
        </Card>
        {x.customer?.phone && x.status !== 'DRAFT' ? <Btn title="Share on WhatsApp" kind="secondary" onPress={share} /> : null}
        {num(x.balance) > 0 && <><Section title="Take payment" /><Card>
          <Chips value={pay.mode} onChange={m => setPay({...pay, mode: m})} options={['CASH', 'UPI', 'CARD', 'BANK', 'ADVANCE'].map(m => ({value: m, label: label(m)}))} />
          <Field label={`Amount (due ${money(x.balance)})`} keyboardType="decimal-pad" value={pay.amount} onChangeText={v => setPay({...pay, amount: v})} />
          <Btn title="Save payment" onPress={takePayment} busy={busy} disabled={!(num(pay.amount) > 0)} />
        </Card></>}
        <Section title="Pieces" />
        {(x.lines || []).map((l: any) => <Card key={l.id}><Text style={s.strong}>{l.tagNo} · {l.description}{l.returned ? ' (returned)' : ''}</Text>
          <Text style={s.sub}>{l.purity} · {grams(l.netWeight)} · {money(l.rate)}/g{l.huid ? ` · HUID ${l.huid}` : ''}</Text><Text style={[s.strong, {marginTop: 4}]}>{money(l.total)}</Text></Card>)}
        <Card><Row k="Taxable" v={money(x.totals?.taxable)} /><Row k="GST" v={money(x.totals?.gst)} />{num(x.totals?.discount) > 0 && <Row k="Discount" v={money(x.totals.discount)} />}<Row k="Total" v={money(x.total)} strong /></Card>
        {(x.payments || []).length > 0 && <><Section title="Payments" />{x.payments.map((p: any) => <Card key={p.id}><Row k={`${p.paymentNo} · ${label(p.mode)}`} v={`${p.direction === 'OUT' ? '− ' : ''}${money(p.amount)}`} /><Text style={s.sub}>{p.date}{p.reference ? ` · ${p.reference}` : ''}</Text></Card>)}</>}
        <Text style={[s.sub, {color: C.muted}]}>Returns, cancellation and printing are on the web app.</Text>
      </>}
    </State>
  </Screen>;
}
