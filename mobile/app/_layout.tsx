import 'react-native-gesture-handler';
import React, {useEffect} from 'react';
import {Stack} from 'expo-router';
import {StatusBar} from 'expo-status-bar';
import {SafeAreaProvider} from 'react-native-safe-area-context';
import {useSession} from '@/src/session';

export default function RootLayout() {
  const start = useSession(s => s.start);
  useEffect(() => { void start(); }, [start]);
  return <SafeAreaProvider><StatusBar style="dark" /><Stack screenOptions={{headerShown: false}} /></SafeAreaProvider>;
}
