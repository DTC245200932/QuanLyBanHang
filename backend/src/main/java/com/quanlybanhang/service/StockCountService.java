package com.quanlybanhang.service;

import com.quanlybanhang.dto.request.*;
import com.quanlybanhang.dto.response.*;
import com.quanlybanhang.model.*;
import com.quanlybanhang.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Service Quản lý Kiểm kê Kho (S5-08).
 *
 * Nghiệp vụ:
 * - Tạo phiếu kiểm kê theo kho hoặc theo nhóm hàng, hệ thống chốt số tồn sổ tại thời điểm tạo.
 * - Nhập số đếm thực tế, hệ thống tính chênh lệch từng dòng.
 * - Chốt kiểm kê điều chỉnh tồn về số thực đếm và bắt buộc nhập lý do.
 * - Chỉ Quản lý kho (quyền WAREHOUSE_ADJUST hoặc ROLE_ADMIN) được chốt phiếu kiểm kê.
 * - Kiểm tra phạm vi kho được phân công của người dùng theo ma trận phân quyền.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StockCountService {

    private final WarehouseStockCountRepository stockCounts;
    private final WarehouseStockCountItemRepository stockCountItems;
    private final WarehouseRepository warehouses;
    private final WarehouseInventoryRepository inventories;
    private final ProductRepository products;
    private final CategoryRepository categories;
    private final UserRepository users;
    private final AuditLogService audit;

    private record Scope(boolean global, boolean canAdjust, Set<Long> ids) {
        boolean contains(Long id) {
            return global || (id != null && ids.contains(id));
        }

        void require(Long id) {
            if (!contains(id)) {
                throw new AccessDeniedException("Kho này không thuộc phạm vi được giao");
            }
        }
    }

    private Scope scope(boolean requireAdjust) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new AccessDeniedException("Bạn chưa được xác thực");
        }
        Set<String> grants = new HashSet<>();
        auth.getAuthorities().forEach(a -> grants.add(a.getAuthority()));
        boolean admin = grants.contains("ROLE_ADMIN");
        boolean canAdjust = admin || grants.contains("WAREHOUSE_ADJUST");
        boolean canView = admin || canAdjust || grants.contains("WAREHOUSE_VIEW");

        if (requireAdjust && !canAdjust) {
            throw new AccessDeniedException("Chỉ Quản lý kho mới được chốt phiếu kiểm kê");
        }
        if (!requireAdjust && !canView) {
            throw new AccessDeniedException("Bạn không có quyền xem hoặc thực hiện kiểm kê kho");
        }

        if (admin) {
            return new Scope(true, true, Set.of());
        }

        User user = users.findByUsername(auth.getName())
                .orElseThrow(() -> new AccessDeniedException("Không tìm thấy tài khoản"));
        Set<Long> ids = new HashSet<>();
        if (user.getWarehouses() != null) {
            user.getWarehouses().forEach(w -> ids.add(w.getId()));
        }
        // Phân quyền theo vai trò kho: nếu không được gán kho nào thì fail closed
        boolean warehouseRole = grants.contains("ROLE_WAREHOUSE_STAFF") || grants.contains("ROLE_WAREHOUSE_MANAGER");
        return new Scope(ids.isEmpty() && !warehouseRole, canAdjust, ids);
    }

    public StockCountOptionsResponse getOptions() {
        Scope access = scope(false);
        List<Warehouse> activeWhs = warehouses.findAllByActiveTrueOrderByNameAsc();
        List<StockCountOptionsResponse.NamedOption> whOptions = activeWhs.stream()
                .filter(w -> access.contains(w.getId()))
                .map(w -> new StockCountOptionsResponse.NamedOption(w.getId(), w.getCode(), w.getName()))
                .toList();

        List<Category> allCategories = categories.findAll(Sort.by(Sort.Direction.ASC, "name"));
        List<StockCountOptionsResponse.NamedOption> catOptions = allCategories.stream()
                .map(c -> new StockCountOptionsResponse.NamedOption(c.getId(), c.getCode(), c.getName()))
                .toList();

        List<StockCountOptionsResponse.StatusOption> statusOptions = Arrays.stream(StockCountStatus.values())
                .map(s -> new StockCountOptionsResponse.StatusOption(s.name(), s.getDisplayName()))
                .toList();

        return new StockCountOptionsResponse(whOptions, catOptions, statusOptions, access.canAdjust());
    }

    public Page<StockCountResponse> search(Long warehouseId, Long categoryId, StockCountStatus status,
                                          String keyword, int page, int size) {
        Scope access = scope(false);
        if (warehouseId != null) {
            access.require(warehouseId);
        }
        PageRequest pageRequest = PageRequest.of(Math.max(0, page), Math.max(1, Math.min(size, 100)),
                Sort.by(Sort.Direction.DESC, "createdAt", "id"));
        return stockCounts.findAll(buildFilter(access, warehouseId, categoryId, status, keyword), pageRequest)
                .map(sc -> toSummaryResponse(sc, access));
    }

    public StockCountResponse getById(Long id) {
        Scope access = scope(false);
        WarehouseStockCount sc = stockCounts.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy phiếu kiểm kê #" + id));
        access.require(sc.getWarehouse().getId());
        return toDetailResponse(sc, access);
    }

    @Transactional
    public StockCountResponse create(StockCountCreateRequest request, String username) {
        Scope access = scope(false);
        if (request == null || request.getWarehouseId() == null) {
            throw new IllegalArgumentException("Vui lòng chọn kho kiểm kê");
        }
        access.require(request.getWarehouseId());

        Warehouse warehouse = warehouses.findById(request.getWarehouseId())
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy kho kiểm kê"));
        if (!Boolean.TRUE.equals(warehouse.getActive())) {
            throw new IllegalArgumentException("Kho '" + warehouse.getName() + "' đã ngừng hoạt động");
        }

        Category category = null;
        if (request.getCategoryId() != null) {
            category = categories.findById(request.getCategoryId())
                    .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy nhóm hàng #" + request.getCategoryId()));
        }

        String code = request.getCode() != null ? request.getCode().trim() : "";
        if (code.isBlank()) {
            code = "PKK-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"));
        } else {
            if (stockCounts.existsByCode(code)) {
                throw new IllegalArgumentException("Mã phiếu kiểm kê '" + code + "' đã tồn tại");
            }
        }

        // Tìm danh sách sản phẩm active cần kiểm kê
        List<Product> productsToCount = (category != null)
                ? products.findByCategoryId(category.getId()).stream()
                .filter(p -> Boolean.TRUE.equals(p.getActive()))
                .toList()
                : products.findByActiveTrueOrderByNameAsc();

        if (productsToCount.isEmpty()) {
            throw new IllegalArgumentException(category != null
                    ? "Không tìm thấy sản phẩm đang hoạt động nào trong nhóm hàng '" + category.getName() + "'"
                    : "Hệ thống chưa có sản phẩm nào đang hoạt động để kiểm kê");
        }

        WarehouseStockCount sc = WarehouseStockCount.builder()
                .code(code)
                .warehouse(warehouse)
                .category(category)
                .status(StockCountStatus.DRAFT)
                .note(request.getNote())
                .createdBy(username != null ? username : "system")
                .createdAt(LocalDateTime.now())
                .items(new ArrayList<>())
                .build();

        // Tạo map nếu request có sẵn số đếm thực tế ban đầu
        Map<Long, StockCountItemUpdateRequest> initialCountMap = new HashMap<>();
        if (request.getItems() != null) {
            for (StockCountItemUpdateRequest itemReq : request.getItems()) {
                if (itemReq != null && itemReq.getProductId() != null) {
                    initialCountMap.put(itemReq.getProductId(), itemReq);
                }
            }
        }

        // Chốt số tồn sổ sách tại thời điểm tạo phiếu cho từng sản phẩm
        for (Product product : productsToCount) {
            WarehouseInventory inv = inventories
                    .findByWarehouseIdAndProductId(warehouse.getId(), product.getId())
                    .orElse(null);

            BigDecimal bookQty = (inv != null && inv.getBaseQuantity() != null)
                    ? inv.getBaseQuantity()
                    : BigDecimal.ZERO;

            WarehouseStockCountItem item = WarehouseStockCountItem.builder()
                    .product(product)
                    .baseUnit(product.getUnit())
                    .bookQuantity(bookQty)
                    .build();

            StockCountItemUpdateRequest initial = initialCountMap.get(product.getId());
            if (initial != null && initial.getActualQuantity() != null) {
                if (initial.getActualQuantity().compareTo(BigDecimal.ZERO) < 0) {
                    throw new IllegalArgumentException("Số lượng đếm thực tế của sản phẩm '" + product.getName() + "' không được âm");
                }
                item.setActualQuantity(initial.getActualQuantity());
                item.calculateDifference();
                item.setNote(initial.getNote());
            }

            sc.addItem(item);
        }

        WarehouseStockCount saved = stockCounts.save(sc);
        audit.logAction("STOCK_COUNT", saved.getCode(), "CREATE", null, saved.getStatus().name(),
                "Tạo phiếu kiểm kê kho " + warehouse.getCode(), username != null ? username : "system");

        return toDetailResponse(saved, access);
    }

    @Transactional
    public StockCountResponse updateItems(Long id, StockCountUpdateItemsRequest request, String username) {
        Scope access = scope(false);
        WarehouseStockCount sc = stockCounts.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy phiếu kiểm kê #" + id));
        access.require(sc.getWarehouse().getId());

        if (sc.getStatus() != StockCountStatus.DRAFT) {
            throw new IllegalStateException("Chỉ được nhập số đếm thực tế khi phiếu đang ở trạng thái mới tạo");
        }

        if (request == null || request.getItems() == null || request.getItems().isEmpty()) {
            throw new IllegalArgumentException("Danh sách sản phẩm kiểm đếm không được để trống");
        }

        Map<Long, WarehouseStockCountItem> itemMap = new HashMap<>();
        for (WarehouseStockCountItem item : sc.getItems()) {
            itemMap.put(item.getProduct().getId(), item);
        }

        for (StockCountItemUpdateRequest itemReq : request.getItems()) {
            if (itemReq == null || itemReq.getProductId() == null) continue;
            WarehouseStockCountItem item = itemMap.get(itemReq.getProductId());
            if (item == null) {
                throw new IllegalArgumentException("Sản phẩm ID " + itemReq.getProductId() + " không thuộc phiếu kiểm kê này");
            }
            if (itemReq.getActualQuantity() == null) {
                item.setActualQuantity(null);
                item.setDifferenceQuantity(null);
            } else {
                if (itemReq.getActualQuantity().compareTo(BigDecimal.ZERO) < 0) {
                    throw new IllegalArgumentException("Số lượng đếm thực tế không được âm cho sản phẩm '" + item.getProduct().getName() + "'");
                }
                item.setActualQuantity(itemReq.getActualQuantity());
                item.calculateDifference();
            }
            if (itemReq.getNote() != null) {
                item.setNote(itemReq.getNote());
            }
        }

        if (request.getNote() != null) {
            sc.setNote(request.getNote());
        }

        WarehouseStockCount saved = stockCounts.save(sc);
        return toDetailResponse(saved, access);
    }

    /**
     * Chốt kiểm kê điều chỉnh tồn về số thực đếm và bắt buộc nhập lý do.
     * CHỈ Quản lý kho (có quyền WAREHOUSE_ADJUST hoặc ROLE_ADMIN) được phép thực hiện!
     */
    @Transactional
    public StockCountResponse complete(Long id, StockCountCompleteRequest request, String username) {
        Scope access = scope(true); // Enforce WAREHOUSE_ADJUST or ADMIN
        WarehouseStockCount sc = stockCounts.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy phiếu kiểm kê #" + id));
        access.require(sc.getWarehouse().getId());

        if (sc.getStatus() != StockCountStatus.DRAFT) {
            throw new IllegalStateException("Phiếu kiểm kê đã được chốt hoặc đã bị hủy");
        }

        if (request == null || request.getReason() == null || request.getReason().trim().isBlank()) {
            throw new IllegalArgumentException("Bắt buộc nhập lý do khi chốt kiểm kê");
        }
        String reason = request.getReason().trim();

        // Nếu request gửi kèm số đếm thực tế để cập nhật và chốt luôn
        if (request.getItems() != null && !request.getItems().isEmpty()) {
            Map<Long, WarehouseStockCountItem> itemMap = new HashMap<>();
            for (WarehouseStockCountItem item : sc.getItems()) {
                itemMap.put(item.getProduct().getId(), item);
            }
            for (StockCountItemUpdateRequest itemReq : request.getItems()) {
                if (itemReq == null || itemReq.getProductId() == null) continue;
                WarehouseStockCountItem item = itemMap.get(itemReq.getProductId());
                if (item != null && itemReq.getActualQuantity() != null) {
                    if (itemReq.getActualQuantity().compareTo(BigDecimal.ZERO) < 0) {
                        throw new IllegalArgumentException("Số lượng đếm thực tế không được âm cho sản phẩm '" + item.getProduct().getName() + "'");
                    }
                    item.setActualQuantity(itemReq.getActualQuantity());
                    item.calculateDifference();
                    if (itemReq.getNote() != null) {
                        item.setNote(itemReq.getNote());
                    }
                }
            }
        }

        // Kiểm tra xem tất cả các dòng đã được nhập số thực đếm hay chưa
        boolean hasUncounted = sc.getItems().stream().anyMatch(i -> i.getActualQuantity() == null);
        if (hasUncounted) {
            throw new IllegalStateException("Vui lòng nhập số lượng đếm thực tế cho tất cả sản phẩm trước khi chốt kiểm kê");
        }

        Warehouse warehouse = sc.getWarehouse();
        int adjustedCount = 0;

        // Cập nhật tồn kho thực tế về số thực đếm cho từng sản phẩm
        for (WarehouseStockCountItem item : sc.getItems()) {
            Product product = item.getProduct();
            WarehouseInventory inv = inventories
                    .findByWarehouseIdAndProductId(warehouse.getId(), product.getId())
                    .orElseGet(() -> WarehouseInventory.builder()
                            .warehouse(warehouse)
                            .product(product)
                            .baseQuantity(BigDecimal.ZERO)
                            .baseUnit(item.getBaseUnit())
                            .build());

            BigDecimal beforeQty = inv.getBaseQuantity() != null ? inv.getBaseQuantity() : BigDecimal.ZERO;
            BigDecimal actualQty = item.getActualQuantity();

            inv.setBaseQuantity(actualQty);
            inventories.save(inv);

            if (beforeQty.compareTo(actualQty) != 0) {
                adjustedCount++;
                audit.logAction("INVENTORY", warehouse.getCode() + ":" + product.getCode(),
                        "STOCK_COUNT_ADJUST",
                        beforeQty.toPlainString() + " " + item.getBaseUnit(),
                        actualQty.toPlainString() + " " + item.getBaseUnit(),
                        reason, username != null ? username : "system");
            }
        }

        sc.setStatus(StockCountStatus.COMPLETED);
        sc.setAdjustmentReason(reason);
        sc.setCompletedBy(username != null ? username : "system");
        sc.setCompletedAt(LocalDateTime.now());

        WarehouseStockCount saved = stockCounts.save(sc);
        audit.logAction("STOCK_COUNT", saved.getCode(), "COMPLETE",
                StockCountStatus.DRAFT.name(), StockCountStatus.COMPLETED.name(),
                "Chốt kiểm kê (điều chỉnh " + adjustedCount + " dòng): " + reason,
                username != null ? username : "system");

        return toDetailResponse(saved, access);
    }

    @Transactional
    public StockCountResponse cancel(Long id, StockCountCancelRequest request, String username) {
        Scope access = scope(false);
        WarehouseStockCount sc = stockCounts.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy phiếu kiểm kê #" + id));
        access.require(sc.getWarehouse().getId());

        if (sc.getStatus() != StockCountStatus.DRAFT) {
            throw new IllegalStateException("Chỉ được hủy phiếu kiểm kê ở trạng thái mới tạo");
        }

        String cancelReason = (request != null && request.getReason() != null) ? request.getReason().trim() : null;
        sc.setStatus(StockCountStatus.CANCELLED);
        sc.setCancelledBy(username != null ? username : "system");
        sc.setCancelledAt(LocalDateTime.now());
        sc.setCancelReason(cancelReason);

        WarehouseStockCount saved = stockCounts.save(sc);
        audit.logAction("STOCK_COUNT", saved.getCode(), "CANCEL",
                StockCountStatus.DRAFT.name(), StockCountStatus.CANCELLED.name(),
                cancelReason, username != null ? username : "system");

        return toDetailResponse(saved, access);
    }

    private Specification<WarehouseStockCount> buildFilter(Scope access, Long warehouseId, Long categoryId,
                                                          StockCountStatus status, String keyword) {
        String term = escapeKeyword(keyword);
        return (root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            if (!access.global()) {
                if (access.ids().isEmpty()) {
                    predicates.add(cb.disjunction());
                } else {
                    predicates.add(root.get("warehouse").get("id").in(access.ids()));
                }
            }
            if (warehouseId != null) {
                predicates.add(cb.equal(root.get("warehouse").get("id"), warehouseId));
            }
            if (categoryId != null) {
                predicates.add(cb.equal(root.get("category").get("id"), categoryId));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (term != null) {
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("code")), term, '!'),
                        cb.like(cb.lower(root.get("note")), term, '!')
                ));
            }
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
    }

    private String escapeKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) return null;
        return "%" + keyword.trim().toLowerCase(Locale.ROOT)
                .replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_") + "%";
    }

    private StockCountResponse toSummaryResponse(WarehouseStockCount sc, Scope access) {
        int total = sc.getItems() != null ? sc.getItems().size() : 0;
        int matched = 0;
        int diff = 0;
        int uncounted = 0;

        if (sc.getItems() != null) {
            for (WarehouseStockCountItem item : sc.getItems()) {
                if (item.getActualQuantity() == null) {
                    uncounted++;
                } else if (item.getDifferenceQuantity() != null && item.getDifferenceQuantity().compareTo(BigDecimal.ZERO) == 0) {
                    matched++;
                } else {
                    diff++;
                }
            }
        }

        boolean canAdjust = access.canAdjust() && access.contains(sc.getWarehouse().getId())
                && sc.getStatus() == StockCountStatus.DRAFT;
        boolean canEdit = access.contains(sc.getWarehouse().getId())
                && sc.getStatus() == StockCountStatus.DRAFT;

        return StockCountResponse.builder()
                .id(sc.getId())
                .code(sc.getCode())
                .warehouseId(sc.getWarehouse().getId())
                .warehouseCode(sc.getWarehouse().getCode())
                .warehouseName(sc.getWarehouse().getName())
                .categoryId(sc.getCategory() != null ? sc.getCategory().getId() : null)
                .categoryName(sc.getCategory() != null ? sc.getCategory().getName() : null)
                .status(sc.getStatus())
                .statusLabel(sc.getStatus() != null ? sc.getStatus().getDisplayName() : null)
                .note(sc.getNote())
                .adjustmentReason(sc.getAdjustmentReason())
                .cancelReason(sc.getCancelReason())
                .createdBy(sc.getCreatedBy())
                .createdAt(sc.getCreatedAt())
                .completedBy(sc.getCompletedBy())
                .completedAt(sc.getCompletedAt())
                .cancelledBy(sc.getCancelledBy())
                .cancelledAt(sc.getCancelledAt())
                .totalItems(total)
                .matchedItems(matched)
                .diffItems(diff)
                .uncountedItems(uncounted)
                .canAdjust(canAdjust)
                .canEdit(canEdit)
                .build();
    }

    private StockCountResponse toDetailResponse(WarehouseStockCount sc, Scope access) {
        StockCountResponse resp = toSummaryResponse(sc, access);
        if (sc.getItems() != null) {
            List<StockCountItemResponse> itemResponses = sc.getItems().stream().map(item ->
                    StockCountItemResponse.builder()
                            .id(item.getId())
                            .productId(item.getProduct().getId())
                            .productCode(item.getProduct().getCode())
                            .productName(item.getProduct().getName())
                            .categoryName(item.getProduct().getCategory() != null ? item.getProduct().getCategory().getName() : null)
                            .baseUnit(item.getBaseUnit())
                            .bookQuantity(item.getBookQuantity())
                            .actualQuantity(item.getActualQuantity())
                            .differenceQuantity(item.getDifferenceQuantity())
                            .note(item.getNote())
                            .build()
            ).toList();
            resp.setItems(itemResponses);
        }
        return resp;
    }
}
