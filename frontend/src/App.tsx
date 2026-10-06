import {useEffect,useState} from 'react';
import Auth from './pages/Auth';
import Dashboard from './pages/Dashboard';
import {Loading} from './shared/ui';
import {ThemeKey,User} from './shared/types';
import {me} from './api';

export default function App(){
  const [token,setToken]=useState(localStorage.getItem('j360_token'));
  const [user,setUser]=useState<User|null>(null);
  const [loading,setLoading]=useState(!!token);
  const [mode,setMode]=useState<'login'|'register'|'reset'>('login');
  const [theme,setTheme]=useState<ThemeKey>((localStorage.getItem('j360_theme') as ThemeKey)||'LUXURY_GOLD');
  useEffect(()=>{
    if(!token){setUser(null);setLoading(false);return;}
    setLoading(true);
    me().then(r=>setUser(r.data)).catch(()=>{localStorage.removeItem('j360_token');setToken(null);setUser(null);}).finally(()=>setLoading(false));
  },[token]);
  const signOut=()=>{localStorage.removeItem('j360_token');localStorage.removeItem('j360_context_company');localStorage.removeItem('j360_context_branch');setToken(null)};
  // The company admin can change a staff user's pages and powers while they are signed in: re-read them every minute and when the window
  // gets focus. A user who was deactivated is signed out.
  useEffect(()=>{
    if(!token||!user||user.role==='APP_ADMIN'||user.role==='COMPANY_ADMIN')return;
    const refresh=()=>{me().then(r=>{
      const next=r.data as User;
      if(next&&next.enabled===false){signOut();return;}
      setUser(cur=>cur&&JSON.stringify([cur.role,cur.permissions,cur.enabled,cur.branchId])===JSON.stringify([next.role,next.permissions,next.enabled,next.branchId])?cur:next);
    }).catch((e:any)=>{if(e?.response?.status===401)signOut();});};
    const timer=window.setInterval(refresh,60000);
    window.addEventListener('focus',refresh);
    return()=>{window.clearInterval(timer);window.removeEventListener('focus',refresh);};
  },[token,user?.id,user?.role]);
  if(!token) return <Auth mode={mode} setMode={setMode} onLogin={t=>{localStorage.setItem('j360_token',t);setToken(t)}}/>;
  if(loading||!user) return <Loading/>;
  return <Dashboard user={user} theme={theme} setTheme={v=>{setTheme(v);localStorage.setItem('j360_theme',v)}} logout={signOut}/>;
}
