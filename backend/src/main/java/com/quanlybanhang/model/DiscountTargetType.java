package com.quanlybanhang.model;

/**
 * Đối tượng áp dụng chính sách chiết khấu theo sản lượng (S3-01):
 * - PRODUCT: Áp dụng cho một SKU / sản phẩm cụ thể
 * - CATEGORY: Áp dụng cho một nhóm hàng (bao gồm cả các nhóm hàng con)
 */
public enum DiscountTargetType {
    PRODUCT,
    CATEGORY
}

