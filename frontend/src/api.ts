import axios from 'axios';

export const api = axios.create({ baseURL: import.meta.env.VITE_API_BASE_URL || '/api' });

api.interceptors.request.use(config => {
  const token = localStorage.getItem('j360_token');
  if (token) config.headers.Authorization = `Bearer ${token}`;
  const companyId = localStorage.getItem('j360_context_company');
  const branchId = localStorage.getItem('j360_context_branch');
  if (companyId) config.headers['X-Company-Id'] = companyId;
  if (branchId) config.headers['X-Branch-Id'] = branchId;
  return config;
});


/**
 * Convert an Axios/API failure into a useful, user-facing explanation.
 * Prefer the backend's actual message; only fall back to status-specific
 * guidance when the server did not provide a safe message.
 */
export const getFriendlyApiError = (error: any, fallback = 'The request could not be completed.') => {
  const status = error?.response?.status;
  const data = error?.response?.data;
  const endpoint = String(error?.config?.url || '');

  const direct = typeof data === 'string'
    ? data
    : data?.message || data?.error || data?.detail;

  if (typeof direct === 'string' && direct.trim() && !/^request failed with status code/i.test(direct.trim())) {
    return direct.trim();
  }

  const fieldErrors = data?.errors || data?.violations;
  if (Array.isArray(fieldErrors) && fieldErrors.length) {
    const details = fieldErrors.map((x: any) => {
      if (typeof x === 'string') return x;
      return [x?.field || x?.property, x?.message || x?.defaultMessage].filter(Boolean).join(': ');
    }).filter(Boolean);
    if (details.length) return details.join(' • ');
  }

  if (endpoint.includes('/auth/register')) {
    switch (status) {
      case 400: return 'Registration was rejected because one or more required details are invalid or missing.';
      case 401: return 'Registration was not authorized. Please check the application authentication configuration.';
      case 403: return 'Registration is currently not permitted. Please contact the platform administrator.';
      case 404: return 'Registration endpoint was not found. Restart the current Jewellery360 backend and make sure it is running on port 8080.';
      case 409: return 'Registration could not be completed because one of the supplied company, email, phone, or username details conflicts with an existing record.';
      case 500: return 'The server encountered an error while creating the company, branch, or administrator account.';
      case 503: return 'The registration service is temporarily unavailable.';
    }
  }

  switch (status) {
    case 400: return 'The server rejected the submitted information. Please review the form.';
    case 401: return 'Authentication failed. Please sign in again.';
    case 403: return 'You do not have permission to perform this action.';
    case 404: return 'The requested API endpoint was not found.';
    case 409: return 'The submitted information conflicts with an existing record.';
    case 500: return 'The server encountered an unexpected error. Check the backend log for the root cause.';
    case 502:
    case 503:
    case 504: return 'The backend service is currently unavailable.';
    default:
      if (error?.request && !error?.response) return 'Could not connect to the backend. Make sure the Spring Boot server is running.';
      return fallback;
  }
};

export const login=(identifier:string,password:string)=>api.post('/auth/login',{identifier,password});
export const registerCompany=(x:{companyName:string;username:string;email:string;phone:string;password:string;confirmPassword:string})=>api.post('/auth/register',x);
export const adminReset=(x:any)=>api.post('/auth/password-reset/admin',x);
export const otpRequest=(identifier:string)=>api.post('/auth/password-reset/otp/request',{identifier});
export const otpVerify=(x:any)=>api.post('/auth/password-reset/otp/verify',x);
export const me=()=>api.get('/users/me');

export const users=(search='')=>api.get('/users',{params: search.trim() ? {search:search.trim()} : undefined});
export const createUser=(x:any)=>api.post('/users',x);
export const updateUser=(id:number,x:any)=>api.put(`/users/${id}`,x);
export const enableUser=(id:number)=>api.post(`/users/${id}/enable`);

export const approvals=()=>api.get('/approvals/pending');
export const approve=(id:number,note?:string)=>api.post(`/approvals/${id}/approve`,note?{note}:undefined);
export const reject=(id:number,note?:string)=>api.post(`/approvals/${id}/reject`,note?{note}:undefined);

export const companies=()=>api.get('/companies');
export const createCompany=(x:any)=>api.post('/companies',x);
export const branches=()=>api.get('/branches');
export const createBranch=(x:any)=>api.post('/branches',x);

export const audit=(params?:any)=>api.get('/audit',{params});

/* ============================================================
   Jewellery workflows (consolidated backend). The server scopes every call to the signed-in user's company and branch
   (X-Company-Id / X-Branch-Id are only honoured for administrators).
   ============================================================ */
const clean=(p?:Record<string,any>)=>p?Object.fromEntries(Object.entries(p).filter(([,v])=>v!==undefined&&v!==null&&v!=='')):undefined;

// owner dashboard, global search, reports
export const dashboard=()=>api.get('/dashboard');
export const globalSearch=(q:string)=>api.get('/search',{params:{q}});
export const reportCatalogue=()=>api.get('/reports');
export const runReport=(key:string,from?:string,to?:string)=>api.get(`/reports/${key}`,{params:clean({from,to})});

// masters (categories, designs, products, purities, suppliers, karigars)
export type MasterKind='categories'|'designs'|'products'|'purities'|'suppliers'|'karigars';
export const masters=()=>api.get('/masters');
export const saveMaster=(kind:MasterKind,x:any,id?:number)=>id?api.put(`/masters/${kind}/${id}`,x):api.post(`/masters/${kind}`,x);

// stock (tagged pieces)
export const stockItems=(p?:{status?:string;q?:string;allBranches?:boolean;limit?:number})=>api.get('/stock/items',{params:clean(p)});
export const stockItem=(id:number)=>api.get(`/stock/items/${id}`);
export const stockTimeline=(id:number)=>api.get(`/stock/items/${id}/timeline`);
export const scanStock=(code:string)=>api.get('/stock/scan',{params:{code}});
export const saveStockItem=(x:any,id?:number)=>id?api.put(`/stock/items/${id}`,x):api.post('/stock/items',x);
export const removeStockItem=(id:number,reason:string)=>api.post(`/stock/items/${id}/remove`,{reason});
export const restoreStockItem=(id:number)=>api.post(`/stock/items/${id}/restore`);
export const stockTransfers=()=>api.get('/stock/transfers');
export const transferStock=(x:{toBranchId:number;itemIds:number[];notes?:string})=>api.post('/stock/transfers',x);
export const nextTag=()=>api.get('/stock/next-tag');
export const bulkJewelleryImportPreview=(rows:any[])=>api.post('/stock/import/preview',rows);
export const bulkJewelleryImportCommit=(rows:any[])=>api.post('/stock/import/commit',rows);

// gold rates (the shop's own selling rates; the market feed is only a reference)
export const goldRateBoard=()=>api.get('/gold-rates');
export const saveGoldRates=(x:{date?:string;rates:{purityId:number;ratePerGram:number;source?:string}[]})=>api.post('/gold-rates',x);
export const goldRateHistory=(days=30)=>api.get('/gold-rates/history',{params:{days}});
export const goldRateMarket=()=>api.get('/gold-rates/market');
export const goldRateImpact=()=>api.get('/gold-rates/impact');

// customers
export const customers=(q?:string,limit?:number)=>api.get('/customers',{params:clean({q,limit})});
export const customerProfile=(id:number)=>api.get(`/customers/${id}`);
export const saveCustomer=(x:any,id?:number)=>id?api.put(`/customers/${id}`,x):api.post('/customers',x);
export const removeCustomer=(id:number)=>api.delete(`/customers/${id}`);
export const collectFromCustomer=(id:number,x:{amount:number;mode:string;reference?:string;excessToAdvance?:boolean})=>api.post(`/customers/${id}/collect`,x);

// bills
export const sales=(p?:{from?:string;to?:string;status?:string;q?:string;limit?:number})=>api.get('/sales',{params:clean(p)});
export const sale=(id:number)=>api.get(`/sales/${id}`);
export const previewSale=(x:any)=>api.post('/sales/preview',x);
export const createSale=(x:any)=>api.post('/sales',x);
export const nextInvoiceNo=(date?:string)=>api.get('/sales/invoice-number',{params:clean({date})});
export const checkInvoiceNo=(no:string)=>api.get('/sales/invoice-number/check',{params:{no}});
export const deleteDraftSale=(id:number)=>api.delete(`/sales/${id}`);
export const paySale=(id:number,payments:any[])=>api.post(`/sales/${id}/payments`,{payments});
export const cancelSale=(id:number,x:{reason:string;refundMode?:string})=>api.post(`/sales/${id}/cancel`,x);
export const returnSale=(id:number,x:{saleItemIds:number[];reason?:string;refundMode?:string})=>api.post(`/sales/${id}/returns`,x);

// old gold
export const oldGoldList=(p?:{from?:string;to?:string;openOnly?:boolean})=>api.get('/old-gold',{params:clean(p)});
export const oldGoldDetail=(id:number)=>api.get(`/old-gold/${id}`);
export const previewOldGold=(items:any[])=>api.post('/old-gold/preview',{items});
export const createOldGold=(x:any)=>api.post('/old-gold',x);
export const settleOldGold=(id:number,mode:string)=>api.post(`/old-gold/${id}/settle`,{mode});

// purchases and suppliers
export const purchases=(p?:{from?:string;to?:string})=>api.get('/purchases',{params:clean(p)});
export const purchase=(id:number)=>api.get(`/purchases/${id}`);
export const createPurchase=(x:any)=>api.post('/purchases',x);
export const payPurchase=(id:number,x:{mode:string;amount:number;reference?:string})=>api.post(`/purchases/${id}/payments`,x);
export const returnPurchase=(id:number,x:{purchaseItemIds:number[];reason?:string})=>api.post(`/purchases/${id}/returns`,x);
export const supplierLedger=(id:number)=>api.get(`/suppliers/${id}/ledger`);

// orders, repairs, karigar job work
export const orders=(status?:string)=>api.get('/orders',{params:clean({status})});
export const order=(id:number)=>api.get(`/orders/${id}`);
export const createOrder=(x:any)=>api.post('/orders',x);
export const updateOrder=(id:number,x:any)=>api.put(`/orders/${id}`,x);
export const orderAdvance=(id:number,x:{amount:number;mode:string;reference?:string})=>api.post(`/orders/${id}/advances`,x);
export const repairs=(status?:string)=>api.get('/repairs',{params:clean({status})});
export const repair=(id:number)=>api.get(`/repairs/${id}`);
export const createRepair=(x:any)=>api.post('/repairs',x);
export const updateRepair=(id:number,x:any)=>api.put(`/repairs/${id}`,x);
export const karigarSummary=()=>api.get('/karigars/summary');
export const karigar=(id:number)=>api.get(`/karigars/${id}`);
export const payKarigar=(id:number,x:{amount:number;mode:string;reference?:string;notes?:string})=>api.post(`/karigars/${id}/payments`,x);
export const jobs=(p?:{karigarId?:number;status?:string})=>api.get('/jobs',{params:clean(p)});
export const job=(id:number)=>api.get(`/jobs/${id}`);
export const createJob=(x:any)=>api.post('/jobs',x);
export const jobMetal=(id:number,x:{type:'ISSUE'|'RECEIPT';weight:number;notes?:string})=>api.post(`/jobs/${id}/metal`,x);
export const closeJob=(id:number,x:{acceptShortfall?:boolean;labourCharge?:number})=>api.post(`/jobs/${id}/close`,x);

// money: payments register, advances, expenses, day close
export const moneyPayments=(p?:{from?:string;to?:string;type?:string;mode?:string})=>api.get('/money/payments',{params:clean(p)});
export const advances=(openOnly=true)=>api.get('/money/advances',{params:{openOnly}});
export const createAdvance=(x:{customerId:number;amount:number;mode:string;reference?:string;notes?:string})=>api.post('/money/advances',x);
export const refundAdvance=(id:number,x:{amount:number;mode:string;reference?:string})=>api.post(`/money/advances/${id}/refund`,x);
export const expenses=(p?:{from?:string;to?:string})=>api.get('/money/expenses',{params:clean(p)});
export const createExpense=(x:any)=>api.post('/money/expenses',x);
export const deleteExpense=(id:number)=>api.delete(`/money/expenses/${id}`);
export const dayClose=(date?:string)=>api.get('/money/day-close',{params:clean({date})});
export const closeDay=(x:{date?:string;actualCash:number;openingCash?:number;notes?:string})=>api.post('/money/day-close',x);
export const dayCloseHistory=()=>api.get('/money/day-close/history');

// backups (platform administrator only)
export const backups=()=>api.get('/backups');
export const backupNow=()=>api.post('/backups');
export const restoreBackup=(id:number,confirm:string)=>api.post(`/backups/${id}/restore`,{confirm});
export const backupSettings=(x:{directory:string;pgDump?:string;pgRestore?:string})=>api.put('/backups/settings',x);
export const backupFolders=(path?:string)=>api.get('/backups/folders',{params:path?{path}:undefined});
export const downloadBackup=(id:number)=>api.get(`/backups/${id}/download`,{responseType:'blob'});

// invoices and WhatsApp
export const salePdf=(id:number,template?:string,format?:string)=>api.get(`/invoices/sales/${id}/pdf`,{responseType:'blob',params:clean({template,format})});
export const returnPdf=(id:number)=>api.get(`/invoices/returns/${id}/pdf`,{responseType:'blob'});
// sample invoice in a given design (A4), used by the design picker in Settings
export const invoicePreview=(template:string)=>api.get('/invoices/preview',{responseType:'blob',params:{template}});
export const sendInvoiceWhatsApp=(saleId:number)=>api.post(`/whatsapp/sales/${saleId}`);
export const whatsappLogs=()=>api.get('/whatsapp/logs');

/** Opens a PDF response in a new tab; downloads it when pop-ups are blocked. */
export const openPdf=(data:BlobPart,name:string)=>{
  const url=URL.createObjectURL(new Blob([data],{type:'application/pdf'}));
  const w=window.open(url,'_blank');
  if(!w){const a=document.createElement('a');a.href=url;a.download=name;a.click();}
  window.setTimeout(()=>URL.revokeObjectURL(url),60000);
};

// company menu layout: order, groups, labels, hidden pages and roles per page (Settings → Menu)
export const getMenuLayout=(companyId?:number|null)=>api.get('/properties/menu',{params:companyId?{companyId}:undefined});
export const saveMenuLayout=(items:any[],companyId?:number|null)=>api.put('/properties/menu',{items},{params:companyId?{companyId}:undefined});
// the signed-in user's own dashboard arrangement (order + hidden widgets) in this company
export const getDashboardLayout=(companyId?:number|null)=>api.get('/properties/dashboard',{params:companyId?{companyId}:undefined});
export const saveDashboardLayout=(items:{id:string;hidden:boolean}[],companyId?:number|null)=>api.put('/properties/dashboard',{items},{params:companyId?{companyId}:undefined});
export const resetDashboardLayout=(companyId?:number|null)=>api.delete('/properties/dashboard',{params:companyId?{companyId}:undefined});
export const resetMenuLayout=(companyId?:number|null)=>api.delete('/properties/menu',{params:companyId?{companyId}:undefined});
export const getWhatsAppSettings=(companyId?:number|null)=>api.get('/properties/whatsapp',{params:companyId?{companyId}:undefined});
export const saveWhatsAppSettings=(x:any,companyId?:number|null)=>api.put('/properties/whatsapp',x,{params:companyId?{companyId}:undefined});
export const getTaxSettings=(companyId?:number|null)=>api.get('/properties/tax',{params:companyId?{companyId}:undefined});
export const saveTaxSettings=(x:any,companyId?:number|null)=>api.put('/properties/tax',x,{params:companyId?{companyId}:undefined});


// Executive dashboard analytics: today's summary, From/To totals and the daily / weekly / monthly trend series.
export type Granularity='DAY'|'WEEK'|'MONTH';
export const salesAnalytics=(params:{from?:string;to?:string;granularity?:Granularity})=>api.get('/analytics/sales',{params});

// Business profile (phone, corporate e-mail, GSTIN, print layout, SKU prefix) stored in the company property table.
export const getBusinessSettings=(companyId?:number|null)=>api.get('/properties/business',{params:companyId?{companyId}:undefined});
export const saveBusinessSettings=(x:{companyName?:string;phone:string;email:string;gstin:string;printFormat:string;skuPrefix:string;invoiceTemplate?:string},companyId?:number|null)=>api.put('/properties/business',x,{params:companyId?{companyId}:undefined});


/* ============================================================
   Sign-in helpers, staff access and requests for new users
   ============================================================ */
export const availability=(params:{username?:string;email?:string;phone?:string;company?:string})=>api.get('/auth/availability',{params});
export const validateReset=(identifier:string)=>api.post('/auth/password-reset/validate',{identifier});
export const setUserPermissions=(id:number,permissions:string[])=>api.put(`/users/${id}/permissions`,{permissions});
export const disableUser=(id:number)=>api.post(`/users/${id}/disable`);
export const userSlotRequests=()=>api.get('/users/slot-requests');
export const createSlotRequest=(x:{requestedName:string;note?:string})=>api.post('/users/slot-requests',x);
export const cancelSlotRequest=(id:number)=>api.delete(`/users/slot-requests/${id}`);
export const userRequestHistory=()=>api.get('/approvals/user-requests');
