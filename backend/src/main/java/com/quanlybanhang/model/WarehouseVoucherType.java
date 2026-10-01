package com.quanlybanhang.model;

/**
 * Loại phiếu kho (S2-07):
 * - IMPORT: Phiếu nhập kho (tăng tồn kho theo số lượng cơ sở)
 * - EXPORT: Phiếu xuất kho (giảm tồn kho theo số lượng cơ sở)
 * - ADJUSTMENT: Phiếu điều chỉnh tồn kho (cập nhật tồn kho theo số lượng cơ sở)
 */
public enum WarehouseVoucherType {
    IMPORT,
    EXPORT,
    ADJUSTMENT
}
