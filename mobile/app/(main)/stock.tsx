import React, {useState} from 'react';
import {Text, TextInput, View} from 'react-native';
import {router} from 'expo-router';
import {J} from '@/src/api';
import {Btn, Card, Chips, Pill, Screen, State, grams, label, num, s, useLoad} from '@/src/ui';

/* Find any piece by tag, barcode, HUID or name; adding and editing pieces is on the web app. */
export default function Stock() {
  const [q, setQ] = useState('');
  const [query, setQuery] = useState('');
  const [status, setStatus] = useState<'IN_STOCK' | 'SOLD' | 'ALL'>('IN_STOCK');
  const list = useLoad(() => J.stock({status, q: query, limit: 200}), [status, query]);
  const rows: any[] = (list.data as any) || [];
  const net = rows.reduce((t, r) => t + num(r.netWeight), 0);
  return <Screen title="Stock" subtitle={`${rows.length} pieces · ${grams(net)} net`} onRefresh={list.reload} refreshing={list.loading}>
    <View style={{flexDirection: 'row', gap: 8, marginBottom: 10}}>
      <TextInput style={[s.input, {flex: 1}]} placeholder="Tag, barcode, HUID or name" placeholderTextColor="#9C9488" value={q} onChangeText={setQ} onSubmitEditing={() => setQuery(q.trim())} autoCapitalize="characters" returnKeyType="search" accessibilityLabel="Search stock" />
      <Btn title="Search" kind="secondary" onPress={() => setQuery(q.trim())} />
    </View>
    <Chips value={status} onChange={setStatus} options={[{value: 'IN_STOCK', label: 'In stock'}, {value: 'SOLD', label: 'Sold'}, {value: 'ALL', label: 'All'}]} />
    <State loading={list.loading} error={list.error} onRetry={list.reload} empty={!rows.length} emptyText={query ? 'Nothing matches' : 'No pieces here'} emptyHint="Pieces are added from the web app (Stock or Purchases).">
      {rows.map(r => <Card key={r.id} onPress={() => router.push({pathname: '/(main)/piece', params: {id: String(r.id)}})}>
        <View style={s.between}><Text style={s.strong}>{r.tagNo} · {r.productName}</Text><Pill text={label(r.status)} tone={r.status === 'IN_STOCK' ? 'good' : undefined} /></View>
        <Text style={s.sub}>{r.purity?.name} · {grams(r.grossWeight)} gross · {grams(r.netWeight)} net{r.huid ? ` · HUID ${r.huid}` : ''}</Text>
        <Text style={s.sub}>{r.branchName}{r.location ? ` · ${r.location}` : ''} · {r.ageDays} days</Text>
      </Card>)}
    </State>
  </Screen>;
}
