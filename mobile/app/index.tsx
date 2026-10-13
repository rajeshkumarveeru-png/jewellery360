import React from 'react';
import {ActivityIndicator, View} from 'react-native';
import {Redirect} from 'expo-router';
import {useSession} from '@/src/session';
import {C} from '@/src/ui';

export default function Index() {
  const {ready, token} = useSession();
  if (!ready) return <View style={{flex: 1, alignItems: 'center', justifyContent: 'center', backgroundColor: C.bg}}><ActivityIndicator color={C.gold} /></View>;
  return <Redirect href={token ? '/(main)/home' : '/(auth)/login'} />;
}
