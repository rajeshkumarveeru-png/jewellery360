import type {CSSProperties} from 'react';

/* Small line-icon set (24x24, round caps) used by the sign-in screens, dialogs, Users and Approvals pages.
   No icon library is needed. */
const PATHS: Record<string, string> = {
  eye: 'M2 12s3.6-7 10-7 10 7 10 7-3.6 7-10 7S2 12 2 12z M12 9a3 3 0 100 6 3 3 0 000-6z',
  'eye-off': 'M3 3l18 18 M10.6 5.1A10.9 10.9 0 0112 5c6.4 0 10 7 10 7a17.6 17.6 0 01-3.2 4.2 M6.6 6.6A17.5 17.5 0 002 12s3.6 7 10 7a9.7 9.7 0 004.4-1 M9.9 9.9a3 3 0 004.2 4.2',
  lock: 'M5 11h14v10H5z M8 11V7a4 4 0 018 0v4',
  'check-circle': 'M21 12a9 9 0 11-18 0 9 9 0 0118 0z M8 12.5l2.7 2.7L16 9.5',
  'alert-circle': 'M21 12a9 9 0 11-18 0 9 9 0 0118 0z M12 8v5 M12 16.5v.5',
  'x-circle': 'M21 12a9 9 0 11-18 0 9 9 0 0118 0z M9 9l6 6 M15 9l-6 6',
  info: 'M21 12a9 9 0 11-18 0 9 9 0 0118 0z M12 11v5 M12 8v.5',
  triangle: 'M12 3l10 18H2z M12 10v5 M12 18v.5',
  help: 'M21 12a9 9 0 11-18 0 9 9 0 0118 0z M9.5 9.5a2.5 2.5 0 114 2c-.9.6-1.5 1-1.5 2.2 M12 17v.5',
  x: 'M6 6l12 12 M18 6L6 18',
  check: 'M5 12.5l4.5 4.5L19 7',
  plus: 'M12 5v14 M5 12h14',
  loader: 'M12 3a9 9 0 019 9',
  shield: 'M12 3l8 3v6c0 4.5-3.2 7.9-8 9-4.8-1.1-8-4.5-8-9V6z M9 12l2 2 4-4',
  users: 'M9 11a3.5 3.5 0 100-7 3.5 3.5 0 000 7z M2.5 20a6.5 6.5 0 0113 0 M16 4.5a3.3 3.3 0 010 6.3 M18 14.5a6 6 0 013.5 5.5',
  'user-plus': 'M9 11a4 4 0 100-8 4 4 0 000 8z M2 21a7 7 0 0114 0 M19 8v6 M16 11h6',
  'user-check': 'M9 11a4 4 0 100-8 4 4 0 000 8z M2 21a7 7 0 0114 0 M16 11l2 2 4-4',
  'user-x': 'M9 11a4 4 0 100-8 4 4 0 000 8z M2 21a7 7 0 0114 0 M17 8l5 5 M22 8l-5 5',
  key: 'M21 2l-2 2m-7.6 7.6a5.5 5.5 0 11-7.8 7.8 5.5 5.5 0 017.8-7.8zm0 0L15.5 7.5m0 0l3 3L22 7l-3-3m-3.5 3.5L19 4',
  search: 'M11 4a7 7 0 100 14 7 7 0 000-14z M20 20l-3.5-3.5',
  edit: 'M12 20h9 M16.5 3.5a2.1 2.1 0 013 3L7 19l-4 1 1-4z',
  copy: 'M9 9h11v11H9z M5 15H4V4h11v1',
  clock: 'M21 12a9 9 0 11-18 0 9 9 0 0118 0z M12 7v5l3 2',
  ban: 'M21 12a9 9 0 11-18 0 9 9 0 0118 0z M5.6 5.6l12.8 12.8',
  refresh: 'M21 12a9 9 0 11-3-6.7 M21 4v5h-5',
  phone: 'M5 4h4l2 5-2.5 1.5a11 11 0 005 5L15 13l5 2v4a2 2 0 01-2 2A16 16 0 013 6a2 2 0 012-2z',
  mail: 'M3 5h18v14H3z M3 7l9 6 9-6',
  list: 'M8 6h13 M8 12h13 M8 18h13 M3 6h.01 M3 12h.01 M3 18h.01',
  grid: 'M4 4h7v7H4z M13 4h7v7h-7z M4 13h7v7H4z M13 13h7v7h-7z',
  sparkles: 'M12 3l1.8 4.7L18.5 9.5 13.8 11.3 12 16l-1.8-4.7L5.5 9.5l4.7-1.8z M19 15l.8 2.2L22 18l-2.2.8L19 21l-.8-2.2L16 18l2.2-.8z',
  whatsapp: 'M21 12a9 9 0 01-13.3 7.9L3 21l1.2-4.5A9 9 0 1121 12z M9 9.5c.3 2.6 2.4 4.7 5 5l1.2-1.4-1.9-1-.9.6a3 3 0 01-1.6-1.6l.6-.9-1-1.9z',
  building: 'M4 20V6l8-3 8 3v14 M9 20v-5h6v5 M8 9h2 M14 9h2 M8 12h2 M14 12h2'
};

export type IconName = keyof typeof PATHS | string;

export function Ico({name, size = 18, className, style}: {name: IconName; size?: number; className?: string; style?: CSSProperties}) {
  return (
    <svg className={`ico${className ? ' ' + className : ''}`} style={style} viewBox="0 0 24 24" width={size} height={size} fill="none" stroke="currentColor"
         strokeWidth="1.9" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d={PATHS[name] || PATHS.info} />
    </svg>
  );
}
