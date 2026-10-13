import React from 'react';
import {Alert, Text} from 'react-native';
import {router} from 'expo-router';
import {API_URL} from '@/src/api';
import {can, useSession} from '@/src/session';
import {Btn, Card, Screen, Section, s} from '@/src/ui';

export default function More() {
  const {user, signOut} = useSession();
  const items = [
    {page: 'BILLING', title: 'Bills', hint: 'History, balances, take payments', to: '/(main)/bills'},
    {page: 'GOLD_RATES', title: 'Gold rates', hint: "Today's selling rates", to: '/(main)/rates'},
    {page: 'SERVICES', title: 'Orders & repairs', hint: 'Move work along, see what is ready', to: '/(main)/work'},
    {page: 'MONEY', title: 'Day close', hint: "Count the drawer against today's books", to: '/(main)/dayclose'},
  ].filter(i => can(user, i.page));
  return <Screen title="More" subtitle={`${user?.username} · ${(user?.role || '').replace(/_/g, ' ').toLowerCase()}`}>
    {items.map(i => <Card key={i.title} onPress={() => router.push(i.to as any)}><Text style={s.strong}>{i.title}</Text><Text style={s.sub}>{i.hint}</Text></Card>)}
    <Section title="Account" />
    <Card><Text style={s.sub}>{user?.companyName} · {user?.branchName || 'All branches'}</Text><Text style={s.sub}>Server: {API_URL}</Text>
      <Text style={s.sub}>Stock entry, purchases, reports, settings and users are on the web app.</Text></Card>
    <Btn title="Sign out" kind="danger" onPress={() => Alert.alert('Sign out?', '', [{text: 'Cancel'}, {text: 'Sign out', style: 'destructive', onPress: () => void signOut()}])} />
  </Screen>;
}
