import {useEffect, useRef, useState} from 'react';

export default function BarcodeCameraScanner({open,onClose,onDetected}:{open:boolean;onClose:()=>void;onDetected:(value:string)=>void}){
  const videoRef=useRef<HTMLVideoElement|null>(null);
  const streamRef=useRef<MediaStream|null>(null);
  const [devices,setDevices]=useState<MediaDeviceInfo[]>([]);
  const [deviceId,setDeviceId]=useState('');
  const [error,setError]=useState('');
  const [supported,setSupported]=useState(true);

  useEffect(()=>{
    if(!open)return;
    let stopped=false;
    let timer:number|undefined;
    const start=async()=>{
      try{
        setError('');
        if(!navigator.mediaDevices?.getUserMedia){throw new Error('Camera access is not supported by this browser.');}
        if(!(window as any).BarcodeDetector){setSupported(false);}
        const stream=await navigator.mediaDevices.getUserMedia({video:deviceId?{deviceId:{exact:deviceId}}:{facingMode:{ideal:'environment'}},audio:false});
        if(stopped){stream.getTracks().forEach(t=>t.stop());return;}
        streamRef.current=stream;
        if(videoRef.current){videoRef.current.srcObject=stream;await videoRef.current.play();}
        const all=await navigator.mediaDevices.enumerateDevices();
        const cams=all.filter(x=>x.kind==='videoinput');setDevices(cams);
        if(!deviceId&&cams[0])setDeviceId(cams[0].deviceId);
        const Detector=(window as any).BarcodeDetector;
        if(!Detector)return;
        const detector=new Detector({formats:['code_128','code_39','ean_13','ean_8','upc_a','upc_e','itf','codabar','qr_code','data_matrix']});
        const scan=async()=>{
          if(stopped||!videoRef.current)return;
          try{
            const codes=await detector.detect(videoRef.current);
            const value=codes?.find((x:any)=>x.rawValue)?.rawValue;
            if(value){onDetected(String(value));return;}
          }catch{}
          timer=window.setTimeout(scan,180);
        };
        timer=window.setTimeout(scan,250);
      }catch(e:any){if(!stopped)setError(e?.message||'Unable to open camera. Check browser camera permission.');}
    };
    start();
    return()=>{stopped=true;if(timer)window.clearTimeout(timer);streamRef.current?.getTracks().forEach(t=>t.stop());streamRef.current=null;if(videoRef.current)videoRef.current.srcObject=null;};
  },[open,deviceId]);

  if(!open)return null;
  const switchCamera=(id:string)=>{streamRef.current?.getTracks().forEach(t=>t.stop());setDeviceId(id);};
  return <div className="scannerOverlay" role="dialog" aria-modal="true">
    <div className="scannerModal">
      <div className="scannerModalHead"><div><span className="eyebrow">CAMERA SCANNER</span><h3>Scan jewellery barcode</h3></div><button className="ghost" onClick={onClose}>Close</button></div>
      <div className="scannerViewport"><video ref={videoRef} playsInline muted/><div className="scannerFrame"><span/></div>{!supported&&<div className="scannerHint">Your browser opened the camera, but automatic barcode detection is unavailable. Use a USB/Bluetooth scanner or type the code manually.</div>}</div>
      {devices.length>1&&<select className="scannerCameraSelect" value={deviceId} onChange={e=>switchCamera(e.target.value)}>{devices.map((d,i)=><option key={d.deviceId} value={d.deviceId}>{d.label||`Camera ${i+1}`}</option>)}</select>}
      {error&&<div className="scannerError">{error}</div>}
      <small className="scannerFooter">Point the camera at the barcode. The scan closes automatically when a code is detected.</small>
    </div>
  </div>;
}
