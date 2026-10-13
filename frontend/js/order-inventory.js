/**
 * order-inventory.js — Quản lý tồn khả dụng và kiểm soát vượt tồn (S4-03).
 *
 * Nghiệp vụ:
 * 1. Tồn khả dụng = Tồn thực tế − Tồn đang giữ chỗ cho các đơn hàng khác.
 * 2. Đơn vị quy đổi: Tồn khả dụng theo đơn vị đóng gói = floor(tồn cơ sở / hệ số quy đổi).
 * 3. Chặn đặt vượt tồn khả dụng, gợi ý số lượng tối đa còn đặt được.
 * 4. Kiểm tra tồn tức thời trước khi chốt đơn và xử lý xung đột đồng thời để tránh âm tồn.
 */
const OrderInventory = (() => {
  const HOLDING_ORDER_STATUSES = new Set(['RECORDED', 'PENDING_APPROVAL', 'APPROVED', 'PROCESSING']);

  /**
   * Tính tồn khả dụng theo đơn vị cơ sở.
   * @param {number|string} physicalBaseQty Tồn thực tế trong kho (base unit)
   * @param {number|string} reservedBaseQty Tồn đang giữ chỗ cho đơn khác (base unit)
   * @returns {number} Tồn khả dụng >= 0
   */
  function calculateAvailable(physicalBaseQty, reservedBaseQty) {
    const physical = Math.max(0, Number(physicalBaseQty) || 0);
    const reserved = Math.max(0, Number(reservedBaseQty) || 0);
    return Math.max(0, physical - reserved);
  }

  /**
   * Quy đổi số lượng tồn cơ sở sang đơn vị được chọn.
   * Số lượng đặt hàng theo đơn vị quy đổi phải là số nguyên không âm.
   * @param {number} baseQty Số lượng theo đơn vị cơ sở
   * @param {number} conversionFactor Hệ số quy đổi (mặc định 1)
   * @returns {number} Số đơn vị tối đa có thể đặt
   */
  function convertStockToUnit(baseQty, conversionFactor = 1) {
    const factor = conversionFactor !== undefined && conversionFactor !== null
      ? Number(conversionFactor)
      : 1;
    if (isNaN(factor) || factor <= 0) return 0;
    const available = Math.max(0, Number(baseQty) || 0);
    return Math.floor(available / factor);
  }

  /**
   * Tính tổng tồn giữ chỗ cho một sản phẩm từ danh sách các đơn hàng đang hoạt động.
   * @param {Array} orders Danh sách đơn hàng
   * @param {number|string} productId ID sản phẩm
   * @param {number|string|null} excludeOrderId ID đơn cần loại trừ (nếu đang sửa đơn)
   * @returns {number} Tổng số lượng giữ chỗ (theo đơn vị cơ sở)
   */
  function calculateReservedByProduct(orders, productId, excludeOrderId = null) {
    if (!Array.isArray(orders)) return 0;
    let total = 0;
    for (const order of orders) {
      if (excludeOrderId && String(order.id) === String(excludeOrderId)) continue;
      const status = String(order.status || '').toUpperCase();
      if (!HOLDING_ORDER_STATUSES.has(status)) continue;
      if (!Array.isArray(order.items)) continue;

      for (const item of order.items) {
        if (Number(item.productId) === Number(productId)) {
          const factor = Number(item.conversionFactor) || 1;
          const baseQty = item.baseQuantity != null
            ? Number(item.baseQuantity)
            : (Number(item.quantity) || 0) * factor;
          if (Number.isFinite(baseQty) && baseQty > 0) {
            total += baseQty;
          }
        }
      }
    }
    return total;
  }

  /**
   * Kiểm tra tính hợp lệ của số lượng đặt hàng so với tồn khả dụng.
   * @param {number} requestedQty Số lượng muốn đặt
   * @param {number} availableInUnit Số lượng tồn khả dụng theo đơn vị hiện tại
   * @returns {{ valid: boolean, maxAllowed: number, message: string }}
   */
  function validateStock(requestedQty, availableInUnit) {
    const qty = Number(requestedQty) || 0;
    const maxAllowed = Math.max(0, Number(availableInUnit) || 0);

    if (qty <= 0) {
      return {
        valid: false,
        maxAllowed,
        message: 'Số lượng phải lớn hơn 0.',
      };
    }

    if (maxAllowed === 0) {
      return {
        valid: false,
        maxAllowed: 0,
        message: 'Mặt hàng đã hết tồn khả dụng trong kho phục vụ.',
      };
    }

    if (qty > maxAllowed) {
      return {
        valid: false,
        maxAllowed,
        message: `Vượt tồn khả dụng. Số lượng tối đa còn đặt được: ${maxAllowed}.`,
      };
    }

    return {
      valid: true,
      maxAllowed,
      message: '',
    };
  }

  /**
   * Xác định kho phục vụ cho đại lý.
   * Ưu tiên:
   * 1. Kho được gắn trên đại lý hoặc điểm giao hàng
   * 2. Kho trong phạm vi phụ trách của nhân viên (user.warehouses)
   * 3. Kho đầu tiên đang hoạt động trong hệ thống
   */
  function resolveServingWarehouse(dealer, user, warehouseOptions = []) {
    if (!Array.isArray(warehouseOptions) || warehouseOptions.length === 0) {
      return null;
    }

    // 1. Kiểm tra kho gắn trực tiếp trên dealer
    if (dealer?.warehouseId) {
      const match = warehouseOptions.find((w) => Number(w.id) === Number(dealer.warehouseId));
      if (match) return match;
    }

    // 2. Kiểm tra kho theo phạm vi user đăng nhập
    if (Array.isArray(user?.warehouses) && user.warehouses.length > 0) {
      const userWarehouseIds = new Set(user.warehouses.map((w) => Number(w.id)));
      const match = warehouseOptions.find((w) => userWarehouseIds.has(Number(w.id)));
      if (match) return match;
    }

    // 3. Mặc định kho đầu tiên trong danh mục hoạt động
    return warehouseOptions[0] || null;
  }

  /**
   * Gọi API lấy danh sách kho hoạt động.
   */
  async function fetchWarehouseOptions() {
    if (typeof Auth === 'undefined' || !Auth.apiFetch) return [];
    try {
      const res = await Auth.apiFetch('/warehouse/options');
      const result = await res.json().catch(() => null);
      if (res.ok && result?.success && Array.isArray(result.data)) {
        return result.data;
      }
    } catch {
      // Fallback nếu không có quyền xem options
    }
    return [];
  }

  /**
   * Gọi API lấy dữ liệu tồn kho thực tế.
   */
  async function fetchWarehouseInventory(warehouseId) {
    if (typeof Auth === 'undefined' || !Auth.apiFetch) return [];
    try {
      const url = warehouseId
        ? `/warehouse/inventory?warehouseId=${encodeURIComponent(warehouseId)}`
        : '/warehouse/inventory';
      const res = await Auth.apiFetch(url);
      const result = await res.json().catch(() => null);
      if (res.ok && result?.success && Array.isArray(result.data)) {
        return result.data;
      }
    } catch {
      // Bỏ qua lỗi kết nối
    }
    return [];
  }

  /**
   * Gọi API lấy danh sách đơn hàng để tính lượng giữ chỗ.
   */
  async function fetchActiveOrders() {
    if (typeof Auth === 'undefined' || !Auth.apiFetch) return [];
    try {
      const res = await Auth.apiFetch('/orders');
      const result = await res.json().catch(() => null);
      if (res.ok && result?.success && Array.isArray(result.data)) {
        return result.data;
      }
    } catch {
      // Bỏ qua lỗi kết nối
    }
    return [];
  }

  /**
   * Tính toán bảng tổng hợp tồn kho (thực tế, giữ chỗ, khả dụng) cho tất cả sản phẩm trong kho.
   * @param {Array} inventoryList Dữ liệu tồn thực tế từ kho
   * @param {Array} ordersList Danh sách đơn hàng
   * @returns {Map<number, { physicalBase: number, reservedBase: number, availableBase: number, baseUnit: string }>}
   */
  function buildInventoryMap(inventoryList = [], ordersList = []) {
    const map = new Map();

    // 1. Nạp tồn thực tế
    if (Array.isArray(inventoryList)) {
      for (const item of inventoryList) {
        const pId = Number(item.productId);
        if (!pId) continue;
        const physical = Number(item.baseQuantity) || 0;
        const baseUnit = item.baseUnit || 'cái';
        map.set(pId, {
          physicalBase: physical,
          reservedBase: 0,
          availableBase: physical,
          baseUnit,
        });
      }
    }

    // 2. Tính tồn giữ chỗ từ các đơn hàng
    if (Array.isArray(ordersList)) {
      for (const order of ordersList) {
        const status = String(order.status || '').toUpperCase();
        if (!HOLDING_ORDER_STATUSES.has(status)) continue;
        if (!Array.isArray(order.items)) continue;

        for (const it of order.items) {
          const pId = Number(it.productId);
          if (!pId) continue;
          const factor = Number(it.conversionFactor) || 1;
          const baseQty = it.baseQuantity != null
            ? Number(it.baseQuantity)
            : (Number(it.quantity) || 0) * factor;

          if (Number.isFinite(baseQty) && baseQty > 0) {
            let record = map.get(pId);
            if (!record) {
              record = { physicalBase: 0, reservedBase: 0, availableBase: 0, baseUnit: it.baseUnit || 'cái' };
              map.set(pId, record);
            }
            record.reservedBase += baseQty;
          }
        }
      }
    }

    // 3. Tính tồn khả dụng = thực tế - giữ chỗ (tối thiểu 0)
    for (const record of map.values()) {
      record.availableBase = Math.max(0, record.physicalBase - record.reservedBase);
    }

    return map;
  }

  return {
    calculateAvailable,
    convertStockToUnit,
    calculateReservedByProduct,
    validateStock,
    resolveServingWarehouse,
    fetchWarehouseOptions,
    fetchWarehouseInventory,
    fetchActiveOrders,
    buildInventoryMap,
    HOLDING_ORDER_STATUSES,
  };
})();

if (typeof module !== 'undefined' && module.exports) {
  module.exports = OrderInventory;
}
