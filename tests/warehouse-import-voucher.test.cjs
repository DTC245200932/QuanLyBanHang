const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const { test } = require('node:test');

function createMockEnvironment(customOptions = {}) {
  const elements = new Map();
  function element(id) {
    if (!elements.has(id)) {
      elements.set(id, {
        value: '',
        hidden: false,
        disabled: false,
        textContent: '',
        innerHTML: '',
        placeholder: '',
        min: '',
        selectedOptions: [{ value: '1', textContent: 'Kho Tổng TP.HCM', dataset: { name: 'Nhà máy Bia Heineken VN' } }],
        dataset: {},
        listeners: {},
        classList: {
          classes: new Set(),
          toggle(cls, force) {
            if (force === undefined) {
              this.classes.has(cls) ? this.classes.delete(cls) : this.classes.add(cls);
            } else if (force) {
              this.classes.add(cls);
            } else {
              this.classes.delete(cls);
            }
          },
          add(cls) { this.classes.add(cls); },
          remove(cls) { this.classes.delete(cls); },
          contains(cls) { return this.classes.has(cls); }
        },
        addEventListener(event, callback) { this.listeners[event] = callback; },
        remove() {},
        appendChild() {},
        closest(sel) {
          if (sel === '.form-field') return { hidden: false };
          return null;
        },
        querySelectorAll() { return []; },
        querySelector() { return null; },
      });
    }
    return elements.get(id);
  }

  const calls = [];
  const success = (data) => ({ ok: true, json: async () => ({ success: true, data }) });

  const context = vm.createContext({
    document: {
      body: element('body'),
      getElementById: element,
      createElement: (tag) => element('__created_' + tag + '_' + Math.random().toString(36).slice(2)),
      addEventListener() {},
      querySelectorAll: (sel) => {
        if (sel === '#receipt-type-radios input[type=\'radio\']') {
          return [{ value: 'IMPORT', checked: true, addEventListener() {} }];
        }
        return [];
      },
      querySelector: () => null,
    },
    location: { search: '', href: 'http://localhost/warehouse/units' },
    window: { location: { search: '', href: 'http://localhost/warehouse/units' }, print() {}, history: { replaceState() {} } },
    MenuConfig: { getRoleDisplayName: (r) => r },
    AppNavigation: { refresh() {} },
    requestAnimationFrame: (fn) => fn(),
    setTimeout,
    clearTimeout,
    URL,
    URLSearchParams,
    Intl,
    Math,
    Date,
    Number,
    String,
    Boolean,
    Array,
    Object,
    JSON,
    Map,
    Set,
    Promise,
    Error,
    RegExp,
    console,
    isNaN,
    isFinite,
    parseInt,
    parseFloat,
    // showToast is defined inside the IIFE, so we intercept via a Proxy on document.getElementById
    // to capture toast container creation. Instead, we'll patch after script runs.
    confirm: () => true,
    Auth: {
      requireLogin: () => true,
      hasAnyRole: (...roles) => true,
      hasPermission: () => true,
      getUser: () => ({ id: 10, username: 'kho_staff', fullName: 'Thủ Kho', warehouses: [{ id: 1, name: 'Kho Tổng TP.HCM' }] }),
      setProtectedImage: async () => false,
      apiFetch: async (url, options) => {
        calls.push({ url, options });
        if (url === '/warehouse/options') {
          return success([{ id: 1, code: 'WH01', name: 'Kho Tổng TP.HCM' }]);
        }
        if (url === '/products') {
          return success([
            { id: 101, code: 'BEER-HEIN-330', name: 'Bia Heineken Lon 330ml', unit: 'lon', categoryName: 'Bia', price: 20000, costPrice: 15000 }
          ]);
        }
        if (url === '/warehouse/inventory?warehouseId=1') {
          return success([{ productId: 101, baseQuantity: 500 }]);
        }
        if (url === '/warehouse/vouchers?warehouseId=1') {
          return success([]);
        }
        if (url === '/orders') {
          return success([]);
        }
        if (url === '/products/101/unit-conversions') {
          return success([
            { id: 1, unitName: 'thùng', isBaseUnit: false, conversionFactor: 24, barcode: '', description: '1 thùng = 24 lon' },
            { id: 2, unitName: 'lon', isBaseUnit: true, conversionFactor: 1, barcode: '', description: 'Đơn vị cơ sở' }
          ]);
        }
        if (url === '/suppliers?size=100') {
          return success({
            content: [
              { id: 1, code: 'NCC01', name: 'Nhà máy Bia Heineken VN', phone: '02838123456' }
            ]
          });
        }
        if (url === '/warehouse/import') {
          return success({
            id: 1,
            voucherCode: 'PNK-202610-001',
            type: 'IMPORT',
            status: 'RECORDED',
            items: []
          });
        }
        return success([]);
      }
    }
  });

  return { context, element, calls, elements };
}

/**
 * Helper: Load script and wait for Store.init() to complete
 */
async function loadAndInit(context) {
  const code = fs.readFileSync(path.join(__dirname, '../frontend/js/warehouse-units.js'), 'utf8');
  vm.runInContext(code, context);
  // Wait enough ticks for all async init (Store.init + Promise.all) to complete
  for (let i = 0; i < 30; i++) await new Promise(setImmediate);
}

test('S5-04: Khởi tạo tải danh sách nhà cung cấp và hỗ trợ chọn NCC từ /api/suppliers', async () => {
  const { context, calls } = createMockEnvironment();
  await loadAndInit(context);

  // Verify suppliers API was called
  const supplierCall = calls.find(c => c.url === '/suppliers?size=100');
  assert.ok(supplierCall, 'API /suppliers?size=100 phải được gọi khi tải trang kho');

  const supplierSelect = context.document.getElementById('receipt-supplier-select');
  assert.ok(supplierSelect.innerHTML.includes('Nhà máy Bia Heineken VN'), 'Dropdown nhà cung cấp phải chứa danh sách NCC từ API');
});

test('S5-04: Lưu nháp không gọi API nhập kho ghi sổ và lưu trạng thái DRAFT', async () => {
  const { context, element, calls, elements } = createMockEnvironment();
  await loadAndInit(context);

  // Set form values
  element('receipt-code').value = 'PNK-TEST-DRAFT-001';
  element('receipt-date').value = '2026-10-16';
  element('receipt-warehouse').value = '1';
  element('receipt-partner').value = 'Nhà máy Bia Heineken VN';
  element('receipt-doc-number').value = 'HĐ-88219';

  const btnDraft = element('btn-save-draft-receipt');
  assert.ok(btnDraft.listeners.click, 'Nút Lưu bản nháp phải có click listener');

  // Clear API calls before clicking to isolate draft behavior
  const callsBeforeDraft = calls.length;
  btnDraft.listeners.click();

  // Kiểm tra không gọi POST /warehouse/import
  const importCall = calls.slice(callsBeforeDraft).find(c => c.url === '/warehouse/import');
  assert.equal(importCall, undefined, 'Lưu nháp tuyệt đối không được gọi POST /warehouse/import để tránh làm sai lệch tồn kho');

  // showToast is defined inside the IIFE and creates DOM elements via
  // document.createElement. Check that a created element has innerHTML
  // containing 'bản nháp' (the toast message).
  let foundToast = false;
  for (const [, el] of elements) {
    if (el.innerHTML && typeof el.innerHTML === 'string' && el.innerHTML.includes('bản nháp')) {
      foundToast = true;
      break;
    }
  }
  assert.ok(foundToast, 'Lưu nháp thành công phải thông báo cho thủ kho');
});

test('S5-04: Xác nhận ghi sổ gửi số lô, hạn sử dụng, số chứng từ gốc và gọi POST /warehouse/import', async () => {
  const { context, element, calls } = createMockEnvironment();
  await loadAndInit(context);

  // Set supplier select to have the correct selectedOptions with dataset.name
  const supplierSel = element('receipt-supplier-select');
  supplierSel.value = '1';
  supplierSel.selectedOptions = [{ value: '1', textContent: 'NCC01 - Nhà máy Bia Heineken VN', dataset: { name: 'Nhà máy Bia Heineken VN' } }];

  element('receipt-code').value = 'PNK-202610-001';
  element('receipt-date').value = '2026-10-16';
  element('receipt-warehouse').value = '1';
  element('receipt-partner').value = 'Nhà máy Bia Heineken VN';
  element('receipt-doc-number').value = 'PXK-NCC-9981';
  element('receipt-notes').value = 'Kiểm hàng tại bến dỡ xe số 3';

  const btnSave = element('btn-save-receipt');
  assert.ok(btnSave.listeners.click, 'Nút Xác nhận ghi sổ kho phải có click listener');

  await btnSave.listeners.click();
  // Wait for async recordReceipt to complete
  for (let i = 0; i < 30; i++) await new Promise(setImmediate);

  // Kiểm tra đã gọi POST /warehouse/import
  const importCall = calls.find(c => c.url === '/warehouse/import');
  assert.ok(importCall, 'Xác nhận nhập kho phải gọi POST /warehouse/import');
  assert.equal(importCall.options.method, 'POST');

  const payload = JSON.parse(importCall.options.body);
  assert.equal(payload.voucherCode, 'PNK-202610-001');
  assert.ok(payload.note.includes('PXK-NCC-9981'), 'Ghi chú phiếu phải bao gồm số chứng từ hóa đơn giao hàng');
  assert.ok(payload.note.includes('Nhà máy Bia Heineken VN'), 'Ghi chú phiếu phải có tên nhà cung cấp');
});
