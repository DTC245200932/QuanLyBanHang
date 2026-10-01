package com.quanlybanhang.controller;

import com.quanlybanhang.dto.response.ApiResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Controller Quản lý Kho & Tồn kho.
 *
 * Quy tắc nghiệp vụ cốt lõi:
 * - Nhân viên kinh doanh (ROLE_SALES) và Thủ kho (ROLE_WAREHOUSE_STAFF)
 *   KHÔNG ĐƯỢC PHÉP điều chỉnh tồn kho (/api/warehouse/adjust).
 *   Chỉ Quản lý kho (ROLE_WAREHOUSE_MANAGER) và Quản trị viên (ROLE_ADMIN) được điều chỉnh.
 * - Thủ kho (ROLE_WAREHOUSE_STAFF) được phép nhập/xuất kho (/api/warehouse/import, /api/warehouse/export)
 *   và xem tồn kho (/api/warehouse/inventory).
 */
@RestController
@RequestMapping("/api/warehouse")
public class WarehouseController {

    /**
     * API điều chỉnh tồn kho — CHỈ Quản lý kho (ROLE_WAREHOUSE_MANAGER) và Quản trị viên.
     * Đảm bảo Nhân viên kinh doanh (ROLE_SALES) và Thủ kho (ROLE_WAREHOUSE_STAFF) không sửa được tồn kho!
     */
    @PostMapping("/adjust")
    @PreAuthorize("hasAnyRole('WAREHOUSE_MANAGER', 'ADMIN')")
    public ApiResponse adjustInventory(@RequestBody(required = false) Map<String, Object> body) {
        return ApiResponse.success("Điều chỉnh tồn kho thành công", body != null ? body : Map.of("status", "ADJUSTED"));
    }

    /**
     * API nhập kho — Thủ kho, Quản lý kho, Quản trị viên.
     */
    @PostMapping("/import")
    @PreAuthorize("hasAnyRole('WAREHOUSE_STAFF', 'WAREHOUSE_MANAGER', 'ADMIN')")
    public ApiResponse importGoods(@RequestBody(required = false) Map<String, Object> body) {
        return ApiResponse.success("Tạo phiếu nhập kho thành công", body != null ? body : Map.of("status", "IMPORTED"));
    }

    /**
     * API xuất kho — Thủ kho, Quản lý kho, Quản trị viên.
     */
    @PostMapping("/export")
    @PreAuthorize("hasAnyRole('WAREHOUSE_STAFF', 'WAREHOUSE_MANAGER', 'ADMIN')")
    public ApiResponse exportGoods(@RequestBody(required = false) Map<String, Object> body) {
        return ApiResponse.success("Tạo phiếu xuất kho thành công", body != null ? body : Map.of("status", "EXPORTED"));
    }

    /**
     * API xem danh sách tồn kho — Thủ kho, Quản lý kho, Quản lý KD, Admin, Viewer.
     */
    @GetMapping("/inventory")
    @PreAuthorize("hasAnyRole('WAREHOUSE_STAFF', 'WAREHOUSE_MANAGER', 'SALES_MANAGER', 'ADMIN', 'VIEWER')")
    public ApiResponse getInventory() {
        List<Map<String, Object>> stock = List.of(
                Map.of("productCode", "SP001", "productName", "Sản phẩm A", "quantity", 150),
                Map.of("productCode", "SP002", "productName", "Sản phẩm B", "quantity", 80)
        );
        return ApiResponse.success("Danh sách tồn kho", stock);
    }
}
