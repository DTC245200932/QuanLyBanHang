const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const {test} = require('node:test');

function setup(responder) {
  const elements = new Map();
  function element(id) {
    if (!elements.has(id)) {
      const events = new Map();
      const classes = new Set();
      elements.set(id, {
        id, hidden: true, disabled: false, textContent: '', innerHTML: '', value: '', style: {},
        classList: {add: (...values) => values.forEach(v => classes.add(v)), remove: value => classes.delete(value),
          toggle: (value, force) => force ? classes.add(value) : classes.delete(value)},
        addEventListener: (name, callback) => events.set(name, callback),
        focus() {}, click() {return events.get('click')?.({target: this});},
        async selectFile(file) {return events.get('change')?.({target: {files: [file]}});},
      });
    }
    return elements.get(id);
  }
  const calls = [];
  const document = {readyState: 'complete', body: {style: {}}, activeElement: null,
    getElementById: element, querySelectorAll: () => [], addEventListener() {}};
  const context = vm.createContext({document, window: {}, console,
    FormData: class {constructor() {this.values = new Map();} append(key, value) {this.values.set(key, value);}},
    Auth: {hasAnyRole: () => true, apiFetch: async (url, options) => {calls.push({url, options}); return responder(url, options);}},
  });
  for (const key of ['localStorage','sessionStorage']) {
    Object.defineProperty(context, key, {get() {throw new Error('Do not store business data in browser storage');}});
  }
  vm.runInContext(fs.readFileSync(path.join(__dirname, '../frontend/js/products-import.js'), 'utf8'), context);
  return {element, calls};
}
const file = name => ({name, size: 1024});
const response = (data, success = true, message = '') => ({ok: success, json: async () => ({success, data, message})});
const preview = {totalRows: 2, validRows: 1, invalidRows: 1, createCount: 0, updateCount: 1, rows: [
  {rowNumber: 2, sku: 'EXISTING', action: 'UPDATE', valid: true, errors: []},
  {rowNumber: 3, sku: 'INVALID', valid: false, errors: ['Giá bán không hợp lệ']},
]};

test('Excel preview labels existing SKU as update and limits import to valid rows', async () => {
  const {element} = setup(() => response(preview));
  element('btn-import-excel').click();
  await element('import-file-input').selectFile(file('catalog.xlsx'));
  assert.equal(element('import-step-preview').hidden, false);
  assert.match(element('import-preview-body').innerHTML, /Cập nhật/);
  assert.match(element('import-preview-body').innerHTML, /Giá bán không hợp lệ/);
  assert.equal(element('btn-start-import').textContent, 'Nhập dữ liệu (1 dòng)');
});

test('failed second preview cannot submit the previous file or unreviewed replacement', async () => {
  let count = 0;
  const {element, calls} = setup(() => ++count === 1 ? response(preview) : response(null, false, 'Không đọc được tệp'));
  element('btn-import-excel').click();
  await element('import-file-input').selectFile(file('first.xlsx'));
  await element('import-file-input').selectFile(file('broken.xlsx'));
  assert.equal(element('import-step-upload').hidden, false);
  assert.equal(element('btn-start-import').disabled, true);
  assert.match(element('import-upload-message').textContent, /Không đọc được/);
  await element('btn-start-import').click();
  assert.equal(calls.filter(call => call.url === '/products/import').length, 0);
});

test('late preview result cannot replace a newly selected file', async () => {
  let finishFirst;
  let count = 0;
  const {element} = setup(() => ++count === 1 ? new Promise(resolve => {finishFirst = resolve;}) : response(preview));
  element('btn-import-excel').click();
  const first = element('import-file-input').selectFile(file('first.xlsx'));
  await element('import-file-input').selectFile(file('second.xlsx'));
  finishFirst(response({totalRows: 0, rows: []}));
  await first;
  assert.equal(element('import-preview-file').textContent, 'second.xlsx');
  assert.equal(element('btn-start-import').textContent, 'Nhập dữ liệu (1 dòng)');
});

test('closing while preview is pending allows a fresh import after reopening', async () => {
  let finish;
  const {element} = setup(() => new Promise(resolve => {finish = resolve;}));
  element('btn-import-excel').click();
  const pending = element('import-file-input').selectFile(file('first.xlsx'));
  assert.equal(element('import-dropzone').disabled, true);
  element('btn-close-import').click();
  element('btn-import-excel').click();
  assert.equal(element('import-dropzone').disabled, false);
  finish(response(preview));
  await pending;
  assert.equal(element('import-step-upload').hidden, false);
  assert.equal(element('btn-start-import').disabled, true);
});

test('import sends the reviewed Excel file and shows partial import summary', async () => {
  const excel = file('catalog.xlsx');
  const {element, calls} = setup(url => response(url.endsWith('/preview') ? preview : {
    totalRows: 2, importedRows: 1, invalidRows: 1, createCount: 0, updateCount: 1,
  }));
  element('btn-import-excel').click();
  await element('import-file-input').selectFile(excel);
  await element('btn-start-import').click();
  await new Promise(setImmediate);
  const request = calls.find(call => call.url === '/products/import');
  assert.equal(request.options.body.values.get('file'), excel);
  assert.equal(element('import-step-done').hidden, false);
  assert.match(element('import-done-summary').innerHTML, /1 \/ 2 dòng/);
  assert.match(element('import-done-summary').innerHTML, /1 bỏ qua/);
});
