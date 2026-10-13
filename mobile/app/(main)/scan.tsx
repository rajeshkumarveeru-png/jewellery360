import React, {useRef, useState} from 'react';
import {Text, View} from 'react-native';
import {CameraView, useCameraPermissions} from 'expo-camera';
import {router} from 'expo-router';
import {J, errText} from '@/src/api';
import {pieceOf, useCart} from '@/src/cart';
import {Btn, C, Note, Screen, s} from '@/src/ui';

/* Scans a tag barcode / QR and adds the piece to the bill. One scan at a time so a code is never added twice. */
export default function Scan() {
  const [perm, ask] = useCameraPermissions();
  const add = useCart(c => c.add);
  const [msg, setMsg] = useState<{text: string; tone?: 'bad' | 'good'} | null>(null);
  const busy = useRef(false);
  const onCode = async (code: string) => {
    if (busy.current) return;
    busy.current = true;
    try {
      const r = await J.scan(code);
      if (r.data?.available === false) setMsg({text: r.data?.message || 'This piece cannot be billed here.', tone: 'bad'});
      else setMsg(add(pieceOf(r.data)) ? {text: `${r.data.tagNo} · ${r.data.productName} added`, tone: 'good'} : {text: `${r.data.tagNo} is already on the bill`});
    } catch (e) { setMsg({text: errText(e), tone: 'bad'}); }
    setTimeout(() => { busy.current = false; }, 1500);
  };
  if (!perm) return <Screen title="Scan tag"><Text style={s.sub}>Checking camera permission…</Text></Screen>;
  if (!perm.granted) return <Screen title="Scan tag">
    <Note text="Allow the camera to scan tag barcodes. You can also type the tag on the bill screen." />
    <Btn title="Allow camera" onPress={ask} /><Btn title="Back to bill" kind="ghost" onPress={() => router.back()} style={{marginTop: 8}} />
  </Screen>;
  return <Screen title="Scan tag" scroll={false}>
    <View style={{flex: 1, borderRadius: 16, overflow: 'hidden', backgroundColor: '#000', marginBottom: 12}}>
      <CameraView style={{flex: 1}} facing="back" barcodeScannerSettings={{barcodeTypes: ['code128', 'code39', 'ean13', 'ean8', 'qr', 'upc_a']}} onBarcodeScanned={e => void onCode(e.data)} />
    </View>
    {msg ? <Note text={msg.text} tone={msg.tone} /> : <Text style={[s.sub, {color: C.muted, marginBottom: 10}]}>Point the camera at the tag barcode.</Text>}
    <Btn title="Done - back to bill" onPress={() => router.push('/(main)/bill')} />
  </Screen>;
}
