import React, {useCallback, useEffect, useRef, useState} from 'react';
import {ActivityIndicator, KeyboardAvoidingView, Platform, Pressable, RefreshControl, ScrollView, StyleSheet, Text, TextInput, View} from 'react-native';
import type {TextInputProps, ViewStyle} from 'react-native';
import {SafeAreaView} from 'react-native-safe-area-context';
import {errText} from './api';

/* Small shared kit: every screen loads, fails, refreshes and confirms the same way. Touch targets are at least 48 px. */

export const C = {
  gold: '#A9792B', goldSoft: '#F4EAD7', ink: '#1E1A14', muted: '#6E665A', line: '#E6DFD2', bg: '#F7F3EC', card: '#FFFFFF',
  good: '#1F7A45', goodSoft: '#E5F3EA', bad: '#B23A30', badSoft: '#FBE7E4', warn: '#8A5A0B', warnSoft: '#FFF3D6',
};

export const money = (v: any) => `₹${Number(v || 0).toLocaleString('en-IN', {minimumFractionDigits: 2, maximumFractionDigits: 2})}`;
export const grams = (v: any) => `${Number(v || 0).toFixed(3)} g`;
export const num = (v: any) => { const n = Number(v); return Number.isFinite(n) ? n : 0; };
// "OLD_GOLD" -> "Old gold"; short codes such as UPI stay as they are
const KEEP = ['UPI', 'GST', 'HUID', 'MRP', 'OTP'];
export const label = (s?: string | null) => KEEP.includes(String(s)) ? String(s) : (s || '').replace(/_/g, ' ').toLowerCase().replace(/^./, c => c.toUpperCase());

/** Loads data, keeps the last good result while reloading, exposes pull-to-refresh. */
export function useLoad<T>(load: () => Promise<{data: T}>, deps: unknown[] = [], enabled = true) {
  const [data, setData] = useState<T | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const seq = useRef(0);
  const reload = useCallback(async () => {
    if (!enabled) return;
    const mine = ++seq.current;
    setLoading(true); setError('');
    try { const r = await load(); if (mine === seq.current) setData(r.data); }
    catch (e) { if (mine === seq.current) setError(errText(e)); }
    finally { if (mine === seq.current) setLoading(false); }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [enabled, ...deps]);
  useEffect(() => { void reload(); }, [reload]);
  return {data, loading, error, reload, setData};
}

export function Screen({title, subtitle, children, onRefresh, refreshing, scroll = true, right}: {
  title: string; subtitle?: string; children: React.ReactNode; onRefresh?: () => void; refreshing?: boolean; scroll?: boolean; right?: React.ReactNode;
}) {
  const head = <View style={s.head}><View style={{flex: 1}}><Text style={s.title}>{title}</Text>{subtitle ? <Text style={s.sub}>{subtitle}</Text> : null}</View>{right}</View>;
  return <SafeAreaView style={s.safe} edges={['top']}>
    <KeyboardAvoidingView style={{flex: 1}} behavior={Platform.OS === 'ios' ? 'padding' : undefined}>
      {scroll ? <ScrollView contentContainerStyle={s.body} keyboardShouldPersistTaps="handled"
                            refreshControl={onRefresh ? <RefreshControl refreshing={!!refreshing} onRefresh={onRefresh} tintColor={C.gold} /> : undefined}>
        {head}{children}
      </ScrollView> : <View style={[s.body, {flex: 1}]}>{head}{children}</View>}
    </KeyboardAvoidingView>
  </SafeAreaView>;
}

export function Card({children, style, onPress}: {children: React.ReactNode; style?: ViewStyle; onPress?: () => void}) {
  if (onPress) return <Pressable onPress={onPress} style={({pressed}) => [s.card, style, pressed && {opacity: 0.7}]} accessibilityRole="button">{children}</Pressable>;
  return <View style={[s.card, style]}>{children}</View>;
}

export function Btn({title, onPress, kind = 'primary', disabled, busy, style}: {title: string; onPress: () => void; kind?: 'primary' | 'secondary' | 'danger' | 'ghost'; disabled?: boolean; busy?: boolean; style?: ViewStyle}) {
  const bg = kind === 'primary' ? C.gold : kind === 'danger' ? C.bad : kind === 'secondary' ? C.goldSoft : 'transparent';
  const fg = kind === 'primary' || kind === 'danger' ? '#fff' : C.gold;
  return <Pressable accessibilityRole="button" accessibilityState={{disabled: disabled || busy}} disabled={disabled || busy} onPress={onPress}
                    style={({pressed}) => [s.btn, {backgroundColor: bg, opacity: disabled ? 0.45 : pressed ? 0.75 : 1}, style]}>
    {busy ? <ActivityIndicator color={fg} /> : <Text style={[s.btnText, {color: fg}]}>{title}</Text>}
  </Pressable>;
}

export function Field({label: l, hint, ...p}: TextInputProps & {label: string; hint?: string}) {
  return <View style={{marginBottom: 12}}>
    <Text style={s.label}>{l}</Text>
    <TextInput placeholderTextColor="#9C9488" {...p} style={[s.input, p.style]} accessibilityLabel={l} />
    {hint ? <Text style={s.hint}>{hint}</Text> : null}
  </View>;
}

export function Chips<T extends string>({value, options, onChange}: {value: T; options: {value: T; label: string}[]; onChange: (v: T) => void}) {
  return <View style={s.chips}>{options.map(o =>
    <Pressable key={o.value} onPress={() => onChange(o.value)} accessibilityRole="button" accessibilityState={{selected: value === o.value}}
               style={[s.chip, value === o.value && {backgroundColor: C.gold, borderColor: C.gold}]}>
      <Text style={{color: value === o.value ? '#fff' : C.ink, fontWeight: '600'}}>{o.label}</Text>
    </Pressable>)}</View>;
}

export function Row({k, v, strong}: {k: string; v: React.ReactNode; strong?: boolean}) {
  return <View style={s.row}><Text style={[s.rowK, strong && s.strong]}>{k}</Text><Text style={[s.rowV, strong && s.strong]}>{v}</Text></View>;
}

export function Pill({text, tone}: {text: string; tone?: 'good' | 'bad' | 'warn'}) {
  const bg = tone === 'good' ? C.goodSoft : tone === 'bad' ? C.badSoft : tone === 'warn' ? C.warnSoft : C.goldSoft;
  const fg = tone === 'good' ? C.good : tone === 'bad' ? C.bad : tone === 'warn' ? C.warn : C.gold;
  return <Text style={[s.pill, {backgroundColor: bg, color: fg}]}>{text}</Text>;
}

export function Note({text, tone}: {text: string; tone?: 'bad' | 'warn' | 'good'}) {
  const bg = tone === 'bad' ? C.badSoft : tone === 'warn' ? C.warnSoft : tone === 'good' ? C.goodSoft : C.goldSoft;
  const fg = tone === 'bad' ? C.bad : tone === 'warn' ? C.warn : tone === 'good' ? C.good : C.ink;
  return <Text style={[s.note, {backgroundColor: bg, color: fg}]} accessibilityLiveRegion="polite">{text}</Text>;
}

/** Loading, error (with retry) and empty states with a next step. */
export function State({loading, error, empty, emptyText, emptyHint, onRetry, children}: {
  loading?: boolean; error?: string; empty?: boolean; emptyText?: string; emptyHint?: string; onRetry?: () => void; children?: React.ReactNode;
}) {
  if (error) return <Card><Text style={[s.strong, {color: C.bad}]}>Could not load</Text><Text style={s.sub}>{error}</Text>{onRetry && <Btn title="Try again" kind="secondary" onPress={onRetry} style={{marginTop: 10}} />}</Card>;
  if (loading && empty) return <View style={{padding: 30}}><ActivityIndicator color={C.gold} /></View>;
  if (empty) return <Card><Text style={s.strong}>{emptyText || 'Nothing here yet'}</Text>{emptyHint ? <Text style={s.sub}>{emptyHint}</Text> : null}</Card>;
  return <>{children}</>;
}

export const Section = ({title}: {title: string}) => <Text style={s.section}>{title}</Text>;

export const s = StyleSheet.create({
  safe: {flex: 1, backgroundColor: C.bg},
  body: {padding: 16, paddingBottom: 40},
  head: {flexDirection: 'row', alignItems: 'center', marginBottom: 14},
  title: {fontSize: 24, fontWeight: '800', color: C.ink},
  sub: {fontSize: 13, color: C.muted, marginTop: 3},
  card: {backgroundColor: C.card, borderRadius: 14, padding: 14, marginBottom: 10, borderWidth: 1, borderColor: C.line},
  btn: {minHeight: 48, borderRadius: 12, paddingHorizontal: 16, alignItems: 'center', justifyContent: 'center'},
  btnText: {fontSize: 16, fontWeight: '700'},
  label: {fontSize: 13, fontWeight: '600', color: C.muted, marginBottom: 5},
  hint: {fontSize: 12, color: C.muted, marginTop: 4},
  input: {minHeight: 48, borderWidth: 1, borderColor: C.line, borderRadius: 12, paddingHorizontal: 12, fontSize: 16, backgroundColor: '#fff', color: C.ink},
  chips: {flexDirection: 'row', flexWrap: 'wrap', gap: 8, marginBottom: 12},
  chip: {minHeight: 40, paddingHorizontal: 14, borderRadius: 20, borderWidth: 1, borderColor: C.line, justifyContent: 'center', backgroundColor: '#fff'},
  row: {flexDirection: 'row', justifyContent: 'space-between', paddingVertical: 5},
  rowK: {color: C.muted, fontSize: 14, flexShrink: 1, paddingRight: 8},
  rowV: {color: C.ink, fontSize: 14, textAlign: 'right'},
  strong: {fontWeight: '800', color: C.ink, fontSize: 16},
  pill: {alignSelf: 'flex-start', paddingHorizontal: 10, paddingVertical: 3, borderRadius: 12, overflow: 'hidden', fontSize: 12, fontWeight: '700'},
  note: {padding: 12, borderRadius: 12, marginBottom: 10, fontSize: 14, overflow: 'hidden'},
  section: {fontSize: 12, fontWeight: '800', letterSpacing: 1, color: C.muted, marginTop: 14, marginBottom: 8, textTransform: 'uppercase'},
  between: {flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center'},
});
