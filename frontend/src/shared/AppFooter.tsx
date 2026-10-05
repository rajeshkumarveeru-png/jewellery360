import {useEffect, useState} from 'react';
import {User} from './types';

/* Fixed footer: brand, live connection state, this session's timer and the quick-jump shortcut. */
const SESSION_KEY = 'j360_session_start';

export default function AppFooter({brand, user, onPalette}: {brand: string; user: User; onPalette: () => void}) {
  const [online, setOnline] = useState(typeof navigator === 'undefined' ? true : navigator.onLine);
  const [start] = useState(() => {
    try {
      const saved = Number(sessionStorage.getItem(SESSION_KEY));
      if (saved) return saved;
      const now = Date.now();
      sessionStorage.setItem(SESSION_KEY, String(now));
      return now;
    } catch { return Date.now(); }
  });
  const [now, setNow] = useState(Date.now());
  useEffect(() => {
    const up = () => setOnline(true);
    const down = () => setOnline(false);
    window.addEventListener('online', up);
    window.addEventListener('offline', down);
    const id = window.setInterval(() => setNow(Date.now()), 30000);
    return () => { window.removeEventListener('online', up); window.removeEventListener('offline', down); window.clearInterval(id); };
  }, []);
  const mins = Math.max(0, Math.floor((now - start) / 60000));
  const session = mins < 60 ? `${mins} min` : `${Math.floor(mins / 60)} h ${mins % 60} min`;
  return (
    <footer className="appFooter" role="contentinfo">
      <div className="footBrand"><i aria-hidden="true">◆</i><b>{brand}</b><span>Jewellery360 · © {new Date().getFullYear()}</span></div>
      <div className="footStatus">
        <span className={`footPill ${online ? 'ok' : 'bad'}`}><i aria-hidden="true" />{online ? 'Online' : 'Offline — changes cannot be saved'}</span>
        <span className="footPill">{user.username} · {user.role.replaceAll('_', ' ')}</span>
        <span className="footPill">Session {session}</span>
      </div>
      <button type="button" className="footKbd" onClick={onPalette} title="Jump to any page or action"><kbd>Ctrl</kbd><kbd>K</kbd> Quick jump</button>
    </footer>
  );
}
