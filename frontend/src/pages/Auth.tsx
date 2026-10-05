import {FormEvent, useState} from 'react';
import {
  api, login, registerCompany, getFriendlyApiError, adminReset, otpRequest, otpVerify, me, users, createUser, enableUser,
  approvals, approve, reject, companies, createCompany, branches, createBranch, records, createRecord,
  updateRecord, deleteRecord, dashboard, report, audit, calculateBilling, createBilling, createDomainBilling, invoicePdf,
  domainCustomers, createDomainCustomer, domainCategories, createDomainCategory, domainDesigns, createDomainDesign,
  domainProducts, createDomainProduct, domainTags, createDomainTag, domainItems, createDomainItem, domainPurities,
  domainGoldRates, createDomainGoldRate, domainStock, domainStockMovements, createDomainTransfer, createDomainStockMovement, domainSuppliers, domainPurchases, domainOldGold, domainRepairs, domainCustomOrders, domainPayments, domainAdvances, phase3Transfer, phase3CompleteTransfer, phase3Purchase, phase3OldGold, phase3RepairUpdate, phase3CustomOrderUpdate, phase3Advance, phase3Outstanding, phase3Report
} from '../api';
import {Role,User,ThemeKey,RecordItem,Company,Branch} from '../shared/types';
import {Table,Empty,Field} from '../shared/ui';
import {brandInitials,getBrandName} from '../shared/brand';
import './Auth.css';
export default function Auth({mode,setMode,onLogin}:{mode:string;setMode:(x:any)=>void;onLogin:(t:string)=>void}){
 const [f,setF]=useState<any>({method:'OTP'});
 // The company name last used in this browser is the brand of the portal (falls back to the product name).
 const brand=getBrandName();
 const [msg,setMsg]=useState('');
 const [regErrors,setRegErrors]=useState<Record<string,string>>({});

 const emailPattern=/^[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?(?:\.[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)+$/;
 const phoneDigits=(value:string)=>String(value||'').replace(/\D/g,'');

 const validateRegistration=(source:any=f)=>{
   const errors:Record<string,string>={};
   const companyName=String(source.companyName||'').trim();
   const username=String(source.username||'').trim();
   const email=String(source.email||'').trim();
   const phone=String(source.phone||'').trim();
   const password=String(source.password||'');
   const confirmPassword=String(source.confirmPassword||'');

   if(!companyName) errors.companyName='Company name is required.';
   else if(companyName.length<2) errors.companyName='Company name must contain at least 2 characters.';
   if(!username) errors.username='Username is required.';
   else if(!/^[A-Za-z0-9._-]{3,80}$/.test(username)) errors.username='Use 3-80 letters, numbers, dot, underscore or hyphen.';
   if(!email) errors.email='Email address is required.';
   else if(!emailPattern.test(email)) errors.email='Enter a valid email address.';
   const digits=phoneDigits(phone);
   if(!phone) errors.phone='WhatsApp phone number is required.';
   else if(digits.length<10 || digits.length>15) errors.phone='Enter a valid phone number with 10-15 digits.';
   if(!password) errors.password='Password is required.';
   else if(password.length<8) errors.password='Password must contain at least 8 characters.';
   if(!confirmPassword) errors.confirmPassword='Please confirm your password.';
   else if(password!==confirmPassword) errors.confirmPassword='Passwords do not match.';
   return errors;
 };

 const updateRegister=(key:string,value:string)=>{
   setF((prev:any)=>({...prev,[key]:value}));
   setRegErrors(prev=>{
     const next={...prev};
     delete next[key];
     if(key==='password' || key==='confirmPassword'){
       delete next.password;
       delete next.confirmPassword;
     }
     return next;
   });
 };

 const validateOne=(key:string)=>{
   const nextSource={...f};
   const errors=validateRegistration(nextSource);
   setRegErrors(prev=>{
     const next={...prev};
     if(errors[key]) next[key]=errors[key]; else delete next[key];
     if(key==='password' || key==='confirmPassword'){
       if(errors.password) next.password=errors.password; else delete next.password;
       if(errors.confirmPassword) next.confirmPassword=errors.confirmPassword; else delete next.confirmPassword;
     }
     return next;
   });
 };

 const applyRegistrationApiError=(error:any)=>{
   const message=getFriendlyApiError(error);
   const lower=message.toLowerCase();
   const next:Record<string,string>={};
   if(lower.includes('company name')) next.companyName=message;
   else if(lower.includes('username')) next.username=message;
   else if(lower.includes('email')) next.email=message;
   else if(lower.includes('phone')) next.phone=message;
   else if(lower.includes('password')) next.password=message;
   else if(lower.includes('confirm')) next.confirmPassword=message;
   setRegErrors(next);
   setMsg(next && Object.keys(next).length ? 'Please correct the highlighted field.' : message);
 };

 const submit=async(e:FormEvent)=>{
   e.preventDefault();
   setMsg('');
   if(mode==='register'){
     const errors=validateRegistration(f);
     setRegErrors(errors);
     if(Object.keys(errors).length){
       setMsg('Please correct the highlighted registration details.');
       return;
     }
   }
   try{
     if(mode==='login'){const r=await login(f.identifier,f.password);onLogin(r.data.token);return;}
     if(mode==='register'){
       const payload={
         companyName:String(f.companyName||'').trim(),
         username:String(f.username||'').trim(),
         email:String(f.email||'').trim().toLowerCase(),
         phone:phoneDigits(f.phone),
         password:String(f.password||''),
         confirmPassword:String(f.confirmPassword||'')
       };
       const r=await registerCompany(payload);
       setMsg(r.data.message || 'Company registration submitted successfully. An App Admin must approve the request before you can sign in.');
       setRegErrors({});
       return;
     }
     if(f.method==='OTP'){
       if(!f.challengeId){const r=await otpRequest(f.identifier);setF({...f,challengeId:r.data.challengeId});setMsg(r.data.message);}
       else {const r=await otpVerify({challengeId:f.challengeId,otp:f.otp,newPassword:f.newPassword});setMsg(r.data.message);}
     } else {const r=await adminReset(f);setMsg(r.data.message);}
   }catch(err:any){
     if(mode==='register') applyRegistrationApiError(err);
     else setMsg(getFriendlyApiError(err));
   }
 };

 return <main className="auth"><section className="brand"><div className="logo" aria-hidden="true">{brandInitials(brand)}</div><span className="eyebrow">JEWELLERY ERP PLATFORM</span><h1>{brand}</h1><p>Premium jewellery showroom management · Billing · Inventory · Customers · Operations</p><div className="brandPills"><span>Multi-company</span><span>Branch control</span><span>JWT secured</span><span>Audit ready</span></div></section>
 <form className="authCard singleColumnForm" onSubmit={submit} noValidate><span className="eyebrow dark">SECURE ACCESS</span><h2>{mode==='login'?'Sign in':mode==='register'?'Create company':'Reset password'}</h2>
 {mode==='login'&&<><Field placeholder="Username, email or phone number" onChange={v=>setF({...f,identifier:v})}/><Field type="password" placeholder="Password" onChange={v=>setF({...f,password:v})}/></>}
 {mode==='register'&&<>
   <Field placeholder="Company name" autoComplete="organization" error={regErrors.companyName} onBlur={()=>validateOne('companyName')} onChange={v=>updateRegister('companyName',v)}/>
   <Field placeholder="Username" autoComplete="username" error={regErrors.username} onBlur={()=>validateOne('username')} onChange={v=>updateRegister('username',v)}/>
   <Field placeholder="Email" type="email" autoComplete="email" error={regErrors.email} onBlur={()=>validateOne('email')} onChange={v=>updateRegister('email',v)}/>
   <Field placeholder="WhatsApp phone" type="tel" inputMode="tel" autoComplete="tel" error={regErrors.phone} onBlur={()=>validateOne('phone')} onChange={v=>updateRegister('phone',v)}/>
   <Field placeholder="Password" type="password" autoComplete="new-password" error={regErrors.password} onBlur={()=>validateOne('password')} onChange={v=>updateRegister('password',v)}/>
   <Field placeholder="Confirm password" type="password" autoComplete="new-password" error={regErrors.confirmPassword} onBlur={()=>validateOne('confirmPassword')} onChange={v=>updateRegister('confirmPassword',v)}/>
 </>}
 {mode==='reset'&&<><Field placeholder="Username, email or phone number" onChange={v=>setF({...f,identifier:v})}/><select value={f.method} onChange={e=>setF({...f,method:e.target.value})}><option>OTP</option><option>ADMIN</option></select>{f.method==='OTP'?<>{f.challengeId&&<Field placeholder="6 digit OTP" onChange={v=>setF({...f,otp:v})}/>}<Field type="password" placeholder="New password" onChange={v=>setF({...f,newPassword:v})}/></>:<><Field type="password" placeholder="New password" onChange={v=>setF({...f,password:v})}/><Field type="password" placeholder="Confirm password" onChange={v=>setF({...f,confirmPassword:v})}/></>}</>}
 <button className="primary" type="submit">{mode==='login'?'Sign in':mode==='register'?'Submit registration':'Continue'}</button>{msg&&<div className={`msg ${mode==='register' && Object.keys(regErrors).length ? 'msgError' : ''}`} role="status">{msg}</div>}<div className="links"><button type="button" onClick={()=>setMode(mode==='login'?'register':'login')}>{mode==='login'?'Create company':'Back to login'}</button>{mode==='login'&&<button type="button" onClick={()=>setMode('reset')}>Forgot password?</button>}</div>
 </form></main>
}
