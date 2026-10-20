package com.quanlybanhang.service;

import com.quanlybanhang.dto.request.StockMinimumRequest;
import com.quanlybanhang.dto.response.*;
import com.quanlybanhang.model.*;
import com.quanlybanhang.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.*;
import java.time.LocalDateTime;
import java.util.*;

@Service @RequiredArgsConstructor @Transactional(readOnly = true)
public class StockMinimumService {
    private final StockMinimumRepository minimums;
    private final StockLedgerRepository ledger;
    private final StockLedgerService scope;
    private final WarehouseRepository warehouses;
    private final ProductRepository products;
    private final WarehouseInventoryRepository inventories;
    private final CategoryRepository categories;
    private final AuditLogService audit;

    public record Options(List<NamedResourceResponse> warehouses, List<NamedResourceResponse> categories) {}

    public Options options() {
        return new Options(allowedWarehouses().stream().map(w -> new NamedResourceResponse(w.getId(), w.getCode(),
                w.getName() + (Boolean.TRUE.equals(w.getActive()) ? "" : " (ngừng hoạt động)"))).toList(),
                categories.findAllByOrderByLevelAscNameAsc().stream().map(c -> new NamedResourceResponse(c.getId(), c.getCode(), c.getName())).toList());
    }

    public Page<StockMinimumSettingResponse> search(Long warehouseId, Long categoryId, String keyword, int page, int size) {
        if (page < 0 || size < 1 || size > 200) throw new IllegalArgumentException("Trang phải không âm; mỗi trang từ 1 đến 200 dòng");
        if ((warehouseId != null && warehouseId < 1) || (categoryId != null && categoryId < 1)) throw new IllegalArgumentException("Mã kho/nhóm hàng không hợp lệ");
        if (keyword != null && keyword.length() > 150) throw new IllegalArgumentException("Từ khóa tối đa 150 ký tự");
        List<Long> allowed = allowedWarehouses().stream().map(Warehouse::getId).toList();
        if (warehouseId != null && !allowed.contains(warehouseId)) throw new AccessDeniedException("Kho không thuộc phạm vi được giao");
        var paging = PageRequest.of(page, size);
        if (allowed.isEmpty()) return Page.empty(paging);
        String term = keyword == null || keyword.isBlank() ? null : "%" + keyword.trim().toLowerCase(Locale.ROOT)
                .replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
        return ledger.findLedger(allowed, warehouseId, categoryId, term, null, paging).map(StockMinimumSettingResponse::from);
    }

    @Transactional
    public StockMinimumSettingResponse save(Long warehouseId, Long productId, StockMinimumRequest request) {
        requireWarehouse(warehouseId);
        if (productId == null || productId < 1) throw new IllegalArgumentException("Mã sản phẩm không hợp lệ");
        Warehouse warehouse = warehouses.findByIdForUpdate(warehouseId).orElseThrow(() -> new IllegalArgumentException("Không tìm thấy kho"));
        Product product = products.findById(productId).orElseThrow(() -> new IllegalArgumentException("Không tìm thấy sản phẩm"));
        if (!Boolean.TRUE.equals(warehouse.getActive()) || !Boolean.TRUE.equals(product.getActive()))
            throw new IllegalArgumentException("Chỉ khai báo cho kho và sản phẩm đang hoạt động");
        if (request == null || request.minimumQuantity() == null || request.minimumQuantity().signum() < 0
                || request.minimumQuantity().stripTrailingZeros().scale() > 4
                || request.minimumQuantity().compareTo(new BigDecimal("99999999999.9999")) > 0)
            throw new IllegalArgumentException("Mức tối thiểu phải không âm, tối đa 11 chữ số nguyên và 4 số thập phân");
        String unit = inventories.findByWarehouseIdAndProductId(warehouseId, productId)
                .map(WarehouseInventory::getBaseUnit).orElse(product.getUnit());
        if (unit == null || !unit.equals(request.baseUnit()))
            throw new IllegalArgumentException("Đơn vị cơ sở đã thay đổi hoặc không hợp lệ. Tải lại trước khi khai báo");
        StockMinimum minimum = minimums.findByWarehouseIdAndProductId(warehouseId, productId)
                .orElseGet(() -> StockMinimum.builder().warehouse(warehouse).product(product).build());
        String before = minimum.getMinimumQuantity() == null ? "Chưa khai báo" : minimum.getMinimumQuantity().toPlainString() + " " + minimum.getBaseUnit();
        minimum.setMinimumQuantity(request.minimumQuantity().setScale(4, RoundingMode.UNNECESSARY));
        minimum.setBaseUnit(unit); minimum.setUpdatedAt(LocalDateTime.now());
        minimum.setUpdatedBy(SecurityContextHolder.getContext().getAuthentication().getName());
        minimums.saveAndFlush(minimum);
        audit.logAction("INVENTORY", warehouseId + ":" + productId, "SET_MINIMUM", before,
                minimum.getMinimumQuantity().toPlainString() + " " + unit, minimum.getUpdatedBy());
        return new StockMinimumSettingResponse(warehouseId, warehouse.getCode(), warehouse.getName(), productId,
                product.getCode(), product.getName(), product.getCategory() == null ? null : product.getCategory().getName(),
                unit, minimum.getMinimumQuantity(), unit, true, true, true, minimum.getUpdatedBy(), minimum.getUpdatedAt());
    }

    @Transactional
    public void clear(Long warehouseId, Long productId) {
        requireWarehouse(warehouseId);
        if (productId == null || productId < 1) throw new IllegalArgumentException("Mã sản phẩm không hợp lệ");
        warehouses.findByIdForUpdate(warehouseId).orElseThrow(() -> new IllegalArgumentException("Không tìm thấy kho"));
        minimums.findByWarehouseIdAndProductId(warehouseId, productId).ifPresent(minimum -> {
            String before = minimum.getMinimumQuantity().toPlainString() + " " + minimum.getBaseUnit();
            minimums.delete(minimum); minimums.flush();
            audit.logAction("INVENTORY", warehouseId + ":" + productId, "CLEAR_MINIMUM", before, "Chưa khai báo",
                    SecurityContextHolder.getContext().getAuthentication().getName());
        });
    }

    private List<Warehouse> allowedWarehouses() { return scope.visibleWarehousesFor("WAREHOUSE_MANAGE"); }
    private void requireWarehouse(Long id) {
        if (id == null || id < 1) throw new IllegalArgumentException("Mã kho không hợp lệ");
        if (allowedWarehouses().stream().noneMatch(w -> w.getId().equals(id))) throw new AccessDeniedException("Kho không thuộc phạm vi được giao");
    }
}
