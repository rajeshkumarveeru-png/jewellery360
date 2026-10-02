import {FormEvent, useEffect, useMemo, useState} from 'react';
import {domainGoldRates, domainPurities, createDomainGoldRate, headerGoldRates} from '../api';
import {Empty, Table} from '../shared/ui';
import './GoldRates.css';

const localToday = () => {
  const d = new Date();
  const y = d.getFullYear();
  const m = String(d.getMonth()+1).padStart(2,'0');
  const day = String(d.getDate()).padStart(2,'0');
  return `${y}-${m}-${day}`;
};

const toKarat = (value:any) => {
  const raw=String(value??'').toUpperCase();
  const match=raw.match(/(24|22|18|14)/);
  return match ? `${match[1]}K` : raw;
};

const normalizeRateRows = (value:any):any[] => {
  const source=Array.isArray(value)
    ? value
    : Array.isArray(value?.marketRates) ? value.marketRates
    : Array.isArray(value?.rates) ? value.rates
    : Array.isArray(value?.data?.marketRates) ? value.data.marketRates
    : Array.isArray(value?.data?.rates) ? value.data.rates
    : [];
  return source.map((x:any)=>({
    ...x,
    karat:toKarat(x?.karat ?? x?.purity ?? x?.goldKarat ?? x?.name),
    ratePerGram:Number(x?.ratePerGram ?? x?.rate ?? x?.pricePerGram ?? x?.price ?? 0)
  })).filter(x=>x.karat && x.ratePerGram>0);
};

const extractDate=(value:any)=>value?.date||value?.rateDate||value?.data?.date||value?.data?.rateDate||'';
const extractSource=(value:any)=>value?.marketSource||value?.source||value?.data?.marketSource||value?.data?.source||'GoodReturns - Cuddalore';

export default function GoldRatesModule({ready,onNotice}:{ready:boolean;onNotice:(x:string)=>void}){
  const [rows,setRows]=useState<any[]>([]);
  const [purities,setPurities]=useState<any[]>([]);
  const [market,setMarket]=useState<any>({rates:[]});
  const [marketError,setMarketError]=useState(false);
  const [f,setF]=useState<any>({rateDate:localToday(),rates:{}});

  const load=async()=>{
    if(!ready){setRows([]);setPurities([]);setMarket({rates:[]});return;}
    setMarketError(false);
    const [ratesResult,puritiesResult,marketResult]=await Promise.allSettled([
      domainGoldRates(),domainPurities(),headerGoldRates()
    ]);

    let saved:any[]=[];
    if(ratesResult.status==='fulfilled'){
      saved=Array.isArray(ratesResult.value.data)?ratesResult.value.data:[];
      setRows(saved);
    }else{
      setRows([]);
    }
    if(puritiesResult.status==='fulfilled'){
      setPurities(Array.isArray(puritiesResult.value.data)?puritiesResult.value.data:[]);
    }else{
      setPurities([]);
    }

    if(marketResult.status==='fulfilled'){
      const raw=marketResult.value.data||{};
      const normalized=normalizeRateRows(raw);
      setMarket({
        ...raw,
        marketRates:normalized,
        date:extractDate(raw)||localToday(),
        source:extractSource(raw)
      });
      if(!normalized.length) setMarketError(true);
    }else{
      setMarket({rates:[]});
      setMarketError(true);
    }

    // If the live market service is unavailable, still surface the latest database
    // gold rates instead of leaving the screen blank. These are clearly labelled as
    // saved database rates, not as a live market quote.
    const live = marketResult.status==='fulfilled' ? normalizeRateRows(marketResult.value.data) : [];
    if(!live.length && saved.length){
      const latestByKarat:any={};
      [...saved].sort((a:any,b:any)=>String(b.rateDate||'').localeCompare(String(a.rateDate||''))).forEach((x:any)=>{
        const k=toKarat(x?.purity?.karat ?? x?.purity?.name ?? x?.karat);
        if(k && Number(x.ratePerGram)>0 && !latestByKarat[k]) latestByKarat[k]={karat:k,ratePerGram:Number(x.ratePerGram)};
      });
      const fallback=Object.values(latestByKarat);
      if(fallback.length) setMarket({marketRates:fallback,date:String(saved[0]?.rateDate||localToday()),source:'Saved PostgreSQL GoldRate'});
    }

    // Populate the entry form from the live market response only. Database fallback
    // is intentionally not copied into the form because it is not a live quote.
    if(live.length){
      const next:any={};
      live.forEach((x:any)=>{if(x.karat)next[x.karat]=x.ratePerGram;});
      setF((v:any)=>({...v,rates:{...next,...v.rates}}));
    }
  };

  useEffect(()=>{void load();},[ready]);

  const purityByKarat=useMemo(()=>{
    const m:any={};
    purities.forEach(x=>{
      const k=toKarat(x.karat||x.name);
      if(k)m[k]=x;
    });
    return m;
  },[purities]);

  const marketRates=normalizeRateRows(market);
  const liveMarket=marketRates.length>0 && !String(market?.source||'').includes('Saved PostgreSQL');

  const save=async(e:FormEvent)=>{
    e.preventDefault();
    if(!ready){onNotice('Select company and branch context first.');return;}
    try{
      const entries=Object.entries(f.rates||{}).filter(([,v])=>Number(v)>0);
      if(!entries.length){onNotice('Enter at least one gold rate.');return;}
      for(const [karat,value] of entries){
        const purity=purityByKarat[toKarat(karat)];
        if(!purity){onNotice(`Purity ${karat} is not configured.`);return;}
        await createDomainGoldRate({purityId:purity.id,rateDate:f.rateDate,ratePerGram:Number(value),source:'Manual / Jewellery360',active:true});
      }
      onNotice('Gold rates saved to PostgreSQL.');
      await load();
    }catch(e:any){onNotice(e?.response?.data?.message||'Unable to save gold rates');}
  };

  const useMarket=()=>{
    if(!liveMarket){onNotice('Live Cuddalore market rate is not available right now.');return;}
    const next:any={};marketRates.forEach((x:any)=>{if(x.karat)next[x.karat]=x.ratePerGram;});
    setF((v:any)=>({...v,rates:{...v.rates,...next},rateDate:market.date||localToday()}));
    onNotice('Current Cuddalore market rates loaded into the form.');
  };

  return <section className="menuPage menu-gold-rates-rates">
    <div className="goldRateTopGrid">
      <div className="panel goldRateFormPanel">
        <div className="panelHead"><div><span className="eyebrow">GOLD & RATES</span><h2>Save Gold Rates</h2></div><span className="pill success">LIVE DATABASE</span></div>
        <form className="formGrid" onSubmit={save}>
          <input type="date" required value={f.rateDate} onChange={e=>setF({...f,rateDate:e.target.value})}/>
          {['24K','22K','18K','14K'].map(k=><input key={k} type="number" step="0.001" placeholder={`${k} rate / g`} value={f.rates?.[k]??''} onChange={e=>setF({...f,rates:{...f.rates,[k]:e.target.value}})}/>) }
          <div className="buttonRow"><button type="button" className="secondary" onClick={useMarket} disabled={!liveMarket}>Use current market rate</button><button className="primary">Save Gold Rates</button></div>
        </form>
      </div>
      <div className="panel marketRatePanel">
        <div className="panelHead"><div><span className="eyebrow">MARKET REFERENCE</span><h2>Today's Cuddalore rate</h2></div><div className="marketHeadActions"><span className="pill">{market.date||localToday()}</span><button type="button" className="ghost" onClick={load}>Refresh</button></div></div>
        {marketRates.length?<>
          <div className="marketRateCards">{marketRates.map((x:any)=><div className="marketRateCard" key={x.karat}><span>{x.karat}</span><strong>₹{Number(x.ratePerGram).toLocaleString('en-IN',{maximumFractionDigits:2})}</strong><small>per gram</small></div>)}</div>
          <div className={`marketAvailability ${liveMarket?'live':'fallback'}`}>{liveMarket?'● Live market reference':'● Latest saved PostgreSQL rate — market feed unavailable'}</div>
        </>:<Empty text={marketError?'Cuddalore market rate could not be fetched from the configured market service. Check the backend market-rate endpoint / external source.':'Market rate temporarily unavailable.'}/>} 
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
