package com.quanlybanhang.controller;

import com.quanlybanhang.dto.request.ProductLocationRequest;
import com.quanlybanhang.dto.request.WarehouseLocationRequest;
import com.quanlybanhang.dto.request.WarehouseRequest;
import com.quanlybanhang.dto.request.WarehouseVoucherRequest;
import com.quanlybanhang.dto.response.*;
import com.quanlybanhang.model.LocationType;
import com.quanlybanhang.dto.request.WarehouseTransferReceiveRequest;
import com.quanlybanhang.dto.request.WarehouseTransferRequest;
import com.quanlybanhang.dto.response.ApiResponse;
import com.quanlybanhang.dto.response.WarehouseInventoryResponse;
import com.quanlybanhang.dto.response.WarehouseTransferResponse;
import com.quanlybanhang.dto.response.WarehouseVoucherResponse;
import com.quanlybanhang.model.WarehouseTransferStatus;
import com.quanlybanhang.model.WarehouseVoucherType;
import com.quanlybanhang.service.WarehouseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.Map;

/**
 * Controller Quản lý Kho, Vị trí lưu kho & Tồn kho (S1-05, S2-07 & S5-03).
 *
 * S5-03:
 * - Khai báo nhiều kho: mã, tên, địa chỉ, người phụ trách, trạng thái
 * - Khai báo vị trí lưu trong kho ở mức kệ hoặc khu (ZONE, SHELF)
 * - Gán sản phẩm vào vị trí kệ để biết chính xác hàng nằm ở kệ nào khi soạn đơn
 * Controller Quản lý Kho & Tồn kho (S1-05, S2-07, S5-04).
 *
 * Quy tắc nghiệp vụ cốt lõi:
 * - Nhân viên kinh doanh (ROLE_SALES) và Thủ kho (ROLE_WAREHOUSE_STAFF)
 *   KHÔNG ĐƯỢC PHÉP điều chỉnh tồn kho (/api/warehouse/adjust, /api/warehouse/vouchers/{id}/adjust).
 *   Chỉ Quản lý kho (ROLE_WAREHOUSE_MANAGER) và Quản trị viên (ROLE_ADMIN) được điều chỉnh.
 * - Thủ kho (ROLE_WAREHOUSE_STAFF) được phép nhập/xuất kho (/api/warehouse/import, /api/warehouse/export)
 *   và xem tồn kho (/api/warehouse/inventory).
 * - S2-07: Nhập/xuất theo bất kỳ đơn vị nào (thùng, lốc, lon) đều được tự động quy về
 *   đơn vị cơ sở (số lon) để ghi sổ sách kho.
 * - S5-04:
 *   + Khai báo nhà cung cấp, số chứng từ, ngày nhập, kho nhập.
 *   + Nhập theo đơn vị tính bất kỳ, quy về đơn vị cơ sở khi ghi sổ.
 *   + Ghi nhận số lô và hạn sử dụng cho mặt hàng có quản lý lô.
 *   + Xác nhận phiếu mới cộng tồn; phiếu nháp không ảnh hưởng tồn.
 *   + Phiếu đã xác nhận không sửa được, chỉ lập phiếu điều chỉnh.
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
    @PreAuthorize("hasAnyAuthority('ORDER_CREATE', 'WAREHOUSE_VIEW')")
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
     * API điều chỉnh tồn kho — CHỈ Quản lý kho (ROLE_WAREHOUSE_MANAGER) và Quản trị viên (ROLE_ADMIN).
     * Đảm bảo Nhân viên kinh doanh (ROLE_SALES) và Thủ kho (ROLE_WAREHOUSE_STAFF) không sửa được tồn kho!
     */
    @PostMapping("/adjust")
    @PreAuthorize("hasAuthority('WAREHOUSE_ADJUST') or hasAnyRole('WAREHOUSE_MANAGER', 'ADMIN')")
    public ApiResponse adjustInventory(
            @Valid @RequestBody WarehouseVoucherRequest request,
            Principal principal) {
        String username = principal != null ? principal.getName() : "system";
        WarehouseVoucherResponse response = warehouseService.adjustInventory(request, username);
        return ApiResponse.success("Điều chỉnh tồn kho thành công", response);
    }

    /**
     * API lập phiếu điều chỉnh từ phiếu kho đã xác nhận (S5-04).
     * Chỉ Quản lý kho và Quản trị viên được quyền điều chỉnh.
     */
    @PostMapping("/vouchers/{id}/adjust")
    @PreAuthorize("hasAuthority('WAREHOUSE_ADJUST') or hasAnyRole('WAREHOUSE_MANAGER', 'ADMIN')")
    public ApiResponse adjustVoucher(
            @PathVariable Long id,
            @Valid @RequestBody WarehouseVoucherRequest request,
            Principal principal) {
        String username = principal != null ? principal.getName() : "system";
        WarehouseVoucherResponse response = warehouseService.adjustVoucher(id, request, username);
        return ApiResponse.success("Lập phiếu điều chỉnh kho thành công", response);
    }

    /**
     * API nhập kho — Thủ kho, Quản lý kho, Quản trị viên (S2-07 & S5-04).
     * Nhận nhà cung cấp, số chứng từ, ngày nhập, kho nhập, số lô, hạn sử dụng.
     * Nếu request có draft=true hoặc status="DRAFT": lưu nháp, chưa cộng tồn kho.
     * Nếu không: ghi sổ và cộng tồn kho ngay.
     */
    @PostMapping("/import")
    @PreAuthorize("hasAuthority('WAREHOUSE_IMPORT') or hasAnyRole('WAREHOUSE_STAFF', 'WAREHOUSE_MANAGER', 'ADMIN')")
    public ApiResponse importGoods(
            @Valid @RequestBody WarehouseVoucherRequest request,
            Principal principal) {
        String username = principal != null ? principal.getName() : "system";
        WarehouseVoucherResponse response = warehouseService.importGoods(request, username);
        boolean isDraft = response != null && "DRAFT".equalsIgnoreCase(response.getStatus());
        String message = isDraft
                ? "Lưu phiếu nhập kho nháp thành công (chưa cộng tồn kho)"
                : "Tạo phiếu nhập kho thành công";
        return ApiResponse.success(message, response);
    }

    /**
     * API tạo nhanh phiếu nhập kho ở trạng thái Nháp (DRAFT) (S5-04).
     */
    @PostMapping("/import/draft")
    @PreAuthorize("hasAuthority('WAREHOUSE_IMPORT') or hasAnyRole('WAREHOUSE_STAFF', 'WAREHOUSE_MANAGER', 'ADMIN')")
    public ApiResponse createImportDraft(
            @Valid @RequestBody WarehouseVoucherRequest request,
            Principal principal) {
        String username = principal != null ? principal.getName() : "system";
        request.setStatus("DRAFT");
        request.setDraft(true);
        WarehouseVoucherResponse response = warehouseService.importGoods(request, username);
        return ApiResponse.success("Lưu phiếu nhập kho nháp thành công (chưa cộng tồn kho)", response);
    }

    /**
     * API xác nhận phiếu kho nháp — Thủ kho, Quản lý kho, Admin (S5-04).
     * Khi xác nhận mới thực hiện cộng tồn kho và cập nhật sổ tồn kho lô.
     */
    @PostMapping("/vouchers/{id}/confirm")
    @PreAuthorize("hasAuthority('WAREHOUSE_IMPORT') or hasAnyRole('WAREHOUSE_STAFF', 'WAREHOUSE_MANAGER', 'ADMIN')")
    public ApiResponse confirmVoucher(
            @PathVariable Long id,
            Principal principal) {
        String username = principal != null ? principal.getName() : "system";
        WarehouseVoucherResponse response = warehouseService.confirmVoucher(id, username);
        return ApiResponse.success("Xác nhận phiếu kho thành công, đã cộng tồn kho", response);
    }

    /**
     * API cập nhật phiếu kho nháp (S5-04).
     * Phiếu đã xác nhận (CONFIRMED / RECORDED) KHÔNG SỬA ĐƯỢC, sẽ trả lỗi để yêu cầu lập phiếu điều chỉnh.
     */
    @PutMapping("/vouchers/{id}")
    @PreAuthorize("hasAuthority('WAREHOUSE_IMPORT') or hasAnyRole('WAREHOUSE_STAFF', 'WAREHOUSE_MANAGER', 'ADMIN')")
    public ApiResponse updateVoucher(
            @PathVariable Long id,
            @Valid @RequestBody WarehouseVoucherRequest request,
            Principal principal) {
        String username = principal != null ? principal.getName() : "system";
        WarehouseVoucherResponse response = warehouseService.updateVoucher(id, request, username);
        return ApiResponse.success("Cập nhật phiếu kho thành công", response);
    }

    /**
     * API xóa phiếu kho nháp (S5-04).
     * Phiếu đã xác nhận KHÔNG THỂ XÓA, chỉ được lập phiếu điều chỉnh.
     */
    @DeleteMapping("/vouchers/{id}")
    @PreAuthorize("hasAuthority('WAREHOUSE_IMPORT') or hasAnyRole('WAREHOUSE_STAFF', 'WAREHOUSE_MANAGER', 'ADMIN')")
    public ApiResponse deleteVoucher(
            @PathVariable Long id,
            Principal principal) {
        String username = principal != null ? principal.getName() : "system";
        warehouseService.deleteVoucher(id, username);
        return ApiResponse.success("Xóa phiếu kho nháp thành công", null);
    }

    /**
     * API xuất kho — Thủ kho, Quản lý kho, Quản trị viên.
     * Tự động quy đổi từ đơn vị xuất (thùng, lốc) về đơn vị cơ sở (lon) khi ghi sổ.
     */
    @PostMapping("/export")
    @PreAuthorize("hasAuthority('WAREHOUSE_EXPORT') or hasAnyRole('WAREHOUSE_STAFF', 'WAREHOUSE_MANAGER', 'ADMIN')")
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
    @PreAuthorize("hasAnyAuthority('ORDER_CREATE', 'WAREHOUSE_VIEW')")
    public ApiResponse getInventory(@RequestParam(required = false) Long warehouseId) {
        List<WarehouseInventoryResponse> stock = warehouseService.getInventory(warehouseId);
        return ApiResponse.success("Danh sách tồn kho", stock);
    }

    /**
     * API xem danh sách tồn kho theo Lô & Hạn sử dụng (S5-04).
     */
    @GetMapping("/inventory/lots")
    @PreAuthorize("hasAuthority('WAREHOUSE_VIEW') or hasAnyRole('WAREHOUSE_STAFF', 'WAREHOUSE_MANAGER', 'SALES_MANAGER', 'ADMIN', 'VIEWER')")
    public ApiResponse getLotInventory(
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) Long productId) {
        List<WarehouseLotInventoryResponse> stock = warehouseService.getLotInventory(warehouseId, productId);
        return ApiResponse.success("Danh sách tồn kho theo lô", stock);
    }

    /**
     * API xem danh sách các phiếu kho đã tạo (lọc theo kho, loại phiếu, trạng thái, nhà cung cấp).
     */
    @GetMapping("/vouchers")
    @PreAuthorize("hasAuthority('WAREHOUSE_VIEW') or hasAnyRole('WAREHOUSE_STAFF', 'WAREHOUSE_MANAGER', 'SALES_MANAGER', 'ADMIN', 'VIEWER')")
    public ApiResponse getVouchers(
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) WarehouseVoucherType type,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long supplierId) {
        List<WarehouseVoucherResponse> vouchers = warehouseService.getVouchers(warehouseId, type, status, supplierId);
        return ApiResponse.success("Danh sách phiếu kho", vouchers);
    }

    /**
     * API xem chi tiết phiếu kho theo ID.
     */
    @GetMapping("/vouchers/{id}")
    @PreAuthorize("hasAuthority('WAREHOUSE_VIEW') or hasAnyRole('WAREHOUSE_STAFF', 'WAREHOUSE_MANAGER', 'SALES_MANAGER', 'ADMIN', 'VIEWER')")
    public ApiResponse getVoucherById(@PathVariable Long id) {
        WarehouseVoucherResponse voucher = warehouseService.getVoucherById(id);
        return ApiResponse.success("Chi tiết phiếu kho", voucher);
    }

    // ═════════════════════════════════════════════════════════════════════════
    // API CHUYỂN KHO NỘI BỘ (S5-07)
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * Lập phiếu chuyển kho nội bộ — Thủ kho, Quản lý kho, Quản trị viên.
     * Hàng xuất khỏi kho đi, ghi nhận đang trên đường chuyển, CHƯA cộng vào kho đến.
     */
    @PostMapping(value = {"/transfers", "/transfer"})
    @PreAuthorize("hasAuthority('WAREHOUSE_TRANSFER') or hasAuthority('WAREHOUSE_EXPORT') or hasAnyRole('WAREHOUSE_STAFF', 'WAREHOUSE_MANAGER', 'ADMIN')")
    public ApiResponse createTransfer(
            @Valid @RequestBody WarehouseTransferRequest request,
            Principal principal) {
        String username = principal != null ? principal.getName() : "system";
        WarehouseTransferResponse response = warehouseService.createTransfer(request, username);
        return ApiResponse.success("Lập phiếu chuyển kho thành công", response);
    }

    /**
     * Danh sách phiếu chuyển kho nội bộ.
     */
    @GetMapping("/transfers")
    @PreAuthorize("hasAnyAuthority('WAREHOUSE_TRANSFER', 'WAREHOUSE_VIEW') or hasAnyRole('WAREHOUSE_STAFF', 'WAREHOUSE_MANAGER', 'SALES_MANAGER', 'ADMIN', 'VIEWER')")
    public ApiResponse getTransfers(
            @RequestParam(required = false) Long fromWarehouseId,
            @RequestParam(required = false) Long toWarehouseId,
            @RequestParam(required = false) WarehouseTransferStatus status) {
        List<WarehouseTransferResponse> list = warehouseService.getTransfers(fromWarehouseId, toWarehouseId, status);
        return ApiResponse.success("Danh sách phiếu chuyển kho", list);
    }

    /**
     * Chi tiết phiếu chuyển kho nội bộ theo ID.
     */
    @GetMapping("/transfers/{id}")
    @PreAuthorize("hasAnyAuthority('WAREHOUSE_TRANSFER', 'WAREHOUSE_VIEW') or hasAnyRole('WAREHOUSE_STAFF', 'WAREHOUSE_MANAGER', 'SALES_MANAGER', 'ADMIN', 'VIEWER')")
    public ApiResponse getTransferById(@PathVariable Long id) {
        WarehouseTransferResponse transfer = warehouseService.getTransferById(id);
        return ApiResponse.success("Chi tiết phiếu chuyển kho", transfer);
    }

    /**
     * Kho đến xác nhận nhận hàng.
     * Khi kho đến xác nhận nhận đủ/thực nhận thì tồn mới được cộng.
     * Chênh lệch khi nhận bắt buộc phải nhập lý do giải trình.
     */
    @PostMapping("/transfers/{id}/receive")
    @PreAuthorize("hasAuthority('WAREHOUSE_TRANSFER') or hasAuthority('WAREHOUSE_IMPORT') or hasAnyRole('WAREHOUSE_STAFF', 'WAREHOUSE_MANAGER', 'ADMIN')")
    public ApiResponse receiveTransfer(
            @PathVariable Long id,
            @RequestBody(required = false) WarehouseTransferReceiveRequest request,
            Principal principal) {
        String username = principal != null ? principal.getName() : "system";
        WarehouseTransferResponse response = warehouseService.receiveTransfer(id, request, username);
        return ApiResponse.success("Xác nhận nhận hàng thành công", response);
    }

    /**
     * Hủy phiếu chuyển kho nội bộ (nếu chưa nhận hàng).
     * Hoàn lại tồn kho cho kho xuất và xóa hàng đang trên đường.
     */
    @PostMapping("/transfers/{id}/cancel")
    @PreAuthorize("hasAuthority('WAREHOUSE_TRANSFER') or hasAuthority('WAREHOUSE_APPROVE') or hasAnyRole('WAREHOUSE_MANAGER', 'ADMIN')")
    public ApiResponse cancelTransfer(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body,
            Principal principal) {
        String username = principal != null ? principal.getName() : "system";
        String reason = body != null ? body.get("reason") : null;
        WarehouseTransferResponse response = warehouseService.cancelTransfer(id, reason, username);
        return ApiResponse.success("Hủy phiếu chuyển kho thành công", response);
    }
}
