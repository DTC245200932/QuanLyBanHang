// Real admin UI with mocked HTTP; never saves the real permission matrix.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { chromium } = require('playwright');
const base = process.env.NAV_TEST_URL || 'http://127.0.0.1:5519';
const output = path.join(require('node:os').tmpdir(), 'qlbh-permission-feedback');
const user = { id: 1, username: 'admin', fullName: 'Quản trị viên', roles: ['ROLE_ADMIN'], permissions: [] };
const token = 'test.' + Buffer.from(JSON.stringify({ exp: Math.floor(Date.now()/1000)+3600 })).toString('base64url') + '.test';

(async () => {
  fs.mkdirSync(output, { recursive: true });
  const browser = await chromium.launch({ headless: true, channel: 'chrome' });
  const errors = [];
  let failSave = false, saves = 0;
  let matrix = { ROLE_ADMIN: ['ORDER_VIEW'], ROLE_VIEWER: ['ORDER_VIEW'] };
  try {
    const context = await browser.newContext({ viewport: { width: 1440, height: 900 }, reducedMotion: 'reduce' });
    await context.addInitScript(({ user, token }) => {
      sessionStorage.setItem('user', JSON.stringify(user)); sessionStorage.setItem('accessToken', token);
    }, { user, token });
    await context.route('http://localhost:8080/api/**', async route => {
      const request = route.request();
      const endpoint = new URL(request.url()).pathname.replace('/api', '');
      let data = [], status = 200;
      if (endpoint === '/auth/permissions') data = { roles: user.roles, permissions: [] };
      else if (endpoint === '/admin/assignment-options') data = { roles: Object.keys(matrix), warehouses: [], territories: [], dealers: [] };
      else if (endpoint === '/admin/users') data = { content: [], totalPages: 1, totalElements: 0 };
      else if (endpoint === '/admin/permissions/reset') matrix = { ...matrix, ROLE_VIEWER: ['ORDER_VIEW'] };
      else if (endpoint === '/admin/permissions') {
        if (request.method() === 'PUT') {
          saves++;
          if (failSave) status = 500;
          else matrix = { ...matrix, ...request.postDataJSON() };
        }
        data = { matrix, permissions: [{ code: 'ORDER_VIEW', label: 'Xem đơn hàng' }] };
      }
      await route.fulfill({ status, contentType: 'application/json',
        headers: { 'Access-Control-Allow-Origin': base, 'Access-Control-Allow-Headers': 'Authorization,Content-Type',
          'Access-Control-Allow-Methods': 'GET,POST,PUT,OPTIONS' },
        body: JSON.stringify({ success: status === 200, message: status === 500 ? 'Không lưu được ma trận quyền' : '', data }) });
    });
    const page = await context.newPage();
    page.on('pageerror', error => errors.push(error.message));
    page.on('dialog', dialog => dialog.accept());
    await page.goto(base + '/admin/users');
    await page.locator('#manage-permissions').click();
    const checkbox = page.locator('[data-permission-role="ROLE_VIEWER"] input[value="ORDER_VIEW"]');
    const message = page.locator('#permissions-message');
    await checkbox.uncheck();
    await page.locator('#save-permissions').click();
    await page.waitForFunction(() => document.getElementById('permissions-message').textContent === 'Đã lưu ma trận quyền.');
    assert.equal(await message.getAttribute('role'), 'status');
    assert.equal(await message.evaluate(box => box.classList.contains('form-message--success')), true);
    const green = await message.evaluate(box => getComputedStyle(box).color.match(/\d+/g).map(Number));
    assert.ok(green[1] > green[0] && green[1] > green[2]);
    await page.screenshot({ path: path.join(output, 'saved.png') });

    await page.locator('#save-permissions').click();
    assert.equal(await message.innerText(), 'Không có quyền nào thay đổi.');
    assert.equal(await message.evaluate(box => box.classList.contains('form-message--info')), true);
    assert.equal(saves, 1, 'unchanged matrix must not be saved again');

    failSave = true;
    await checkbox.check();
    await page.locator('#save-permissions').click();
    await page.waitForFunction(() => document.getElementById('permissions-message').textContent === 'Không lưu được ma trận quyền');
    assert.equal(await message.getAttribute('role'), 'alert');
    assert.equal(await message.evaluate(box => box.classList.contains('form-message--success')), false);
    const red = await message.evaluate(box => getComputedStyle(box).color.match(/\d+/g).map(Number));
    assert.ok(red[0] > red[1] && red[0] > red[2]);
    await page.locator('#reset-permissions').click();
    await page.waitForFunction(() => document.getElementById('permissions-message').textContent === 'Đã khôi phục quyền mặc định.');
    assert.equal(await message.evaluate(box => box.classList.contains('form-message--success')), true);
    assert.deepEqual(errors, []);
    console.log(JSON.stringify({ passed: true, saveGreen: true, unchangedNeutral: true, errorsRed: true, resetGreen: true, output }));
  } finally { await browser.close(); }
})().catch(error => { console.error(error); process.exitCode = 1; });
