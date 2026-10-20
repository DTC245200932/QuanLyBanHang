/**
 * warehouse-stocktake.js — Nghiệp vụ kiểm kê kho và ghi nhận chênh lệch (S5-08).
 *
 * Nghiệp vụ cốt lõi:
 * 1. Tạo phiếu kiểm kê theo kho hoặc theo nhóm hàng: chốt số tồn sổ tại thời điểm tạo (Freeze Snapshot).
 * 2. Nhập số đếm thực tế: hệ thống tự động tính chênh lệch từng dòng (Khớp / Thừa / Thiếu).
 * 3. Chốt kiểm kê: điều chỉnh tồn kho về số thực đếm và BẮT BUỘC nhập lý do điều chỉnh.
 * 4. Phân quyền chặt chẽ (Permission Matrix):
 *    - ROLE_ADMIN luôn có toàn quyền.
 *    - ROLE_WAREHOUSE_MANAGER chỉ được chốt khi có quyền WAREHOUSE_ADJUST trong ma trận quyền.
 *    - Các vai trò khác (ROLE_WAREHOUSE_STAFF, ROLE_SALES...) không được chốt phiếu kiểm kê.
 */

(function (root, factory) {
  if (typeof module === "object" && module.exports) {
    module.exports = factory();
  } else {
    root.WarehouseStocktake = factory();
  }
})(typeof self !== "undefined" ? self : this, function () {
  "use strict";

  /**
   * Tính toán chênh lệch giữa số lượng thực tế đếm và số tồn sổ sách đã chốt
   * @param {number|string} actualQty - Số lượng kiểm đếm thực tế
   * @param {number|string} bookQty - Số lượng tồn sổ sách đã chốt
   * @returns {{ diff: number, status: 'MATCH'|'SURPLUS'|'DEFICIT', label: string }}
   */
  function calculateDifference(actualQty, bookQty) {
    const actual = Number(actualQty) || 0;
    const book = Number(bookQty) || 0;
    const diff = actual - book;

    let status = "MATCH";
    let label = "0";

    if (diff > 0) {
      status = "SURPLUS";
      label = `+${diff}`;
    } else if (diff < 0) {
      status = "DEFICIT";
      label = `${diff}`;
    }

    return { diff, status, label };
  }

  /**
   * Tổng hợp các chỉ số kiểm đếm (Tổng dòng, Khớp, Thừa, Thiếu)
   * @param {Array} items - Danh sách mặt hàng kiểm kê
   * @returns {{ total: number, match: number, surplus: number, deficit: number }}
   */
  function calculateMetrics(items) {
    if (!Array.isArray(items) || items.length === 0) {
      return { total: 0, match: 0, surplus: 0, deficit: 0 };
    }

    let match = 0;
    let surplus = 0;
    let deficit = 0;

    for (const item of items) {
      const actual = Number(item.actualQuantity) || 0;
      const book = Number(item.bookQuantity) || 0;
      const diff = actual - book;
      if (diff === 0) {
        match++;
      } else if (diff > 0) {
        surplus++;
      } else {
        deficit++;
      }
    }

    return {
      total: items.length,
      match,
      surplus,
      deficit,
    };
  }

  /**
   * Chốt số tồn sổ sách (Freeze Book Stock) tại thời điểm khởi tạo phiếu kiểm kê
   * @param {Array} products - Danh sách sản phẩm từ hệ thống kho
   * @param {string} scope - Phạm vi kiểm kê ('ALL' hoặc tên nhóm hàng cụ thể)
   * @returns {Array} Danh sách chi tiết kiểm kê đã chốt tồn sổ
   */
  function freezeBookStock(products, scope = "ALL") {
    if (!Array.isArray(products)) return [];

    const filtered = (scope === "ALL" || !scope)
      ? products
      : products.filter((p) => p.category === scope);

    return filtered.map((p) => {
      const bookQty = Number(p.inventoryBaseQty) || 0;
      return {
        productId: p.id,
        productCode: p.sku,
        productName: p.name,
        category: p.category || "Chưa phân nhóm",
        baseUnit: p.baseUnit || "Đơn vị",
        bookQuantity: bookQty,
        actualQuantity: bookQty, // Mặc định ban đầu bằng tồn sổ sách
        difference: 0,
        diffStatus: "MATCH",
        notes: "",
      };
    });
  }

  /**
   * Kiểm tra quyền chốt phiếu kiểm kê theo Ma trận phân quyền (Database-backed Permission Matrix)
   * Quy tắc S5-08 & AGENTS.md:
   * - ROLE_ADMIN luôn có toàn quyền.
   * - ROLE_WAREHOUSE_MANAGER phải có quyền WAREHOUSE_ADJUST.
   * - Thu hồi WAREHOUSE_ADJUST khỏi vai trò phải chặn quyền ngay lập tức.
   * - ROLE_SALES, ROLE_WAREHOUSE_STAFF... không được phép chốt phiếu.
   * @param {object} user - Đối tượng thông tin người dùng từ Auth
   * @returns {boolean}
   */
  function canUserConfirmStocktake(user) {
    if (!user) return false;

    const roles = Array.isArray(user.roles) ? user.roles : [];
    const permissions = Array.isArray(user.permissions) ? user.permissions : [];

    // ROLE_ADMIN luôn được phép
    if (roles.includes("ROLE_ADMIN")) {
      return true;
    }

    // ROLE_WAREHOUSE_MANAGER yêu cầu quyền WAREHOUSE_ADJUST trong Permission Matrix
    const isWarehouseManager = roles.includes("ROLE_WAREHOUSE_MANAGER");
    const hasAdjustPermission = permissions.includes("WAREHOUSE_ADJUST");

    if (isWarehouseManager && hasAdjustPermission) {
      return true;
    }

    // Tất cả trường hợp khác không được phép
    return false;
  }

  /**
   * Xác thực điều kiện chốt phiếu kiểm kê
   * @param {object} params
   * @param {object} params.stocktake - Phiếu kiểm kê hiện tại
   * @param {string} params.reason - Lý do điều chỉnh tồn kho
   * @param {object} params.user - Người dùng hiện tại
   * @returns {{ valid: boolean, error?: string }}
   */
  function validateStocktakeConfirmation({ stocktake, reason, user }) {
    if (!canUserConfirmStocktake(user)) {
      return {
        valid: false,
        error: "Chỉ Quản lý kho có quyền WAREHOUSE_ADJUST mới được chốt phiếu kiểm kê và điều chỉnh tồn kho.",
      };
    }

    if (!stocktake || !Array.isArray(stocktake.items) || stocktake.items.length === 0) {
      return {
        valid: false,
        error: "Phiếu kiểm kê không có mặt hàng nào để chốt.",
      };
    }

    const trimmedReason = (reason || "").trim();
    if (!trimmedReason) {
      return {
        valid: false,
        error: "Vui lòng nhập lý do kiểm kê & điều chỉnh tồn kho (bắt buộc khi chốt phiếu).",
      };
    }

    for (const item of stocktake.items) {
      const actual = Number(item.actualQuantity);
      if (isNaN(actual) || actual < 0) {
        return {
          valid: false,
          error: `Số lượng thực tế của mặt hàng "${item.productName || item.productCode}" không hợp lệ (phải >= 0).`,
        };
      }
    }

    return { valid: true };
  }

  /**
   * Tạo payload gửi lên API điều chỉnh kho: POST /api/warehouse/adjust
   * Đưa tồn kho về số thực tế đếm và lưu lý do điều chỉnh
   * @param {object} params
   * @param {object} params.stocktake - Phiếu kiểm kê
   * @param {string} params.reason - Lý do điều chỉnh
   * @param {number|string} params.warehouseId - ID kho
   * @returns {object}
   */
  function buildAdjustmentPayload({ stocktake, reason, warehouseId }) {
    const trimmedReason = (reason || "").trim();
    const scopeLabel = stocktake.scopeName || (stocktake.scope === "ALL" ? "Toàn bộ kho" : stocktake.scope) || "Toàn kho";

    return {
      voucherCode: stocktake.code,
      warehouseId: warehouseId || stocktake.warehouseId || null,
      adjustmentReason: trimmedReason,
      note: `Phiếu kiểm kê ${stocktake.code} (${scopeLabel}) — ${trimmedReason}`,
      items: stocktake.items.map((item) => ({
        productId: item.productId,
        productCode: item.productCode,
        productName: item.productName,
        unit: item.baseUnit,
        quantity: Number(item.actualQuantity) || 0,
        note: item.notes || (item.difference !== 0 ? `Lệch: ${item.difference > 0 ? "+" : ""}${item.difference} ${item.baseUnit}` : "Khớp"),
      })),
    };
  }

  /**
   * Sinh mã phiếu kiểm kê định dạng PKK-YYYYMM-XXX
   * @param {number} count - Số thứ tự hiện tại
   * @returns {string}
   */
  function generateStocktakeCode(count = 1) {
    const ym = new Date().toISOString().slice(0, 7).replace("-", "");
    return `PKK-${ym}-${String(count).padStart(3, "0")}`;
  }

  return {
    calculateDifference,
    calculateMetrics,
    freezeBookStock,
    canUserConfirmStocktake,
    validateStocktakeConfirmation,
    buildAdjustmentPayload,
    generateStocktakeCode,
  };
});

