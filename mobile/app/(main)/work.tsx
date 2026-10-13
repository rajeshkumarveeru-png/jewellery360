import React, {useState} from 'react';
import {Alert, Text, View} from 'react-native';
import {router} from 'expo-router';
import {J, errText} from '@/src/api';
import {useSession} from '@/src/session';
import {Btn, C, Card, Chips, Pill, Screen, State, grams, label, money, num, s, useLoad} from '@/src/ui';

/* Open orders and repairs: move them along. Final bills for orders and repair delivery with payment are done on the web
   app or at the counter, because they take money. */
const NEXT_ORDER: Record<string, string> = {ORDERED: 'IN_PROGRESS', IN_PROGRESS: 'READY'};
const NEXT_REPAIR: Record<string, string> = {RECEIVED: 'IN_REPAIR', IN_REPAIR: 'READY'};

export default function Work() {
  const viewer = useSession(st => st.user?.role === 'VIEWER');
  const [tab, setTab] = useState<'orders' | 'repairs'>('orders');
  const o = useLoad(() => J.orders(), []);
  const r = useLoad(() => J.repairs(), []);
  const orders = ((o.data as any) || []).filter((x: any) => ['ORDERED', 'IN_PROGRESS', 'READY'].includes(x.status));
  const repairs = ((r.data as any) || []).filter((x: any) => ['RECEIVED', 'IN_REPAIR', 'READY'].includes(x.status));
  const move = (kind: 'order' | 'repair', x: any, next: string) => Alert.alert(`Mark ${x.orderNo} ${label(next).toLowerCase()}?`, '', [{text: 'Cancel'}, {text: 'Confirm', onPress: async () => {
    try { if (kind === 'order') { await J.updateOrder(x.id, {status: next}); await o.reload(); } else { await J.updateRepair(x.id, {status: next}); await r.reload(); } }
    catch (e) { Alert.alert('Not updated', errText(e)); }
  }}]);
  const cur = tab === 'orders' ? o : r;
  const rows: any[] = tab === 'orders' ? orders : repairs;
  return <Screen title="Orders & repairs" onRefresh={cur.reload} refreshing={cur.loading} right={<Btn title="Back" kind="ghost" onPress={() => router.back()} />}>
    <Chips value={tab} onChange={setTab} options={[{value: 'orders', label: `Orders (${orders.length})`}, {value: 'repairs', label: `Repairs (${repairs.length})`}]} />
    <State loading={cur.loading} error={cur.error} onRetry={cur.reload} empty={!rows.length} emptyText={`No open ${tab}`}>
      {rows.map(x => { const next = (tab === 'orders' ? NEXT_ORDER : NEXT_REPAIR)[x.status]; const due = tab === 'orders' ? x.deliveryDate : x.expectedDate; return <Card key={x.id}>
        <View style={s.between}><Text style={s.strong}>{x.orderNo}</Text><Pill text={label(x.status)} tone={x.status === 'READY' ? 'good' : x.overdue ? 'bad' : undefined} /></View>
        <Text style={s.sub}>{x.customer?.name}{x.customer?.phone ? ` · ${x.customer.phone}` : ''}</Text>
        <Text style={{color: C.ink, marginTop: 4}}>{tab === 'orders' ? x.description : `${x.itemDescription} - ${x.problem}`}</Text>
        <Text style={s.sub}>{due ? `Due ${due}` : 'No date'}{x.overdue ? ' · overdue' : ''}{tab === 'orders' && num(x.targetWeight) ? ` · ${grams(x.targetWeight)}` : ''} · {tab === 'orders' ? `advance ${money(x.advancePaid)}` : `due ${money(x.balance)}`}</Text>
        {next && !viewer && <Btn title={`Mark ${label(next).toLowerCase()}`} kind="secondary" style={{marginTop: 8}} onPress={() => move(tab === 'orders' ? 'order' : 'repair', x, next)} />}
        {x.status === 'READY' && <Text style={[s.sub, {marginTop: 6}]}>{tab === 'orders' ? 'Ready - make the final bill at the counter (web).' : 'Ready - collect the charge and deliver at the counter (web).'}</Text>}
      </Card>; })}
    </State>
  </Screen>;
}
