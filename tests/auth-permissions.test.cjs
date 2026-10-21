const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const { test } = require('node:test');

function setup() {
  const session = new Map([
    ['accessToken', 'existing-token'],
    ['user', JSON.stringify({ username: 'staff', roles: ['ROLE_WAREHOUSE_STAFF'], permissions: ['WAREHOUSE_EXPORT'] })],
  ]);
  const handlers = new Map();
  const attributes = new Set();
  const button = {
    hidden: false, dataset: {},
    hasAttribute: name => attributes.has(name),
    setAttribute: name => attributes.add(name),
    removeAttribute: name => attributes.delete(name),
  };
  let granted = ['WAREHOUSE_EXPORT'];
  let requests = 0;
  const context = vm.createContext({
    sessionStorage: { getItem: key => session.get(key) ?? null, setItem: (key, value) => session.set(key, value), removeItem: key => session.delete(key) },
    window: {
      location: {},
      addEventListener: (name, callback) => handlers.set(name, [...(handlers.get(name) || []), callback]),
      dispatchEvent: event => (handlers.get(event.type) || []).forEach(callback => callback(event)),
    },
    document: { querySelectorAll: selector => selector === '#export' ? [button] : [] },
    CustomEvent: class { constructor(type, options) { this.type = type; this.detail = options.detail; } },
    fetch: async () => {
      requests++;
      return { ok: true, json: async () => ({ success: true, data: { roles: ['ROLE_WAREHOUSE_STAFF'], permissions: granted } }) };
    },
    console, setInterval() {}, clearInterval() {},
  });
  const auth = vm.runInContext(fs.readFileSync(require.resolve('../frontend/js/auth.js'), 'utf8') + '\nAuth;', context);
  return { auth, button, handlers, revoke: () => { granted = []; }, grant: () => { granted = ['WAREHOUSE_EXPORT']; }, count: () => requests };
}

test('active session loses and regains permission without changing its role or token', async () => {
  const { auth, button, revoke, grant } = setup();
  auth.bindPermissions('#export', 'WAREHOUSE_EXPORT');
  assert.equal(button.hidden, false);
  revoke();
  await auth.syncPermissions();
  assert.equal(auth.hasPermission('WAREHOUSE_EXPORT'), false);
  assert.equal(button.hidden, true);
  assert.equal(auth.getAccessToken(), 'existing-token');
  assert.deepEqual(Array.from(auth.getUser().roles), ['ROLE_WAREHOUSE_STAFF']);
  grant();
  await auth.syncPermissions();
  assert.equal(auth.hasPermission('WAREHOUSE_EXPORT'), true);
  assert.equal(button.hidden, false);
});

test('unchanged permissions do not redraw the page and concurrent synchronization shares one request', async () => {
  const { auth, handlers, count } = setup();
  let changes = 0;
  handlers.set('auth:permissions-changed', [() => changes++]);
  await Promise.all([auth.syncPermissions(), auth.syncPermissions()]);
  assert.equal(count(), 1);
  assert.equal(changes, 0);
});

test('granting permission respects the page handler when an action was hidden before binding', async () => {
  const { auth, button, handlers, revoke, grant } = setup();
  revoke();
  await auth.syncPermissions();
  button.hidden = true;
  auth.bindPermissions('#export', 'WAREHOUSE_EXPORT');
  handlers.set('auth:permissions-changed', [() => { button.hidden = !auth.hasPermission('WAREHOUSE_EXPORT'); }]);
  grant();
  await auth.syncPermissions();
  assert.equal(button.hidden, false);
});
