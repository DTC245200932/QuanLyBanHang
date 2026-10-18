const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const { test } = require('node:test');

function createMockEnvironment(apiResponses = {}) {
  const elements = new Map();
  function getEl(id) {
    if (!elements.has(id)) {
      elements.set(id, {
        id,
        value: '',
        hidden: false,
        disabled: false,
        textContent: '',
        innerHTML: '',
        selectedOptions: [{ value: 'thùng', dataset: { price: '360000', factor: '24' } }],
        listeners: {},
        classList: {
          toggle() {},
          add() {},
          remove() {},
        },
        dataset: {},
        focus() {},
        addEventListener(event, callback) {
          this.listeners[event] = callback;
        },
        querySelectorAll() {
          return [];
        },
        querySelector() {
          return null;
        },
      });
    }
    return elements.get(id);
  }

  const calls = [];
  const success = (data) => ({ ok: true, json: async () => ({ success: true, data }) });

  const windowObj = {};
  const context = vm.createContext({
    window: windowObj,
    document: {
      getElementById: getEl,
      createElement: () => getEl(Symbol('created')),
      addEventListener(event, cb) {
        if (event === 'DOMContentLoaded') cb();
      },
      readyState: 'complete',
      body: { style: {} },
    },
    location: {},
    Intl,
    Math,
    Date,
    Number,
    String,
    parseInt,
    encodeURIComponent,
    console: { log() {}, warn() {}, error() {} },
    Auth: {
      requireLogin: () => true,
      hasAnyRole: (...roles) => roles.includes('ROLE_DEALER'),
      hasPermission: () => true,
      getUser: () => ({ id: 99, username: 'dealer.test', roles: ['ROLE_DEALER'], dealerId: 10 }),
      apiFetch: async (url, opts = {}) => {
        calls.push({ url, method: opts.method || 'GET', body: opts.body ? JSON.parse(opts.body) : null });
        if (apiResponses[url]) return success(apiResponses[url]);
        if (url === '/portal/profile') {
          return success({ id: 10, code: 'DL-HOANGPHAT', name: 'Đại lý Hoàng Phát', customerGroup: 'DAI_LY_CAP_1', active: true });
        }
        if (url === '/dealers/active') {
          return success([
            { id: 10, code: 'DL-HOANGPHAT', name: 'Đại lý Hoàng Phát', customerGroup: 'DAI_LY_CAP_1' },
            { id: 20, code: 'DL-MINHTHANH', name: 'Đại lý Minh Thành', customerGroup: 'DAI_LY_CAP_2' },
          ]);
        }
        if (url.includes('/delivery-points')) {
          return success([{ id: 1, name: 'Kho chính', address: '123 Đường 1', isDefault: true }]);
        }
        if (url.includes('/debt')) {
          return success({
            debtLimit: 100000000,
            currentDebt: 30000000,
            remainingLimit: 70000000,
            hasOverdueDebt: false,
            isBlocked: false,
          });
        }
        if (url.includes('/price-lists/effective')) {
          return success({
            id: 1,
            code: 'BG-CAP1',
            customerGroup: 'DAI_LY_CAP_1',
            items: [
              { productId: 101, price: 15000, product: { id: 101, sku: 'KEN330', name: 'Bia Heineken 330ml' } },
              { productId: 102, price: 13000, product: { id: 102, sku: 'TIGER330', name: 'Bia Tiger 330ml' } },
            ],
          });
        }
        if (url === '/portal/catalog') {
          return success([
            { id: 101, sku: 'KEN330', name: 'Bia Heineken 330ml', baseUnit: 'lon', basePrice: 15000, categoryName: 'Bia', units: [{unitName:'lon',factor:1,price:15000},{unitName:'thùng',factor:24,price:360000}] },
            { id: 102, sku: 'TIGER330', name: 'Bia Tiger 330ml', baseUnit: 'lon', basePrice: 13000, categoryName: 'Bia', units: [{unitName:'lon',factor:1,price:13000}] },
          ]);
        }
        if (url.includes('/products?active=true')) {
          return success([
            { id: 101, sku: 'KEN330', name: 'Bia Heineken 330ml', baseUnit: 'lon', category: { name: 'Bia' } },
            { id: 102, sku: 'TIGER330', name: 'Bia Tiger 330ml', baseUnit: 'lon', category: { name: 'Bia' } },
            { id: 103, sku: 'COCA330', name: 'Coca Cola 330ml', baseUnit: 'lon', category: { name: 'Nước giải khát' } }, // NOT in price list!
          ]);
        }
        if (url.includes('/unit-conversions')) {
          return success([{ unitName: 'thùng', factor: 24 }]);
        }
        if (url === '/portal/orders' && opts.method === 'POST') {
          return success({
            id: 501,
            orderCode: 'DH-DL-20261014-001',
            status: 'PENDING_APPROVAL',
            totalAmount: 1800000,
          });
        }
        return success([]);
      },
    },
    Toast: { show() {} },
  });

  // Strict check: No localStorage or sessionStorage for business state
  for (const storage of ['localStorage', 'sessionStorage']) {
    Object.defineProperty(context, storage, {
      get() {
        throw new Error(`Không được lưu dữ liệu nghiệp vụ vào ${storage}`);
      },
    });
  }

  const script = fs.readFileSync(path.join(__dirname, '../frontend/js/dealer-portal.js'), 'utf8');
  vm.runInContext(script, context);

  return { context, elements, calls, portal: windowObj.DealerPortal };
}

test('S4-10: Cổng đại lý chỉ hiển thị sản phẩm và giá thuộc bảng giá nhóm khách hàng của mình', async () => {
  const { elements, calls } = createMockEnvironment();
  await new Promise((r) => setTimeout(r, 60));

  assert.ok(calls.some((call) => call.url === '/portal/catalog'));
  assert.ok(calls.every((call) => call.url.startsWith('/portal/')));

  // Verify dealer name and group displayed
  assert.equal(elements.get('info-dealer-name').textContent, 'Đại lý Hoàng Phát');
  assert.equal(elements.get('topbar-group-badge').textContent, 'Đại lý cấp 1');

  // Verify catalog count: only products 101 and 102 (Heineken, Tiger) are in price list; 103 (Coca) is omitted
  assert.equal(elements.get('dp-catalog-count').textContent, '2 sản phẩm');
});

test('S4-10: Thêm sản phẩm vào giỏ, xem tổng tiền và tính toán công nợ còn lại trước khi gửi đơn', async () => {
  const { elements, portal } = createMockEnvironment();
  await new Promise((r) => setTimeout(r, 60));

  // Verify debt metric values
  assert.match(elements.get('val-debt-limit').textContent, /100\.000\.000/);
  assert.match(elements.get('val-current-debt').textContent, /30\.000\.000/);
  assert.match(elements.get('val-avail-debt').textContent, /70\.000\.000/);

  // Add 5 thùng Heineken (unitPrice = 360,000đ; total = 1,800,000đ)
  portal.addToCart({ id: 101, sku: 'KEN330', name: 'Bia Heineken 330ml', basePrice: 15000 }, 'thùng', 24, 360000, 5);

  // Verify cart totals
  assert.equal(elements.get('cart-badge-count').textContent, 5);
  assert.match(elements.get('dp-subtotal-val').textContent, /1\.800\.000/);
  assert.match(elements.get('dp-total-val').textContent, /1\.800\.000/);

  // Verify projected remaining credit: 70,000,000 - 1,800,000 = 68,200,000 ₫
  assert.match(elements.get('dp-projected-remaining-debt').textContent, /68\.200\.000/);
  assert.equal(elements.get('dp-debt-warning-msg').hidden, true);
});

test('S4-10: Cảnh báo khi đơn hàng làm vượt hạn mức công nợ còn lại của đại lý', async () => {
  const { elements, portal } = createMockEnvironment();
  await new Promise((r) => setTimeout(r, 60));

  // Add huge quantity exceeding available credit (200 thùng = 72,000,000đ > 70,000,000đ)
  portal.addToCart({ id: 101, sku: 'KEN330', name: 'Bia Heineken 330ml', basePrice: 15000 }, 'thùng', 24, 360000, 200);

  assert.equal(elements.get('dp-debt-warning-msg').hidden, false);
  assert.match(elements.get('dp-debt-warning-msg').textContent, /vượt hạn mức công nợ còn lại/);
});

test('S4-10: Đơn gửi lên luôn ở trạng thái Chờ duyệt (PENDING_APPROVAL), gửi đúng payload và reset giỏ hàng', async () => {
  const { context, elements, calls, portal } = createMockEnvironment();
  await new Promise((r) => setTimeout(r, 60));

  const pointEl = context.document.getElementById('dp-delivery-point-select');
  pointEl.value = '1';
  const dateEl = context.document.getElementById('dp-delivery-date-input');
  dateEl.value = '2026-10-15';
  const noteEl = context.document.getElementById('dp-order-note');
  noteEl.value = 'Giao trước 8h sáng';

  // Add 2 thùng Heineken
  portal.addToCart({ id: 101, sku: 'KEN330', name: 'Bia Heineken 330ml', basePrice: 15000 }, 'thùng', 24, 360000, 2);

  // Submit dealer order
  await portal.submitDealerOrder();

  // Check POST /orders payload
  const postOrder = calls.find((c) => c.url === '/portal/orders' && c.method === 'POST');
  assert.ok(postOrder, 'Phải gửi POST /orders');
  assert.equal(postOrder.body.dealerId, undefined);
  assert.equal(postOrder.body.deliveryPointId, 1);
  assert.equal(postOrder.body.expectedDeliveryDate, '2026-10-15');
  assert.match(postOrder.body.note, /Đại lý tự đặt qua cổng/);
  assert.deepEqual(postOrder.body.items, [{ productId: 101, unit: 'thùng', quantity: 2 }]);

  // Check success modal shows order code and Chờ duyệt
  assert.equal(elements.get('dp-success-code').textContent, 'DH-DL-20261014-001');
  assert.equal(elements.get('dp-success-modal').hidden, false);
  assert.equal(elements.get('cart-badge-count').textContent, 0);
});
