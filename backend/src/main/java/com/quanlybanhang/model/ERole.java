package com.quanlybanhang.model;

/**
 * Enum định nghĩa các vai trò (role) trong hệ thống.
 */
public enum ERole {
    ROLE_ADMIN,       // Quản trị viên - toàn quyền
    ROLE_MANAGER,     // Quản lý - quản lý nhân viên, sản phẩm, đơn hàng
    ROLE_EMPLOYEE     // Nhân viên bán hàng - bán hàng, xem sản phẩm
}
