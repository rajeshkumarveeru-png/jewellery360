export type Role='APP_ADMIN'|'COMPANY_ADMIN'|'MANAGER'|'CASHIER'|'SALESMAN'|'INVENTORY_MANAGER'|'ACCOUNTANT'|'VIEWER';
export type User={id:number;username:string;email:string;phone?:string|null;role:Role;enabled:boolean;companyId?:number|null;companyName?:string|null;branchId?:number|null;branchName?:string|null;permissions?:string[];createdAt?:string|null};
export type ThemeKey='LUXURY_GOLD'|'CLASSIC_IVORY'|'PREMIUM_DARK'|'MODERN_LIGHT';
export type RecordItem={id:number;module:string;subtype?:string;title:string;status:string;amount:number;quantity:number;date:string;owner?:string;notes?:string;companyId:number;companyName:string;branchId:number;branchName:string;payload?:Record<string,any>};
export type Company={id:number;name:string;code:string;gstin?:string;phone?:string;email?:string;address?:string;active:boolean};
export type Branch={id:number;companyId:number;companyName:string;name:string;code:string;phone?:string;email?:string;gstin?:string;website?:string;invoiceTitle?:string;invoiceSubtitle?:string;invoiceTerms?:string;invoiceFooter?:string;address?:string;active:boolean};
