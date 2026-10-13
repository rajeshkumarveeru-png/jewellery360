import React, {useState} from 'react';
import {Alert} from 'react-native';
import {router} from 'expo-router';
import {J, errText} from '@/src/api';
import {useSession} from '@/src/session';
import {Btn, Card, Field, Note, Row, Screen, Section, State, money, num, useLoad} from '@/src/ui';

/* End of day: what the drawer should hold according to the books, and the cash actually counted. */
export default function DayClose() {
  const user = useSession(st => st.user);
  const d = useLoad(() => J.dayClose(), []);
  const x: any = d.data;
  const [cash, setCash] = useState('');
  const [notes, setNotes] = useState('');
  const [busy, setBusy] = useState(false);
  const diff = cash === '' ? null : num(cash) - num(x?.expectedCash);
  const canClose = x && (!x.closed || user?.role === 'COMPANY_ADMIN') && user?.role !== 'VIEWER';
  const save = () => {
    const go = async () => {
      setBusy(true);
      try { await J.closeDay({actualCash: num(cash), notes: notes || undefined}); setCash(''); await d.reload(); Alert.alert('Day closed'); }
      catch (e) { Alert.alert('Not saved', errText(e)); }
      finally { setBusy(false); }
    };
    if (diff !== null && Math.abs(diff) > 0.5) Alert.alert(diff > 0 ? `${money(diff)} more than expected` : `Short by ${money(-diff)}`, 'Close the day with this difference?', [{text: 'Recount'}, {text: 'Close the day', onPress: () => void go()}]);
    else void go();
  };
  return <Screen title="Day close" subtitle={x?.date} onRefresh={d.reload} refreshing={d.loading} right={<Btn title="Back" kind="ghost" onPress={() => router.back()} />}>
    <State loading={d.loading} error={d.error} onRetry={d.reload} empty={!x}>
      {x && <>
        {x.closed && <Note text={`Closed${x.closedBy ? ` by ${x.closedBy}` : ''}. Counted ${money(x.actualCash)}, difference ${money(x.difference)}.`} tone={Math.abs(num(x.difference)) > 0.5 ? 'warn' : 'good'} />}
        <Card>
          <Row k="Opening cash" v={money(x.openingCash)} /><Row k="Cash received" v={money(x.cashIn)} /><Row k="Cash paid out" v={`− ${money(x.cashOut)}`} />
          <Row k="Cash expenses" v={`− ${money(x.cashExpenses)}`} /><Row k="Expected in drawer" v={money(x.expectedCash)} strong />
        </Card>
        <Section title="Other money today" />
        <Card><Row k="UPI" v={money(x.upi)} /><Row k="Card" v={money(x.card)} /><Row k="Bank" v={money(x.bank)} /><Row k="Sales" v={money(x.salesTotal)} /><Row k="On credit" v={money(x.creditSales)} /></Card>
        {canClose && <><Section title={x.closed ? 'Close again (owner)' : 'Count the drawer'} /><Card>
          <Field label="Cash counted (₹)" keyboardType="decimal-pad" value={cash} onChangeText={setCash} />
          {diff !== null && <Note text={Math.abs(diff) <= 0.5 ? 'Matches the books.' : diff > 0 ? `${money(diff)} more than expected.` : `Short by ${money(-diff)}.`} tone={Math.abs(diff) <= 0.5 ? 'good' : 'warn'} />}
          <Field label="Notes" value={notes} onChangeText={setNotes} placeholder="Explain any difference" />
          <Btn title="Close the day" onPress={save} busy={busy} disabled={cash === ''} />
        </Card></>}
      </>}
    </State>
  </Screen>;
}
