import {FormEvent, useEffect, useMemo, useRef, useState} from 'react';
import {api, calculateMultiBilling, createDomainBilling, domainCustomers, createDomainCustomer, domainItems, headerGoldRates, getTaxSettings, getBusinessSettings, salePdf, sendInvoiceWhatsApp} from '../api';
import BillSummary, {estimateBill} from './BillSummary';
import './Billing.css';
import BarcodeCameraScanner from './BarcodeCameraScanner';

type TaxConfig={enabled:boolean;mode:'GST'|'CGST_SGST'|'NONE';rate:number;cgstRate:number;sgstRate:number;totalRate:number};

type Line={
 id:string; jewelleryItemId:string; name:string; design:string; tag:string; barcode:string; purity:string;
 grossWeight:number; stoneWeight:number; netWeight:number; goldRate:number; wastagePercent:number;
 makingCharge:number; stoneCharge:number; otherCharge:number;
};

const emptyLine=(x:any, rate:number):Line=>({
 id:String(x.id), jewelleryItemId:String(x.id), name:x?.product?.name||'Jewellery', design:x?.product?.design?.name||'',
 tag:x?.tag?.tagNo||'', barcode:x?.tag?.barcode||'', purity:x?.purity?.name||x?.purity?.karat||'',
 grossWeight:Number(x?.grossWeight||0), stoneWeight:Number(x?.stoneWeight||0), netWeight:Number(x?.netWeight ?? Math.max(Number(x?.grossWeight||0)-Number(x?.stoneWeight||0),0)),
 goldRate:Number(rate||0), wastagePercent:Number(x?.wastagePercent||0), makingCharge:Number(x?.makingCharge||0), stoneCharge:Number(x?.stoneValue||0), otherCharge:0
});

export default function BillingModule({ready,onNotice}:{ready:boolean;onNotice:(x:string)=>void}){
 const [customers,setCustomers]=useState<any[]>([]),[items,setItems]=useState<any[]>([]),[lines,setLines]=useState<Line[]>([]);
 const initialBillingDate = new Date();
 const [f,setF]=useState<any>({invoiceNo:`INV-${Date.now()}`,saleDate:initialBillingDate.toISOString().slice(0,10),saleTime:initialBillingDate.toTimeString().slice(0,5),customerId:'',goldRate:0,gstPercent:3,discount:0,paymentMode:'CASH',paymentAmount:0});
 const [tax,setTax]=useState<TaxConfig>({enabled:true,mode:'GST',rate:3,cgstRate:1.5,sgstRate:1.5,totalRate:3});
 const [marketSource,setMarketSource]=useState('');
 const [marketFallback,setMarketFallback]=useState(false);
 const [calc,setCalc]=useState<any>(null),[lastCreated,setLastCreated]=useState<any>(null),[barcode,setBarcode]=useState(''),[scanBusy,setScanBusy]=useState(false),[cameraOpen,setCameraOpen]=useState(false),[marketRates,setMarketRates]=useState<any[]>([]);
 const [itemSearch,setItemSearch]=useState(''),[itemOpen,setItemOpen]=useState(false),[customerSearch,setCustomerSearch]=useState(''),[customerOpen,setCustomerOpen]=useState(false);
 const [newCustomerMode,setNewCustomerMode]=useState(false),[newCustomer,setNewCustomer]=useState<any>({name:'',phone:'',email:'',gstin:'',address:''}),[customerSaving,setCustomerSaving]=useState(false);
 const barcodeRef=useRef<HTMLInputElement>(null);
 const [printFormat,setPrintFormat]=useState('A4');
 const applyMarket = (data:any, force:boolean=true) => {
   const rates=Array.isArray(data?.marketRates)&&data.marketRates.length?data.marketRates:(Array.isArray(data?.rates)?data.rates:[]);
   setMarketRates(rates);
   setMarketSource(String(data?.marketSource||'GoodReturns - Cuddalore'));
   setMarketFallback(Boolean(data?.marketFallback));
   const r22=rates.find((x:any)=>String(x.karat??x.purity??'').replace(/[^0-9]/g,'')==='22');
   if(Number(r22?.ratePerGram)>0 && force){
     setF((v:any)=>({...v,goldRate:Number(r22.ratePerGram)}));
     setLines(v=>v.map(x=>({...x,goldRate:Number(r22.ratePerGram)})));
     setCalc(null);
   }
 };

 const loadMarketAndTax = async () => {
   const [marketResult,taxResult] = await Promise.allSettled([headerGoldRates(),getTaxSettings()]);

   if(taxResult.status==='fulfilled'){
     const data=taxResult.value.data||{};
     const nextTax:TaxConfig={
       enabled:Boolean(data.enabled ?? true),
       mode:data.mode==='CGST_SGST'?'CGST_SGST':(data.enabled===false?'NONE':'GST'),
       rate:Number(data.rate ?? 3),
       cgstRate:Number(data.cgstRate ?? 1.5),
       sgstRate:Number(data.sgstRate ?? 1.5),
       totalRate:Number(data.totalRate ?? (data.mode==='CGST_SGST'?Number(data.cgstRate||0)+Number(data.sgstRate||0):Number(data.rate||0)))
     };
     setTax(nextTax);
     setF((v:any)=>({...v,gstPercent:nextTax.totalRate}));
   }

   if(marketResult.status==='fulfilled'){
     applyMarket(marketResult.value.data,true);
   }else{
     onNotice('Unable to refresh the current market gold rate. Billing will keep the last available rate.');
   }

   if(taxResult.status==='rejected'){
     onNotice('Unable to load tax settings. The configured/default tax will be used until Settings is available.');
   }
 };

 useEffect(()=>{
   if(!ready){setCustomers([]);setItems([]);setMarketRates([]);setMarketSource('');return;}
   Promise.all([domainCustomers(),domainItems()]).then(([c,i])=>{setCustomers(Array.isArray(c.data)?c.data:[]);setItems(Array.isArray(i.data)?i.data:[]);}).catch(e=>onNotice(e?.response?.data?.message||'Unable to load billing masters'));
   void loadMarketAndTax();
   const timer=window.setInterval(()=>{void headerGoldRates().then(r=>applyMarket(r.data,true)).catch(()=>undefined);},5*60*1000);
   return ()=>window.clearInterval(timer);
 },[ready]);
 useEffect(()=>{if(ready)window.setTimeout(()=>barcodeRef.current?.focus(),150);},[ready]);
 useEffect(()=>{if(!ready)return;const cid=Number(localStorage.getItem('j360_context_company'))||undefined;getBusinessSettings(cid).then(r=>{const v=String(r.data?.printFormat||'A4').toUpperCase();setPrintFormat(['A4','80MM','50MM'].includes(v)?v:'A4');}).catch(()=>undefined);},[ready]);
 const update=(k:string,v:any)=>setF((x:any)=>({...x,[k]:v}));
 const selectedCustomer=customers.find(x=>String(x.id)===String(f.customerId));
 const filteredCustomers=useMemo(()=>{const q=customerSearch.trim().toLowerCase();const src=customers.filter(x=>x.active!==false);return (q?src.filter(x=>[x?.name,x?.phone,x?.email,x?.gstin,x?.address].filter(Boolean).join(' ').toLowerCase().includes(q)):src).slice(0,80);},[customers,customerSearch]);
 const availableItems=useMemo(()=>items.filter(x=>x.status==='IN_STOCK'&&!lines.some(l=>String(l.jewelleryItemId)===String(x.id))),[items,lines]);
 const filteredItems=useMemo(()=>{const q=itemSearch.trim().toLowerCase();const src=availableItems;if(!q)return src.slice(0,80);return src.filter(x=>[x?.product?.name,x?.product?.sku,x?.product?.design?.name,x?.tag?.tagNo,x?.tag?.barcode,x?.huid,x?.purity?.name,x?.purity?.karat].filter(Boolean).join(' ').toLowerCase().includes(q)).slice(0,80);},[availableItems,itemSearch]);
 const rateForPurity=(purity:any)=>{const key=String(purity?.karat??purity?.name??purity??'').toUpperCase().replace(/[^0-9]/g,'');const found=marketRates.find(x=>String(x.karat??x.purity??'').toUpperCase().replace(/[^0-9]/g,'')===key);return Number(found?.ratePerGram||0);};
 const addItem=(x:any)=>{const rate=Number(f.goldRate)>0?Number(f.goldRate):rateForPurity(x?.purity);setLines(v=>[...v,emptyLine(x,rate)]);setItemSearch('');setItemOpen(false);setCalc(null);setLastCreated(null);};
 const removeItem=(id:string)=>{setLines(v=>v.filter(x=>x.id!==id));setCalc(null);};
 const updateLine=(id:string,k:keyof Line,v:any)=>setLines(vs=>vs.map(x=>x.id===id?{...x,[k]:v}:x));
 const scanBarcodeValue=async(value:string)=>{const clean=String(value||'').trim();if(!clean||scanBusy)return;setScanBusy(true);try{const r=await api.get(`/barcode/scan/${encodeURIComponent(clean)}`);if(String(r.data?.status||'').toUpperCase()!=='IN_STOCK'){onNotice(`Scanned item is ${r.data?.status||'not available'} and cannot be billed.`);return;}let x=items.find(i=>String(i.id)===String(r.data?.itemId));if(!x){const refreshed=await domainItems();setItems(refreshed.data);x=refreshed.data.find((i:any)=>String(i.id)===String(r.data?.itemId));}if(!x){onNotice('Barcode found, but the item is not available in this branch.');return;}if(lines.some(l=>String(l.jewelleryItemId)===String(x.id))){onNotice('This item is already added to the invoice.');return;}addItem(x);setBarcode('');onNotice(`Added ${r.data?.tagNo||clean} to invoice.`);window.setTimeout(()=>barcodeRef.current?.focus(),0);}catch(err:any){onNotice(err?.response?.data?.message||'Barcode/tag not found');}finally{setScanBusy(false);}};
 const scanBarcode=()=>scanBarcodeValue(barcode);
 const calculate=async()=>{if(!lines.length){onNotice('Add at least one jewellery item before calculating.');return;}const commonRate=Number(f.goldRate||0);if(commonRate<=0){onNotice('Gold rate is required.');return;}try{const r=await calculateMultiBilling({goldRate:commonRate,items:lines.map(x=>toPayloadLine(x,commonRate)),gstPercent:tax.totalRate,discount:Number(f.discount||0)});setCalc(r.data);update('paymentAmount',Number(r.data?.total||0));setLines(v=>v.map(x=>({...x,goldRate:commonRate})));}catch(err:any){onNotice(err?.response?.data?.message||'Unable to calculate invoice total');}};
 const save=async(e:FormEvent)=>{e.preventDefault();if(!ready){onNotice('Select company and branch context first.');return;}if(!f.customerId){onNotice('Select a customer.');return;}if(!lines.length){onNotice('Add at least one jewellery item.');return;}if(Number(f.goldRate||0)<=0){onNotice('Gold rate is required.');return;}try{const payload={invoiceNo:f.invoiceNo,saleDate:f.saleDate,saleTime:f.saleTime,customerId:Number(f.customerId),goldRate:Number(f.goldRate||0),items:lines.map(x=>toPayloadLine(x,Number(f.goldRate||0))),gstPercent:tax.totalRate,discount:Number(f.discount||0),paymentMode:f.paymentMode,paymentAmount:Number(f.paymentAmount||0)};const r=await createDomainBilling(payload);const billedTotal=Number(r.data?.total ?? calc?.total ?? estimateBill(lines,Number(f.goldRate||0),tax,Number(f.discount||0)).total);setLastCreated({id:Number(r.data.id),invoiceNo:r.data.invoiceNo,itemCount:r.data.itemCount,total:billedTotal,paymentStatus:r.data?.paymentStatus});setCalc(null);onNotice(`Invoice ${r.data.invoiceNo} created with ${r.data.itemCount} item${r.data.itemCount===1?'':'s'} and stock movement.`);setLines([]);const resetDate=new Date();setF({invoiceNo:`INV-${Date.now()}`,saleDate:resetDate.toISOString().slice(0,10),saleTime:resetDate.toTimeString().slice(0,5),customerId:'',goldRate:Number(marketRates.find((x:any)=>String(x.karat??x.purity??'').replace(/[^0-9]/g,'')==='22')?.ratePerGram||0),gstPercent:tax.totalRate,discount:0,paymentMode:'CASH',paymentAmount:0});setItemSearch('');setItemOpen(false);setCustomerSearch('');setCustomerOpen(false);try{const r2=await domainItems();setItems(r2.data);}catch{onNotice(`Invoice ${r.data.invoiceNo} was created, but stock refresh failed. Please refresh Billing.`);}}catch(err:any){onNotice(err?.response?.data?.message||'Unable to create invoice');}};
 const openCreatedPdf=async(template?:string)=>{if(!lastCreated?.id)return;const popup=window.open('about:blank','_blank');try{const response=await salePdf(Number(lastCreated.id),template);const url=URL.createObjectURL(new Blob([response.data],{type:'application/pdf'}));if(popup){popup.location.href=url;popup.focus();}else{const a=document.createElement('a');a.href=url;a.target='_blank';a.rel='noopener';a.download=`${lastCreated.invoiceNo||'invoice'}.pdf`;a.click();}window.setTimeout(()=>URL.revokeObjectURL(url),60000);}catch(err:any){popup?.close();onNotice(err?.response?.data?.message||'Unable to open invoice PDF');}};
 const sendCreatedWhatsApp=async()=>{if(!lastCreated?.id)return;try{const response=await sendInvoiceWhatsApp(Number(lastCreated.id));onNotice(response?.data?.status==='SENT'?'Invoice sent on WhatsApp.':(response?.data?.message||'WhatsApp message submitted.'));}catch(err:any){onNotice(err?.response?.data?.message||'Unable to send invoice on WhatsApp');}};
 const money=(v:any)=>`₹${Number(v||0).toLocaleString('en-IN',{minimumFractionDigits:2,maximumFractionDigits:2})}`;
 const lineAmount=(x:Line)=>{const rate=Number(f.goldRate||0)>0?Number(f.goldRate):Number(x.goldRate||0);const g=Number(x.netWeight||0)*rate;return g+g*Number(x.wastagePercent||0)/100+Number(x.makingCharge||0)+Number(x.stoneCharge||0)+Number(x.otherCharge||0);};
 const SHORT:Record<string,string>={'Gross weight':'Gross g','Net weight':'Net g','Wastage percent':'Wastage %','Making charge':'Making ₹','Stone charge':'Stone ₹','Other charge':'Other ₹'};
 const num=(x:Line,k:keyof Line,label:string,step='0.001')=><label className="pc" data-l={SHORT[label]||label}><input type="number" step={step} aria-label={`${label} for ${x.name}`} value={x[k] as number} onChange={e=>updateLine(x.id,k,Number(e.target.value))}/></label>;
 return <div className="pos">
  {ready&&<form id="billing-form" className="posMain" onSubmit={save}>
   <div className="posBar">
    <div className="posTitle"><b>New Bill</b><span className={ready?'pill success':'pill'}>{ready?'READY':'SELECT CONTEXT'}</span></div>
    <label className="pf"><span>Invoice no</span><input value={f.invoiceNo} onChange={e=>update('invoiceNo',e.target.value)} aria-label="Invoice number"/></label>
    <label className="pf narrow"><span>Date</span><input type="date" value={f.saleDate} onChange={e=>update('saleDate',e.target.value)} aria-label="Invoice date"/></label>
    <label className="pf narrow"><span>Time</span><input type="time" value={f.saleTime} onChange={e=>update('saleTime',e.target.value)} aria-label="Invoice time"/></label>
    <label className="pf rate"><span>Gold ₹/g <em>{marketSource||'market rate'}</em></span><div className="goldRateAutoField"><input readOnly type="number" min="0" step="0.001" value={f.goldRate} aria-label="Current gold rate" placeholder="Loading…"/><span className={`goldRateStatus ${marketFallback?'fallback':'live'}`}>{marketFallback?'Saved':'● Live'}</span></div></label>
   </div>
   <div className="posFind">
    <div className="pf cust"><span>Customer <em>required</em></span><div className="customerSelectRow"><div className="customerPicker" onBlur={e=>{if(!e.currentTarget.contains(e.relatedTarget as Node))setCustomerOpen(false)}}><input required={!f.customerId&&!newCustomerMode} aria-label="Search customer" role="combobox" aria-expanded={customerOpen} value={selectedCustomer?`${selectedCustomer.name} · ${selectedCustomer.phone||'no phone'}`:customerSearch} placeholder="Search customer name or phone" disabled={newCustomerMode} onFocus={()=>setCustomerOpen(true)} onChange={e=>{setCustomerSearch(e.target.value);setCustomerOpen(true);if(f.customerId)update('customerId','')}} onKeyDown={e=>{if(e.key==='Escape')setCustomerOpen(false);if(e.key==='Enter'&&filteredCustomers[0]){e.preventDefault();update('customerId',String(filteredCustomers[0].id));setCustomerSearch('');setCustomerOpen(false);}}}/>{customerOpen&&!newCustomerMode&&<div className="customerPickerMenu">{filteredCustomers.length?filteredCustomers.map(x=><button type="button" key={x.id} className="customerPickerOption" onMouseDown={e=>e.preventDefault()} onClick={()=>{update('customerId',String(x.id));setCustomerSearch('');setCustomerOpen(false)}}><strong>{x.name||'Unnamed customer'}</strong><span>{x.phone||'No phone'}{x.email?` · ${x.email}`:''}</span></button>):<div className="customerPickerEmpty">No customer matches this search.</div>}</div>}</div><button type="button" className="secondary customerAddButton" onClick={()=>{setNewCustomerMode(v=>!v);setCustomerOpen(false)}}>{newCustomerMode?'Use existing':'＋ New customer'}</button></div></div>
    <div className="pf scan"><span>Scan barcode / tag</span><div className="barcodeScanRow"><div className="smartScanField billingSmartScanField"><input ref={barcodeRef} value={barcode} onChange={e=>setBarcode(e.target.value)} onKeyDown={e=>{if(e.key==='Enter'){e.preventDefault();scanBarcode();}}} placeholder="Scan or enter barcode / tag no" aria-label="Scan or enter barcode or tag number"/><button type="button" className="fieldScanButton" aria-label="Scan barcode with camera" title={scanBusy?'Scanning…':'Scan barcode'} disabled={scanBusy} onClick={()=>setCameraOpen(true)}><span className="scanFieldIcon" aria-hidden="true">▦</span><span className="scanFieldText">Scan</span></button></div></div></div>
    <div className="pf item"><span>Add jewellery <em>required</em></span><div className="itemPicker" onBlur={e=>{if(!e.currentTarget.contains(e.relatedTarget as Node))setItemOpen(false)}}><input aria-label="Search jewellery item" onClick={()=>setItemOpen(true)} role="combobox" aria-expanded={itemOpen} value={itemSearch} placeholder="Search product, tag, barcode or HUID" onFocus={e=>{setItemOpen(true);const el=e.currentTarget;window.setTimeout(()=>el.scrollIntoView({block:'center',behavior:'smooth'}),60)}} onChange={e=>{setItemSearch(e.target.value);setItemOpen(true)}} onKeyDown={e=>{if(e.key==='Escape')setItemOpen(false);if(e.key==='Enter'&&filteredItems[0]){e.preventDefault();addItem(filteredItems[0]);}}}/>{itemOpen&&<div className="itemPickerMenu">{filteredItems.length?filteredItems.map(x=><button type="button" key={x.id} className="itemPickerOption" onMouseDown={e=>e.preventDefault()} onClick={()=>addItem(x)}><strong>{x?.product?.name||'Unnamed product'}</strong><span>Tag: {x?.tag?.tagNo||'—'} · Barcode: {x?.tag?.barcode||'—'}</span><small>{x?.purity?.karat||x?.purity?.name||'—'} · Net {x?.netWeight??0} g</small></button>):<div className="itemPickerEmpty">No available item matches this search.</div>}</div>}</div></div>
   </div>
   {newCustomerMode&&<div className="inlineCustomerCard"><div className="inlineCustomerGrid"><input required placeholder="Customer name *" value={newCustomer.name} onChange={e=>setNewCustomer({...newCustomer,name:e.target.value})}/><input required placeholder="Phone number *" inputMode="tel" value={newCustomer.phone} onChange={e=>setNewCustomer({...newCustomer,phone:e.target.value})}/><input placeholder="Email" value={newCustomer.email} onChange={e=>setNewCustomer({...newCustomer,email:e.target.value})}/><input placeholder="GSTIN" value={newCustomer.gstin} onChange={e=>setNewCustomer({...newCustomer,gstin:e.target.value})}/><input className="wide" placeholder="Address" value={newCustomer.address} onChange={e=>setNewCustomer({...newCustomer,address:e.target.value})}/></div><button type="button" className="primary saveCustomerButton" disabled={customerSaving} onClick={async()=>{if(!newCustomer.name.trim()||!newCustomer.phone.trim()){onNotice('Customer name and phone number are required.');return;}setCustomerSaving(true);try{const r=await createDomainCustomer({...newCustomer,name:newCustomer.name.trim(),phone:newCustomer.phone.trim()});const saved=r.data||{};setCustomers(v=>[saved,...v.filter(x=>String(x.id)!==String(saved.id))]);update('customerId',String(saved.id));setNewCustomerMode(false);setNewCustomer({name:'',phone:'',email:'',gstin:'',address:''});onNotice(`Customer ${saved.name||'record'} saved and selected.`);}catch(err:any){onNotice(err?.response?.data?.message||'Unable to save customer');}finally{setCustomerSaving(false);}}}>{customerSaving?'Saving customer…':'Save customer & use for bill'}</button></div>}
   <div className="posCart">
    <div className="posCartHead"><b>Bill items</b><span className="posCount">{lines.length}</span><small>{!tax.enabled?'Tax disabled':tax.mode==='GST'?`GST @ ${tax.rate.toFixed(2)}%`:`CGST @ ${tax.cgstRate.toFixed(2)}% + SGST @ ${tax.sgstRate.toFixed(2)}%`} · set in Settings</small></div>
    {lines.length===0?<div className="posEmpty"><b>No jewellery added yet</b><small>Scan a barcode or search above — items appear here with editable weights and charges.</small></div>:
    <div className="posLines" role="table" aria-label="Bill items">
     <div className="posLine head" role="row"><span>#</span><span>Item</span><span>Gross g</span><span>Stone g</span><span>Net g</span><span>Wst %</span><span>Making ₹</span><span>Stone ₹</span><span>Other ₹</span><span className="r">Amount</span><span /></div>
     {lines.map((x,i)=><div className="posLine" role="row" key={x.id}>
      <span className="n">{i+1}</span>
      <div className="it"><b title={x.name}>{x.name}</b><small>{x.purity}{x.tag?` · Tag ${x.tag}`:''}</small></div>
      {num(x,'grossWeight','Gross weight')}
      <label className="pc" data-l="Stone g"><input type="number" step="0.001" aria-label={`Stone weight for ${x.name}`} value={x.stoneWeight} onChange={e=>{const stone=Number(e.target.value);updateLine(x.id,'stoneWeight',stone);updateLine(x.id,'netWeight',Math.max(x.grossWeight-stone,0));}}/></label>
      {num(x,'netWeight','Net weight')}
      {num(x,'wastagePercent','Wastage percent')}
      {num(x,'makingCharge','Making charge')}
      {num(x,'stoneCharge','Stone charge')}
      {num(x,'otherCharge','Other charge')}
      <b className="amt">{money(lineAmount(x))}</b>
      <button type="button" className="removeItemButton posX" aria-label={`Remove ${x.name}`} title="Remove item" onClick={()=>removeItem(x.id)}>✕</button>
     </div>)}
    </div>}
   </div>
   <div className="posPay">
    <div className="posTax"><b>{!tax.enabled?'No tax':tax.mode==='GST'?`GST ${tax.rate.toFixed(2)}%`:`CGST ${tax.cgstRate.toFixed(2)}% + SGST ${tax.sgstRate.toFixed(2)}%`}</b><small>applied automatically</small></div>
    <label className="pf"><span>Discount ₹</span><input value={f.discount} type="number" step="0.001" onChange={e=>update('discount',e.target.value)}/></label>
    <label className="pf"><span>Payment received ₹</span><input value={f.paymentAmount} type="number" step="0.001" onChange={e=>update('paymentAmount',e.target.value)}/></label>
    <label className="pf"><span>Payment mode</span><select value={f.paymentMode} onChange={e=>update('paymentMode',e.target.value)}><option>CASH</option><option>UPI</option><option>CARD</option><option>CREDIT</option></select></label>
   </div>
  </form>}
  {!ready&&<section className="posMain"><div className="posEmpty"><b>Select company and branch</b><small>Choose the operating context in the header to start billing.</small></div></section>}
  <BarcodeCameraScanner open={cameraOpen} onClose={()=>setCameraOpen(false)} onDetected={v=>{setCameraOpen(false);scanBarcodeValue(v)}}/>
  <BillSummary
   invoiceNo={String(f.invoiceNo||'')} saleDate={String(f.saleDate||'')} saleTime={String(f.saleTime||'')}
   customer={selectedCustomer?{name:selectedCustomer.name,phone:selectedCustomer.phone}:null}
   lines={lines} goldRate={Number(f.goldRate||0)} tax={tax} discount={Number(f.discount||0)}
   paymentAmount={Number(f.paymentAmount||0)} paymentMode={String(f.paymentMode||'CASH')} calc={calc} printFormat={printFormat}
   onCalculate={calculate} onPayment={v=>update('paymentAmount',v)} onMode={m=>update('paymentMode',m)} onRemove={removeItem}
   formId="billing-form" lastCreated={lastCreated} onPrint={openCreatedPdf} onWhatsApp={sendCreatedWhatsApp} onNewBill={()=>setLastCreated(null)}
  />
 </div>;
}

function toPayloadLine(x:Line, commonRate?:number){return {jewelleryItemId:Number(x.jewelleryItemId),grossWeight:Number(x.grossWeight||0),stoneWeight:Number(x.stoneWeight||0),netWeight:Number(x.netWeight||0),goldRate:Number(commonRate??x.goldRate??0),wastagePercent:Number(x.wastagePercent||0),makingCharge:Number(x.makingCharge||0),stoneCharge:Number(x.stoneCharge||0),otherCharge:Number(x.otherCharge||0)};}
