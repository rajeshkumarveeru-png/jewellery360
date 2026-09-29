import { ThemeKey, Role } from './types';

export const roleMenus: Record<string,string[]> = {
  APP_ADMIN:['Overview','Companies','Branches','Users','Approvals','Billing','Jewellery','Gold & Rates','Inventory','Purchases','Old Gold','Customers','Services','Payments','Reports','WhatsApp','Settings','Audit Logs'],
  COMPANY_ADMIN:['Overview','Branches','Users','Billing','Jewellery','Gold & Rates','Inventory','Purchases','Old Gold','Customers','Services','Payments','Reports','WhatsApp','Settings','Audit Logs'],
  MANAGER:['Overview','Billing','Jewellery','Gold & Rates','Inventory','Purchases','Old Gold','Customers','Services','Payments','Reports','WhatsApp','Audit Logs'],
  CASHIER:['Overview','Billing','Customers','Payments','Reports'],
  SALESMAN:['Overview','Billing','Customers','Services','Payments'],
  INVENTORY_MANAGER:['Overview','Jewellery','Gold & Rates','Inventory','Purchases','Old Gold','Reports'],
  ACCOUNTANT:['Overview','Customers','Payments','Reports'],
  VIEWER:['Overview','Customers','Inventory','Reports']
};

export const navigationCatalog = [
  'Overview','Companies','Branches','Users','Approvals','Billing','Jewellery','Gold & Rates','Inventory',
  'Purchases','Old Gold','Customers','Services','Payments','Reports','WhatsApp','Settings','Audit Logs'
];

export function defaultPreferredMenu(role:string): string[] {
  return [...(roleMenus[role] || ['Overview'])];
}

export const themeMap: Record<string,{bg:string;ink:string}> = {
  LUXURY_GOLD:{bg:'#f5efe4',ink:'#211c18'},
  CLASSIC_IVORY:{bg:'#fbf8f1',ink:'#2b2722'},
  PREMIUM_DARK:{bg:'#151719',ink:'#f5f0e7'},
  MODERN_LIGHT:{bg:'#eef2f5',ink:'#20252b'}
};

export const moduleConfig:Record<string,{subtypes:string[];fields:{key:string;label:string;type?:string;required?:boolean}[]}> = {
 Jewellery:{subtypes:['ITEM','DESIGN','CATEGORY','TAG','BARCODE'],fields:[
  {key:'tag',label:'Tag / SKU',required:true},{key:'barcode',label:'Barcode'},{key:'itemName',label:'Item name',required:true},
  {key:'design',label:'Design'},{key:'category',label:'Category'},{key:'purity',label:'Purity'},
  {key:'grossWeight',label:'Gross weight (g)',type:'number'},{key:'stoneWeight',label:'Stone weight (g)',type:'number'},
  {key:'netWeight',label:'Net weight (g)',type:'number'},{key:'huid',label:'HUID'},{key:'wastage',label:'Wastage %',type:'number'},
  {key:'makingCharge',label:'Making charge',type:'number'}
 ]},
 'Gold & Rates':{subtypes:['GOLD_RATE','PURITY','RATE_HISTORY'],fields:[
  {key:'rateDate',label:'Effective date',type:'date',required:true},{key:'effectiveTime',label:'Effective time',type:'time'},
  {key:'24K',label:'24K rate / g',type:'number'},{key:'22K',label:'22K rate / g',type:'number'},
  {key:'18K',label:'18K rate / g',type:'number'},{key:'14K',label:'14K rate / g',type:'number'},
  {key:'silver',label:'Silver rate / g',type:'number'},{key:'purityPercent',label:'Purity %',type:'number'}
 ]},
 Inventory:{subtypes:['STOCK','ADJUSTMENT','TRANSFER','STOCK_AUDIT'],fields:[
  {key:'tag',label:'Tag / SKU',required:true},{key:'item',label:'Item',required:true},{key:'quantity',label:'Quantity',type:'number',required:true},
  {key:'weight',label:'Weight (g)',type:'number'},{key:'location',label:'Location'},{key:'reason',label:'Reason'}
 ]},
 Purchases:{subtypes:['SUPPLIER','PURCHASE','PURCHASE_RETURN'],fields:[
  {key:'purchaseNo',label:'Purchase number',required:true},{key:'supplier',label:'Supplier',required:true},
  {key:'invoiceNo',label:'Supplier invoice'},{key:'purity',label:'Purity'},{key:'grossWeight',label:'Gross weight (g)',type:'number'},
  {key:'netWeight',label:'Net weight (g)',type:'number'},{key:'stoneWeight',label:'Stone weight (g)',type:'number'},
  {key:'purchaseRate',label:'Purchase rate',type:'number'},{key:'making',label:'Making',type:'number'},{key:'amount',label:'Amount',type:'number'}
 ]},
 'Old Gold':{subtypes:['PURCHASE','EXCHANGE','HISTORY'],fields:[
  {key:'receiptNo',label:'Receipt number',required:true},{key:'customer',label:'Customer',required:true},
  {key:'grossWeight',label:'Gross weight (g)',type:'number'},{key:'stoneWeight',label:'Stone weight (g)',type:'number'},
  {key:'netWeight',label:'Net weight (g)',type:'number'},{key:'purity',label:'Purity'},
  {key:'rate',label:'Applicable rate',type:'number'},{key:'deduction',label:'Deduction',type:'number'},{key:'amount',label:'Exchange value',type:'number'}
 ]},
 Customers:{subtypes:['CUSTOMER'],fields:[
  {key:'customerName',label:'Customer name',required:true},{key:'phone',label:'Phone',required:true},{key:'whatsapp',label:'WhatsApp'},
  {key:'email',label:'Email',type:'email'},{key:'address',label:'Address'},{key:'gstin',label:'GSTIN'},{key:'pan',label:'PAN'},
  {key:'dateOfBirth',label:'Date of birth',type:'date'},{key:'anniversary',label:'Anniversary',type:'date'}
 ]},
 Services:{subtypes:['REPAIR','CUSTOM_ORDER'],fields:[
  {key:'serviceNo',label:'Service / order no',required:true},{key:'customer',label:'Customer',required:true},
  {key:'item',label:'Jewellery item'},{key:'description',label:'Description'},{key:'receivedDate',label:'Received date',type:'date'},
  {key:'expectedDate',label:'Expected date',type:'date'},{key:'charges',label:'Charges',type:'number'},{key:'assignedTo',label:'Assigned staff'}
 ]},
 Payments:{subtypes:['RECEIPT','ADVANCE','DUE'],fields:[
  {key:'receiptNo',label:'Receipt number',required:true},{key:'customer',label:'Customer',required:true},
  {key:'invoiceNo',label:'Invoice / order'},{key:'amount',label:'Amount',type:'number',required:true},{key:'mode',label:'Payment mode'}
 ]},
 WhatsApp:{subtypes:['MESSAGE','INVOICE','RECEIPT','REMINDER','ORDER_READY','REPAIR_READY'],fields:[
  {key:'recipient',label:'Recipient',required:true},{key:'template',label:'Template'},{key:'message',label:'Message',required:true}
 ]},
 Settings:{subtypes:['BUSINESS','INVOICE','INTEGRATION'],fields:[
  {key:'key',label:'Setting key',required:true},{key:'value',label:'Setting value',required:true},{key:'description',label:'Description'}
 ]}
};


