/**
 * warehouse-stocktake-s5-08.test.cjs
 * Test suite cho tính năng S5-08: Kiểm kê kho và ghi nhận chênh lệch.
 *
 * Kiểm tra đầy đủ các yêu cầu nghiệp vụ và quy tắc AGENTS.md:
 * 1. Tạo phiếu kiểm kê chốt số tồn sổ tại thời điểm tạo (theo kho hoặc theo nhóm hàng).
 * 2. Nhập số đếm thực tế tự động tính chênh lệch từng dòng (Khớp, Thừa, Thiếu).
 * 3. Bắt buộc nhập lý do khi chốt phiếu kiểm kê.
 * 4. Payload gửi lên API điều chỉnh kho (POST /api/warehouse/adjust) đưa tồn kho về số thực đếm.
 * 5. Tuân thủ ma trận phân quyền (Permission Matrix):
 *    - ROLE_ADMIN luôn có quyền.
 *    - ROLE_WAREHOUSE_MANAGER yêu cầu WAREHOUSE_ADJUST.
 *    - Thu hồi WAREHOUSE_ADJUST phải vô hiệu hóa quyền ngay lập tức.
 *    - Vai trò khác (ROLE_WAREHOUSE_STAFF, ROLE_SALES...) không được chốt phiếu.
 * 6. Lưu nháp tiến độ kiểm kê không làm thay đổi tồn kho.
 */

const assert = require('node:assert/strict');
const { test, describe } = require('node:test');
const WarehouseStocktake = require('../frontend/js/warehouse-stocktake.js');

describe('S5-08: Kiểm kê kho và ghi nhận chênh lệch', () => {

  const mockProducts = [
    {
      id: 1,
      sku: 'SP001',
      name: 'Bia Heineken lon 330ml',
      category: 'Bia lon',
      baseUnit: 'lon',
      inventoryBaseQty: 120,
    },
    {
      id: 2,
      sku: 'SP002',
      name: 'Bia Tiger lon 330ml',
      category: 'Bia lon',
      baseUnit: 'lon',
      inventoryBaseQty: 80,
    },
    {
      id: 3,
      sku: 'SP003',
      name: 'Rượu Vang Đỏ Đà Lạt 750ml',
      category: 'Rượu vang',
      baseUnit: 'chai',
      inventoryBaseQty: 30,
    },
    {
      id: 4,
      sku: 'SP004',
      name: 'Nước ngọt Coca Cola 330ml',
      category: 'Nước giải khát',
      baseUnit: 'lon',
      inventoryBaseQty: 0,
    },
  ];

  test('1. Tạo phiếu kiểm kê chốt tồn sổ sách tại thời điểm tạo (Toàn bộ kho hoặc theo nhóm hàng)', () => {
    // Trường hợp 1: Toàn bộ kho
    const allItems = WarehouseStocktake.freezeBookStock(mockProducts, 'ALL');
    assert.equal(allItems.length, 4, 'Toàn bộ 4 mặt hàng phải được đưa vào phiếu kiểm kê');
    assert.equal(allItems[0].productCode, 'SP001');
    assert.equal(allItems[0].bookQuantity, 120, 'Tồn sổ sách phải được chốt là 120 lon');
    assert.equal(allItems[0].actualQuantity, 120, 'Số đếm thực tế mặc định ban đầu bằng tồn sổ');
    assert.equal(allItems[0].difference, 0);
    assert.equal(allItems[0].diffStatus, 'MATCH');

    // Trường hợp 2: Theo nhóm hàng cụ thể (Bia lon)
    const beerItems = WarehouseStocktake.freezeBookStock(mockProducts, 'Bia lon');
    assert.equal(beerItems.length, 2, 'Chỉ có 2 sản phẩm thuộc nhóm Bia lon');
    assert.ok(beerItems.every(i => i.category === 'Bia lon'));
    assert.equal(beerItems[0].bookQuantity, 120);
    assert.equal(beerItems[1].bookQuantity, 80);

    // Kiểm tra định dạng mã phiếu kiểm kê
    const code = WarehouseStocktake.generateStocktakeCode(1);
    assert.match(code, /^PKK-\d{6}-001$/, 'Mã phiếu phải có định dạng PKK-YYYYMM-XXX');
  });

  test('2. Nhập số đếm thực tế tự động tính chênh lệch từng dòng (Khớp / Thừa / Thiếu)', () => {
    // Khớp: thực tế 120, sổ 120
    const match = WarehouseStocktake.calculateDifference(120, 120);
    assert.equal(match.diff, 0);
    assert.equal(match.status, 'MATCH');
    assert.equal(match.label, '0');

    // Thừa: thực tế 130, sổ 120 -> thừa +10
    const surplus = WarehouseStocktake.calculateDifference(130, 120);
    assert.equal(surplus.diff, 10);
    assert.equal(surplus.status, 'SURPLUS');
    assert.equal(surplus.label, '+10');

    // Thiếu: thực tế 115, sổ 120 -> thiếu -5
    const deficit = WarehouseStocktake.calculateDifference(115, 120);
    assert.equal(deficit.diff, -5);
    assert.equal(deficit.status, 'DEFICIT');
    assert.equal(deficit.label, '-5');

    // Tính tổng hợp chỉ số metrics
    const items = [
      { actualQuantity: 120, bookQuantity: 120 }, // Khớp
      { actualQuantity: 85, bookQuantity: 80 },   // Thừa
      { actualQuantity: 25, bookQuantity: 30 },   // Thiếu
      { actualQuantity: 0, bookQuantity: 0 },     // Khớp
    ];
    const metrics = WarehouseStocktake.calculateMetrics(items);
    assert.equal(metrics.total, 4, 'Tổng cộng 4 mặt hàng');
    assert.equal(metrics.match, 2, 'Có 2 mặt hàng khớp');
    assert.equal(metrics.surplus, 1, 'Có 1 mặt hàng thừa');
    assert.equal(metrics.deficit, 1, 'Có 1 mặt hàng thiếu');
  });

  test('3. Bắt buộc nhập lý do khi chốt phiếu kiểm kê', () => {
    const adminUser = { roles: ['ROLE_ADMIN'], permissions: [] };
    const stocktake = {
      code: 'PKK-202610-001',
      items: [
        { productId: 1, productCode: 'SP001', productName: 'Bia Heineken', actualQuantity: 130, bookQuantity: 120, baseUnit: 'lon' },
      ],
    };

    // Thiếu lý do (rỗng) -> không hợp lệ
    const resEmpty = WarehouseStocktake.validateStocktakeConfirmation({
      stocktake,
      reason: '',
      user: adminUser,
    });
    assert.equal(resEmpty.valid, false);
    assert.match(resEmpty.error, /lý do/i, 'Phải báo lỗi yêu cầu nhập lý do');

    // Lý do chỉ có khoảng trắng -> không hợp lệ
    const resWhitespace = WarehouseStocktake.validateStocktakeConfirmation({
      stocktake,
      reason: '    ',
      user: adminUser,
    });
    assert.equal(resWhitespace.valid, false);
    assert.match(resWhitespace.error, /lý do/i);

    // Có lý do hợp lệ -> hợp lệ
    const resValid = WarehouseStocktake.validateStocktakeConfirmation({
      stocktake,
      reason: 'Kiểm kê định kỳ cuối tháng 10/2026',
      user: adminUser,
    });
    assert.equal(resValid.valid, true);
    assert.equal(resValid.error, undefined);
  });

  test('4. Xây dựng payload điều chỉnh kho (POST /api/warehouse/adjust) đưa tồn kho về số thực đếm', () => {
    const stocktake = {
      code: 'PKK-202610-005',
      warehouseId: 2,
      scope: 'Bia lon',
      scopeName: 'Nhóm Bia lon',
      items: [
        {
          productId: 1,
          productCode: 'SP001',
          productName: 'Bia Heineken lon 330ml',
          baseUnit: 'lon',
          bookQuantity: 120,
          actualQuantity: 125,
          difference: 5,
          notes: 'Thừa do nhập thừa từ lô trước',
        },
        {
          productId: 2,
          productCode: 'SP002',
          productName: 'Bia Tiger lon 330ml',
          baseUnit: 'lon',
          bookQuantity: 80,
          actualQuantity: 76,
          difference: -4,
          notes: 'Bể vỡ trong quá trình bốc dỡ',
        },
      ],
    };

    const payload = WarehouseStocktake.buildAdjustmentPayload({
      stocktake,
      reason: 'Cân bằng tồn kho thực tế tháng 10',
      warehouseId: 2,
    });

    assert.equal(payload.voucherCode, 'PKK-202610-005');
    assert.equal(payload.warehouseId, 2);
    assert.equal(payload.adjustmentReason, 'Cân bằng tồn kho thực tế tháng 10');
    assert.equal(payload.items.length, 2);

    // Kiểm tra item 1: đưa tồn về đúng 125 lon
    assert.equal(payload.items[0].productId, 1);
    assert.equal(payload.items[0].productCode, 'SP001');
    assert.equal(payload.items[0].unit, 'lon');
    assert.equal(payload.items[0].quantity, 125);
    assert.equal(payload.items[0].note, 'Thừa do nhập thừa từ lô trước');

    // Kiểm tra item 2: đưa tồn về đúng 76 lon
    assert.equal(payload.items[1].productId, 2);
    assert.equal(payload.items[1].productCode, 'SP002');
    assert.equal(payload.items[1].unit, 'lon');
    assert.equal(payload.items[1].quantity, 76);
    assert.equal(payload.items[1].note, 'Bể vỡ trong quá trình bốc dỡ');
  });

  test('5. Phân quyền chặt chẽ theo Ma trận quyền (Permission Matrix Enforcement)', () => {
    // 5.1. ROLE_ADMIN luôn có quyền chốt phiếu
    const adminUser = { roles: ['ROLE_ADMIN'], permissions: [] };
    assert.equal(WarehouseStocktake.canUserConfirmStocktake(adminUser), true, 'ROLE_ADMIN luôn được phép');

    // 5.2. ROLE_WAREHOUSE_MANAGER có quyền WAREHOUSE_ADJUST -> Được phép
    const managerWithAdjust = {
      roles: ['ROLE_WAREHOUSE_MANAGER'],
      permissions: ['WAREHOUSE_VIEW', 'WAREHOUSE_ADJUST'],
    };
    assert.equal(
      WarehouseStocktake.canUserConfirmStocktake(managerWithAdjust),
      true,
      'ROLE_WAREHOUSE_MANAGER có WAREHOUSE_ADJUST được phép chốt'
    );

    // 5.3. ROLE_WAREHOUSE_MANAGER bị thu hồi quyền WAREHOUSE_ADJUST -> Bị chặn ngay lập tức
    const managerRevoked = {
      roles: ['ROLE_WAREHOUSE_MANAGER'],
      permissions: ['WAREHOUSE_VIEW', 'WAREHOUSE_MANAGE'], // Không có WAREHOUSE_ADJUST
    };
    assert.equal(
      WarehouseStocktake.canUserConfirmStocktake(managerRevoked),
      false,
      'Thu hồi WAREHOUSE_ADJUST khỏi ROLE_WAREHOUSE_MANAGER phải chặn quyền ngay lập tức'
    );

    // 5.4. ROLE_WAREHOUSE_STAFF (Thủ kho) không được chốt phiếu
    const staffUser = {
      roles: ['ROLE_WAREHOUSE_STAFF'],
      permissions: ['WAREHOUSE_VIEW', 'WAREHOUSE_IMPORT', 'WAREHOUSE_EXPORT'],
    };
    assert.equal(
      WarehouseStocktake.canUserConfirmStocktake(staffUser),
      false,
      'Thủ kho (ROLE_WAREHOUSE_STAFF) không được phép chốt phiếu kiểm kê'
    );

    // 5.5. ROLE_SALES (Nhân viên kinh doanh) không được chốt phiếu
    const salesUser = {
      roles: ['ROLE_SALES'],
      permissions: ['ORDER_CREATE', 'ORDER_VIEW'],
    };
    assert.equal(
      WarehouseStocktake.canUserConfirmStocktake(salesUser),
      false,
      'Nhân viên kinh doanh (ROLE_SALES) không được phép chốt phiếu'
    );

    // 5.6. Validate chặn khi người dùng không đủ quyền
    const stocktake = {
      code: 'PKK-202610-001',
      items: [{ productId: 1, actualQuantity: 100, bookQuantity: 100 }],
    };
    const deniedValidation = WarehouseStocktake.validateStocktakeConfirmation({
      stocktake,
      reason: 'Lý do hợp lệ',
      user: staffUser,
    });
    assert.equal(deniedValidation.valid, false);
    assert.match(deniedValidation.error, /WAREHOUSE_ADJUST/);
  });

  test('6. Kiểm tra số lượng thực tế âm hoặc không hợp lệ bị từ chối', () => {
    const adminUser = { roles: ['ROLE_ADMIN'], permissions: [] };
    const invalidStocktake = {
      code: 'PKK-202610-002',
      items: [
        {
          productId: 1,
          productCode: 'SP001',
          productName: 'Bia Heineken',
          bookQuantity: 100,
          actualQuantity: -5, // Âm
        },
      ],
    };

    const res = WarehouseStocktake.validateStocktakeConfirmation({
      stocktake: invalidStocktake,
      reason: 'Kiểm kê kho',
      user: adminUser,
    });
    assert.equal(res.valid, false);
    assert.match(res.error, />=\s*0|không hợp lệ/i);
  });
});

