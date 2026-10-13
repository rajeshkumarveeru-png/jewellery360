import {useEffect, useState} from 'react';
import type {DragEvent} from 'react';
import {getMenuLayout, resetMenuLayout, saveMenuLayout} from '../api';
import {DEFAULT_LABELS, PAGE_TABS} from './config';
import {ROLE_LABEL} from './permissions';
import {NavIcon} from './ui';
import {errText} from './kit';

/* Company menu (Settings → Menu): drag pages to reorder them or move them into another group, rename pages and groups,
   hide pages and limit pages to some roles. The menu is one level deep (group → page), so nothing can be nested wrongly.
   It only changes what is shown - what each person may open is set on the Users page and checked by the server. */

type Item = {key: string; label: string; group: string; hidden: boolean; roles: string[]};

export default function MenuEditor({companyId, canEdit, onNotice}: {companyId: number | null; canEdit: boolean; onNotice: (s: string) => void}) {
  const [items, setItems] = useState<Item[]>([]);
  const [saved, setSaved] = useState('');
  const [custom, setCustom] = useState(false);
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [open, setOpen] = useState<string | null>(null);
  const [drag, setDrag] = useState<string | null>(null);
  const [over, setOver] = useState<string | null>(null);
  const load = () => {
    if (!companyId) return;
    setLoading(true);
    getMenuLayout(companyId).then(r => {
      setCustom(!!r.data?.custom);
      const raw: any[] = Array.isArray(r.data?.items) ? r.data.items : [];
      const list: Item[] = raw.filter(i => PAGE_TABS[i.key]).map(i => ({key: i.key, label: i.label || '', group: i.group || 'Pages', hidden: !!i.hidden, roles: Array.isArray(i.roles) ? i.roles : []}));
      for (const k of (r.data?.pages || Object.keys(PAGE_TABS)) as string[]) if (PAGE_TABS[k] && !list.some(i => i.key === k)) list.push({key: k, label: '', group: 'Pages', hidden: false, roles: []});
      setItems(list);
      setSaved(JSON.stringify(list));
    }).catch(e => onNotice(errText(e))).finally(() => setLoading(false));
  };
  useEffect(load, [companyId]);
  const dirty = JSON.stringify(items) !== saved;
  const set = (key: string, p: Partial<Item>) => setItems(list => list.map(x => x.key === key ? {...x, ...p} : x));
  const groups = items.reduce<string[]>((g, i) => g.includes(i.group) ? g : [...g, i.group], []);

  /** Moves a page in front of another page (taking that page's group), or to the end of a group. */
  const place = (key: string, before: string | null, group: string) => setItems(list => {
    const moving = list.find(x => x.key === key);
    if (!moving) return list;
    const rest = list.filter(x => x.key !== key);
    const moved = {...moving, group};
    let at = before ? rest.findIndex(x => x.key === before) : -1;
    if (at < 0) { const lastOfGroup = rest.map(x => x.group).lastIndexOf(group); at = lastOfGroup < 0 ? rest.length : lastOfGroup + 1; }
    rest.splice(at, 0, moved);
    return rest;
  });
  // keyboard alternative to dragging: the first step across a group edge joins that group, the next one moves on
  const step = (key: string, delta: -1 | 1) => setItems(list => {
    const i = list.findIndex(x => x.key === key), j = i + delta;
    if (i < 0 || j < 0 || j >= list.length) return list;
    const n = [...list];
    if (list[i].group !== list[j].group) { n[i] = {...list[i], group: list[j].group}; return n; }
    [n[i], n[j]] = [n[j], n[i]];
    return n;
  });
  const renameGroup = (from: string, to: string) => setItems(list => list.map(x => x.group === from ? {...x, group: to} : x));
  const dragProps = (it: Item) => canEdit ? {
    draggable: true,
    onDragStart: (e: DragEvent) => { setDrag(it.key); e.dataTransfer.effectAllowed = 'move'; e.dataTransfer.setData('text/plain', it.key); },
    onDragEnd: () => { setDrag(null); setOver(null); },
    onDragOver: (e: DragEvent) => { e.preventDefault(); if (over !== it.key) setOver(it.key); },
    onDrop: (e: DragEvent) => { e.preventDefault(); e.stopPropagation(); const k = drag || e.dataTransfer.getData('text/plain'); setDrag(null); setOver(null); if (k && k !== it.key) place(k, it.key, it.group); }
  } : {};
  const save = async () => {
    if (groups.some(g => !g.trim())) { onNotice('Every group needs a name.'); return; }
    setSaving(true);
    try { await saveMenuLayout(items, companyId); onNotice('Menu saved for everyone in this company.'); setSaved(JSON.stringify(items)); setCustom(true); window.dispatchEvent(new Event('j360-menu-preferences-changed')); }
    catch (e) { onNotice(errText(e)); }
    finally { setSaving(false); }
  };
  const reset = async () => {
    try { await resetMenuLayout(companyId); onNotice('Default menu restored.'); load(); window.dispatchEvent(new Event('j360-menu-preferences-changed')); } catch (e) { onNotice(errText(e)); }
  };
  if (!companyId) return <p className="settingsHelp">Choose a company first.</p>;
  const name = (it: Item) => it.label.trim() || DEFAULT_LABELS[it.key] || PAGE_TABS[it.key];
  return <div className="mnEditor">
    <p className="settingsHelp">Drag pages by the ⠿ handle to reorder them or drop them on a page of another group to move them there. Rename pages and groups, hide pages, or show a page to some roles only. This only changes the menu - what each person may open is set on the Users page. {custom ? '' : 'You are seeing the default menu.'}</p>
    {loading && <div className="settingsNotice">Loading menu…</div>}
    <div className="mnLayout">
      <div className="mnGroups">
        {groups.map((g, gi) => <section key={gi} className={`mnGroup${drag && over === `group:${g}` ? ' over' : ''}`}
          onDragOver={e => { if (!canEdit) return; e.preventDefault(); }} onDrop={e => { if (!canEdit) return; e.preventDefault(); const k = drag || e.dataTransfer.getData('text/plain'); setDrag(null); setOver(null); if (k) place(k, null, g); }}>
          <div className="mnGroupHead" onDragEnter={() => setOver(`group:${g}`)}>
            <input value={g} disabled={!canEdit} maxLength={30} onChange={e => renameGroup(g, e.target.value)} aria-label={`Group name ${g}`} />
            <small>{items.filter(i => i.group === g).length} pages</small>
          </div>
          {items.filter(i => i.group === g).map(it => <div key={it.key} className={`mnRow${drag === it.key ? ' dragging' : ''}${over === it.key && drag !== it.key ? ' over' : ''}${it.hidden ? ' off' : ''}`} {...dragProps(it)}>
            {canEdit && <span className="mnHandle" title="Drag to move" aria-hidden="true">⠿</span>}
            <label className="mnShow"><input type="checkbox" checked={!it.hidden} disabled={!canEdit || it.key === 'SETTINGS'} onChange={e => set(it.key, {hidden: !e.target.checked})} aria-label={`Show ${PAGE_TABS[it.key]}`} /><span>{PAGE_TABS[it.key]}</span></label>
            <input className="mnName" value={it.label} disabled={!canEdit} maxLength={30} placeholder={DEFAULT_LABELS[it.key] || 'Name in menu'} onChange={e => set(it.key, {label: e.target.value})} aria-label={`Menu name for ${PAGE_TABS[it.key]}`} />
            <button type="button" className="mnRoles" onClick={() => setOpen(open === it.key ? null : it.key)} aria-expanded={open === it.key}>{it.roles.length ? `${it.roles.length} role${it.roles.length === 1 ? '' : 's'}` : 'All roles'}</button>
            {canEdit && <span className="mnSteps"><button type="button" onClick={() => step(it.key, -1)} aria-label={`Move ${PAGE_TABS[it.key]} up`}>↑</button><button type="button" onClick={() => step(it.key, 1)} aria-label={`Move ${PAGE_TABS[it.key]} down`}>↓</button></span>}
            {canEdit && groups.length > 1 && <select className="mnMove" value={it.group} onChange={e => place(it.key, null, e.target.value)} aria-label={`Group for ${PAGE_TABS[it.key]}`}>{groups.map(x => <option key={x} value={x}>{x}</option>)}</select>}
            {open === it.key && <div className="mnRoleList"><small>Show only to (none ticked = every role that has access):</small>
              {Object.entries(ROLE_LABEL).map(([r, l]) => <label key={r}><input type="checkbox" disabled={!canEdit} checked={it.roles.includes(r)} onChange={e => set(it.key, {roles: e.target.checked ? [...it.roles, r] : it.roles.filter(x => x !== r)})} /> {l}</label>)}</div>}
          </div>)}
        </section>)}
        {canEdit && <button type="button" className="mnAddGroup" onClick={() => { const last = items[items.length - 1]; if (last) place(last.key, null, `Group ${groups.length + 1}`); }}>＋ New group (moves the last page into it)</button>}
      </div>
      <section className="mnPreview" aria-label="Menu preview">
        <small>PREVIEW</small>
        {groups.map(g => { const shown = items.filter(i => i.group === g && !i.hidden); return shown.length ? <div key={g}><b>{g || '—'}</b>{shown.map(i => <span key={i.key}><NavIcon name={PAGE_TABS[i.key]} />{name(i)}</span>)}</div> : null; })}
      </section>
    </div>
    {canEdit && <div className="menuPreferenceActions"><button className="secondary" type="button" disabled={saving || !custom} onClick={reset}>Restore default</button>
      <button className="secondary" type="button" disabled={!dirty} onClick={() => setItems(JSON.parse(saved))}>Undo changes</button>
      <button className="settingsSave" type="button" disabled={saving || loading || !dirty} onClick={save}>{saving ? 'Saving…' : 'Save menu'}</button></div>}
  </div>;
}
