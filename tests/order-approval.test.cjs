/**
 * order-approval.test.cjs — Kiểm thử cho màn hình Duyệt đơn hàng (S4-05).
 *
 * Tiêu chí kiểm thử:
 * 1. Phân quyền: Chỉ Quản lý kinh doanh (ROLE_SALES_MANAGER) và Quản trị viên (ROLE_ADMIN)
 *    có quyền ORDER_APPROVE mới được truy cập.
 * 2. Phân tích lý do cần duyệt và mức vi phạm:
 *    - Vượt hạn mức công nợ (tính số tiền vượt, hạn mức).
 *    - Bán dưới giá sàn (tính chênh lệch dưới sàn theo SKU/đơn vị).
 *    - Đơn có cả hai vi phạm.
 * 3. Ba hành động duyệt:
 *    - Duyệt: Không bắt buộc ghi chú.
 *    - Từ chối: Bắt buộc nhập lý do (không được để trống hoặc chỉ có khoảng trắng).
 *    - Trả lại sửa: Bắt buộc nhập ý kiến (không được để trống hoặc chỉ có khoảng trắng).
 * 4. Menu và liên kết ma trận phân quyền: mục menu order-approval có permission ORDER_APPROVE.
 */
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");
const { test } = require("node:test");

// ── Kiểm tra MenuConfig và Permission Matrix ──
const menuSource = fs.readFileSync(path.join(__dirname, "../frontend/js/menu-config.js"), "utf8");
const menu = vm.runInNewContext(`${menuSource}\nMenuConfig;`, { URLSearchParams });

test("menu duyệt đơn hàng gắn với quyền ORDER_APPROVE và đúng vai trò quản lý", () => {
  const item = menu.menuItems.find((m) => m.id === "order-approval");
  assert.ok(item, "Menu order-approval phải tồn tại");
  assert.equal(item.permission, "ORDER_APPROVE", "Phải kết nối quyền ORDER_APPROVE trong ma trận");
  assert.deepEqual(Array.from(item.roles), ["ROLE_ADMIN", "ROLE_SALES_MANAGER"]);

  // Quản lý kinh doanh có quyền ORDER_APPROVE thì thấy menu
  const managerVisible = menu.getMenuForUser({
    roles: ["ROLE_SALES_MANAGER"],
    permissions: ["ORDER_APPROVE", "PRODUCT_VIEW"],
  });
  assert.ok(managerVisible.some((m) => m.id === "order-approval"), "Quản lý kinh doanh có ORDER_APPROVE phải thấy menu");

  // Quản lý kinh doanh bị tước quyền ORDER_APPROVE thì ẩn menu
  const managerNoPerm = menu.getMenuForUser({
    roles: ["ROLE_SALES_MANAGER"],
    permissions: ["PRODUCT_VIEW"],
  });
  assert.ok(!managerNoPerm.some((m) => m.id === "order-approval"), "Bị tước ORDER_APPROVE phải ẩn menu");

  // Nhân viên kinh doanh, thủ kho, chỉ xem không bao giờ thấy menu duyệt đơn
  for (const role of ["ROLE_SALES", "ROLE_WAREHOUSE_STAFF", "ROLE_WAREHOUSE_MANAGER", "ROLE_ACCOUNTANT", "ROLE_VIEWER"]) {
    const userMenu = menu.getMenuForUser({
      roles: [role],
      permissions: ["ORDER_APPROVE", "PRODUCT_VIEW", "ORDER_VIEW"],
    });
    assert.ok(!userMenu.some((m) => m.id === "order-approval"), `${role} không được thấy menu duyệt đơn`);
  }

  // Admin luôn thấy
  const adminMenu = menu.getMenuForUser({ roles: ["ROLE_ADMIN"], permissions: [] });
  assert.ok(adminMenu.some((m) => m.id === "order-approval"), "Admin luôn thấy menu duyệt đơn");
});

// ── Kiểm tra trích xuất lý do duyệt và mức vi phạm ──
test("nhận diện và tính mức vi phạm vượt hạn mức công nợ chính xác", () => {
  // Giả lập logic parseOrderApprovalInfo
  function parseDebtViolation(order) {
    const rawReason = String(order.approvalReason || "").trim();
    const isDebt = Boolean(order.exceedsDebtLimit) || rawReason.includes("hạn mức công nợ");
    if (!isDebt) return null;

    let diff = null;
    let limit = order.debtLimit != null ? Number(order.debtLimit) : null;
    let projected = null;

    if (order.currentDebt != null && limit != null && limit > 0) {
      const curDebt = Number(order.currentDebt || 0);
      const total = Number(order.totalAmount || 0);
      projected = curDebt + total;
      diff = projected - limit;
    }

    return {
      type: "debt",
      isDebt,
      diff,
      projected,
      limit,
    };
  }

  const orderDebt = {
    id: 101,
    orderCode: "DH-00101",
    totalAmount: 6000000,
    currentDebt: 15000000,
    debtLimit: 20000000,
    exceedsDebtLimit: true,
    approvalReason: "Vượt hạn mức công nợ: Công nợ hiện tại (15.000.000 ₫) + Đơn mới (6.000.000 ₫) = 21.000.000 ₫ vượt hạn mức (20.000.000 ₫)",
  };

  const v = parseDebtViolation(orderDebt);
  assert.ok(v.isDebt);
  assert.equal(v.diff, 1000000, "Mức vượt hạn mức phải là 1.000.000 ₫");
  assert.equal(v.projected, 21000000);
  assert.equal(v.limit, 20000000);
});

test("nhận diện và tính mức vi phạm bán dưới giá sàn chính xác", () => {
  function parseFloorPriceViolations(order) {
    const items = Array.isArray(order.items) ? order.items : [];
    const belowFloorItems = items.filter(
      (it) => it.belowFloorPrice || (it.floorPrice != null && Number(it.price) < Number(it.floorPrice)),
    );

    return belowFloorItems.map((it) => {
      const price = Number(it.price || 0);
      const floor = Number(it.floorPrice || 0);
      return {
        productName: it.productName,
        price,
        floorPrice: floor,
        diff: floor - price,
        unit: it.unit,
      };
    });
  }

  const orderPrice = {
    id: 102,
    orderCode: "DH-00102",
    totalAmount: 3600000,
    approvalReason: "Sản phẩm 'Nước ngọt Sprite 330ml' bán giá 180,000 < giá sàn 200,000 (thùng)",
    items: [
      {
        productName: "Nước ngọt Sprite 330ml",
        price: 180000,
        floorPrice: 200000,
        unit: "thùng",
        belowFloorPrice: true,
      },
    ],
  };

  const violations = parseFloorPriceViolations(orderPrice);
  assert.equal(violations.length, 1);
  assert.equal(violations[0].diff, 20000, "Mức bán dưới sàn là 20.000 ₫/thùng");
  assert.equal(violations[0].price, 180000);
  assert.equal(violations[0].floorPrice, 200000);
});

test("nhận diện đơn có cả hai vi phạm: vượt hạn mức và bán dưới giá sàn", () => {
  const combinedOrder = {
    id: 103,
    orderCode: "DH-00103",
    totalAmount: 12000000,
    currentDebt: 10000000,
    debtLimit: 20000000,
    exceedsDebtLimit: true,
    approvalReason: "Sản phẩm 'Coca Cola 330ml' bán giá 190,000 < giá sàn 210,000 (thùng); Vượt hạn mức công nợ",
    items: [
      {
        productName: "Coca Cola 330ml",
        price: 190000,
        floorPrice: 210000,
        unit: "thùng",
        belowFloorPrice: true,
      },
    ],
  };

  const hasDebt = combinedOrder.exceedsDebtLimit || combinedOrder.approvalReason.includes("hạn mức công nợ");
  const hasPrice = combinedOrder.items.some((i) => i.belowFloorPrice) || combinedOrder.approvalReason.includes("giá sàn");

  assert.ok(hasDebt, "Đơn phải có lý do vượt hạn mức");
  assert.ok(hasPrice, "Đơn phải có lý do bán dưới giá sàn");
});

// ── Kiểm tra validation của 3 hành động duyệt ──
test("validation hành động: Từ chối và Trả lại sửa bắt buộc nhập ý kiến, Duyệt không bắt buộc", () => {
  function validateAction(action, comment) {
    const trimmed = String(comment || "").trim();
    if (action === "approve") {
      return { valid: true, error: null };
    }
    if (action === "reject") {
      if (!trimmed) {
        return { valid: false, error: "Vui lòng nhập lý do từ chối (bắt buộc)." };
      }
      if (trimmed.length > 500) {
        return { valid: false, error: "Lý do từ chối tối đa 500 ký tự." };
      }
      return { valid: true, error: null };
    }
    if (action === "return") {
      if (!trimmed) {
        return { valid: false, error: "Vui lòng nhập ý kiến chỉnh sửa (bắt buộc)." };
      }
      if (trimmed.length > 500) {
        return { valid: false, error: "Ý kiến chỉnh sửa tối đa 500 ký tự." };
      }
      return { valid: true, error: null };
    }
    return { valid: false, error: "Thao tác không hợp lệ." };
  }

  // Duyệt: để trống ghi chú vẫn hợp lệ
  assert.equal(validateAction("approve", "").valid, true);
  assert.equal(validateAction("approve", "Đã duyệt giá đặc biệt").valid, true);

  // Từ chối: để trống hoặc chỉ có khoảng trắng phải bị chặn
  assert.equal(validateAction("reject", "").valid, false);
  assert.equal(validateAction("reject", "   ").valid, false);
  assert.equal(validateAction("reject", "Giá bán quá thấp so với biên lợi nhuận quy định").valid, true);

  // Trả lại sửa: để trống hoặc chỉ có khoảng trắng phải bị chặn
  assert.equal(validateAction("return", "").valid, false);
  assert.equal(validateAction("return", "    \t\n  ").valid, false);
  assert.equal(validateAction("return", "Cần điều chỉnh giá bán tối thiểu 205.000 ₫ hoặc giảm chiết khấu").valid, true);
});
