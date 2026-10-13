import React from 'react';
import {Redirect, Stack} from 'expo-router';
import {useSession} from '@/src/session';

export default function AuthLayout() {
  const {token, ready} = useSession();
  if (!ready) return null;
  if (token) return <Redirect href="/(main)/home" />;
  return <Stack screenOptions={{headerShown: false}} />;
}
