const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const { test } = require('node:test');

const source = file => fs.readFileSync(path.join(__dirname, '../frontend/js', file), 'utf8');
const token = seconds => `header.${Buffer.from(JSON.stringify({ exp: Math.floor(Date.now() / 1000) + seconds })).toString('base64')}.signature`;

function session(user, fetch) {
  const values = new Map([
    ['user', JSON.stringify(user)], ['accessToken', token(30)], ['refreshToken', 'test-refresh'],
  ]);
  const location = { href: '/dashboard' };
  const context = vm.createContext({
    URLSearchParams, location, window: { location }, fetch,
    atob: value => Buffer.from(value, 'base64').toString('utf8'),
    setInterval: () => 1, clearInterval() {},
    sessionStorage: {
      getItem: key => values.get(key) ?? null,
      setItem: (key, value) => values.set(key, value),
      removeItem: key => values.delete(key),
    },
  });
  vm.runInContext(`${source('auth.js')}\n${source('menu-config.js')}`, context);
  return context;
}

test('server refresh with revoked grants hides create-order menu and blocks direct page access', async () => {
  const calls = [];
  const context = session({ roles: ['ROLE_SALES'], permissions: ['ORDER_CREATE'] }, async url => {
    calls.push(url);
    if (url.endsWith('/auth/refresh')) return {
      ok: true,
      json: async () => ({ accessToken: token(3600), roles: ['ROLE_SALES'], permissions: [] }),
    };
    return { status: 403, clone: () => ({ json: async () => ({ message: 'Không có quyền' }) }) };
  });
  assert.equal(vm.runInContext('Auth.hasPermission("ORDER_CREATE")', context), true);
  await vm.runInContext('Auth.apiFetch("/warehouse/options")', context);
  assert.equal(calls.length, 2);
  assert.equal(vm.runInContext('Auth.hasPermission("ORDER_CREATE")', context), false);
  assert.equal(vm.runInContext('MenuConfig.getMenuForUser(Auth.getUser()).some(item => item.id === "create-order")', context), false);
  context.location.href = '/create-order';
  vm.runInContext(source('create-order.js'), context);
  assert.equal(context.location.href, '/error?status=403');
});

test('admin retains permissions and menus when the server returns an empty permission array', () => {
  const context = session({ roles: ['ROLE_ADMIN'], permissions: [] });
  assert.equal(vm.runInContext('Auth.hasPermission("WAREHOUSE_MANAGE")', context), true);
  assert.equal(vm.runInContext('Auth.hasPermission("ORDER_CREATE")', context), true);
  assert.equal(vm.runInContext('MenuConfig.getMenuForUser(Auth.getUser()).some(item => item.id === "create-order")', context), true);
});
