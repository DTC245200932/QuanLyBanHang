package com.quanlybanhang.model;

/** S5-05: availability states, not the minimum-stock alerts of S5-09. */
public enum StockStatus {
    IN_STOCK("Còn khả dụng"), OUT_OF_STOCK("Hết tồn"), FULLY_RESERVED("Đã giữ hết");
    private final String label;
    StockStatus(String label) { this.label = label; }
    public String getLabel() { return label; }
}
