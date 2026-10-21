// Run with the bundled Playwright dependency via NODE_PATH; never changes the real database.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { chromium } = require('playwright');

const base = process.env.NAV_TEST_URL || 'http://127.0.0.1:5519';
const output = process.env.NAV_TEST_OUTPUT || path.join(require('node:os').tmpdir(), 'qlbh-navigation');
const user = { id: 1, username: 'admin', fullName: 'Quản trị viên', email: 'admin@example.invalid', phone: '0900000000', roles: ['ROLE_ADMIN'], permissions: [], warehouses: [], territories: [] };
const token = 'test.' + Buffer.from(JSON.stringify({ exp: Math.floor(Date.now() / 1000) + 3600 })).toString('base64url') + '.test';

(async () => {
  fs.mkdirSync(output, { recursive: true });
  const browser = await chromium.launch({ headless: true, channel: process.env.NAV_BROWSER_CHANNEL || 'chrome' });
  const errors = [];
  try {
    const context = await browser.newContext({ viewport: { width: 1440, height: 800 }, reducedMotion: 'no-preference' });
    await context.addInitScript(({ user, token }) => {
      sessionStorage.setItem('user', JSON.stringify(user));
      sessionStorage.setItem('accessToken', token);
      window.addEventListener('pagereveal', event => { window.__nativeTransition = Boolean(event.viewTransition); });
    }, { user, token });
    await context.route('http://localhost:8080/api/**', async route => {
      const request = route.request();
      const url = new URL(request.url());
      const endpoint = url.pathname.replace('/api', '');
      let data = [];
      if (endpoint === '/auth/permissions') data = { roles: user.roles, permissions: user.permissions };
      else if (endpoint === '/users/profile' || endpoint === '/auth/me') data = user;
      else if (endpoint === '/admin/assignment-options') data = { roles: user.roles, warehouses: [], territories: [], dealers: [] };
      else if (endpoint === '/admin/users') data = { content: [], totalElements: 0, totalPages: 1, number: 0, first: true, last: true };
      else if (endpoint === '/dealers/options') data = { assignedUsers: [], territories: [] };
      else if (endpoint === '/orders/draft') data = null;
      await route.fulfill({ status: 200, contentType: 'application/json',
        headers: { 'Access-Control-Allow-Origin': base, 'Access-Control-Allow-Headers': 'Authorization,Content-Type', 'Access-Control-Allow-Methods': 'GET,POST,PUT,DELETE,OPTIONS' },
        body: JSON.stringify({ success: true, data, totalElements: 0, totalPages: 0 }) });
    });
    const page = await context.newPage();
    page.on('pageerror', error => errors.push({ url: page.url(), message: error.message, stack: error.stack }));
    await page.goto(base + '/dashboard');
    await page.waitForFunction(() => document.querySelector('#sidebar-nav [data-page="users"]') && !document.getElementById('sidebar-nav').inert);
    await page.locator('#sidebar-nav').evaluate(nav => { nav.scrollTop = nav.scrollHeight; });
    const before = await page.locator('#sidebar-nav').evaluate(nav => ({ top: nav.scrollTop, height: nav.clientHeight }));
    assert.ok(before.top > 0, 'fixture sidebar must actually overflow');
    await page.locator('#sidebar-nav [data-page="users"]').click();
    await page.waitForURL(base + '/admin/users');
    await page.waitForFunction(() => document.querySelector('#sidebar-nav [aria-current="page"]')?.dataset.page === 'users' && !document.getElementById('sidebar-nav').inert);
    const after = await page.locator('#sidebar-nav').evaluate(nav => ({ top: nav.scrollTop, height: nav.clientHeight }));
    assert.ok(Math.abs(after.top - before.top) < 3, `menu moved after click: ${JSON.stringify({ before, after })}`);
    await page.waitForFunction(() => typeof window.__nativeTransition === 'boolean');
    const transition = await page.evaluate(() => ({ active: window.__nativeTransition,
      reduced: matchMedia('(prefers-reduced-motion: reduce)').matches,
      supports: CSS.supports('view-transition-name', 'app-main'),
      rules: [...document.styleSheets].flatMap(sheet => [...sheet.cssRules]).filter(rule => rule.cssText.startsWith('@view-transition')).map(rule => rule.cssText) }));
    console.log(JSON.stringify({ browser: browser.version(), transition }));
    assert.equal(transition.active, true, 'native content transition should run');
    await page.screenshot({ path: path.join(output, 'desktop.png') });

    await page.goBack();
    await page.waitForFunction(() => document.querySelector('#sidebar-nav [aria-current="page"]')?.dataset.page === 'dashboard');
    assert.ok(Math.abs(await page.locator('#sidebar-nav').evaluate(nav => nav.scrollTop) - before.top) < 3);
    await page.goForward();
    await page.waitForFunction(() => document.querySelector('#sidebar-nav [aria-current="page"]')?.dataset.page === 'users');
    assert.ok(Math.abs(await page.locator('#sidebar-nav').evaluate(nav => nav.scrollTop) - after.top) < 3);
    await page.reload();
    await page.waitForFunction(() => document.querySelector('#sidebar-nav [data-page="users"]') && !document.getElementById('sidebar-nav').inert);
    assert.ok(Math.abs(await page.locator('#sidebar-nav').evaluate(nav => nav.scrollTop) - after.top) < 3);

    for (const id of ['create-order', 'products', 'profile', 'orders']) {
      await page.locator(`#sidebar-nav [data-page="${id}"]`).click();
      await page.waitForURL(base + '/' + id);
      await page.waitForFunction(id => document.querySelector('#sidebar-nav [aria-current="page"]')?.dataset.page === id && !document.getElementById('sidebar-nav').inert, id);
      assert.equal(await page.locator('aside#sidebar').count(), 1);
      assert.equal(await page.locator('.main-area').count(), 1);
      assert.ok(await page.locator('.main-area').isVisible());
    }

    await page.setViewportSize({ width: 390, height: 844 });
    await page.emulateMedia({ reducedMotion: 'reduce' });
    await page.goto(base + '/products');
    await page.waitForFunction(() => document.querySelector('#sidebar-nav [data-page="users"]') && !document.getElementById('sidebar-nav').inert);
    await page.locator('#sidebar-toggle').click();
    await page.locator('#sidebar-nav').evaluate(nav => { nav.scrollTop = nav.scrollHeight; });
    await page.locator('#sidebar-nav [data-page="users"]').click();
    await page.waitForURL(base + '/admin/users');
    await page.waitForFunction(() => document.querySelector('#sidebar-nav [data-page="users"]') && !document.getElementById('sidebar-nav').inert);
    assert.equal(await page.locator('#sidebar').evaluate(sidebar => sidebar.classList.contains('sidebar--open')), false);
    await page.waitForFunction(() => typeof window.__nativeTransition === 'boolean');
    assert.equal(await page.evaluate(() => window.__nativeTransition), false, 'reduced motion disables navigation animation');
    await page.screenshot({ path: path.join(output, 'mobile.png') });
    assert.deepEqual(errors, [], 'page scripts must keep working');
    console.log(JSON.stringify({ passed: true, desktopScroll: { before, after }, checked: ['click', 'back', 'forward', 'reload', 'page content', 'mobile', 'reduced motion'], errors, output }));
  } finally { await browser.close(); }
})().catch(error => { console.error(error); process.exitCode = 1; });
