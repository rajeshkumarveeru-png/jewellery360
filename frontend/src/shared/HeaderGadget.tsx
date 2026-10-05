import {useEffect, useMemo, useRef, useState} from 'react';
import {headerGoldRates} from '../api';

/* ------------------------------------------------------------------------------------------------
   Header gadget: analog + digital Indian-time clock, date plate and today's gold-rate board.
   Time is always Asia/Kolkata. Rates refresh every minute; an arrow shows whether a rate moved since the last refresh.
   ------------------------------------------------------------------------------------------------ */
const TZ = 'Asia/Kolkata';

const partsOf = (d: Date) => {
  const f = new Intl.DateTimeFormat('en-GB', {timeZone: TZ, hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false, weekday: 'long', day: '2-digit', month: 'short', year: 'numeric'});
  const m: Record<string, string> = {};
  f.formatToParts(d).forEach(p => { if (p.type !== 'literal') m[p.type] = p.value; });
  const h24 = Number(m.hour) % 24;
  return {h24, h12: h24 % 12 === 0 ? 12 : h24 % 12, minute: Number(m.minute), second: Number(m.second), ampm: h24 >= 12 ? 'PM' : 'AM', weekday: m.weekday, day: m.day, month: m.month, year: m.year};
};

const greeting = (h: number) => (h < 12 ? 'Good morning' : h < 17 ? 'Good afternoon' : 'Good evening');
const karatKey = (x: any) => String(x?.karat || x?.purity || '').replace(/[^0-9]/g, '');

function AnalogClock({h, m, s}: {h: number; m: number; s: number}) {
  const hourDeg = ((h % 12) + m / 60) * 30;
  const minDeg = (m + s / 60) * 6;
  const secDeg = s * 6;
  return (
    <svg className="jClock" viewBox="0 0 100 100" role="img" aria-label="Analog clock">
      <defs>
        <linearGradient id="jBezel" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stopColor="#f6dc9a" /><stop offset=".5" stopColor="#c9953f" /><stop offset="1" stopColor="#8f6420" /></linearGradient>
        <radialGradient id="jFace" cx=".35" cy=".3" r=".9"><stop offset="0" stopColor="#2a3544" /><stop offset="1" stopColor="#0d141c" /></radialGradient>
      </defs>
      <circle cx="50" cy="50" r="47" fill="url(#jBezel)" />
      <circle cx="50" cy="50" r="42.5" fill="url(#jFace)" />
      <circle cx="50" cy="50" r="42.5" fill="none" stroke="rgba(255,255,255,.14)" strokeWidth=".8" />
      {Array.from({length: 60}).map((_, i) => {
        const major = i % 5 === 0;
        const a = (i * 6 * Math.PI) / 180;
        const r1 = major ? 35.5 : 39;
        const r2 = 41;
        return <line key={i} x1={50 + r1 * Math.sin(a)} y1={50 - r1 * Math.cos(a)} x2={50 + r2 * Math.sin(a)} y2={50 - r2 * Math.cos(a)} stroke={major ? '#e3bd6c' : 'rgba(255,255,255,.28)'} strokeWidth={major ? 1.6 : .7} strokeLinecap="round" />;
      })}
      {/* diamond at 12 o'clock */}
      <path d="M50 10.5 L53 15 L50 19.5 L47 15 Z" fill="#fff4d6" stroke="#e3bd6c" strokeWidth=".6" />
      <g className="jHand" style={{transform: `rotate(${hourDeg}deg)`}}><line x1="50" y1="53" x2="50" y2="29" stroke="#f3dca0" strokeWidth="3.4" strokeLinecap="round" /></g>
      <g className="jHand" style={{transform: `rotate(${minDeg}deg)`}}><line x1="50" y1="55" x2="50" y2="20" stroke="#ffffff" strokeWidth="2.4" strokeLinecap="round" /></g>
      <g className="jHand sec" style={{transform: `rotate(${secDeg}deg)`}}><line x1="50" y1="60" x2="50" y2="15" stroke="#e8837f" strokeWidth="1.1" strokeLinecap="round" /><circle cx="50" cy="50" r="2.2" fill="#e8837f" /></g>
      <circle cx="50" cy="50" r="1.4" fill="#0d141c" />
    </svg>
  );
}

export default function HeaderGadget({ready}: {ready: boolean}) {
  const [now, setNow] = useState(() => new Date());
  const [rates, setRates] = useState<any[]>([]);
  const [source, setSource] = useState('');
  const [rateDate, setRateDate] = useState('');
  const [updated, setUpdated] = useState<Date | null>(null);
  const [moves, setMoves] = useState<Record<string, 'up' | 'down'>>({});
  const last = useRef<Record<string, number>>({});

  useEffect(() => {
    const id = window.setInterval(() => setNow(new Date()), 1000);
    return () => window.clearInterval(id);
  }, []);

  useEffect(() => {
    if (!ready) { setRates([]); setSource(''); return; }
    let cancelled = false;
    const load = () => headerGoldRates().then(r => {
      if (cancelled) return;
      const list: any[] = Array.isArray(r.data?.marketRates) && r.data.marketRates.length ? r.data.marketRates : (Array.isArray(r.data?.rates) ? r.data.rates : []);
      const next: Record<string, 'up' | 'down'> = {};
      list.forEach(x => {
        const k = karatKey(x);
        const v = Number(x.ratePerGram || 0);
        const before = last.current[k];
        if (before !== undefined && v !== before) next[k] = v > before ? 'up' : 'down';
        last.current[k] = v;
      });
      if (Object.keys(next).length) setMoves(m => ({...m, ...next}));
      setRates(list);
      setSource(r.data?.marketSource || '');
      setRateDate(r.data?.date || '');
      setUpdated(new Date());
    }).catch(() => { if (!cancelled) setRates([]); });
    void load();
    const id = window.setInterval(load, 60000);
    return () => { cancelled = true; window.clearInterval(id); };
  }, [ready]);

  const t = partsOf(now);
  const top = useMemo(() => {
    const unique = new Map<string, any>();
    rates.filter(x => x.active !== false).forEach(x => unique.set(karatKey(x), x));
    return [...unique.values()].sort((a, b) => Number(karatKey(b)) - Number(karatKey(a))).slice(0, 3);
  }, [rates]);

  return (
    <div className="jGadget" role="group" aria-label="Date, time and today's gold rates">
      <div className="jTime">
        <AnalogClock h={t.h12} m={t.minute} s={t.second} />
        <div className="jDigital" aria-live="off">
          <small className="jGreet">{greeting(t.h24)}</small>
          <time dateTime={now.toISOString()} aria-label={`${t.h12}:${String(t.minute).padStart(2, '0')} ${t.ampm}`}>
            <b>{String(t.h12).padStart(2, '0')}</b><span className="jColon">:</span><b>{String(t.minute).padStart(2, '0')}</b>
            <i className="jSec">{String(t.second).padStart(2, '0')}</i><em>{t.ampm}</em>
          </time>
        </div>
        <div className="jDate" title={`${t.weekday}, ${t.day} ${t.month} ${t.year}`}>
          <small>{t.weekday.slice(0, 3).toUpperCase()}</small>
          <b>{t.day}</b>
          <span>{t.month} {t.year}</span>
        </div>
      </div>

      <span className="jOrnament" aria-hidden="true"><i /><u>◆</u><i /></span>

      <div className="jRates">
        <div className="jRatesHead"><span>TODAY'S GOLD RATE</span>{rateDate && <small>{rateDate}</small>}</div>
        <div className="jRateRow">
          {!ready ? <span className="jMuted">Select company &amp; branch</span>
            : top.length ? top.map((x, i) => {
              const k = karatKey(x);
              const mv = moves[k];
              return (
                <span className={`jRate${mv ? ` ${mv}` : ''}`} key={`${k}-${i}`} title={mv ? `Moved ${mv === 'up' ? 'up' : 'down'} since the last refresh` : 'Unchanged since the last refresh'}>
                  <b>{x.karat || x.purity}</b>
                  <span>₹{Number(x.ratePerGram || 0).toLocaleString('en-IN', {maximumFractionDigits: 2})}<small>/g</small></span>
                  {mv && <i aria-label={mv === 'up' ? 'rate went up' : 'rate went down'}>{mv === 'up' ? '▲' : '▼'}</i>}
                </span>
              );
            }) : <span className="jMuted">No market rate</span>}
        </div>
        {(source || updated) && <small className="jSource">{source}{source && updated ? ' · ' : ''}{updated ? `updated ${updated.toLocaleTimeString('en-IN', {hour: '2-digit', minute: '2-digit', timeZone: TZ})}` : ''}</small>}
      </div>
    </div>
  );
}
