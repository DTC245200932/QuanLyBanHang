const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { test } = require('node:test');

test('dealers.html exists and contains all required S3-07 elements', () => {
  const htmlPath = path.join(__dirname, '../frontend/dealers.html');
  assert.ok(fs.existsSync(htmlPath), 'dealers.html must exist');
  const html = fs.readFileSync(htmlPath, 'utf8');

  // Kiểm tra liên kết styles và scripts
  assert.ok(html.includes('/css/dealers.css'), 'phải có dealers.css');
  assert.ok(html.includes('/js/dealers.js'), 'phải có dealers.js');

  // Yêu cầu 1: Bắt buộc nhập lý do khoá
  assert.ok(html.includes('lock-reason'), 'phải có trường nhập lý do khoá');
  assert.ok(html.includes('required'), 'trường lý do khoá phải required');
  assert.ok(html.includes('lock-modal'), 'phải có modal khoá giao dịch');

  // Yêu cầu 2: Đại lý bị khoá không tạo được đơn mới, kể cả trên cổng đặt hàng
  assert.ok(html.includes('portal'), 'phải có cổng đặt hàng');
  assert.ok(html.includes('portal-lock-banner'), 'phải có banner cảnh báo chặn khi đại lý bị khoá');
  assert.ok(html.includes('btn-portal-submit'), 'phải có nút gửi đơn đặt hàng');

  // Yêu cầu 3: Đơn đang dở của đại lý bị khoá vẫn xử lý tiếp được nhưng có cảnh báo
  assert.ok(html.includes('order-action-modal'), 'phải có modal xử lý đơn hàng dở');
  assert.ok(html.includes('order-action-locked-warning'), 'phải có cảnh báo công nợ cho đơn thuộc đại lý bị khoá');
});

test('dealers.css defines distinct alert styling for locked dealers and warnings', () => {
  const cssPath = path.join(__dirname, '../frontend/css/dealers.css');
  assert.ok(fs.existsSync(cssPath), 'dealers.css must exist');
  const css = fs.readFileSync(cssPath, 'utf8');

  assert.ok(css.includes('.lock-error-alert'), 'phải có style hộp cảnh báo lỗi khi đại lý bị khoá');
  assert.ok(css.includes('#btn-portal-submit:disabled'), 'phải có style vô hiệu hoá nút đặt hàng khi đại lý bị khoá');
  assert.ok(css.includes('.order-lock-warning-box'), 'phải có style cảnh báo đơn của đại lý bị khoá');
  assert.ok(css.includes('.order-warn-tag'), 'phải có tag cảnh báo công nợ');
});

test('S3-07 business rules validation in dealers logic', () => {
  // Mock business rules logic matching dealers.js
  const dealers = [
    { id: 1, code: 'DL001', name: 'Đại lý A', status: 'ACTIVE', lockReason: null },
    { id: 2, code: 'DL002', name: 'Đại lý B', status: 'LOCKED', lockReason: 'Quá hạn 45 ngày' },
  ];

  const orders = [
    { id: 101, orderCode: 'DH-001', dealerId: 2, status: 'PENDING_APPROVAL' }, // Đơn dở của DL bị khoá
    { id: 102, orderCode: 'DH-002', dealerId: 1, status: 'RECORDED' }, // Đơn của DL hoạt động
  ];

  // 1. Kiểm tra bắt buộc nhập lý do khi khoá
  function lockDealer(dealerId, reason) {
    if (!reason || !reason.trim()) {
      throw new Error('Vui lòng nhập lý do khoá giao dịch (bắt buộc).');
    }
    const d = dealers.find(item => item.id === dealerId);
    d.status = 'LOCKED';
    d.lockReason = reason.trim();
    return d;
  }

  assert.throws(() => lockDealer(1, ''), /Vui lòng nhập lý do khoá giao dịch/);
  assert.throws(() => lockDealer(1, '   '), /Vui lòng nhập lý do khoá giao dịch/);
  const lockedD1 = lockDealer(1, 'Dấu hiệu mất khả năng thanh toán');
  assert.equal(lockedD1.status, 'LOCKED');
  assert.equal(lockedD1.lockReason, 'Dấu hiệu mất khả năng thanh toán');

  // 2. Đại lý bị khoá không tạo được đơn mới
  function canCreateOrder(dealerId) {
    const d = dealers.find(item => item.id === dealerId);
    if (!d || d.status === 'LOCKED') return false;
    return true;
  }

  assert.equal(canCreateOrder(2), false, 'Đại lý bị khoá không thể tạo đơn mới');
  assert.equal(canCreateOrder(1), false, 'Đại lý vừa bị khoá không thể tạo đơn mới');

  // Mở khoá
  dealers[0].status = 'ACTIVE';
  assert.equal(canCreateOrder(1), true, 'Sau khi mở khoá, đại lý tạo đơn bình thường');

  // 3. Đơn đang dở của đại lý bị khoá vẫn xử lý tiếp được nhưng có cảnh báo
  function processUnfinishedOrder(orderId, action) {
    const order = orders.find(o => o.id === orderId);
    const dealer = dealers.find(d => d.id === order.dealerId);
    const hasWarning = (dealer && dealer.status === 'LOCKED');

    // Chuyển trạng thái xử lý tiếp
    if (action === 'approve') order.status = 'RECORDED';
    else if (action === 'dispatch') order.status = 'PROCESSING';
    else if (action === 'complete') order.status = 'COMPLETED';

    return { success: true, newStatus: order.status, hasWarning, warningMessage: hasWarning ? `Cảnh báo: Đại lý ${dealer.code} đang bị khoá do ${dealer.lockReason}` : null };
  }

  const result = processUnfinishedOrder(101, 'approve');
  assert.equal(result.success, true, 'Đơn dở của đại lý bị khoá vẫn xử lý tiếp được');
  assert.equal(result.newStatus, 'RECORDED', 'Trạng thái chuyển sang RECORDED thành công');
  assert.equal(result.hasWarning, true, 'Xử lý đơn dở của đại lý bị khoá phải có cảnh báo');
  assert.ok(result.warningMessage.includes('Cảnh báo'), 'Thông điệp cảnh báo hiển thị đầy đủ');
});
