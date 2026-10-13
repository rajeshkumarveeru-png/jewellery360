import {useEffect, useMemo, useRef, useState} from 'react';
import type {KeyboardEvent} from 'react';

export type PaletteItem = {id: string; label: string; hint: string; group: string; run: () => void};

/* Ctrl/Cmd+K: type to jump to any page or start a common task. Arrow keys + Enter, Esc closes. */
export default function CommandPalette({open, items, onClose, search}: {open: boolean; items: PaletteItem[]; onClose: () => void; search?: (q: string) => Promise<PaletteItem[]>}) {
  const [q, setQ] = useState('');
  const [found, setFound] = useState<PaletteItem[]>([]);
  useEffect(() => {
    if (!search || q.trim().length < 2) { setFound([]); return; }
    let live = true;
    const t = window.setTimeout(() => { void search(q).then(r => { if (live) setFound(r); }); }, 250);
    return () => { live = false; window.clearTimeout(t); };
  }, [q, search]);
  const [cursor, setCursor] = useState(0);
  const input = useRef<HTMLInputElement>(null);
  useEffect(() => { if (open) { setQ(''); setCursor(0); window.setTimeout(() => input.current?.focus(), 30); } }, [open]);
  const results = useMemo(() => {
    const needle = q.trim().toLowerCase();
    if (!needle) return items;
    return [...items.filter(i => `${i.label} ${i.hint} ${i.group}`.toLowerCase().includes(needle)), ...found];
  }, [q, items, found]);
  useEffect(() => setCursor(0), [q]);
  if (!open) return null;
  const onKey = (e: KeyboardEvent<HTMLDivElement>) => {
    if (e.key === 'Escape') { e.preventDefault(); onClose(); }
    else if (e.key === 'ArrowDown') { e.preventDefault(); setCursor(c => Math.min(results.length - 1, c + 1)); }
    else if (e.key === 'ArrowUp') { e.preventDefault(); setCursor(c => Math.max(0, c - 1)); }
    else if (e.key === 'Enter' && results[cursor]) { e.preventDefault(); const r = results[cursor]; onClose(); r.run(); }
  };
  return (
    <div className="cmdOverlay" role="dialog" aria-modal="true" aria-label="Quick jump" onClick={onClose} onKeyDown={onKey}>
      <div className="cmdPanel" onClick={e => e.stopPropagation()}>
        <input ref={input} value={q} onChange={e => setQ(e.target.value)} placeholder="Search customers, tags, bills, orders… or jump to a page" aria-label="Quick jump search" />
        <div className="cmdList" role="listbox">
          {results.length === 0 && <div className="cmdEmpty">Nothing matches “{q}”.</div>}
          {results.map((r, i) => (
            <button key={r.id} type="button" role="option" aria-selected={i === cursor} className={i === cursor ? 'on' : ''} onMouseEnter={() => setCursor(i)} onClick={() => { onClose(); r.run(); }}>
              <span className="cmdGroup">{r.group}</span><b>{r.label}</b><small>{r.hint}</small>
            </button>
          ))}
        </div>
        <div className="cmdHint"><span><kbd>↑</kbd><kbd>↓</kbd> move</span><span><kbd>Enter</kbd> open</span><span><kbd>Esc</kbd> close</span></div>
      </div>
    </div>
  );
}
