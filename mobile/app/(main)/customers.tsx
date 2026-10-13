import React, {useState} from 'react';
import {Alert, Text, TextInput, View} from 'react-native';
import {router} from 'expo-router';
import {J, errText} from '@/src/api';
import {Btn, C, Card, Chips, Field, Screen, State, money, num, s, useLoad} from '@/src/ui';

export default function Customers() {
  const [q, setQ] = useState('');
  const [query, setQuery] = useState('');
  const [show, setShow] = useState<'all' | 'owes'>('all');
  const [adding, setAdding] = useState(false);
  const [f, setF] = useState({name: '', phone: ''});
  const [busy, setBusy] = useState(false);
  const list = useLoad(() => J.customers(query, 300), [query]);
  const rows: any[] = ((list.data as any) || []).filter((c: any) => !c.walkIn && (show === 'all' || num(c.owes) > 0));
  const save = async (allowDuplicate = false) => {
    setBusy(true);
    try {
      const r = await J.saveCustomer({name: f.name.trim(), phone: f.phone.trim() || undefined, allowDuplicate});
      setAdding(false); setF({name: '', phone: ''}); void list.reload();
      router.push({pathname: '/(main)/customer', params: {id: String(r.data.id)}});
    } catch (e: any) {
      if (e?.response?.status === 409 && !allowDuplicate) Alert.alert('Phone already used', errText(e), [{text: 'Cancel'}, {text: 'Save anyway', onPress: () => void save(true)}]);
      else Alert.alert('Not saved', errText(e));
    } finally { setBusy(false); }
  };
  return <Screen title="Customers" onRefresh={list.reload} refreshing={list.loading} right={<Btn title={adding ? 'Cancel' : '+ Add'} kind="ghost" onPress={() => setAdding(!adding)} />}>
    {adding && <Card>
      <Field label="Name *" value={f.name} onChangeText={v => setF({...f, name: v})} />
      <Field label="Phone" keyboardType="phone-pad" value={f.phone} onChangeText={v => setF({...f, phone: v})} />
      <Btn title="Save customer" onPress={() => save()} busy={busy} disabled={!f.name.trim()} />
    </Card>}
    <View style={{flexDirection: 'row', gap: 8, marginBottom: 10}}>
      <TextInput style={[s.input, {flex: 1}]} placeholder="Name or phone" placeholderTextColor="#9C9488" value={q} onChangeText={setQ} onSubmitEditing={() => setQuery(q.trim())} returnKeyType="search" accessibilityLabel="Search customers" />
      <Btn title="Search" kind="secondary" onPress={() => setQuery(q.trim())} />
    </View>
    <Chips value={show} onChange={setShow} options={[{value: 'all', label: 'All'}, {value: 'owes', label: 'Owes money'}]} />
    <State loading={list.loading} error={list.error} onRetry={list.reload} empty={!rows.length} emptyText={query ? 'No customer matches' : 'No customers here'} emptyHint="Customers are also added from a bill.">
      {rows.map(c => <Card key={c.id} onPress={() => router.push({pathname: '/(main)/customer', params: {id: String(c.id)}})}>
        <Text style={s.strong}>{c.name}</Text><Text style={s.sub}>{c.phone || 'no phone'} · {c.bills} bills{c.lastVisit ? ` · last ${c.lastVisit}` : ''}</Text>
        {num(c.owes) > 0 && <Text style={{color: C.bad, fontWeight: '700', marginTop: 4}}>Owes {money(c.owes)}</Text>}
        {num(c.advance) > 0 && <Text style={{color: C.good, marginTop: 2}}>Advance {money(c.advance)}</Text>}
      </Card>)}
    </State>
  </Screen>;
}
