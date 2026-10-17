package com.quanlybanhang.controller;

import com.quanlybanhang.dto.request.ProductLocationRequest;
import com.quanlybanhang.dto.request.WarehouseLocationRequest;
import com.quanlybanhang.dto.request.WarehouseRequest;
import com.quanlybanhang.dto.request.WarehouseVoucherRequest;
import com.quanlybanhang.dto.response.*;
import com.quanlybanhang.model.LocationType;
import com.quanlybanhang.model.WarehouseVoucherType;
import com.quanlybanhang.service.WarehouseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

/**
 * Controller Quản lý Kho, Vị trí lưu kho & Tồn kho (S1-05, S2-07 & S5-03).
 *
 * S5-03:
 * - Khai báo nhiều kho: mã, tên, địa chỉ, người phụ trách, trạng thái
 * - Khai báo vị trí lưu trong kho ở mức kệ hoặc khu (ZONE, SHELF)
 * - Gán sản phẩm vào vị trí kệ để biết chính xác hàng nằm ở kệ nào khi soạn đơn
 */
@RestController
@RequestMapping("/api/warehouse")
@RequiredArgsConstructor
public class WarehouseController {

    private final WarehouseService warehouseService;

    // ═══════════════════════════════════════════════════════════════
    // 1. DANH SÁCH OPTIONS NHANH
    // ═══════════════════════════════════════════════════════════════

    @GetMapping("/options")
    @PreAuthorize("hasAnyRole('WAREHOUSE_STAFF', 'WAREHOUSE_MANAGER', 'SALES_MANAGER', 'SALES', 'ADMIN', 'VIEWER')")
    public ApiResponse warehouseOptions() {
        return ApiResponse.success("Danh sách kho đang hoạt động", warehouseService.getWarehouseOptions());
    }

    // ═══════════════════════════════════════════════════════════════
    // 2. KHAI BÁO & QUẢN LÝ NHIỀU KHO (S5-03)
    // ═══════════════════════════════════════════════════════════════

    @GetMapping("/warehouses")
    @PreAuthorize("hasAuthority('WAREHOUSE_VIEW') or hasAuthority('WAREHOUSE_MANAGE') or hasAnyRole('WAREHOUSE_STAFF', 'WAREHOUSE_MANAGER', 'SALES_MANAGER', 'SALES', 'ADMIN', 'VIEWER')")
    public ApiResponse getAllWarehouses(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Boolean active) {
        List<WarehouseResponse> warehouses = warehouseService.getAllWarehouses(keyword, active);
        return ApiResponse.success("Lấy danh sách kho thành công", warehouses);
    }

    @GetMapping("/warehouses/{id}")
    @PreAuthorize("hasAuthority('WAREHOUSE_VIEW') or hasAuthority('WAREHOUSE_MANAGE') or hasAnyRole('WAREHOUSE_STAFF', 'WAREHOUSE_MANAGER', 'SALES_MANAGER', 'SALES', 'ADMIN', 'VIEWER')")
    public ApiResponse getWarehouseById(@PathVariable Long id) {
        WarehouseResponse warehouse = warehouseService.getWarehouseById(id);
        return ApiResponse.success("Lấy thông tin chi tiết kho thành công", warehouse);
    }

    @PostMapping("/warehouses")
    @PreAuthorize("hasAuthority('WAREHOUSE_MANAGE') or hasAnyRole('WAREHOUSE_MANAGER', 'ADMIN')")
    public ApiResponse createWarehouse(
            @Valid @RequestBody WarehouseRequest request,
            Principal principal) {
        String username = principal != null ? principal.getName() : "system";
        WarehouseResponse warehouse = warehouseService.createWarehouse(request, username);
        return ApiResponse.success("Khai báo kho mới thành công", warehouse);
    }

    @PutMapping("/warehouses/{id}")
    @PreAuthorize("hasAuthority('WAREHOUSE_MANAGE') or hasAnyRole('WAREHOUSE_MANAGER', 'ADMIN')")
    public ApiResponse updateWarehouse(
            @PathVariable Long id,
            @Valid @RequestBody WarehouseRequest request,
            Principal principal) {
        String username = principal != null ? principal.getName() : "system";
        WarehouseResponse warehouse = warehouseService.updateWarehouse(id, request, username);
        return ApiResponse.success("Cập nhật thông tin kho thành công", warehouse);
    }

    @DeleteMapping("/warehouses/{id}")
    @PreAuthorize("hasAuthority('WAREHOUSE_MANAGE') or hasAnyRole('WAREHOUSE_MANAGER', 'ADMIN')")
    public ApiResponse deleteWarehouse(@PathVariable Long id, Principal principal) {
        String username = principal != null ? principal.getName() : "system";
        warehouseService.deleteWarehouse(id, username);
        return ApiResponse.success("Xóa/ngừng hoạt động kho thành công", null);
    }

    // ═══════════════════════════════════════════════════════════════
    // 3. KHAI BÁO VỊ TRÍ LƯU TRONG KHO: MỨC KỆ HOẶC KHU (S5-03)
    // ═══════════════════════════════════════════════════════════════

    @GetMapping("/locations")
    @PreAuthorize("hasAuthority('WAREHOUSE_VIEW') or hasAuthority('WAREHOUSE_MANAGE') or hasAnyRole('WAREHOUSE_STAFF', 'WAREHOUSE_MANAGER', 'SALES_MANAGER', 'SALES', 'ADMIN', 'VIEWER')")
    public ApiResponse getLocations(
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) LocationType type,
            @RequestParam(required = false) Long parentId,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) String keyword) {
        List<WarehouseLocationResponse> locations = warehouseService.getLocations(warehouseId, type, parentId, active, keyword);
        return ApiResponse.success("Lấy danh sách vị trí lưu kho thành công", locations);
    }

    @GetMapping("/warehouses/{warehouseId}/locations")
    @PreAuthorize("hasAuthority('WAREHOUSE_VIEW') or hasAuthority('WAREHOUSE_MANAGE') or hasAnyRole('WAREHOUSE_STAFF', 'WAREHOUSE_MANAGER', 'SALES_MANAGER', 'SALES', 'ADMIN', 'VIEWER')")
    public ApiResponse getLocationsByWarehouse(
            @PathVariable Long warehouseId,
            @RequestParam(required = false) LocationType type,
            @RequestParam(required = false) Long parentId,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) String keyword) {
        List<WarehouseLocationResponse> locations = warehouseService.getLocations(warehouseId, type, parentId, active, keyword);
        return ApiResponse.success("Lấy danh sách vị trí kho thành công", locations);
    }

    @GetMapping("/locations/{id}")
    @PreAuthorize("hasAuthority('WAREHOUSE_VIEW') or hasAuthority('WAREHOUSE_MANAGE') or hasAnyRole('WAREHOUSE_STAFF', 'WAREHOUSE_MANAGER', 'SALES_MANAGER', 'SALES', 'ADMIN', 'VIEWER')")
    public ApiResponse getLocationById(@PathVariable Long id) {
        WarehouseLocationResponse location = warehouseService.getLocationById(id);
        return ApiResponse.success("Lấy chi tiết vị trí lưu kho thành công", location);
    }

    @PostMapping("/locations")
    @PreAuthorize("hasAuthority('WAREHOUSE_MANAGE') or hasAnyRole('WAREHOUSE_MANAGER', 'ADMIN')")
    public ApiResponse createLocation(
            @Valid @RequestBody WarehouseLocationRequest request,
            Principal principal) {
        String username = principal != null ? principal.getName() : "system";
        WarehouseLocationResponse location = warehouseService.createLocation(request.getWarehouseId(), request, username);
        return ApiResponse.success("Khai báo vị trí lưu kho thành công", location);
    }

    @PostMapping("/warehouses/{warehouseId}/locations")
    @PreAuthorize("hasAuthority('WAREHOUSE_MANAGE') or hasAnyRole('WAREHOUSE_MANAGER', 'ADMIN')")
    public ApiResponse createLocationForWarehouse(
            @PathVariable Long warehouseId,
            @Valid @RequestBody WarehouseLocationRequest request,
            Principal principal) {
        String username = principal != null ? principal.getName() : "system";
        WarehouseLocationResponse location = warehouseService.createLocation(warehouseId, request, username);
        return ApiResponse.success("Khai báo vị trí lưu kho thành công", location);
    }

    @PutMapping("/locations/{id}")
    @PreAuthorize("hasAuthority('WAREHOUSE_MANAGE') or hasAnyRole('WAREHOUSE_MANAGER', 'ADMIN')")
    public ApiResponse updateLocation(
            @PathVariable Long id,
            @Valid @RequestBody WarehouseLocationRequest request,
            Principal principal) {
        String username = principal != null ? principal.getName() : "system";
        WarehouseLocationResponse location = warehouseService.updateLocation(id, request, username);
        return ApiResponse.success("Cập nhật vị trí lưu kho thành công", location);
    }

    @DeleteMapping("/locations/{id}")
    @PreAuthorize("hasAuthority('WAREHOUSE_MANAGE') or hasAnyRole('WAREHOUSE_MANAGER', 'ADMIN')")
    public ApiResponse deleteLocation(@PathVariable Long id, Principal principal) {
        String username = principal != null ? principal.getName() : "system";
        warehouseService.deleteLocation(id, username);
        return ApiResponse.success("Xóa vị trí lưu kho thành công", null);
    }

    // ═══════════════════════════════════════════════════════════════
    // 4. GÁN SẢN PHẨM VÀO KỆ / KHU (S5-03)
    // ═══════════════════════════════════════════════════════════════

    @PostMapping("/locations/{locationId}/products")
    @PreAuthorize("hasAuthority('WAREHOUSE_MANAGE') or hasAnyRole('WAREHOUSE_MANAGER', 'ADMIN')")
    public ApiResponse assignProductToLocation(
            @PathVariable Long locationId,
            @Valid @RequestBody ProductLocationRequest request,
            Principal principal) {
        String username = principal != null ? principal.getName() : "system";
        ProductLocationResponse response = warehouseService.assignProductToLocation(locationId, request, username);
        return ApiResponse.success("Gán sản phẩm vào vị trí kệ thành công", response);
    }

    @DeleteMapping("/locations/{locationId}/products/{productId}")
    @PreAuthorize("hasAuthority('WAREHOUSE_MANAGE') or hasAnyRole('WAREHOUSE_MANAGER', 'ADMIN')")
    public ApiResponse removeProductFromLocation(
            @PathVariable Long locationId,
            @PathVariable Long productId,
            Principal principal) {
        String username = principal != null ? principal.getName() : "system";
        warehouseService.removeProductFromLocation(locationId, productId, username);
        return ApiResponse.success("Bỏ gán sản phẩm khỏi vị trí kệ thành công", null);
    }

    @GetMapping("/locations/{locationId}/products")
    @PreAuthorize("hasAuthority('WAREHOUSE_VIEW') or hasAuthority('WAREHOUSE_MANAGE') or hasAnyRole('WAREHOUSE_STAFF', 'WAREHOUSE_MANAGER', 'SALES_MANAGER', 'SALES', 'ADMIN', 'VIEWER')")
    public ApiResponse getProductsByLocation(@PathVariable Long locationId) {
        List<ProductLocationResponse> list = warehouseService.getProductsByLocation(locationId);
        return ApiResponse.success("Danh sách sản phẩm lưu tại vị trí", list);
    }

    @GetMapping("/products/{productId}/locations")
    @PreAuthorize("hasAuthority('WAREHOUSE_VIEW') or hasAuthority('WAREHOUSE_MANAGE') or hasAnyRole('WAREHOUSE_STAFF', 'WAREHOUSE_MANAGER', 'SALES_MANAGER', 'SALES', 'ADMIN', 'VIEWER')")
    public ApiResponse getLocationsByProduct(
            @PathVariable Long productId,
            @RequestParam(required = false) Long warehouseId) {
        List<ProductLocationResponse> list = warehouseService.getLocationsByProduct(productId, warehouseId);
        return ApiResponse.success("Vị trí lưu kho của sản phẩm", list);
    }

    @GetMapping("/warehouses/{warehouseId}/products/{productId}/location")
    @PreAuthorize("hasAuthority('WAREHOUSE_VIEW') or hasAuthority('WAREHOUSE_MANAGE') or hasAnyRole('WAREHOUSE_STAFF', 'WAREHOUSE_MANAGER', 'SALES_MANAGER', 'SALES', 'ADMIN', 'VIEWER')")
    public ApiResponse getProductLocationInWarehouse(
            @PathVariable Long warehouseId,
            @PathVariable Long productId) {
        ProductLocationResponse response = warehouseService.getProductLocationInWarehouse(warehouseId, productId);
        return ApiResponse.success("Vị trí kệ chính xác của sản phẩm trong kho", response);
    }

    // ═══════════════════════════════════════════════════════════════
    // 5. DANH SÁCH SOẠN ĐƠN HÀNG KÈM VỊ TRÍ KỆ CHÍNH XÁC (S5-03)
    // ═══════════════════════════════════════════════════════════════

    @GetMapping("/orders/{orderId}/picking-list")
    @PreAuthorize("hasAuthority('WAREHOUSE_VIEW') or hasAuthority('ORDER_VIEW') or hasAnyRole('WAREHOUSE_STAFF', 'WAREHOUSE_MANAGER', 'SALES_MANAGER', 'ADMIN', 'VIEWER')")
    public ApiResponse getOrderPickingList(@PathVariable Long orderId) {
        OrderPickingListResponse pickingList = warehouseService.getOrderPickingList(orderId);
        return ApiResponse.success("Danh sách vị trí kệ soạn hàng theo đơn", pickingList);
    }

    // ═══════════════════════════════════════════════════════════════
    // 6. NHẬP / XUẤT / ĐIỀU CHỈNH / TỒN KHO (S1-05, S2-07, S4-03)
    // ═══════════════════════════════════════════════════════════════

    /**
     * API điều chỉnh tồn kho — CHỈ Quản lý kho (ROLE_WAREHOUSE_MANAGER) và Quản trị viên.
     * Đảm bảo Nhân viên kinh doanh (ROLE_SALES) và Thủ kho (ROLE_WAREHOUSE_STAFF) không sửa được tồn kho!
     */
    @PostMapping("/adjust")
    @PreAuthorize("hasAnyRole('WAREHOUSE_MANAGER', 'ADMIN')")
    public ApiResponse adjustInventory(
            @Valid @RequestBody WarehouseVoucherRequest request,
            Principal principal) {
        String username = principal != null ? principal.getName() : "system";
        WarehouseVoucherResponse response = warehouseService.adjustInventory(request, username);
        return ApiResponse.success("Điều chỉnh tồn kho thành công", response);
    }

    /**
     * API nhập kho — Thủ kho, Quản lý kho, Quản trị viên.
     * Tự động quy đổi từ đơn vị nhập (thùng, lốc) về đơn vị cơ sở (lon) khi ghi sổ.
     */
    @PostMapping("/import")
    @PreAuthorize("hasAnyRole('WAREHOUSE_STAFF', 'WAREHOUSE_MANAGER', 'ADMIN')")
    public ApiResponse importGoods(
            @Valid @RequestBody WarehouseVoucherRequest request,
            Principal principal) {
        String username = principal != null ? principal.getName() : "system";
        WarehouseVoucherResponse response = warehouseService.importGoods(request, username);
        return ApiResponse.success("Tạo phiếu nhập kho thành công", response);
    }

    /**
     * API xuất kho — Thủ kho, Quản lý kho, Quản trị viên.
     * Tự động quy đổi từ đơn vị xuất (thùng, lốc) về đơn vị cơ sở (lon) khi ghi sổ.
     */
    @PostMapping("/export")
    @PreAuthorize("hasAnyRole('WAREHOUSE_STAFF', 'WAREHOUSE_MANAGER', 'ADMIN')")
    public ApiResponse exportGoods(
            @Valid @RequestBody WarehouseVoucherRequest request,
            Principal principal) {
        String username = principal != null ? principal.getName() : "system";
        WarehouseVoucherResponse response = warehouseService.exportGoods(request, username);
        return ApiResponse.success("Tạo phiếu xuất kho thành công", response);
    }

    /**
     * API xem danh sách tồn kho — Thủ kho, Quản lý kho, Quản lý KD, Admin, Viewer.
     * Sổ sách hiển thị số lượng theo đơn vị cơ sở (số lon) kèm các quy đổi tương đương (thùng, lốc)
     * và thông tin vị trí kệ/khu lưu trữ (S5-03).
     */
    @GetMapping("/inventory")
    @PreAuthorize("hasAnyRole('WAREHOUSE_STAFF', 'WAREHOUSE_MANAGER', 'SALES_MANAGER', 'SALES', 'ADMIN', 'VIEWER')")
    public ApiResponse getInventory(@RequestParam(required = false) Long warehouseId) {
        List<WarehouseInventoryResponse> stock = warehouseService.getInventory(warehouseId);
        return ApiResponse.success("Danh sách tồn kho", stock);
    }

    /**
     * API xem danh sách các phiếu kho đã ghi sổ.
     */
    @GetMapping("/vouchers")
    @PreAuthorize("hasAnyRole('WAREHOUSE_STAFF', 'WAREHOUSE_MANAGER', 'SALES_MANAGER', 'ADMIN', 'VIEWER')")
    public ApiResponse getVouchers(
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) WarehouseVoucherType type) {
        List<WarehouseVoucherResponse> vouchers = warehouseService.getVouchers(warehouseId, type);
        return ApiResponse.success("Danh sách phiếu kho", vouchers);
    }

    /**
     * API xem chi tiết phiếu kho theo ID.
     */
    @GetMapping("/vouchers/{id}")
    @PreAuthorize("hasAnyRole('WAREHOUSE_STAFF', 'WAREHOUSE_MANAGER', 'SALES_MANAGER', 'ADMIN', 'VIEWER')")
    public ApiResponse getVoucherById(@PathVariable Long id) {
        WarehouseVoucherResponse voucher = warehouseService.getVoucherById(id);
        return ApiResponse.success("Chi tiết phiếu kho", voucher);
    }
}
