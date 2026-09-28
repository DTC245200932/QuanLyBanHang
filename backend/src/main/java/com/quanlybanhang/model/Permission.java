package com.quanlybanhang.model;

/**
 * Enum định nghĩa tất cả quyền (permission) trong hệ thống.
 *
 * Nhóm theo module nghiệp vụ:
 * - USER_*: quản lý tài khoản
 * - PRODUCT_*: quản lý sản phẩm
 * - ORDER_*: quản lý đơn hàng
 * - WAREHOUSE_*: quản lý kho, nhập/xuất
 * - CUSTOMER_*: quản lý khách hàng
 * - FINANCE_*: tài chính, công nợ, giá vốn
 * - REPORT_*: xem báo cáo
 */
public enum Permission {

    // ── Quản lý tài khoản ──
    USER_VIEW,
    USER_CREATE,
    USER_EDIT,
    USER_LOCK,
    USER_ASSIGN_ROLE,

    // ── Sản phẩm ──
    PRODUCT_VIEW,
    PRODUCT_CREATE,
    PRODUCT_EDIT,
    PRODUCT_DELETE,

    // ── Đơn hàng ──
    ORDER_VIEW,
    ORDER_CREATE,
    ORDER_EDIT,
    ORDER_CANCEL,
    ORDER_APPROVE,

    // ── Kho & tồn kho ──
    WAREHOUSE_VIEW,
    WAREHOUSE_IMPORT,        // Nhập kho
    WAREHOUSE_EXPORT,        // Xuất kho
    WAREHOUSE_ADJUST,        // Điều chỉnh tồn kho
    WAREHOUSE_APPROVE,       // Phê duyệt phiếu nhập/xuất

    // ── Khách hàng / Đại lý ──
    CUSTOMER_VIEW,
    CUSTOMER_CREATE,
    CUSTOMER_EDIT,

    // ── Tài chính (nhạy cảm) ──
    FINANCE_VIEW_COST_PRICE,      // Xem giá vốn — chỉ SALES_MANAGER
    FINANCE_VIEW_PROFIT_MARGIN,   // Xem biên lợi nhuận — chỉ SALES_MANAGER
    FINANCE_VIEW_DEBT,            // Xem công nợ
    FINANCE_MANAGE,               // Quản lý tài chính

    // ── Báo cáo ──
    REPORT_SALES,
    REPORT_INVENTORY,
    REPORT_FINANCE
}
