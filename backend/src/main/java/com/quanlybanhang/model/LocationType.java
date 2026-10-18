package com.quanlybanhang.model;

/**
 * Loại vị trí lưu kho (S5-03).
 * Khai báo ở mức khu (ZONE) hoặc kệ (SHELF).
 */
public enum LocationType {
    ZONE("Khu vực"),
    SHELF("Kệ"),
    AISLE("Dãy / Lối đi"),
    RACK("Giá kệ"),
    BIN("Ngăn / Ô");

    private final String displayName;

    LocationType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
