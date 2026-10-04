package com.quanlybanhang.model;

/**
 * Nhóm khách hàng áp dụng bảng giá (S2-10):
 * - DAI_LY_CAP_1: Đại lý cấp 1
 * - DAI_LY_CAP_2: Đại lý cấp 2
 * - KHACH_LE: Khách lẻ
 */
public enum CustomerGroup {
    DAI_LY_CAP_1("Đại lý cấp 1"),
    DAI_LY_CAP_2("Đại lý cấp 2"),
    KHACH_LE("Khách lẻ");

    private final String displayName;

    CustomerGroup(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
