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
  if(!token) return <Auth mode={mode} setMode={setMode} onLogin={t=>{localStorage.setItem('j360_token',t);setToken(t)}}/>;
  if(loading||!user) return <Loading/>;
  return <Dashboard user={user} theme={theme} setTheme={v=>{setTheme(v);localStorage.setItem('j360_theme',v)}} logout={()=>{localStorage.removeItem('j360_token');localStorage.removeItem('j360_context_company');localStorage.removeItem('j360_context_branch');setToken(null)}}/>;
}
