import React, {useState} from 'react';
import {Text, TextInput, View} from 'react-native';
import {router} from 'expo-router';
import {J} from '@/src/api';
import {Btn, Card, Chips, Pill, Screen, State, money, num, s, useLoad} from '@/src/ui';

/* Bill history: last 30 days, or every bill with a balance due. */
export default function Bills() {
  const [status, setStatus] = useState<'' | 'UNPAID'>('');
  const [q, setQ] = useState('');
  const [query, setQuery] = useState('');
  const list = useLoad(() => J.sales({status, q: query, limit: 100}), [status, query]);
  const rows: any[] = (list.data as any) || [];
  return <Screen title="Bills" onRefresh={list.reload} refreshing={list.loading} right={<Btn title="Back" kind="ghost" onPress={() => router.back()} />}>
    <Chips value={status} onChange={setStatus} options={[{value: '', label: 'Last 30 days'}, {value: 'UNPAID', label: 'Balance due'}]} />
    <View style={{flexDirection: 'row', gap: 8, marginBottom: 10}}>
      <TextInput style={[s.input, {flex: 1}]} placeholder="Invoice, customer or phone" placeholderTextColor="#9C9488" value={q} onChangeText={setQ} onSubmitEditing={() => setQuery(q.trim())} returnKeyType="search" accessibilityLabel="Search bills" />
      <Btn title="Search" kind="secondary" onPress={() => setQuery(q.trim())} />
    </View>
    <State loading={list.loading} error={list.error} onRetry={list.reload} empty={!rows.length} emptyText="No bills found" emptyHint="Change the filter or search.">
      {rows.map(r => <Card key={r.id} onPress={() => router.push({pathname: '/(main)/sale', params: {id: String(r.id)}})}>
        <View style={s.between}><Text style={s.strong}>{r.invoiceNo}</Text><Pill text={r.label} tone={r.label === 'Paid' ? 'good' : r.label === 'Cancelled' ? 'bad' : 'warn'} /></View>
        <Text style={s.sub}>{r.date} · {r.customer?.name}</Text>
        <Text style={{marginTop: 4}}>{money(r.total)}{num(r.balance) > 0 ? `  ·  due ${money(r.balance)}` : ''}</Text>
      </Card>)}
    </State>
  </Screen>;
}
