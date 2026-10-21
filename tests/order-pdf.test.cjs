const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const { test } = require('node:test');

function environment(response) {
  const nodes = new Map();
  const calls = [];
  const revoked = [];
  const get = (id) => {
    if (!nodes.has(id)) nodes.set(id, {
      hidden: true, listeners: {}, children: [], style: {},
      addEventListener(event, handler) { this.listeners[event] = handler; },
      replaceChildren(...children) { this.children = children; },
    });
    return nodes.get(id);
  };
  const context = vm.createContext({
    document: { getElementById: get, createElement: () => ({ style: {} }) },
    Auth: { hasPermission: () => true, apiFetch: async (path) => { calls.push(path); return response; } },
    URL: { createObjectURL: () => 'blob:order-pdf', revokeObjectURL: (url) => revoked.push(url) },
    window: { open: (...args) => calls.push(args) }, encodeURIComponent,
  });
  const pdf = vm.runInContext(fs.readFileSync('frontend/js/order-pdf.js', 'utf8') + '\nOrderPdf;', context);
  return { pdf, get, calls, revoked };
}

test('PDF lấy từ đơn đã lưu, xem/in được và giải phóng blob khi đóng', async () => {
  const env = environment({ ok: true, blob: async () => ({ type: 'application/pdf' }) });
  await env.pdf.open({ id: 12, orderCode: 'DH-12' });
  assert.equal(env.calls[0], '/orders/12/pdf');
  assert.equal(env.get('print-modal').hidden, false);
  assert.equal(env.get('order-print-area-preview').children[0].src, 'blob:order-pdf');
  env.get('btn-print-order').listeners.click();
  assert.equal(env.calls[1][0], 'blob:order-pdf');
  env.pdf.close();
  assert.equal(env.get('print-modal').hidden, true);
  assert.deepEqual(env.revoked, ['blob:order-pdf']);
});

test('lỗi xuất PDF hiển thị riêng và không gửi lại request tạo đơn', async () => {
  const env = environment({ ok: false, json: async () => ({ message: 'Không đủ quyền in đơn' }) });
  await assert.rejects(env.pdf.open({ id: 12 }), /Không đủ quyền in đơn/);
  assert.deepEqual(env.calls, ['/orders/12/pdf']);
  assert.equal(env.get('print-modal').hidden, true);
});
