import axios from 'axios';

export const api = axios.create({ baseURL: '/api' });

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
export const enableUser=(id:number)=>api.post(`/users/${id}/enable`);

export const approvals=()=>api.get('/approvals/pending');
export const approve=(id:number)=>api.post(`/approvals/${id}/approve`);
export const reject=(id:number)=>api.post(`/approvals/${id}/reject`);

export const companies=()=>api.get('/companies');
export const createCompany=(x:any)=>api.post('/companies',x);
export const branches=()=>api.get('/branches');
export const createBranch=(x:any)=>api.post('/branches',x);

export const records=(module:string)=>api.get('/records',{params:{module}});
export const createRecord=(x:any)=>api.post('/records',x);
export const updateRecord=(id:number,x:any)=>api.put(`/records/${id}`,x);
export const deleteRecord=(id:number)=>api.delete(`/records/${id}`);

export const dashboard=()=>api.get('/dashboard');
export const report=(type:string,from?:string,to?:string)=>api.get('/reports',{params:{type,from,to}});
export const audit=()=>api.get('/audit');

export const calculateBilling=(x:any)=>api.post('/billing/calculate',x);
export const createBilling=(x:any)=>api.post('/billing',x);
export const createDomainBilling=(x:any)=>api.post('/phase3/sales',x);
export const cancelDomainBilling=(id:number)=>api.post(`/billing/domain/${id}/cancel`);
export const invoicePdf=(id:number)=>api.get(`/invoices/${id}/pdf`,{responseType:'blob'});

// Normalized jewellery-domain APIs. These are the target APIs for the module UI migration;
// legacy /records endpoints remain temporarily for compatibility with the existing screens.
export const domainCustomers=()=>api.get('/domain/customers');
export const createDomainCustomer=(x:any)=>api.post('/domain/customers',x);
export const domainCategories=()=>api.get('/domain/jewellery/categories');
export const createDomainCategory=(x:any)=>api.post('/domain/jewellery/categories',x);
export const domainDesigns=()=>api.get('/domain/jewellery/designs');
export const createDomainDesign=(x:any)=>api.post('/domain/jewellery/designs',x);
export const domainProducts=()=>api.get('/domain/jewellery/products');
export const createDomainProduct=(x:any)=>api.post('/domain/jewellery/products',x);
export const domainTags=()=>api.get('/domain/jewellery/tags');
export const createDomainTag=(x:any)=>api.post('/domain/jewellery/tags',x);
export const domainItems=()=>api.get('/domain/jewellery/items');
export const createDomainItem=(x:any)=>api.post('/domain/jewellery/items',x);
export const domainPurities=()=>api.get('/domain/purities');
export const createDomainPurity=(x:any)=>api.post('/domain/purities',x);
export const domainGoldRates=()=>api.get('/domain/gold-rates');
export const createDomainGoldRate=(x:any)=>api.post('/domain/gold-rates',x);
export const domainSuppliers=()=>api.get('/domain/suppliers');
export const createDomainSupplier=(x:any)=>api.post('/domain/suppliers',x);
export const domainPurchases=()=>api.get('/domain/purchases');
export const createDomainPurchase=(x:any)=>api.post('/domain/purchases',x);
export const domainStock=()=>api.get('/domain/stock');
export const domainStockMovements=()=>api.get('/domain/stock-movements');
export const createDomainStockMovement=(x:any)=>api.post('/domain/stock-movements',x);
export const domainTransfers=()=>api.get('/domain/transfers');
export const createDomainTransfer=(x:any)=>api.post('/domain/transfers',x);
export const domainSales=()=>api.get('/domain/sales');
export const createDomainSale=(x:any)=>api.post('/domain/sales',x);
export const domainPayments=()=>api.get('/domain/payments');
export const createDomainPayment=(x:any)=>api.post('/domain/payments',x);
export const domainOldGold=()=>api.get('/domain/old-gold');
export const createDomainOldGold=(x:any)=>api.post('/domain/old-gold',x);
export const domainRepairs=()=>api.get('/domain/repairs');
export const createDomainRepair=(x:any)=>api.post('/domain/repairs',x);
export const domainCustomOrders=()=>api.get('/domain/custom-orders');
export const createDomainCustomOrder=(x:any)=>api.post('/domain/custom-orders',x);
export const domainAdvances=()=>api.get('/domain/advances');
export const createDomainAdvance=(x:any)=>api.post('/domain/advances',x);
export const domainWhatsappLogs=()=>api.get('/domain/whatsapp-logs');
export const domainNotifications=()=>api.get('/domain/notifications');

// Phase 3 transactional workflows. These endpoints execute normalized domain transactions,
// including stock movements and audit events; they do not use BusinessRecord.
export const phase3Sale=(x:any)=>api.post('/phase3/sales',x);
export const phase3SaleReturn=(saleId:number,x:any)=>api.post(`/phase3/sales/${saleId}/returns`,x);
export const phase3Transfer=(x:any)=>api.post('/phase3/transfers',x);
export const phase3CompleteTransfer=(id:number)=>api.post(`/phase3/transfers/${id}/complete`);
export const phase3Purchase=(x:any)=>api.post('/phase3/purchases',x);
export const phase3PurchaseReturn=(id:number,x:any)=>api.post(`/phase3/purchases/${id}/returns`,x);
export const phase3OldGold=(x:any)=>api.post('/phase3/old-gold',x);
export const phase3RepairUpdate=(id:number,x:any)=>api.patch(`/phase3/repairs/${id}`,x);
export const phase3CustomOrderUpdate=(id:number,x:any)=>api.patch(`/phase3/custom-orders/${id}`,x);
export const phase3Advance=(x:any)=>api.post('/phase3/advances',x);
export const phase3Outstanding=(customerId:number)=>api.get(`/phase3/customers/${customerId}/outstanding`);
export const phase3Report=(type:string)=>api.get(`/phase3/reports/${type}`);


// Phase 4 normalized-domain helpers
export const phase4Api = {
  barcodeScan: (value: string) => api.get(`/barcode/scan/${encodeURIComponent(value)}`),
  salePdf: (id: number) => api.get(`/invoices/sales/${id}/pdf`, { responseType: 'blob' }),
  returnPdf: (id: number) => api.get(`/invoices/returns/${id}/pdf`, { responseType: 'blob' }),
  sendInvoiceWhatsApp: (saleId: number) => api.post(`/whatsapp/sales/${saleId}`),
  report: (type: string) => api.get(`/phase3/reports/${type}`),
};

export const getWhatsAppSettings=(companyId?:number|null)=>api.get('/properties/whatsapp',{params:companyId?{companyId}:undefined});
export const saveWhatsAppSettings=(x:any,companyId?:number|null)=>api.put('/properties/whatsapp',x,{params:companyId?{companyId}:undefined});
