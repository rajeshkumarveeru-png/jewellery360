import {useEffect, useState} from 'react';
import {availability} from '../api';
import type {FieldState} from './AuthFields';

type Kind = 'username' | 'email' | 'phone' | 'company';

/** Live "already registered?" checks for the sign-up and create-user forms. Pass only values that already look valid. */
export function useAvailability(q: Partial<Record<Kind, string>>) {
  const [taken, setTaken] = useState<Record<string, boolean>>({});
  const [off, setOff] = useState(false);
  const key = JSON.stringify(q);
  useEffect(() => {
    const todo: Partial<Record<Kind, string>> = {};
    (['username', 'email', 'phone', 'company'] as Kind[]).forEach(k => { const v = q[k]; if (v && taken[k + ':' + v] === undefined) todo[k] = v; });
    if (!Object.keys(todo).length) return;
    const timer = window.setTimeout(async () => {
      try {
        const r = (await availability(todo)).data || {};
        setTaken(cur => ({
          ...cur,
          ...(todo.username ? {['username:' + todo.username]: !!r.usernameTaken} : {}),
          ...(todo.email ? {['email:' + todo.email]: !!r.emailTaken} : {}),
          ...(todo.phone ? {['phone:' + todo.phone]: !!r.phoneTaken} : {}),
          ...(todo.company ? {['company:' + todo.company]: !!r.companyTaken} : {})
        }));
      } catch { setOff(true); }
    }, 450);
    return () => window.clearTimeout(timer);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [key, taken]);
  /** Combine the format check with the server answer. */
  const state = (base: FieldState, kind: Kind, value: string, noun: string, relevant = true): FieldState => {
    if (base.state !== 'ok' || !relevant || off || !value) return base;
    const t = taken[kind + ':' + value];
    if (t === undefined) return {state: 'wait', message: 'Checking availability…'};
    return t ? {state: 'bad', message: `This ${noun} is already registered.`} : {state: 'ok', message: `This ${noun} is available.`};
  };
  return {state};
}
