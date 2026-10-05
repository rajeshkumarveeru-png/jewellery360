import {useCallback, useEffect, useLayoutEffect, useMemo, useRef, useState} from 'react';
import type {CSSProperties, KeyboardEvent as ReactKeyboardEvent} from 'react';
import {createPortal} from 'react-dom';

/* ------------------------------------------------------------------------------------------------
   Date helpers (local calendar dates as 'YYYY-MM-DD' strings - no time-zone surprises)
   ------------------------------------------------------------------------------------------------ */
export const toIso = (d: Date): string => {
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${d.getFullYear()}-${m}-${day}`;
};

export const fromIso = (iso: string): Date => {
  const [y, m, d] = iso.split('-').map(Number);
  return new Date(y, (m || 1) - 1, d || 1);
};

const addDays = (d: Date, n: number): Date => new Date(d.getFullYear(), d.getMonth(), d.getDate() + n);
const MONTHS = ['January', 'February', 'March', 'April', 'May', 'June', 'July', 'August', 'September', 'October', 'November', 'December'];
const WEEKDAYS = ['Su', 'Mo', 'Tu', 'We', 'Th', 'Fr', 'Sa'];

export const prettyDate = (iso: string): string => {
  if (!iso) return '—';
  const d = fromIso(iso);
  return `${String(d.getDate()).padStart(2, '0')} ${MONTHS[d.getMonth()].slice(0, 3)} ${d.getFullYear()}`;
};

export const inr = (value: number | string | null | undefined, digits = 0): string =>
  `₹${Number(value || 0).toLocaleString('en-IN', {minimumFractionDigits: digits, maximumFractionDigits: digits})}`;

/** Compact money for chart axes: ₹950, ₹12k, ₹3.4L, ₹1.2Cr */
export const inrCompact = (value: number): string => {
  const v = Math.abs(value);
  const sign = value < 0 ? '-' : '';
  if (v >= 10000000) return `${sign}₹${(v / 10000000).toFixed(v >= 100000000 ? 0 : 1).replace(/\.0$/, '')}Cr`;
  if (v >= 100000) return `${sign}₹${(v / 100000).toFixed(v >= 1000000 ? 0 : 1).replace(/\.0$/, '')}L`;
  if (v >= 1000) return `${sign}₹${(v / 1000).toFixed(v >= 10000 ? 0 : 1).replace(/\.0$/, '')}k`;
  return `${sign}₹${Math.round(v)}`;
};

export type RangePreset = {key: string; label: string; range: () => [string, string]};

export const defaultPresets = (): RangePreset[] => {
  const today = () => new Date();
  return [
    {key: 'today', label: 'Today', range: () => [toIso(today()), toIso(today())]},
    {key: 'yesterday', label: 'Yesterday', range: () => [toIso(addDays(today(), -1)), toIso(addDays(today(), -1))]},
    {key: '7d', label: 'Last 7 days', range: () => [toIso(addDays(today(), -6)), toIso(today())]},
    {key: '30d', label: 'Last 30 days', range: () => [toIso(addDays(today(), -29)), toIso(today())]},
    {key: 'month', label: 'This month', range: () => {
      const t = today();
      return [toIso(new Date(t.getFullYear(), t.getMonth(), 1)), toIso(t)];
    }},
    {key: 'lastmonth', label: 'Last month', range: () => {
      const t = today();
      return [toIso(new Date(t.getFullYear(), t.getMonth() - 1, 1)), toIso(new Date(t.getFullYear(), t.getMonth(), 0))];
    }},
    {key: 'year', label: 'This year', range: () => {
      const t = today();
      return [toIso(new Date(t.getFullYear(), 0, 1)), toIso(t)];
    }}
  ];
};

/* ------------------------------------------------------------------------------------------------
   DateRangePicker - From date / To date calendar filter.
   The popover is rendered in a portal on document.body with position:fixed, so it is never cropped by a card's
   overflow, a table wrapper or a stacking context, and always paints above tables and cards.
   ------------------------------------------------------------------------------------------------ */
type DateRangePickerProps = {
  from: string;
  to: string;
  onChange: (from: string, to: string) => void;
  label?: string;
  presets?: RangePreset[];
  maxDate?: string;
};

export function DateRangePicker({from, to, onChange, label = 'Date range', presets, maxDate}: DateRangePickerProps) {
  const list = useMemo(() => presets || defaultPresets(), [presets]);
  const [open, setOpen] = useState(false);
  const [view, setView] = useState<Date>(() => fromIso(to || from || toIso(new Date())));
  const [draftFrom, setDraftFrom] = useState(from);
  const [draftTo, setDraftTo] = useState(to);
  const [picking, setPicking] = useState<'from' | 'to'>('from');
  const [hover, setHover] = useState('');
  const [pos, setPos] = useState<{top: number; left: number; width: number}>({top: 0, left: 0, width: 340});
  const buttonRef = useRef<HTMLButtonElement>(null);
  const popRef = useRef<HTMLDivElement>(null);

  const limit = maxDate || '';
  const place = useCallback(() => {
    const el = buttonRef.current;
    if (!el) return;
    const r = el.getBoundingClientRect();
    const width = Math.min(340, window.innerWidth - 16);
    let left = r.left;
    if (left + width > window.innerWidth - 8) left = Math.max(8, window.innerWidth - width - 8);
    const below = r.bottom + 8;
    const room = window.innerHeight - below;
    // flip above the button when there is not enough room below
    const top = room < 430 && r.top > 430 ? Math.max(8, r.top - 8 - 430) : below;
    setPos({top, left, width});
  }, []);

  useLayoutEffect(() => {
    if (open) place();
  }, [open, place]);

  useEffect(() => {
    if (!open) return;
    const onResize = () => place();
    const onDown = (e: MouseEvent) => {
      const t = e.target as Node;
      if (popRef.current?.contains(t) || buttonRef.current?.contains(t)) return;
      setOpen(false);
    };
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        setOpen(false);
        buttonRef.current?.focus();
      }
    };
    window.addEventListener('resize', onResize);
    window.addEventListener('scroll', onResize, true);
    document.addEventListener('mousedown', onDown);
    document.addEventListener('keydown', onKey);
    return () => {
      window.removeEventListener('resize', onResize);
      window.removeEventListener('scroll', onResize, true);
      document.removeEventListener('mousedown', onDown);
      document.removeEventListener('keydown', onKey);
    };
  }, [open, place]);

  const openPicker = () => {
    setDraftFrom(from);
    setDraftTo(to);
    setPicking('from');
    setView(fromIso(to || from || toIso(new Date())));
    setOpen(true);
  };

  const grid = useMemo(() => {
    const first = new Date(view.getFullYear(), view.getMonth(), 1);
    const start = addDays(first, -first.getDay());
    return Array.from({length: 42}, (_, i) => addDays(start, i));
  }, [view]);

  const pick = (iso: string) => {
    if (limit && iso > limit) return;
    if (picking === 'from' || !draftFrom || iso < draftFrom) {
      setDraftFrom(iso);
      setDraftTo(iso);
      setPicking('to');
    } else {
      setDraftTo(iso);
      setPicking('from');
    }
  };

  const apply = () => {
    const a = draftFrom || draftTo;
    const b = draftTo || draftFrom;
    if (!a || !b) return;
    onChange(a <= b ? a : b, a <= b ? b : a);
    setOpen(false);
    buttonRef.current?.focus();
  };

  const effectiveTo = picking === 'to' && hover && hover >= draftFrom ? hover : draftTo;
  const todayIso = toIso(new Date());
  const onGridKey = (e: ReactKeyboardEvent<HTMLDivElement>) => {
    if (e.key === 'Enter') {
      e.preventDefault();
      apply();
    }
  };

  const popover = open
    ? createPortal(
        <div
          ref={popRef}
          className="drpPopover"
          role="dialog"
          aria-label={`${label} calendar`}
          style={{top: pos.top, left: pos.left, width: pos.width} as CSSProperties}
          onKeyDown={onGridKey}
        >
          <div className="drpPresets" role="group" aria-label="Quick ranges">
            {list.map(p => (
              <button
                key={p.key}
                type="button"
                className="drpPreset"
                onClick={() => {
                  const [a, b] = p.range();
                  onChange(a, b);
                  setOpen(false);
                  buttonRef.current?.focus();
                }}
              >
                {p.label}
              </button>
            ))}
          </div>

          <div className="drpFields">
            <label className={picking === 'from' ? 'active' : ''}>
              <span>From date</span>
              <input type="date" value={draftFrom} max={limit || undefined} onFocus={() => setPicking('from')} onChange={e => { setDraftFrom(e.target.value); if (draftTo && e.target.value > draftTo) setDraftTo(e.target.value); }} />
            </label>
            <label className={picking === 'to' ? 'active' : ''}>
              <span>To date</span>
              <input type="date" value={draftTo} min={draftFrom || undefined} max={limit || undefined} onFocus={() => setPicking('to')} onChange={e => setDraftTo(e.target.value)} />
            </label>
          </div>

          <div className="drpHead">
            <button type="button" className="drpNav" aria-label="Previous month" onClick={() => setView(new Date(view.getFullYear(), view.getMonth() - 1, 1))}>‹</button>
            <b>{MONTHS[view.getMonth()]} {view.getFullYear()}</b>
            <button type="button" className="drpNav" aria-label="Next month" onClick={() => setView(new Date(view.getFullYear(), view.getMonth() + 1, 1))}>›</button>
          </div>
          <div className="drpWeek" aria-hidden="true">{WEEKDAYS.map(w => <span key={w}>{w}</span>)}</div>
          <div className="drpGrid" role="grid" aria-label={`${MONTHS[view.getMonth()]} ${view.getFullYear()}`} onMouseLeave={() => setHover('')}>
            {grid.map(d => {
              const iso = toIso(d);
              const outside = d.getMonth() !== view.getMonth();
              const isStart = iso === draftFrom;
              const isEnd = iso === effectiveTo;
              const inRange = !!draftFrom && !!effectiveTo && iso > draftFrom && iso < effectiveTo;
              const disabled = !!limit && iso > limit;
              const cls = ['drpDay', outside ? 'outside' : '', isStart ? 'start' : '', isEnd ? 'end' : '', inRange ? 'in' : '', iso === todayIso ? 'today' : ''].filter(Boolean).join(' ');
              return (
                <button
                  key={iso}
                  type="button"
                  role="gridcell"
                  className={cls}
                  disabled={disabled}
                  aria-selected={isStart || isEnd}
                  aria-label={prettyDate(iso)}
                  onClick={() => pick(iso)}
                  onMouseEnter={() => setHover(iso)}
                >
                  {d.getDate()}
                </button>
              );
            })}
          </div>

          <div className="drpFoot">
            <span>{draftFrom ? `${prettyDate(draftFrom)} → ${prettyDate(draftTo || draftFrom)}` : 'Pick a start date'}</span>
            <div>
              <button type="button" className="ghost" onClick={() => setOpen(false)}>Cancel</button>
              <button type="button" className="primary" onClick={apply} disabled={!draftFrom}>Apply</button>
            </div>
          </div>
        </div>,
        document.body
      )
    : null;

  return (
    <div className="drp">
      <button
        ref={buttonRef}
        type="button"
        className={`drpButton${open ? ' open' : ''}`}
        aria-haspopup="dialog"
        aria-expanded={open}
        onClick={() => (open ? setOpen(false) : openPicker())}
      >
        <span className="drpIcon" aria-hidden="true">▦</span>
        <span className="drpText"><small>{label}</small><b>{prettyDate(from)} <i>→</i> {prettyDate(to)}</b></span>
        <span className="drpCaret" aria-hidden="true">▾</span>
      </button>
      {popover}
    </div>
  );
}

/* ------------------------------------------------------------------------------------------------
   TrendChart - dependency-free SVG line / bar chart with hover tooltip (daily, weekly or monthly points)
   ------------------------------------------------------------------------------------------------ */
export type TrendPoint = {label: string; value: number; secondary?: string};

type TrendChartProps = {
  data: TrendPoint[];
  mode: 'line' | 'bar';
  height?: number;
  valueFormat?: (n: number) => string;
  emptyText?: string;
  ariaLabel?: string;
};

export function TrendChart({data, mode, height = 240, valueFormat = inr, emptyText = 'No sales recorded in this period.', ariaLabel = 'Sales trend'}: TrendChartProps) {
  const [active, setActive] = useState<number | null>(null);
  const W = 720;
  const H = height;
  const pad = {l: 54, r: 14, t: 16, b: 30};
  const iw = W - pad.l - pad.r;
  const ih = H - pad.t - pad.b;
  const max = Math.max(1, ...data.map(d => d.value));
  const nice = niceMax(max);
  const n = data.length;
  const x = (i: number) => pad.l + (n <= 1 ? iw / 2 : (iw * i) / (n - 1));
  const slot = iw / Math.max(1, n);
  const bx = (i: number) => pad.l + slot * i + slot / 2;
  const y = (v: number) => pad.t + ih - (v / nice) * ih;
  const ticks = [0, 0.25, 0.5, 0.75, 1].map(t => t * nice);
  const every = Math.max(1, Math.ceil(n / 8));
  const total = data.reduce((s, d) => s + d.value, 0);

  if (!n || total <= 0) {
    return <div className="trendEmpty" role="img" aria-label={`${ariaLabel}: ${emptyText}`}><span aria-hidden="true">◇</span><b>{emptyText}</b><small>Create an invoice or widen the date range.</small></div>;
  }

  const points = data.map((d, i) => `${x(i).toFixed(1)},${y(d.value).toFixed(1)}`);
  const linePath = smoothPath(data.map((d, i) => [x(i), y(d.value)]));
  const areaPath = `${linePath} L ${x(n - 1).toFixed(1)} ${(pad.t + ih).toFixed(1)} L ${x(0).toFixed(1)} ${(pad.t + ih).toFixed(1)} Z`;
  const tip = active !== null ? data[active] : null;
  const tipX = active !== null ? (mode === 'bar' ? bx(active) : x(active)) : 0;

  return (
    <div className="trendChart" onMouseLeave={() => setActive(null)}>
      <svg viewBox={`0 0 ${W} ${H}`} role="img" aria-label={`${ariaLabel}. Total ${valueFormat(total)} across ${n} points.`} preserveAspectRatio="xMidYMid meet">
        <defs>
          <linearGradient id="trendFill" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stopColor="var(--j360-accent)" stopOpacity="0.32" />
            <stop offset="100%" stopColor="var(--j360-accent)" stopOpacity="0.02" />
          </linearGradient>
          <linearGradient id="trendBar" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stopColor="var(--j360-accent-2)" />
            <stop offset="100%" stopColor="var(--j360-accent)" />
          </linearGradient>
        </defs>
        {ticks.map(t => (
          <g key={t}>
            <line x1={pad.l} x2={W - pad.r} y1={y(t)} y2={y(t)} className="trendGrid" />
            <text x={pad.l - 8} y={y(t) + 4} textAnchor="end" className="trendAxis">{inrCompact(t)}</text>
          </g>
        ))}
        {mode === 'line' ? (
          <>
            <path d={areaPath} fill="url(#trendFill)" className="trendArea" />
            <path d={linePath} className="trendLine" pathLength={1} />
            {data.map((d, i) => (n <= 40 || i === active) && <circle key={i} cx={x(i)} cy={y(d.value)} r={i === active ? 5 : 3} className={`trendDot${i === active ? ' on' : ''}`} />)}
            <polyline points={points.join(' ')} fill="none" stroke="transparent" />
          </>
        ) : (
          data.map((d, i) => {
            const bw = Math.max(3, Math.min(34, slot * 0.62));
            const top = y(d.value);
            return <rect key={i} x={bx(i) - bw / 2} y={top} width={bw} height={Math.max(1, pad.t + ih - top)} rx={Math.min(6, bw / 2)} className={`trendBar${i === active ? ' on' : ''}`} style={{animationDelay: `${Math.min(i * 18, 600)}ms`} as CSSProperties} />;
          })
        )}
        {data.map((d, i) => (i === n - 1 || (i % every === 0 && n - 1 - i >= Math.ceil(every / 2))) && (
          <text key={`l${i}`} x={mode === 'bar' ? bx(i) : x(i)} y={H - 8} textAnchor="middle" className="trendAxis">{d.label}</text>
        ))}
        {data.map((_, i) => (
          <rect
            key={`h${i}`}
            x={mode === 'bar' ? pad.l + slot * i : x(i) - (iw / Math.max(1, n - 1)) / 2}
            y={pad.t}
            width={mode === 'bar' ? slot : iw / Math.max(1, n - 1)}
            height={ih}
            fill="transparent"
            onMouseEnter={() => setActive(i)}
            onFocus={() => setActive(i)}
            onTouchStart={() => setActive(i)}
            tabIndex={-1}
          />
        ))}
        {active !== null && <line x1={tipX} x2={tipX} y1={pad.t} y2={pad.t + ih} className="trendCursor" />}
      </svg>
      {tip && (
        <div className="trendTip" style={{left: `${(tipX / W) * 100}%`}} role="status">
          <small>{tip.label}</small>
          <b>{valueFormat(tip.value)}</b>
          {tip.secondary && <span>{tip.secondary}</span>}
        </div>
      )}
    </div>
  );
}

function niceMax(v: number): number {
  const exp = Math.pow(10, Math.floor(Math.log10(v)));
  const f = v / exp;
  const nf = f <= 1 ? 1 : f <= 2 ? 2 : f <= 2.5 ? 2.5 : f <= 5 ? 5 : 10;
  return nf * exp;
}

/** monotone-ish smoothing: cubic bezier through the points with horizontal control handles */
function smoothPath(pts: number[][]): string {
  if (!pts.length) return '';
  if (pts.length === 1) return `M ${pts[0][0].toFixed(1)} ${pts[0][1].toFixed(1)}`;
  let d = `M ${pts[0][0].toFixed(1)} ${pts[0][1].toFixed(1)}`;
  for (let i = 1; i < pts.length; i++) {
    const [x0, y0] = pts[i - 1];
    const [x1, y1] = pts[i];
    const cx = (x0 + x1) / 2;
    d += ` C ${cx.toFixed(1)} ${y0.toFixed(1)}, ${cx.toFixed(1)} ${y1.toFixed(1)}, ${x1.toFixed(1)} ${y1.toFixed(1)}`;
  }
  return d;
}

/* ------------------------------------------------------------------------------------------------
   Small presentational helpers
   ------------------------------------------------------------------------------------------------ */
export function Segmented<T extends string>({value, options, onChange, label}: {value: T; options: {value: T; label: string}[]; onChange: (v: T) => void; label: string}) {
  const index = Math.max(0, options.findIndex(o => o.value === value));
  return (
    <div className="segmented" role="radiogroup" aria-label={label} style={{'--n': options.length, '--i': index} as CSSProperties}>
      <span className="segmentedThumb" aria-hidden="true" />
      {options.map(o => (
        <button key={o.value} type="button" role="radio" aria-checked={o.value === value} className={o.value === value ? 'on' : ''} onClick={() => onChange(o.value)}>{o.label}</button>
      ))}
    </div>
  );
}

export function DeltaChip({value}: {value: number}) {
  const up = value >= 0;
  return <span className={`deltaChip ${up ? 'up' : 'down'}`}>{up ? '▲' : '▼'} {Math.abs(value).toFixed(1)}% vs yesterday</span>;
}
