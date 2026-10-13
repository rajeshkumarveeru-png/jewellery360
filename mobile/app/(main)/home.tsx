import React from 'react';
import {Text, View} from 'react-native';
import {router} from 'expo-router';
import {J} from '@/src/api';
import {can, useSession} from '@/src/session';
import {Btn, C, Card, Note, Row, Screen, Section, State, grams, money, num, s, useLoad} from '@/src/ui';

const PAGE_ROUTE: Record<string, string> = {GOLD_RATES: '/(main)/rates', CUSTOMERS: '/(main)/customers', SERVICES: '/(main)/work', MONEY: '/(main)/dayclose', STOCK: '/(main)/stock'};

export default function Home() {
  const user = useSession(st => st.user);
  const platform = user?.role === 'APP_ADMIN';
  const d = useLoad(() => J.dashboard(), [], !platform && can(user, 'OVERVIEW'));
  const x: any = d.data;
  if (platform) return <Screen title="Jewellery360"><Note text="The platform administrator works with many companies - please use the web app. The mobile app is for shop staff and owners." /></Screen>;
  if (!can(user, 'OVERVIEW')) return <Screen title={user?.companyName || 'Jewellery360'} subtitle={user?.username}>
    <Note text="Your account does not include the dashboard. Use the tabs below for the pages you can open." />
    {can(user, 'BILLING') && <Btn title="New bill" onPress={() => router.push('/(main)/bill')} />}
  </Screen>;
  const t = x?.today || {};
  return <Screen title={user?.companyName || 'Jewellery360'} subtitle={`${x?.branch || user?.branchName || ''} · ${user?.username}`} onRefresh={d.reload} refreshing={d.loading}>
    <State loading={d.loading} error={d.error} onRetry={d.reload} empty={!x}>
      {(x?.insights || []).map((i: any, k: number) => <Card key={k} onPress={PAGE_ROUTE[i.page] ? () => router.push(PAGE_ROUTE[i.page] as any) : undefined}
        style={{backgroundColor: i.tone === 'warning' ? C.warnSoft : C.goldSoft, borderColor: 'transparent'}}><Text style={{color: C.ink}}>{i.text}</Text></Card>)}
      <Card>
        <Text style={s.sub}>TODAY'S SALES</Text>
        <Text style={{fontSize: 30, fontWeight: '800', color: C.ink, marginVertical: 4}}>{money(t.sales)}</Text>
        <Row k="Bills" v={num(t.bills)} /><Row k="Gold sold" v={grams(t.goldSoldGrams)} /><Row k="Money in" v={money(t.moneyIn)} /><Row k="Money out" v={money(t.moneyOut)} />
      </Card>
      {can(user, 'BILLING') && <View style={{flexDirection: 'row', gap: 10, marginBottom: 10}}>
        <Btn title="New bill" onPress={() => router.push('/(main)/bill')} style={{flex: 1}} />
        <Btn title="Bills" kind="secondary" onPress={() => router.push('/(main)/bills')} style={{flex: 1}} />
      </View>}
      <Section title="Selling rate per gram" />
      <Card onPress={() => router.push('/(main)/rates')}>
        {(x?.rates || []).length ? x.rates.map((r: any) => <Row key={r.purity} k={r.purity} v={`${r.rate != null ? money(r.rate) : '—'}${r.setToday ? '' : '  (not set today)'}`} />) : <Text style={s.sub}>No rates saved yet.</Text>}
      </Card>
      <Section title="Money owed" />
      <Card>
        <Row k="Customers owe you" v={money(x?.dues?.customersOwe)} />
        <Row k="Advances held" v={money(x?.dues?.advancesHeld)} />
        {x?.dues?.weOweSuppliers !== undefined && <Row k="You owe suppliers" v={money(x.dues.weOweSuppliers)} />}
      </Card>
      <Section title="Work" />
      <Card onPress={can(user, 'SERVICES') ? () => router.push('/(main)/work') : undefined}>
        <Row k="Open orders" v={num(x?.work?.orders)} /><Row k="Open repairs" v={num(x?.work?.repairs)} />
        <Row k="Overdue" v={num(x?.work?.overdue)} /><Row k="Ready to deliver" v={num(x?.work?.readyToDeliver)} />
      </Card>
      {(x?.celebrating || []).length > 0 && <><Section title="Celebrating today" />{x.celebrating.map((c: any) =>
        <Card key={c.id} onPress={() => router.push({pathname: '/(main)/customer', params: {id: String(c.id)}})}><Text style={s.strong}>{c.name}</Text><Text style={s.sub}>{c.occasion}{c.phone ? ` · ${c.phone}` : ''}</Text></Card>)}</>}
    </State>
  </Screen>;
}
