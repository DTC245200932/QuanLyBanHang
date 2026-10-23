// Verify real table ellipsis and unabridged details; HTTP is mocked, no DB access.
const assert = require('node:assert/strict');
const path = require('node:path');
const fs = require('node:fs');
const { chromium } = require('playwright');
const base = process.env.NAV_TEST_URL || 'http://127.0.0.1:5519';
const output = path.join(require('node:os').tmpdir(), 'qlbh-audit-values');
const user = { id: 1, username: 'admin', fullName: 'Quản trị viên', roles: ['ROLE_ADMIN'], permissions: [] };
const token = 'test.' + Buffer.from(JSON.stringify({ exp: Math.floor(Date.now() / 1000) + 3600 })).toString('base64url') + '.test';
const before = 'PENDING_APPROVAL_' + 'A'.repeat(4000);
const after = 'Tổng tiền: 10000000 (Chờ duyệt: Sản phẩm bán dưới giá sàn; Vượt hạn mức công nợ) '.repeat(100)
  + '"><img src=x onerror="alert(1)">';
const logs = [
  { id: 1, createdAt: '2026-10-20T15:00:00', entityType: 'INVOICE', entityId: 'DH-001', username: 'admin', action: 'CREATE', oldValue: before, newValue: after },
  { id: 2, createdAt: '2026-10-20T14:00:00', entityType: 'INVENTORY', entityId: 'SP-001', username: 'admin', action: 'IMPORT', oldValue: '500 lon', newValue: '600 lon' },
  { id: 3, createdAt: '2026-10-20T13:00:00', entityType: 'INVOICE', entityId: 'DH-002', username: 'admin', action: 'CREATE', oldValue: null, newValue: null },
];

(async () => {
  fs.mkdirSync(output, { recursive: true });
  const browser = await chromium.launch({ headless: true, channel: 'chrome' });
  const errors = [];
  try {
    const context = await browser.newContext({ reducedMotion: 'reduce' });
    await context.addInitScript(({ user, token }) => {
      sessionStorage.setItem('user', JSON.stringify(user)); sessionStorage.setItem('accessToken', token);
    }, { user, token });
    await context.route('http://localhost:8080/api/**', async route => {
      const endpoint = new URL(route.request().url()).pathname.replace('/api', '');
      let data = [];
      if (endpoint === '/auth/permissions') data = { roles: user.roles, permissions: [] };
      if (endpoint === '/admin/users') data = { content: [], totalPages: 1 };
      if (endpoint === '/admin/audit-logs') data = logs;
      await route.fulfill({ status: 200, contentType: 'application/json',
        headers: { 'Access-Control-Allow-Origin': base, 'Access-Control-Allow-Headers': 'Authorization,Content-Type' },
        body: JSON.stringify({ success: true, data }) });
    });
    const page = await context.newPage();
    page.on('pageerror', error => errors.push(error.message));
    for (const width of [1920, 1440, 390, 320]) {
      await page.setViewportSize({ width, height: 900 });
      await page.goto(base + '/admin/audit-logs');
      await page.locator('[data-log-id="1"] .diff-val--after').waitFor();
      const measurements = await page.locator('[data-log-id="1"]').evaluate(row => ({
        values: [...row.querySelectorAll('.diff-val')].map(value => ({ width: value.getBoundingClientRect().width,
          overflow: value.scrollWidth > value.clientWidth, ellipsis: getComputedStyle(value).textOverflow })),
        tableWidth: document.querySelector('.audit-table').getBoundingClientRect().width,
        pageOverflow: document.documentElement.scrollWidth > document.documentElement.clientWidth,
      }));
      for (const value of measurements.values) {
        assert.ok(value.width <= 241 && value.overflow);
        assert.equal(value.ellipsis, 'ellipsis');
      }
      assert.ok(measurements.tableWidth < 1600, 'values must not stretch the table to thousands of pixels');
      assert.equal(measurements.pageOverflow, false);
      assert.equal(await page.locator('[data-log-id="1"] .diff-val--after').getAttribute('title'), after);
      assert.equal(await page.locator('[data-log-id="2"] .diff-val--after').innerText(), '600 lon');
      assert.equal(await page.locator('[data-log-id="3"] .diff-val--after').innerText(), '—');
      await page.locator('.audit-panel').scrollIntoViewIfNeeded();
      await page.screenshot({ path: path.join(output, `list-${width}.png`) });
      await page.locator('[data-log-id="1"] [data-action="view"]').click();
      assert.equal(await page.locator('.diff-comparison-table tbody td').nth(1).textContent(), before);
      assert.equal(await page.locator('.diff-comparison-table tbody td').nth(2).textContent(), after);
      assert.equal(await page.locator('#audit-detail-body img[src="x"]').count(), 0, 'log text must stay escaped');
      assert.equal(await page.locator('#modal-audit-detail').evaluate(modal => modal.scrollWidth > modal.clientWidth), false);
      await page.locator('#modal-audit-detail [data-close-detail]').first().click();
    }
    assert.deepEqual(errors, []);
    console.log(JSON.stringify({ passed: true, viewports: [1920,1440,390,320], ellipsis: true, fullDetails: true, output }));
  } finally { await browser.close(); }
})().catch(error => { console.error(error); process.exitCode = 1; });
