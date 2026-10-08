const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const { test } = require('node:test');

const source = fs.readFileSync(path.join(__dirname, '../frontend/js/menu-config.js'), 'utf8');
const menu = vm.runInNewContext(`${source}\nMenuConfig;`);
const ids = user => Array.from(menu.getMenuForUser(user), item => item.id);

test('admin can reach implemented account, audit and catalog pages', () => {
  const visible = ids({roles:['ROLE_ADMIN'], permissions:['USER_VIEW', 'PRODUCT_VIEW', 'WAREHOUSE_VIEW', 'WAREHOUSE_IMPORT']});
  for (const id of ['dashboard', 'profile', 'users', 'audit-logs', 'products', 'product-groups', 'suppliers']) assert.ok(visible.includes(id));
  for (const id of ['orders', 'customers', 'finance', 'reports']) assert.ok(!visible.includes(id));
});

test('warehouse staff and viewers do not receive admin or business management menus', () => {
  for (const role of ['ROLE_WAREHOUSE_STAFF', 'ROLE_VIEWER']) {
    const visible = ids({roles:[role], permissions:['USER_VIEW', 'PRODUCT_VIEW', 'WAREHOUSE_VIEW', 'WAREHOUSE_IMPORT']});
    assert.ok(visible.includes('products'));
    assert.ok(visible.includes('warehouse'));
    assert.ok(!visible.includes('users'));
    assert.ok(!visible.includes('audit-logs'));
    assert.ok(!visible.includes('price-lists'));
  }
});

test('sales manager can use groups, prices and conversions; permission removal hides product pages', () => {
  const visible = ids({roles:['ROLE_SALES_MANAGER'], permissions:['PRODUCT_VIEW', 'WAREHOUSE_VIEW']});
  for (const id of ['product-groups', 'price-lists', 'product-units']) assert.ok(visible.includes(id));
  assert.ok(!ids({roles:['ROLE_SALES_MANAGER'], permissions:[]}).includes('products'));
});

test('every visible page has an absolute, extensionless link even from a nested page', () => {
  for (const item of menu.menuItems) {
    assert.ok(item.href.startsWith('/'), item.id);
    assert.ok(!item.href.includes('.html'), item.id);
    assert.equal(menu.resolveHref(item.href), item.href);
  }
});

test('debt limit menu follows finance roles and view permission', () => {
  for (const role of ['ROLE_ADMIN', 'ROLE_ACCOUNTANT', 'ROLE_SALES_MANAGER']) {
    assert.ok(ids({roles:[role], permissions:['FINANCE_VIEW_DEBT']}).includes('debt-limits'));
    assert.ok(!ids({roles:[role], permissions:[]}).includes('debt-limits'));
  }
  for (const role of ['ROLE_VIEWER', 'ROLE_SALES', 'ROLE_WAREHOUSE_STAFF', 'ROLE_WAREHOUSE_MANAGER']) {
    assert.ok(!ids({roles:[role], permissions:['FINANCE_VIEW_DEBT']}).includes('debt-limits'));
  }
});

test('debt limit menu uses the existing dashboard route', () => {
  const item = menu.menuItems.find(item => item.id === 'debt-limits');
  assert.equal(item.href, '/dashboard?view=debt-limits');
});
