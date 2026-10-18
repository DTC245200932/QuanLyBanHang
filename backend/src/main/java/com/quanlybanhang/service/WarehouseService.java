package com.quanlybanhang.service;

import com.quanlybanhang.dto.request.ProductLocationRequest;
import com.quanlybanhang.dto.request.WarehouseLocationRequest;
import com.quanlybanhang.dto.request.WarehouseRequest;
import com.quanlybanhang.dto.request.WarehouseVoucherRequest;
import com.quanlybanhang.dto.response.*;
import com.quanlybanhang.model.LocationType;
import com.quanlybanhang.model.WarehouseVoucherType;

import java.util.List;

public interface WarehouseService {

    // ── Danh sách options nhanh (cho dropdown) ──
    List<NamedResourceResponse> getWarehouseOptions();

    // ── Quản lý Kho hàng (S5-03) ──
    List<WarehouseResponse> getAllWarehouses(String keyword, Boolean active);

    WarehouseResponse getWarehouseById(Long id);

    WarehouseResponse createWarehouse(WarehouseRequest request, String username);

    WarehouseResponse updateWarehouse(Long id, WarehouseRequest request, String username);

    void deleteWarehouse(Long id, String username);

    // ── Quản lý Vị trí lưu kho ở mức kệ hoặc khu (S5-03) ──
    List<WarehouseLocationResponse> getLocations(Long warehouseId, LocationType type, Long parentId, Boolean active, String keyword);

    WarehouseLocationResponse getLocationById(Long id);

    WarehouseLocationResponse createLocation(Long warehouseId, WarehouseLocationRequest request, String username);

    WarehouseLocationResponse updateLocation(Long id, WarehouseLocationRequest request, String username);

    void deleteLocation(Long id, String username);

    // ── Gán vị trí lưu cho sản phẩm (S5-03: biết chính xác hàng nằm ở kệ nào) ──
    ProductLocationResponse assignProductToLocation(Long locationId, ProductLocationRequest request, String username);

    void removeProductFromLocation(Long locationId, Long productId, String username);

    List<ProductLocationResponse> getProductsByLocation(Long locationId);

    List<ProductLocationResponse> getLocationsByProduct(Long productId, Long warehouseId);

    ProductLocationResponse getProductLocationInWarehouse(Long warehouseId, Long productId);

    // ── Soạn đơn hàng kèm vị trí kệ chính xác (S5-03) ──
    OrderPickingListResponse getOrderPickingList(Long orderId);

    // ── Phiếu kho & Tồn kho (S1-05 & S2-07 & S4-03) ──
    WarehouseVoucherResponse importGoods(WarehouseVoucherRequest request, String username);

    WarehouseVoucherResponse exportGoods(WarehouseVoucherRequest request, String username);

    WarehouseVoucherResponse adjustInventory(WarehouseVoucherRequest request, String username);

    List<WarehouseInventoryResponse> getInventory(Long warehouseId);

    List<WarehouseVoucherResponse> getVouchers(Long warehouseId, WarehouseVoucherType type);

    List<WarehouseVoucherResponse> getVouchers(Long warehouseId, WarehouseVoucherType type, String status, Long supplierId);

    WarehouseVoucherResponse getVoucherById(Long id);

    // S5-04: Quản lý Phiếu nhập kho từ Nhà cung cấp, Lô & Hạn sử dụng, Xác nhận / Nháp / Điều chỉnh
    WarehouseVoucherResponse confirmVoucher(Long id, String username);

    WarehouseVoucherResponse updateVoucher(Long id, WarehouseVoucherRequest request, String username);

    void deleteVoucher(Long id, String username);

    WarehouseVoucherResponse adjustVoucher(Long id, WarehouseVoucherRequest request, String username);

    List<com.quanlybanhang.dto.response.WarehouseLotInventoryResponse> getLotInventory(Long warehouseId, Long productId);
    // Phiếu chuyển kho nội bộ (S5-07)
    WarehouseTransferResponse createTransfer(com.quanlybanhang.dto.request.WarehouseTransferRequest request, String username);

    WarehouseTransferResponse receiveTransfer(Long transferId, com.quanlybanhang.dto.request.WarehouseTransferReceiveRequest request, String username);

    WarehouseTransferResponse cancelTransfer(Long transferId, String reason, String username);

    List<com.quanlybanhang.dto.response.WarehouseTransferResponse> getTransfers(
            Long fromWarehouseId, Long toWarehouseId, com.quanlybanhang.model.WarehouseTransferStatus status);

    com.quanlybanhang.dto.response.WarehouseTransferResponse getTransferById(Long id);
}
