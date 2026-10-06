import {Ico} from './icons';
import {PAGE_DEFS, POWER_DEFS, ROLE_LABEL, STAFF_ROLES, presetOf, roleDefaults} from './permissions';

/* Switches for the pages and powers a staff user gets. Used when creating a user and on "Manage access". */
export default function AccessEditor({value, role, onChange}: {value: string[]; role?: string; onChange: (next: string[]) => void}) {
  const current = value.length ? value : roleDefaults(role || 'CASHIER');
  const preset = presetOf(current, role);
  const pagesOn = PAGE_DEFS.filter(p => current.includes(p.key)).length;
  const toggle = (key: string, isPage: boolean) => {
    const on = current.includes(key);
    if (on && isPage && pagesOn <= 1) return; // keep at least one page so the user can still sign in and work
    onChange(on ? current.filter(k => k !== key) : [...current, key]);
  };
  return (
    <div className="ac-editor">
      <div className="ac-presets" role="group" aria-label="Start from a role">
        <span className="ac-presets-label">Start from</span>
        {STAFF_ROLES.map(r => (
          <button key={r} type="button" className={preset === r ? 'on' : ''} aria-pressed={preset === r} onClick={() => onChange(roleDefaults(r))}>
            {preset === r && <Ico name="check" size={13} />} {ROLE_LABEL[r]}
          </button>
        ))}
        <span className={`ac-custom${preset === 'CUSTOM' ? ' on' : ''}`}>{preset === 'CUSTOM' ? 'Custom access' : 'Same as the ' + ROLE_LABEL[preset].toLowerCase() + ' role'}</span>
      </div>
      <h5>Pages they can open <small>{pagesOn} of {PAGE_DEFS.length}</small></h5>
      <div className="ac-grid">
        {PAGE_DEFS.map(p => {
          const on = current.includes(p.key);
          return (
            <button key={p.key} type="button" role="switch" aria-checked={on} className={`ac-row${on ? ' on' : ''}`} disabled={on && pagesOn <= 1} onClick={() => toggle(p.key, true)}>
              <span className="ac-copy"><b>{p.label}</b><small>{p.hint}</small></span><span className="ac-switch" aria-hidden="true"><i /></span>
            </button>
          );
        })}
      </div>
      <h5>Extra powers <small>off unless the role has them</small></h5>
      <div className="ac-list">
        {POWER_DEFS.map(p => {
          const on = current.includes(p.key);
          return (
            <button key={p.key} type="button" role="switch" aria-checked={on} className={`ac-row${on ? ' on' : ''}${p.sensitive ? ' sensitive' : ''}`} onClick={() => toggle(p.key, false)}>
              <span className="ac-copy"><b>{p.label}</b><small>{p.hint}</small></span><span className="ac-switch" aria-hidden="true"><i /></span>
            </button>
          );
        })}
      </div>
      <p className="ac-note"><Ico name="shield" size={14} /> Changes apply immediately and you can change or revoke them any time. Company admins always have full access.</p>
    </div>
  );
}
