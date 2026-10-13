import React, {useState} from 'react';
import {Alert, Text, TextInput, View} from 'react-native';
import {router} from 'expo-router';
import {J, errText} from '@/src/api';
import {has, useSession} from '@/src/session';
import {Btn, C, Card, Screen, State, money, num, s, useLoad} from '@/src/ui';

/* Today's selling rate per purity. Bills copy the rate, so saving a new rate never changes an old bill. */
export default function Rates() {
  const user = useSession(st => st.user);
  const canEdit = has(user, 'MANAGE_RECORDS') && user?.role !== 'VIEWER';
  const b = useLoad(() => J.rates(), []);
  const rows: any[] = (b.data as any)?.rates || [];
  const [draft, setDraft] = useState<Record<number, string>>({});
  const [busy, setBusy] = useState(false);
  const changed = rows.filter(r => draft[r.purityId] && num(draft[r.purityId]) > 0 && num(draft[r.purityId]) !== num(r.ratePerGram));
  const save = async () => {
    const list = changed.map(r => ({purityId: r.purityId, ratePerGram: num(draft[r.purityId])}));
    const big = list.filter(x => { const old = num(rows.find(r => r.purityId === x.purityId)?.ratePerGram); return old > 0 && Math.abs(x.ratePerGram - old) / old > 0.05; });
    const go = async () => {
      setBusy(true);
      try { await J.saveRates(list); setDraft({}); await b.reload(); Alert.alert('Rates saved for today'); }
      catch (e) { Alert.alert('Not saved', errText(e)); }
      finally { setBusy(false); }
    };
    if (big.length) Alert.alert('Large change', `${big.length} rate(s) changed by more than 5%. Save anyway?`, [{text: 'Check again'}, {text: 'Save', onPress: () => void go()}]);
    else void go();
  };
  return <Screen title="Gold rates" subtitle={(b.data as any)?.date} onRefresh={b.reload} refreshing={b.loading} right={<Btn title="Back" kind="ghost" onPress={() => router.back()} />}>
    <State loading={b.loading} error={b.error} onRetry={b.reload} empty={!rows.length} emptyText="No purities yet" emptyHint="Add purities on the web app (Gold Rates → Purities).">
      {rows.map(r => <Card key={r.purityId}>
        <View style={s.between}><Text style={s.strong}>{r.purity}</Text><Text style={{color: r.setToday ? C.good : C.bad, fontWeight: '700'}}>{r.setToday ? 'set today' : r.rateDate ? `last ${r.rateDate}` : 'never set'}</Text></View>
        <Text style={{fontSize: 20, fontWeight: '800', color: C.ink, marginVertical: 4}}>{r.ratePerGram != null ? `${money(r.ratePerGram)} / g` : '—'}</Text>
        {canEdit && <TextInput style={s.input} keyboardType="decimal-pad" placeholder="New rate per gram" placeholderTextColor="#9C9488" value={draft[r.purityId] ?? ''} onChangeText={v => setDraft({...draft, [r.purityId]: v})} accessibilityLabel={`New rate for ${r.purity}`} />}
      </Card>)}
      {canEdit && <Btn title={`Save ${changed.length || ''} rate${changed.length === 1 ? '' : 's'}`} onPress={save} busy={busy} disabled={!changed.length} />}
    </State>
  </Screen>;
}
