package com.quanlybanhang.controller;

import com.quanlybanhang.dto.request.ProductLocationRequest;
import com.quanlybanhang.dto.request.WarehouseLocationRequest;
import com.quanlybanhang.dto.request.WarehouseRequest;
import com.quanlybanhang.dto.response.*;
import com.quanlybanhang.model.LocationType;
import com.quanlybanhang.service.WarehouseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

/**
 * RESTful Controller cho tài nguyên Kho (/api/warehouses) & Vị trí lưu (/api/warehouse-locations) (S5-03).
 */
@RestController
@RequiredArgsConstructor
public class WarehouseResourceController {

    private final WarehouseService warehouseService;

    // S5-03 uses a narrow warehouse-only projection, not the customer/finance APIs.
    @GetMapping("/api/warehouses/dealer-assignments")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('WAREHOUSE_MANAGE')")
    public ApiResponse getDealerWarehouseAssignments(@RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        if (page < 0 || size < 1 || size > 200) {
            throw new IllegalArgumentException("Trang phải không âm; số dòng mỗi trang từ 1 đến 200");
        }
        return ApiResponse.success("Danh sách kho phục vụ đại lý", warehouseService.getDealerWarehouseAssignments(
                keyword, org.springframework.data.domain.PageRequest.of(page, size,
                        org.springframework.data.domain.Sort.by("name").ascending())));
    }

    @PutMapping("/api/warehouses/dealer-assignments/{dealerId}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('WAREHOUSE_MANAGE')")
    public ApiResponse assignDealerWarehouse(@PathVariable Long dealerId,
            @Valid @RequestBody com.quanlybanhang.dto.request.DealerWarehouseAssignRequest request,
            Principal principal) {
        return ApiResponse.success("Đã lưu kho phục vụ mặc định", warehouseService.assignDealerWarehouse(
                dealerId, request.getWarehouseId(), principal.getName()));
    }

    // ── /api/warehouses ──

    @GetMapping("/api/warehouses")
    @PreAuthorize("hasRole('ADMIN') or (hasAnyAuthority('WAREHOUSE_VIEW', 'WAREHOUSE_MANAGE'))")
    public ApiResponse getAllWarehouses(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Boolean active) {
        List<WarehouseResponse> warehouses = warehouseService.getAllWarehouses(keyword, active);
        return ApiResponse.success("Lấy danh sách kho thành công", warehouses);
    }

    @GetMapping("/api/warehouses/{id}")
    @PreAuthorize("hasRole('ADMIN') or (hasAnyAuthority('WAREHOUSE_VIEW', 'WAREHOUSE_MANAGE'))")
    public ApiResponse getWarehouseById(@PathVariable Long id) {
        WarehouseResponse warehouse = warehouseService.getWarehouseById(id);
        return ApiResponse.success("Lấy chi tiết kho thành công", warehouse);
    }

    @PostMapping("/api/warehouses")
    @PreAuthorize("hasRole('ADMIN') or (hasAuthority('WAREHOUSE_MANAGE'))")
    public ApiResponse createWarehouse(
            @Valid @RequestBody WarehouseRequest request,
            Principal principal) {
        String username = principal != null ? principal.getName() : "system";
        WarehouseResponse warehouse = warehouseService.createWarehouse(request, username);
        return ApiResponse.success("Khai báo kho mới thành công", warehouse);
    }

    @PutMapping("/api/warehouses/{id}")
    @PreAuthorize("hasRole('ADMIN') or (hasAuthority('WAREHOUSE_MANAGE'))")
    public ApiResponse updateWarehouse(
            @PathVariable Long id,
            @Valid @RequestBody WarehouseRequest request,
            Principal principal) {
        String username = principal != null ? principal.getName() : "system";
        WarehouseResponse warehouse = warehouseService.updateWarehouse(id, request, username);
        return ApiResponse.success("Cập nhật thông tin kho thành công", warehouse);
    }

    @DeleteMapping("/api/warehouses/{id}")
    @PreAuthorize("hasRole('ADMIN') or (hasAuthority('WAREHOUSE_MANAGE'))")
    public ApiResponse deleteWarehouse(@PathVariable Long id, Principal principal) {
        String username = principal != null ? principal.getName() : "system";
        warehouseService.deleteWarehouse(id, username);
        return ApiResponse.success("Xóa/ngừng hoạt động kho thành công", null);
    }

    @GetMapping("/api/warehouses/{warehouseId}/locations")
    @PreAuthorize("hasRole('ADMIN') or (hasAnyAuthority('WAREHOUSE_VIEW', 'WAREHOUSE_MANAGE'))")
    public ApiResponse getLocationsByWarehouse(
            @PathVariable Long warehouseId,
            @RequestParam(required = false) LocationType type,
            @RequestParam(required = false) Long parentId,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) String keyword) {
        List<WarehouseLocationResponse> locations = warehouseService.getLocations(warehouseId, type, parentId, active, keyword);
        return ApiResponse.success("Lấy danh sách vị trí kho thành công", locations);
    }

    @PostMapping("/api/warehouses/{warehouseId}/locations")
    @PreAuthorize("hasRole('ADMIN') or (hasAuthority('WAREHOUSE_MANAGE'))")
    public ApiResponse createLocationForWarehouse(
            @PathVariable Long warehouseId,
            @Valid @RequestBody WarehouseLocationRequest request,
            Principal principal) {
        String username = principal != null ? principal.getName() : "system";
        WarehouseLocationResponse location = warehouseService.createLocation(warehouseId, request, username);
        return ApiResponse.success("Khai báo vị trí lưu kho thành công", location);
    }

    // ── /api/warehouse-locations ──

    @GetMapping("/api/warehouse-locations")
    @PreAuthorize("hasRole('ADMIN') or (hasAnyAuthority('WAREHOUSE_VIEW', 'WAREHOUSE_MANAGE'))")
    public ApiResponse getLocations(
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) LocationType type,
            @RequestParam(required = false) Long parentId,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) String keyword) {
        List<WarehouseLocationResponse> locations = warehouseService.getLocations(warehouseId, type, parentId, active, keyword);
        return ApiResponse.success("Lấy danh sách vị trí lưu kho thành công", locations);
    }

    @GetMapping("/api/warehouse-locations/{id}")
    @PreAuthorize("hasRole('ADMIN') or (hasAnyAuthority('WAREHOUSE_VIEW', 'WAREHOUSE_MANAGE'))")
    public ApiResponse getLocationById(@PathVariable Long id) {
        WarehouseLocationResponse location = warehouseService.getLocationById(id);
        return ApiResponse.success("Lấy chi tiết vị trí lưu kho thành công", location);
    }

    @PostMapping("/api/warehouse-locations")
    @PreAuthorize("hasRole('ADMIN') or (hasAuthority('WAREHOUSE_MANAGE'))")
    public ApiResponse createLocation(
            @Valid @RequestBody WarehouseLocationRequest request,
            Principal principal) {
        String username = principal != null ? principal.getName() : "system";
        WarehouseLocationResponse location = warehouseService.createLocation(request.getWarehouseId(), request, username);
        return ApiResponse.success("Khai báo vị trí lưu kho thành công", location);
    }

    @PutMapping("/api/warehouse-locations/{id}")
    @PreAuthorize("hasRole('ADMIN') or (hasAuthority('WAREHOUSE_MANAGE'))")
    public ApiResponse updateLocation(
            @PathVariable Long id,
            @Valid @RequestBody WarehouseLocationRequest request,
            Principal principal) {
        String username = principal != null ? principal.getName() : "system";
        WarehouseLocationResponse location = warehouseService.updateLocation(id, request, username);
        return ApiResponse.success("Cập nhật vị trí lưu kho thành công", location);
    }

    @DeleteMapping("/api/warehouse-locations/{id}")
    @PreAuthorize("hasRole('ADMIN') or (hasAuthority('WAREHOUSE_MANAGE'))")
    public ApiResponse deleteLocation(@PathVariable Long id, Principal principal) {
        String username = principal != null ? principal.getName() : "system";
        warehouseService.deleteLocation(id, username);
        return ApiResponse.success("Xóa vị trí lưu kho thành công", null);
    }

    @PostMapping("/api/warehouse-locations/{locationId}/products")
    @PreAuthorize("hasRole('ADMIN') or (hasAuthority('WAREHOUSE_MANAGE'))")
    public ApiResponse assignProductToLocation(
            @PathVariable Long locationId,
            @Valid @RequestBody ProductLocationRequest request,
            Principal principal) {
        String username = principal != null ? principal.getName() : "system";
        ProductLocationResponse response = warehouseService.assignProductToLocation(locationId, request, username);
        return ApiResponse.success("Gán sản phẩm vào vị trí kệ thành công", response);
    }

    @DeleteMapping("/api/warehouse-locations/{locationId}/products/{productId}")
    @PreAuthorize("hasRole('ADMIN') or (hasAuthority('WAREHOUSE_MANAGE'))")
    public ApiResponse removeProductFromLocation(
            @PathVariable Long locationId,
            @PathVariable Long productId,
            Principal principal) {
        String username = principal != null ? principal.getName() : "system";
        warehouseService.removeProductFromLocation(locationId, productId, username);
        return ApiResponse.success("Bỏ gán sản phẩm khỏi vị trí kệ thành công", null);
    }
}
