const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const { test } = require('node:test');

function loadClient(responder) {
  const calls = [];
  const context = vm.createContext({
    Auth: { apiFetch: async (url, options = {}) => {
      calls.push({ url, options });
      return responder(url, options);
    } },
  });
  // Việc đọc hoặc ghi dữ liệu nghiệp vụ vào browser storage phải làm test thất bại.
  for (const key of ['localStorage', 'sessionStorage']) {
    Object.defineProperty(context, key, { get() { throw new Error('Browser storage is forbidden'); } });
  }
  vm.runInContext(fs.readFileSync(path.join(__dirname, '../frontend/js/dealer-transactions.js'), 'utf8'), context);
  return { client: vm.runInContext('DealerTransactions', context), calls };
}

const response = (success, data, message = '') => ({
  ok: success, json: async () => ({ success, data, message }),
});

test('lý do khoá phải có nội dung và không vượt 500 ký tự trước khi gửi API', async () => {
  const { client, calls } = loadClient(() => response(true, {}));
  await assert.rejects(client.lock(8, '   '), /lý do/);
  await assert.rejects(client.lock(8, 'a'.repeat(501)), /500/);
  assert.equal(calls.length, 0);
});

test('khóa và mở khóa dùng API thật, giữ cờ active và thông tin trả về của server', async () => {
  const locked = { id: 8, locked: true, active: false, lockReason: 'Quá hạn', inProgressOrderCount: 2 };
  const unlocked = { id: 8, locked: false, active: false };
  const { client, calls } = loadClient(url => response(true, url.endsWith('/unlock') ? unlocked : locked));
  assert.equal(await client.lock(8, '  Quá hạn  '), locked);
  assert.equal(calls[0].url, '/dealers/8/lock');
  assert.equal(calls[0].options.method, 'POST');
  assert.deepEqual(JSON.parse(calls[0].options.body), { reason: 'Quá hạn' });
  assert.equal(await client.unlock(8), unlocked);
  assert.equal(calls[1].url, '/dealers/8/unlock');
  assert.equal(calls[1].options.method, 'POST');
});

test('server từ chối phải hiển thị lỗi thay vì báo thành công', async () => {
  const { client } = loadClient(() => response(false, null, 'Bạn không có quyền khoá đại lý'));
  await assert.rejects(client.lock(8, 'Quá hạn'), /không có quyền/);
  await assert.rejects(client.unlock(8), /không có quyền/);
});

test('đơn đang dở giữ cảnh báo server, chỉ gọi các thao tác có backend', async () => {
  const rows = [{ id: 12, dealerLocked: true, dealerLockWarning: 'Cảnh báo công nợ', status: 'PENDING_APPROVAL' }];
  const { client, calls } = loadClient(url => response(true, url.endsWith('/in-progress-orders') ? rows : rows[0]));
  assert.equal(await client.getInProgressOrders(8), rows);
  assert.equal(calls[0].url, '/dealers/8/in-progress-orders');
  await client.processOrder(12, 'approve');
  assert.equal(calls[1].url, '/orders/12/approve');
  assert.equal(calls[1].options.method, 'PUT');
  await assert.rejects(client.processOrder(12, 'dispatch'), /không hợp lệ/);
  assert.equal(calls.length, 2);
});
