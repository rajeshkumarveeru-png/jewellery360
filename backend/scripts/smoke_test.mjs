// Node 18+ end-to-end smoke test (billing, stock, payments, permissions, tenant isolation, analytics). Needs no Python.
// Run ONLY against an EMPTY test database:  node backend/scripts/smoke_test.mjs http://localhost:8080 admin <admin-password>
const BASE = (process.argv[2] || 'http://localhost:8080').replace(/\/$/, '');
const ADMIN_USER = process.argv[3] || 'admin';
const ADMIN_PASS = process.argv[4] || 'admin123';
let passed = 0; const failed = [];
const money = x => Number(x).toFixed(2);

async function call(method, path, body = null, token = null, headers = {}, expect = 200) {
  const h = {'Content-Type': 'application/json'};
  if (token) h.Authorization = 'Bearer ' + token;
  for (const [k, v] of Object.entries(headers || {})) h[k] = String(v);
  const r = await fetch(BASE + path, {method, headers: h, body: body === null ? undefined : JSON.stringify(body)});
  const raw = await r.text();
  let out; try { out = raw && (raw[0] === '{' || raw[0] === '[') ? JSON.parse(raw) : raw; } catch { out = raw; }
  if (expect !== null && r.status !== expect) throw new Error(`${method} ${path} -> ${r.status} (expected ${expect}): ${typeof out === 'string' ? out.slice(0, 300) : JSON.stringify(out).slice(0, 300)}`);
  return out;
}
function check(name, cond, detail = '') { if (cond) { passed++; console.log('  ok  ', name); } else { failed.push(name); console.log('  FAIL', name, JSON.stringify(detail)?.slice(0, 300)); } }

async function main() {
  console.log('Login and setup');
  const admin = (await call('POST', '/api/auth/login', {identifier: ADMIN_USER, password: ADMIN_PASS})).token;
  const company = await call('POST', '/api/companies', {name: 'Smoke Test Jewellers', phone: '9000000001'}, admin);
  const cid = company.id;
  const b1 = (await call('POST', '/api/branches', {companyId: cid, name: 'Main Bazaar', code: 'MAIN'}, admin)).id;
  const b2 = (await call('POST', '/api/branches', {companyId: cid, name: 'Mall Road', code: 'MALL'}, admin)).id;
  const H = {'X-Company-Id': cid, 'X-Branch-Id': b1};
  const owner = (m, p, b = null, expect = 200) => call(m, p, b, admin, H, expect);

  const masters = await owner('GET', '/api/masters');
  const pur = Object.fromEntries(masters.purities.map(p => [p.name, p]));
  check('company starts with purities and categories', Object.keys(pur).join('').includes('22K') && masters.categories.length >= 5, Object.keys(pur));
  const p22 = Object.entries(pur).find(([n]) => n.startsWith('22K'))[1];
  const cat = masters.categories[0];

  console.log('Gold rates');
  await owner('POST', '/api/gold-rates', {rates: [{purityId: p22.id, ratePerGram: 6500}]});
  const board = await owner('GET', '/api/gold-rates');
  check('rate saved for 22K', board.rates.some(r => Number(r.ratePerGram) === 6500), board);

  console.log('Stock');
  const items = [];
  for (let i = 0; i < 4; i++) items.push(await owner('POST', '/api/stock/items', {productName: `Smoke Chain ${i}`, categoryId: cat.id, purityId: p22.id, grossWeight: '10.000', stoneWeight: '0',
    wastagePercent: '8', makingType: 'PER_GRAM', makingValue: '400', costPrice: '60000', huid: `AB${i}CD${i}`}));
  check('4 pieces in stock with tag numbers', items.every(i => i.tagNo));
  const scan = await owner('GET', '/api/stock/scan?code=' + items[0].tagNo);
  check('scan by tag finds the piece', (scan.item || scan).id === items[0].id, scan);

  console.log('Billing');
  const cust = await owner('POST', '/api/customers', {name: 'Lakshmi R', phone: '9876500001'});
  await owner('POST', '/api/customers', {name: 'Dup', phone: '9876500001'}, 409);
  check('duplicate phone refused', true);
  const prev = await owner('POST', '/api/sales/preview', {customerId: cust.id, items: [{itemId: items[0].id}]});
  check('pricing: metal + wastage + making + 3% GST', money(prev.total) === money(76426), prev.total);
  const bill = await owner('POST', '/api/sales', {clientRef: 'smoke-1', customerId: cust.id, items: [{itemId: items[0].id}], payments: [{mode: 'CASH', amount: 50000}]});
  const again = await owner('POST', '/api/sales', {clientRef: 'smoke-1', customerId: cust.id, items: [{itemId: items[0].id}], payments: [{mode: 'CASH', amount: 50000}]});
  check('same clientRef does not create a second bill', again.id === bill.id);
  check('bill is partially paid', bill.paymentStatus === 'PARTIAL' && money(bill.balance) === money(26426), bill.balance);
  await owner('POST', '/api/sales', {customerId: cust.id, items: [{itemId: items[0].id}]}, 400);
  check("sold piece can't be billed again", true);
  await owner('POST', '/api/sales', {items: [{itemId: items[1].id}], payments: [{mode: 'CASH', amount: 100}]}, 400);
  check('walk-in bill must be paid in full', true);
  await owner('GET', `/api/sales/${bill.id}`);
  await owner('POST', `/api/customers/${cust.id}/collect`, {amount: 30000, mode: 'UPI'}, 400);
  check("collecting more than due needs the 'keep as advance' choice", true);
  await owner('POST', `/api/customers/${cust.id}/collect`, {amount: 30000, mode: 'UPI', excessToAdvance: true});
  const prof = await owner('GET', `/api/customers/${cust.id}`);
  check('collecting more than due: bill paid, excess kept as advance', Number(prof.summary.owes) === 0 && money(prof.summary.advance) === money(3574), prof.summary);

  console.log('Orders, karigar and repairs');
  const kar = await owner('POST', '/api/masters/karigars', {name: 'Murugan'});
  const order = await owner('POST', '/api/orders', {customerId: cust.id, description: 'Temple necklace 40 g', purityId: p22.id, targetWeight: '40', estimate: 300000, lockedRate: 6500, advance: {amount: 50000, mode: 'CASH'}});
  check('order with advance', money(order.advancePaid) === money(50000), order.advancePaid);
  let job = await owner('POST', '/api/jobs', {karigarId: kar.id, description: 'Make temple necklace', purityId: p22.id, wastageAllowedPercent: '3', labourCharge: 8000, customOrderId: order.id, issueWeight: '42'});
  job = await owner('POST', `/api/jobs/${job.id}/metal`, {type: 'RECEIPT', weight: '40.5'});
  check('karigar reconciliation computes shortfall', Number(job.differenceFine).toFixed(3) === '-0.220', job.differenceFine);
  await owner('POST', `/api/jobs/${job.id}/close`, {}, 400);
  check('closing a short job needs confirmation', true);
  await owner('POST', `/api/jobs/${job.id}/close`, {acceptShortfall: true});
  check('closed job marks order READY', (await owner('GET', `/api/orders/${order.id}`)).status === 'READY');
  const k = await owner('POST', `/api/karigars/${kar.id}/payments`, {amount: 5000, mode: 'CASH'});
  check('karigar labour due after part payment', money(k.labourDue) === money(3000), k.labourDue);
  let rep = await owner('POST', '/api/repairs', {customerId: cust.id, itemDescription: 'Gold ring', problem: 'Resize', estimate: 800});
  await owner('PUT', `/api/repairs/${rep.id}`, {status: 'READY'});
  await owner('PUT', `/api/repairs/${rep.id}`, {status: 'DELIVERED'}, 400);
  check("repair can't be delivered while money is due", true);
  rep = await owner('PUT', `/api/repairs/${rep.id}`, {status: 'DELIVERED', payment: {amount: 800, mode: 'UPI'}});
  check('repair delivered after payment', rep.status === 'DELIVERED' && Number(rep.balance) === 0);

  console.log('Returns and cancellation');
  const bill2 = await owner('POST', '/api/sales', {customerId: cust.id, items: [{itemId: items[1].id}, {itemId: items[2].id}], payments: [{mode: 'CARD', amount: 152852}]});
  check('two-piece bill fully paid', bill2.paymentStatus === 'PAID', [bill2.total, bill2.paymentStatus]);
  const line = bill2.lines[1];
  await owner('POST', `/api/sales/${bill2.id}/returns`, {saleItemIds: [line.id], reason: 'Size', refundMode: 'ADVANCE'});
  check('returned piece back in stock', (await owner('GET', `/api/stock/items/${items[2].id}`)).status === 'IN_STOCK');
  await owner('POST', `/api/sales/${bill2.id}/cancel`, {reason: 'x', refundMode: 'CASH'}, 400);
  check("a bill with returns can't be cancelled", true);
  const advBefore = Number((await owner('GET', `/api/customers/${cust.id}`)).summary.advance);
  check('return refund kept as customer advance', advBefore > 3574, advBefore);
  const bill3 = await owner('POST', '/api/sales', {customerId: cust.id, items: [{itemId: items[3].id}], payments: [{mode: 'UPI', amount: 1000}]});
  await owner('POST', `/api/sales/${bill3.id}/cancel`, {reason: 'Customer changed mind', refundMode: 'CASH'});
  check('cancelled bill puts piece back', (await owner('GET', `/api/stock/items/${items[3].id}`)).status === 'IN_STOCK');
  check('cancelled bill shows Cancelled', (await owner('GET', `/api/sales/${bill3.id}`)).label === 'Cancelled');

  console.log('Purchases and money');
  const sup = await owner('POST', '/api/masters/suppliers', {name: 'Chennai Bullion'});
  const pu = await owner('POST', '/api/purchases', {supplierId: sup.id, supplierInvoiceNo: 'CB-1', items: [{description: '22K chain', purity: p22.name, quantity: 1, grossWeight: '8', netWeight: '8', amount: 50000, addToStock: true,
    stock: {productName: 'Bought Chain', categoryId: cat.id, purityId: p22.id, grossWeight: '8', makingType: 'FIXED', makingValue: '2000'}}], payment: {mode: 'BANK', amount: 20000}});
  check('purchase leaves supplier due', money(pu.balance) === money(30000), pu.balance);
  const led = await owner('GET', `/api/suppliers/${sup.id}/ledger`);
  check('supplier ledger shows the due', money(led.owed) === money(30000), led);
  await owner('POST', '/api/money/expenses', {category: 'Tea / Refreshments', title: 'Tea', amount: 120, mode: 'CASH'});
  const dc = await owner('GET', '/api/money/day-close');
  const closed = await owner('POST', '/api/money/day-close', {actualCash: dc.expectedCash, openingCash: 0});
  check('day closed with no difference', Number(closed.difference) === 0, closed);

  console.log('Reports, dashboard, search');
  const dash = await owner('GET', '/api/dashboard');
  check("dashboard has today's sales", Number(dash.today.sales) > 0, dash.today);
  for (const r of await owner('GET', '/api/reports')) {
    const rr = await owner('GET', '/api/reports/' + r.key);
    check('report ' + r.key, Array.isArray(rr.rows));
  }
  const an = await owner('GET', '/api/analytics/sales');
  check('analytics trend (tenant-scoped)', Array.isArray(an.series) && Number(an.today.sales) > 0, an.today);
  const hits = (await owner('GET', '/api/search?q=lakshmi')).results;
  check('global search finds the customer', hits.some(h => h.type === 'CUSTOMER'), hits);

  console.log('Isolation and permissions');
  await owner('POST', '/api/users', {username: 'smoke_cashier', email: 'c@smoke.test', phone: '9000000099', password: 'Cashier@123', role: 'CASHIER', branchId: b2});
  const users = await owner('GET', '/api/users');
  const ul = Array.isArray(users) ? users : users.users || [];
  const cash = ul.find(u => u.username === 'smoke_cashier');
  if (cash && !cash.enabled) await owner('POST', `/api/users/${cash.id}/enable`, {}, null);
  const login = await call('POST', '/api/auth/login', {identifier: 'smoke_cashier', password: 'Cashier@123'}, null, {}, null);
  const tok = login && login.token;
  if (tok) {
    await call('GET', `/api/sales/${bill.id}`, null, tok, {}, 404);
    check("branch-2 cashier can't open a branch-1 bill", true);
    await call('PUT', '/api/properties/tax', {enabled: true, mode: 'GST', rate: 0}, tok, {}, 403);
    check("cashier can't change tax settings", true);
    await call('GET', '/api/backups', null, tok, {}, 403);
    check("cashier can't see backups", true);
    await call('GET', '/api/analytics/sales', null, tok, {}, 200);
    await call('GET', '/api/reports/profit', null, tok, {}, 403);
    check("cashier can't run the profit report", true);
    await call('POST', '/api/gold-rates', {rates: [{purityId: p22.id, ratePerGram: 1}]}, tok, {}, 403);
    check("cashier can't change gold rates", true);
  } else check('cashier login', false, login);
  const other = await call('POST', '/api/companies', {name: 'Other Shop'}, admin);
  const ob = (await call('POST', '/api/branches', {companyId: other.id, name: 'Other', code: 'OTH'}, admin)).id;
  await call('GET', `/api/sales/${bill.id}`, null, admin, {'X-Company-Id': other.id, 'X-Branch-Id': ob}, 404);
  check("another company's bill is not found", true);

  console.log('Menu layout');
  const menu = await owner('GET', `/api/properties/menu?companyId=${cid}`);
  const its = menu.items; its[0].hidden = true; its.unshift(its.pop());
  const saved = await owner('PUT', `/api/properties/menu?companyId=${cid}`, {items: its});
  check('menu saved as custom', saved.custom && saved.items[0].key === its[0].key);
  const reset = await owner('DELETE', `/api/properties/menu?companyId=${cid}`);
  check('menu reset to default', !reset.custom);

  console.log(`\n${passed} passed, ${failed.length} failed`);
  if (failed.length) { console.log('Failed:\n  ' + failed.join('\n  ')); process.exit(1); }
}
main().catch(e => { console.log('\nSTOPPED:', e.message); console.log(`${passed} passed, ${failed.length + 1} failed`); process.exit(1); });
