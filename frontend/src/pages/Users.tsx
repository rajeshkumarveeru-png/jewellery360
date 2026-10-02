import {FormEvent, useEffect, useState} from 'react';
import {
  users, createUser, updateUser, enableUser, branches
} from '../api';
import {Role,User,Branch} from '../shared/types';
import {Table,Empty} from '../shared/ui';
import './Users.css';

const editableRoles: Role[] = ['MANAGER','CASHIER','SALESMAN','INVENTORY_MANAGER','ACCOUNTANT','VIEWER'];
const emptyForm = {username:'', email:'', phone:'', password:'', role:'CASHIER' as Role, branchId:''};

export default function UsersModule({user,onNotice}:{user:User;onNotice:(x:string)=>void}){
 const [list,setList]=useState<User[]>([]);
 const [branchList,setBranchList]=useState<Branch[]>([]);
 const [f,setF]=useState<any>({...emptyForm});
 const [search,setSearch]=useState('');
 const [loading,setLoading]=useState(false);
 const [saving,setSaving]=useState(false);
 const [editing,setEditing]=useState<number|null>(null);
 const can=user.role==='APP_ADMIN'||user.role==='COMPANY_ADMIN';

 const load=async(q=search)=>{
   setLoading(true);
   try{
     const r=await users(q);
     setList(Array.isArray(r.data)?r.data:[]);
   }catch(err:any){
     setList([]);
     onNotice(err?.response?.data?.message||'Unable to load users');
   }finally{setLoading(false);}
 };

 const loadBranches=async()=>{
   if(!can){setBranchList([]);return;}
   try{
     const r=await branches();
     const all=Array.isArray(r.data)?r.data:[];
     // COMPANY_ADMIN receives company-scoped branches from the backend. For APP_ADMIN,
     // keep every branch available and show the company name in the option label.
     setBranchList(all.filter((b:any)=>b.active!==false));
   }catch(err:any){
     setBranchList([]);
     onNotice(err?.response?.data?.message||'Unable to load branches');
   }
 };

 useEffect(()=>{
   const t=window.setTimeout(()=>void load(search),250);
   return()=>window.clearTimeout(t);
 },[search]);
 useEffect(()=>{void loadBranches();},[user.id,user.role,user.companyId]);

 const reset=()=>{
   setEditing(null);
   setF({...emptyForm});
 };

 const edit=(x:User)=>{
   setEditing(Number(x.id));
   setF({
     username:x.username||'',
     email:x.email||'',
     phone:x.phone||'',
     password:'',
     role:x.role,
     branchId:x.branchId!=null?String(x.branchId):''
   });
   window.scrollTo({top:0,behavior:'smooth'});
 };

 const save=async(e:FormEvent)=>{
   e.preventDefault();
   if(!can){onNotice('Only APP_ADMIN or COMPANY_ADMIN can manage users.');return;}
   if(!f.branchId){onNotice('Please select a branch.');return;}
   setSaving(true);
   try{
     const payload:any={
       username:String(f.username||'').trim(),
       email:String(f.email||'').trim(),
       phone:String(f.phone||'').trim(),
       role:f.role,
       branchId:Number(f.branchId)
     };
     // Password is required for create, but optional during edit so an admin can
     // change user details without resetting the password accidentally.
     if(String(f.password||'').trim()) payload.password=f.password;

     if(editing){
       await updateUser(editing,payload);
       onNotice('User updated successfully.');
     }else{
       if(!payload.password){onNotice('Temporary password is required for a new user.');return;}
       await createUser(payload);
       onNotice('User created and sent for approval.');
     }
     reset();
     await load(search);
   }catch(err:any){
     onNotice(err?.response?.data?.message||`Unable to ${editing?'update':'create'} user`);
   }finally{setSaving(false);}
 };

 return <div className="page-users moduleGrid">
   <div className="panel userFormPanel">
     <div className="panelHead">
       <div><span className="eyebrow">IDENTITY</span><h2>{editing?'Edit user':'Users'}</h2></div>
       {editing&&<button type="button" className="ghost" onClick={reset}>Cancel edit</button>}
     </div>
     <p className="usersIntro">All active users are shown here. Search by username, email, phone, role, company or branch.</p>
     {can&&<form className="formGrid" onSubmit={save}>
       <input placeholder="Username" required value={f.username||''} onChange={e=>setF({...f,username:e.target.value})}/>
       <input placeholder="Email" type="email" required value={f.email||''} onChange={e=>setF({...f,email:e.target.value})}/>
       <input placeholder="Phone" value={f.phone||''} onChange={e=>setF({...f,phone:e.target.value})}/>
       <input placeholder={editing?'New password (optional)':'Temporary password'} type="password" required={!editing} value={f.password||''} onChange={e=>setF({...f,password:e.target.value})}/>
       <select value={f.role} onChange={e=>setF({...f,role:e.target.value as Role})}>
         {editableRoles.map(r=><option key={r} value={r}>{r}</option>)}
       </select>
       <select required value={f.branchId||''} onChange={e=>setF({...f,branchId:e.target.value})}>
         <option value="">Select branch</option>
         {branchList.map((b:any)=><option key={b.id} value={b.id}>{b.name}{user.role==='APP_ADMIN'&&b.companyName?` · ${b.companyName}`:''}</option>)}
       </select>
       <div className="userFormActions">
         <button type="button" className="secondary" onClick={reset}>Clear</button>
         <button className="primary" disabled={saving}>{saving?(editing?'Updating…':'Creating…'):(editing?'Update user':'Create user')}</button>
       </div>
     </form>}
   </div>
   <div className="panel">
     <div className="panelHead usersListHead"><div><span className="eyebrow">DIRECTORY</span><h2>All users</h2></div><span className="userCount">{list.length} user{list.length===1?'':'s'}</span></div>
     <div className="userSearch"><input value={search} onChange={e=>setSearch(e.target.value)} placeholder="Search username, email, phone, role, company or branch…" aria-label="Search users"/><button className="ghost" type="button" onClick={()=>setSearch('')} disabled={!search}>Clear</button></div>
     {loading?<div className="empty"><div>◇</div><b>Loading users…</b></div>:<Table><thead><tr><th>User</th><th>Email</th><th>Phone</th><th>Role</th><th>Company</th><th>Branch</th><th>Status</th><th>Action</th></tr></thead><tbody>{list.map(x=><tr key={x.id}>
       <td><b>{x.username}</b></td><td>{x.email||'—'}</td><td>{x.phone||'—'}</td><td>{x.role}</td><td>{x.companyName||'Platform'}</td><td>{x.branchName||'—'}</td>
       <td><span className={x.enabled?'pill success':'pill'}>{x.enabled?'ACTIVE':'PENDING'}</span></td>
       <td className="userActions">
         {can&&x.role!=='APP_ADMIN'&&<button className="ghost" type="button" onClick={()=>edit(x)}>Edit</button>}
         {can&&!x.enabled&&x.role!=='APP_ADMIN'&&<button className="ghost" type="button" onClick={async()=>{try{await enableUser(x.id);await load(search);onNotice('User enabled.');}catch(err:any){onNotice(err?.response?.data?.message||'Unable to enable user')}}}>Enable</button>}
       </td>
     </tr>)}</tbody></Table>}
     {!loading&&!list.length&&<Empty text={search?'No users match your search.':'No users found.'}/>} 
   </div>
 </div>
}
