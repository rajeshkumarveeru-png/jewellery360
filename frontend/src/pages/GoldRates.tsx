import {FormEvent, useEffect, useMemo, useState} from 'react';
import {domainGoldRates, domainPurities, createDomainGoldRate, marketGoldRates} from '../api';
import {Empty, Table} from '../shared/ui';
import './GoldRates.css';

const today = () => new Date().toISOString().slice(0, 10);

export default function GoldRatesModule({ready,onNotice}:{ready:boolean;onNotice:(x:string)=>void}){
  const [rows,setRows]=useState<any[]>([]);
  const [purities,setPurities]=useState<any[]>([]);
  const [market,setMarket]=useState<any>({rates:[]});
  const [f,setF]=useState<any>({rateDate:today(),rates:{}});

  const load=async()=>{
    if(!ready){setRows([]);setPurities([]);return;}
    try{
      const [r,p,m]=await Promise.all([domainGoldRates(),domainPurities(),marketGoldRates()]);
      setRows(r.data||[]);setPurities(p.data||[]);setMarket(m.data||{marketRates:[]});
      const next:any={};
      (m.data?.marketRates||[]).forEach((x:any)=>{if(x.karat)next[x.karat]=x.ratePerGram;});
      setF((v:any)=>({...v,rates:{...next,...v.rates}}));
    }catch(e:any){onNotice(e?.response?.data?.message||'Unable to load gold rates');}
  };
  useEffect(()=>{load();},[ready]);

  const purityByKarat=useMemo(()=>{const m:any={};purities.forEach(x=>{m[String(x.karat||x.name).toUpperCase()]=x;});return m;},[purities]);
  const marketRates=Array.isArray(market?.marketRates)?market.marketRates:(Array.isArray(market?.rates)?market.rates:[]);

  const save=async(e:FormEvent)=>{
    e.preventDefault();
    if(!ready){onNotice('Select company and branch context first.');return;}
    try{
      const entries=Object.entries(f.rates||{}).filter(([,v])=>Number(v)>0);
      if(!entries.length){onNotice('Enter at least one gold rate.');return;}
      for(const [karat,value] of entries){
        const purity=purityByKarat[String(karat).toUpperCase()];
        if(!purity){onNotice(`Purity ${karat} is not configured.`);return;}
        await createDomainGoldRate({purityId:purity.id,rateDate:f.rateDate,ratePerGram:Number(value),source:'Manual / Jewellery360',active:true});
      }
      onNotice('Gold rates saved to PostgreSQL.');
      await load();
    }catch(e:any){onNotice(e?.response?.data?.message||'Unable to save gold rates');}
  };

  const useMarket=()=>{
    const next:any={};marketRates.forEach(x=>{if(x.karat)next[x.karat]=x.ratePerGram;});
    setF((v:any)=>({...v,rates:{...v.rates,...next},rateDate:market.date||today()}));
    onNotice('Current market reference rates loaded into the form.');
  };

  return <section className="menuPage menu-gold-rates-rates">
    <div className="goldRateTopGrid">
      <div className="panel goldRateFormPanel">
        <div className="panelHead"><div><span className="eyebrow">GOLD & RATES</span><h2>Save Gold Rates</h2></div><span className="pill success">LIVE DATABASE</span></div>
        <form className="formGrid" onSubmit={save}>
          <input type="date" required value={f.rateDate} onChange={e=>setF({...f,rateDate:e.target.value})}/>
          {['24K','22K','18K','14K'].map(k=><input key={k} type="number" step="0.001" placeholder={`${k} rate / g`} value={f.rates?.[k]??''} onChange={e=>setF({...f,rates:{...f.rates,[k]:e.target.value}})}/>)}
          <div className="buttonRow"><button type="button" className="secondary" onClick={useMarket} disabled={!marketRates.length}>Use current market rate</button><button className="primary">Save Gold Rates</button></div>
        </form>
      </div>
      <div className="panel marketRatePanel">
        <div className="panelHead"><div><span className="eyebrow">MARKET REFERENCE</span><h2>Today's Cuddalore rate</h2></div><span className="pill">{market.date||today()}</span></div>
        {marketRates.length?<div className="marketRateCards">{marketRates.map((x:any)=><div className="marketRateCard" key={x.karat}><span>{x.karat}</span><strong>₹{Number(x.ratePerGram).toLocaleString('en-IN')}</strong><small>per gram</small></div>)}</div>:<Empty text="Market rate temporarily unavailable."/>}
        <small className="marketSource">Indicative reference only · {market.source||'GoodReturns - Cuddalore'}</small>
      </div>
    </div>
    <div className="panel savedRatesPanel">
      <div className="panelHead"><div><span className="eyebrow">POSTGRESQL</span><h2>{rows.length} saved rate records</h2></div><button className="ghost" onClick={load}>Refresh</button></div>
      <Table><thead><tr><th>Purity</th><th>Rate / g</th><th>Rate / 10g</th><th>Date</th><th>Source</th><th>Status</th></tr></thead><tbody>{rows.map((x:any)=><tr key={x.id}><td><b>{x.purity?.name||x.purity?.karat||'—'}</b></td><td>₹{Number(x.ratePerGram||0).toLocaleString('en-IN',{maximumFractionDigits:3})}</td><td>₹{(Number(x.ratePerGram||0)*10).toLocaleString('en-IN',{maximumFractionDigits:3})}</td><td>{x.rateDate}</td><td>{x.source||'—'}</td><td><span className="pill success">{x.active?'ACTIVE':'INACTIVE'}</span></td></tr>)}</tbody></Table>
      {!rows.length&&<Empty text="No saved gold-rate records yet. Save a rate above to create a real PostgreSQL GoldRate record."/>}
    </div>
  </section>;
}
