package com.quanlybanhang.service;

import com.quanlybanhang.dto.request.WarehouseVoucherRequest;
import com.quanlybanhang.dto.response.WarehouseInventoryResponse;
import com.quanlybanhang.dto.response.NamedResourceResponse;
import com.quanlybanhang.dto.response.WarehouseVoucherResponse;
import com.quanlybanhang.model.WarehouseVoucherType;

import java.util.List;

public interface WarehouseService {
    List<NamedResourceResponse> getWarehouseOptions();

    WarehouseVoucherResponse importGoods(WarehouseVoucherRequest request, String username);

    WarehouseVoucherResponse exportGoods(WarehouseVoucherRequest request, String username);

    WarehouseVoucherResponse adjustInventory(WarehouseVoucherRequest request, String username);

    List<WarehouseInventoryResponse> getInventory(Long warehouseId);

    List<WarehouseVoucherResponse> getVouchers(Long warehouseId, WarehouseVoucherType type);

    WarehouseVoucherResponse getVoucherById(Long id);
}
