import React, {useState} from 'react';
import {Text, View} from 'react-native';
import {API_URL, J, errText} from '@/src/api';
import {useSession} from '@/src/session';
import {Btn, C, Card, Field, Note, Screen} from '@/src/ui';

export default function Login() {
  const signIn = useSession(s => s.signIn);
  const [id, setId] = useState('');
  const [pw, setPw] = useState('');
  const [show, setShow] = useState(false);
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState('');
  const submit = async () => {
    if (!id.trim() || !pw) { setErr('Enter your username (or email / mobile) and password.'); return; }
    setBusy(true); setErr('');
    try { const r = await J.login(id.trim(), pw); await signIn(r.data.token); }
    catch (e) { setErr(errText(e, 'Sign-in failed. Check your details.')); }
    finally { setBusy(false); }
  };
  return <Screen title="Jewellery360" subtitle="Sign in to your shop">
    <Card>
      {err ? <Note text={err} tone="bad" /> : null}
      <Field label="Username, email or mobile" value={id} onChangeText={setId} autoCapitalize="none" autoCorrect={false} textContentType="username" returnKeyType="next" />
      <Field label="Password" value={pw} onChangeText={setPw} secureTextEntry={!show} textContentType="password" returnKeyType="go" onSubmitEditing={submit} />
      <Btn title={show ? 'Hide password' : 'Show password'} kind="ghost" onPress={() => setShow(!show)} />
      <Btn title="Sign in" onPress={submit} busy={busy} style={{marginTop: 6}} />
    </Card>
    <View style={{marginTop: 8}}><Text style={{color: C.muted, fontSize: 12}}>Server: {API_URL || 'not set - add EXPO_PUBLIC_API_URL to .env'}</Text>
      <Text style={{color: C.muted, fontSize: 12, marginTop: 4}}>Forgot your password? Ask the shop owner to reset it, or use the web app.</Text></View>
  </Screen>;
}
