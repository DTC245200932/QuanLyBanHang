package com.quanlybanhang.model;

/**
 * Trạng thái phiếu kiểm kê kho (S5-08).
 */
public enum StockCountStatus {
    DRAFT("Mới tạo"),
    COMPLETED("Đã chốt"),
    CANCELLED("Đã hủy");

    private final String displayName;

    StockCountStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
