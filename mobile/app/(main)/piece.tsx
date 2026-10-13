import React from 'react';
import {Text} from 'react-native';
import {router, useLocalSearchParams} from 'expo-router';
import {J} from '@/src/api';
import {pieceOf, useCart} from '@/src/cart';
import {can, useSession} from '@/src/session';
import {Btn, Card, Row, Screen, Section, State, grams, label, money, num, s, useLoad} from '@/src/ui';

export default function Piece() {
  const {id} = useLocalSearchParams<{id: string}>();
  const user = useSession(st => st.user);
  const add = useCart(c => c.add);
  const t = useLoad(() => J.timeline(Number(id)), [id], !!id);
  const d: any = t.data, p = d?.item;
  return <Screen title={p ? `${p.tagNo}` : 'Piece'} subtitle={p?.productName} onRefresh={t.reload} refreshing={t.loading} right={<Btn title="Back" kind="ghost" onPress={() => router.back()} />}>
    <State loading={t.loading} error={t.error} onRetry={t.reload} empty={!p}>
      {p && <>
        {p.status === 'IN_STOCK' && can(user, 'BILLING') && <Btn title="Add to bill" onPress={() => { add(pieceOf(p)); router.push('/(main)/bill'); }} style={{marginBottom: 10}} />}
        <Card>
          <Row k="Status" v={label(p.status)} /><Row k="Purity" v={p.purity?.name} /><Row k="Gross" v={grams(p.grossWeight)} /><Row k="Stone" v={grams(p.stoneWeight)} />
          <Row k="Net" v={grams(p.netWeight)} /><Row k="HUID" v={p.huid || '—'} /><Row k="Category" v={p.category || '—'} />
          <Row k="Wastage" v={`${num(p.wastagePercent)} %`} /><Row k="Making" v={`${label(p.makingType)} ${num(p.makingValue)}`} />
          {num(p.mrp) > 0 && <Row k="Fixed price" v={money(p.mrp)} />}
          <Row k="Branch" v={p.branchName} /><Row k="Location" v={p.location || '—'} /><Row k="In stock for" v={`${p.ageDays} days`} />
        </Card>
        <Section title="History" />
        {(d.events || []).map((e: any, i: number) => <Card key={i}><Text style={s.strong}>{label(e.type)}</Text><Text style={s.sub}>{e.date} · {e.branch}{e.by ? ` · ${e.by}` : ''}</Text>{e.notes ? <Text style={s.sub}>{e.notes}</Text> : null}</Card>)}
      </>}
    </State>
  </Screen>;
}
