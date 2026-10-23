// Native MPA navigation, delayed permission sync and real Chrome compositor.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { chromium } = require('playwright');
const base = process.env.NAV_TEST_URL || 'http://127.0.0.1:5519';
const output = path.join(require('node:os').tmpdir(), 'qlbh-menu-transition');
const token = 'test.' + Buffer.from(JSON.stringify({ exp: Math.floor(Date.now() / 1000) + 3600 })).toString('base64url') + '.test';
const user = { id: 1, username: 'admin', fullName: 'Quản trị viên', email: 'admin@example.invalid',
  roles: ['ROLE_ADMIN'], permissions: [], warehouses: [], territories: [] };

(async () => {
  fs.mkdirSync(output, { recursive: true });
  const browser = await chromium.launch({ headless: true, channel: 'chrome' });
  const errors = [];
  let gate;
  let release;
  try {
    const context = await browser.newContext({ viewport: { width: 1440, height: 800 }, reducedMotion: 'no-preference' });
    await context.addInitScript(({ user, token }) => {
      sessionStorage.setItem('user', JSON.stringify(user));
      sessionStorage.setItem('accessToken', token);
      window.addEventListener('pagereveal', event => {
        window.__transition = event.viewTransition;
        window.__reveal = { sidebar: Boolean(document.getElementById('sidebar')),
          previous: window.navigation?.activation?.from?.getState?.(),
          native: Boolean(event.viewTransition), state: document.readyState };
      });
    }, { user, token });
    await context.route('http://localhost:8080/api/**', async route => {
      const endpoint = new URL(route.request().url()).pathname.replace('/api', '');
      if (endpoint === '/auth/permissions' && gate) await gate;
      let data = [];
      if (endpoint === '/auth/permissions') data = { roles: user.roles, permissions: user.permissions };
      else if (endpoint === '/users/profile') data = user;
      else if (endpoint === '/admin/assignment-options') data = { roles: user.roles, warehouses: [], territories: [], dealers: [] };
      else if (endpoint === '/admin/users') data = { content: [], totalElements: 0, totalPages: 1, number: 0, first: true, last: true };
      await route.fulfill({ status: 200, contentType: 'application/json',
        headers: { 'Access-Control-Allow-Origin': base, 'Access-Control-Allow-Headers': 'Authorization,Content-Type',
          'Access-Control-Allow-Methods': 'GET,POST,PUT,DELETE,OPTIONS' }, body: JSON.stringify({ success: true, data }) });
    });
    const page = await context.newPage();
    page.on('pageerror', error => errors.push(error.message));
    await page.goto(base + '/dashboard');
    await page.waitForFunction(() => document.querySelector('#sidebar-nav [data-page="users"]') && !document.querySelector('#sidebar-nav').inert);
    await page.locator('#sidebar-nav').evaluate(nav => { nav.scrollTop = nav.scrollHeight; });
    const top = await page.locator('#sidebar-nav').evaluate(nav => nav.scrollTop);
    gate = new Promise(resolve => { release = resolve; });
    await page.locator('#sidebar-nav [data-page="users"]').click();
    await page.waitForURL(base + '/admin/users');
    await page.waitForFunction(() => document.documentElement.classList.contains('app-shell-settling'), null, { timeout: 2000 })
      .catch(async error => { console.log(await page.evaluate(() => ({ reveal: window.__reveal, classes: document.documentElement.className, inert: document.querySelector('#sidebar-nav')?.inert }))); throw error; });
    const held = await page.evaluate(async () => {
      await window.__transition.ready;
      const root = document.documentElement;
      const menu = getComputedStyle(root, '::view-transition-old(app-sidebar)');
      const next = getComputedStyle(root, '::view-transition-new(app-sidebar)');
      return { oldOpacity: menu.opacity, oldPlayState: menu.animationPlayState, newOpacity: next.opacity,
        toolbarName: getComputedStyle(document.querySelector('.topbar')).viewTransitionName,
        mainName: getComputedStyle(document.querySelector('.main-area')).viewTransitionName,
        contentName: getComputedStyle(document.querySelector('.admin-main')).viewTransitionName,
        top: document.querySelector('#sidebar-nav').scrollTop, inert: document.querySelector('#sidebar-nav').inert };
    });
    assert.equal(held.oldOpacity, '1');
    assert.equal(held.oldPlayState, 'paused');
    assert.equal(held.newOpacity, '0');
    assert.equal(held.toolbarName, 'none');
    assert.equal(held.mainName, 'none');
    assert.equal(held.contentName, 'app-content');
    assert.equal(held.top, top);
    assert.equal(held.inert, true, 'holding an old image must not unlock stale permissions');
    await page.screenshot({ path: path.join(output, 'menu-held.png') });
    release();
    gate = null;
    await page.waitForFunction(() => !document.documentElement.classList.contains('app-shell-settling') && !document.querySelector('#sidebar-nav').inert);
    await page.evaluate(() => window.__transition.finished);
    assert.equal(await page.locator('#sidebar-nav').evaluate(nav => nav.scrollTop), top);
    await page.locator('#create-user').click();
    assert.equal(await page.locator('#user-modal').isVisible(), true, 'page handlers must still work');
    await page.locator('#user-modal [data-close]').first().click();
    await page.screenshot({ path: path.join(output, 'menu-ready.png') });

    // A hanging permission fetch must not trap the compositor, but must remain
    // locked until the actual permission check completes.
    gate = new Promise(resolve => { release = resolve; });
    await page.locator('#sidebar-nav [data-page="profile"]').click();
    await page.waitForURL(base + '/profile');
    await page.waitForFunction(() => document.documentElement.classList.contains('app-shell-settling'));
    await page.evaluate(() => window.__transition.finished);
    assert.equal(await page.locator('html').evaluate(root => root.classList.contains('app-shell-settling')), false);
    assert.equal(await page.locator('#sidebar-nav').evaluate(nav => nav.inert), true);
    release();
    gate = null;
    await page.waitForFunction(() => !document.querySelector('#sidebar-nav').inert);

    // Start another document while the preceding native animation is active.
    // No SPA script replay, duplicate global declarations, or stuck snapshots.
    for (const id of ['products', 'orders', 'create-order']) {
      await page.evaluate(id => document.querySelector(`#sidebar-nav [data-page="${id}"]`).click(), id);
      await page.waitForURL(base + '/' + id);
      await page.waitForFunction(() => !document.querySelector('#sidebar-nav').inert);
      assert.equal(await page.locator('#sidebar').count(), 1);
      assert.equal(await page.locator('.main-area').count(), 1);
    }
    await page.waitForFunction(() => !document.documentElement.classList.contains('app-shell-settling'));

    // Revocation during navigation still redirects a non-admin; the visual hold
    // must not become an authorization fallback.
    const restricted = await browser.newContext({ viewport: { width: 1440, height: 800 } });
    const viewer = { ...user, id: 7, username: 'viewer', roles: ['ROLE_VIEWER'], permissions: ['PRODUCT_VIEW'] };
    await restricted.addInitScript(({ viewer, token }) => {
      sessionStorage.setItem('user', JSON.stringify(viewer));
      sessionStorage.setItem('accessToken', token);
    }, { viewer, token });
    let revoke = false;
    await restricted.route('http://localhost:8080/api/**', async route => {
      const data = route.request().url().includes('/auth/permissions')
        ? { roles: viewer.roles, permissions: revoke ? [] : viewer.permissions } : [];
      await route.fulfill({ status: 200, contentType: 'application/json',
        headers: { 'Access-Control-Allow-Origin': base, 'Access-Control-Allow-Headers': 'Authorization,Content-Type' },
        body: JSON.stringify({ success: true, data }) });
    });
    const denied = await restricted.newPage();
    denied.on('pageerror', error => errors.push(error.message));
    await denied.goto(base + '/dashboard');
    await denied.waitForFunction(() => !document.querySelector('#sidebar-nav').inert);
    revoke = true;
    await denied.locator('#sidebar-nav [data-page="products"]').click();
    await denied.waitForURL(base + '/error?status=403');
    await restricted.close();
    assert.deepEqual(errors, []);
    console.log(JSON.stringify({ passed: true, held, watchdog: true, pageActions: true, rapidNavigation: true, revocation: true, errors, output }));
  } finally {
    release?.();
    await browser.close();
  }
})().catch(error => { console.error(error); process.exitCode = 1; });
