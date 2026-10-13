import React from 'react';
import {Redirect, Tabs} from 'expo-router';
import {Ionicons} from '@expo/vector-icons';
import {can, useSession} from '@/src/session';
import {C} from '@/src/ui';

/* Bottom tabs follow the user's permissions (the server checks every request again). Detail screens are hidden tabs. */
export default function MainLayout() {
  const {token, user, ready} = useSession();
  if (!ready) return null;   // wait for the saved session before deciding, so deep links survive a cold start
  if (!token) return <Redirect href="/(auth)/login" />;
  const show = (page: string) => (can(user, page) ? undefined : null);
  const icon = (n: string) => ({color, size}: {color: any; size: number}) => <Ionicons name={n as any} color={color} size={size} />;
  return <Tabs screenOptions={{headerShown: false, tabBarActiveTintColor: C.gold, tabBarInactiveTintColor: '#8A8378', tabBarStyle: {height: 64, paddingBottom: 8, paddingTop: 6}, tabBarLabelStyle: {fontSize: 12}}}>
    <Tabs.Screen name="home" options={{title: 'Home', tabBarIcon: icon('grid-outline'), href: show('OVERVIEW')}} />
    <Tabs.Screen name="bill" options={{title: 'Bill', tabBarIcon: icon('receipt-outline'), href: show('BILLING')}} />
    <Tabs.Screen name="customers" options={{title: 'Customers', tabBarIcon: icon('people-outline'), href: show('CUSTOMERS')}} />
    <Tabs.Screen name="stock" options={{title: 'Stock', tabBarIcon: icon('diamond-outline'), href: show('STOCK')}} />
    <Tabs.Screen name="more" options={{title: 'More', tabBarIcon: icon('ellipsis-horizontal-circle-outline')}} />
    <Tabs.Screen name="bills" options={{href: null}} />
    <Tabs.Screen name="sale" options={{href: null}} />
    <Tabs.Screen name="customer" options={{href: null}} />
    <Tabs.Screen name="piece" options={{href: null}} />
    <Tabs.Screen name="rates" options={{href: null}} />
    <Tabs.Screen name="work" options={{href: null}} />
    <Tabs.Screen name="dayclose" options={{href: null}} />
    <Tabs.Screen name="scan" options={{href: null}} />
  </Tabs>;
}
