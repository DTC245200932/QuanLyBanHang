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
import java.math.*;
import java.time.LocalDateTime;
import java.util.*;

/** S5-07. Every entry point enforces live authorities AND warehouse data scope. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WarehouseTransferService {
    private final WarehouseTransferRepository transfers;
    private final WarehouseRepository warehouses;
    private final WarehouseInventoryRepository inventories;
    private final ProductRepository products;
    private final UserRepository users;
    private final UnitConversionService conversions;
    private final AuditLogService audit;
    private static final BigDecimal MAX_QUANTITY = new BigDecimal("99999999999.9999");

    private record Scope(boolean global, boolean write, Set<Long> ids) {
        boolean contains(Long id) { return global || ids.contains(id); }
        void require(Long id) {
            if (!contains(id)) throw new AccessDeniedException("Kho này không thuộc phạm vi được giao");
        }
    }

    private Scope scope(boolean mutation) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) throw new AccessDeniedException("Bạn chưa được xác thực");
        Set<String> grants = new HashSet<>();
        auth.getAuthorities().forEach(a -> grants.add(a.getAuthority()));
        boolean admin = grants.contains("ROLE_ADMIN");
        boolean write = admin || grants.contains("WAREHOUSE_TRANSFER");
        if ((mutation && !write) || (!mutation && !write && !grants.contains("WAREHOUSE_VIEW")))
            throw new AccessDeniedException("Bạn không có quyền chuyển hoặc xem chuyển kho");
        if (admin) return new Scope(true, true, Set.of());
        User user = users.findByUsername(auth.getName()).orElseThrow(() -> new AccessDeniedException("Không tìm thấy tài khoản"));
        Set<Long> ids = new HashSet<>();
        if (user.getWarehouses() != null) user.getWarehouses().forEach(w -> ids.add(w.getId()));
        // Roles may only restrict scope, never grant a missing matrix permission.
        boolean warehouseRole = grants.contains("ROLE_WAREHOUSE_STAFF") || grants.contains("ROLE_WAREHOUSE_MANAGER");
        return new Scope(ids.isEmpty() && !warehouseRole, write, ids);
    }

    public WarehouseTransferOptionsResponse options() {
        Scope access = scope(false);
        List<Warehouse> active = warehouses.findAllByActiveTrueOrderByNameAsc();
        return new WarehouseTransferOptionsResponse(active.stream().filter(w -> access.contains(w.getId())).map(this::named).toList(),
                active.stream().map(this::named).toList(), Arrays.stream(WarehouseTransferStatus.values())
                .map(s -> new WarehouseTransferOptionsResponse.StatusOption(s.name(), statusLabel(s))).toList(), access.write());
    }

    public Page<WarehouseTransferStockResponse> stock(Long warehouseId, String keyword, int page, int size) {
        Scope access = scope(false);
        if (warehouseId == null || warehouseId < 1) throw new IllegalArgumentException("Vui lòng chọn kho đi");
        access.require(warehouseId);
        Warehouse warehouse = warehouses.findById(warehouseId).orElseThrow(() -> new IllegalArgumentException("Không tìm thấy kho đi"));
        if (!Boolean.TRUE.equals(warehouse.getActive())) throw new IllegalArgumentException("Kho đi đã ngừng hoạt động");
        paging(page, size);
        return inventories.findTransferStock(warehouseId, term(keyword), PageRequest.of(page, size)).map(i ->
                new WarehouseTransferStockResponse(i.getProduct().getId(), i.getProduct().getCode(), i.getProduct().getName(),
                        i.getBaseUnit(), i.getBaseQuantity(), i.getReservedQuantity(), i.getAvailableQuantity()));
    }

    public Page<WarehouseTransferResponse> search(Long from, Long to, WarehouseTransferStatus status,
            String keyword, int page, int size) {
        Scope access = scope(false);
        return transfers.findAll(filter(access, from, to, status, keyword), paging(page, size))
                .map(t -> response(t, access));
    }

    public List<WarehouseTransferResponse> list(Long from, Long to, WarehouseTransferStatus status) {
        Scope access = scope(false);
        return transfers.findAll(filter(access, from, to, status, null), Sort.by(Sort.Direction.DESC, "createdAt", "id"))
                .stream().map(t -> response(t, access)).toList();
    }

    private Specification<WarehouseTransfer> filter(Scope access, Long from, Long to, WarehouseTransferStatus status, String keyword) {
        String term = term(keyword);
        if ((from != null && from < 1) || (to != null && to < 1)) throw new IllegalArgumentException("Mã kho không hợp lệ");
        return (root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            if (!access.global()) predicates.add(access.ids().isEmpty() ? cb.disjunction() : cb.or(
                    root.get("fromWarehouse").get("id").in(access.ids()), root.get("toWarehouse").get("id").in(access.ids())));
            if (from != null) predicates.add(cb.equal(root.get("fromWarehouse").get("id"), from));
            if (to != null) predicates.add(cb.equal(root.get("toWarehouse").get("id"), to));
            if (status != null) predicates.add(cb.equal(root.get("status"), status));
            if (term != null) predicates.add(cb.like(cb.lower(root.get("transferCode")), term, '!'));
            return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
    }

    public WarehouseTransferResponse get(Long id) {
        Scope access = scope(false);
        WarehouseTransfer transfer = transfers.findById(id).orElseThrow(() -> new IllegalArgumentException("Không tìm thấy phiếu chuyển kho"));
        requireVisible(access, transfer);
        return response(transfer, access);
    }

    @Transactional
    public WarehouseTransferResponse create(WarehouseTransferRequest request, String username) {
        Scope access = scope(true);
        if (request == null || request.getFromWarehouseId() == null || request.getToWarehouseId() == null)
            throw new IllegalArgumentException("Vui lòng chọn kho đi và kho đến");
        Long fromId = request.getFromWarehouseId(), toId = request.getToWarehouseId();
        if (fromId.equals(toId)) throw new IllegalArgumentException("Kho đi và kho đến không được trùng nhau");
        access.require(fromId);
        Map<Long, Warehouse> locked = lockWarehouses(fromId, toId);
        Warehouse from = locked.get(fromId), to = locked.get(toId);
        if (!Boolean.TRUE.equals(from.getActive()) || !Boolean.TRUE.equals(to.getActive()))
            throw new IllegalArgumentException("Kho đi và kho đến phải đang hoạt động");
        if (request.getItems() == null || request.getItems().isEmpty() || request.getItems().size() > 200)
            throw new IllegalArgumentException("Phiếu chuyển phải có từ 1 đến 200 dòng hàng");
        String code = text(request.getTransferCode(), 50, "Mã phiếu");
        if (code == null) code = "PCK-" + UUID.randomUUID();
        if (transfers.existsByTransferCode(code)) throw new IllegalArgumentException("Mã phiếu chuyển kho đã tồn tại");
        WarehouseTransfer transfer = WarehouseTransfer.builder().transferCode(code).fromWarehouse(from).toWarehouse(to)
                .status(WarehouseTransferStatus.IN_TRANSIT).note(text(request.getNote(), 500, "Ghi chú"))
                .createdBy(username).createdAt(LocalDateTime.now()).dispatchedAt(LocalDateTime.now()).items(new ArrayList<>()).build();
        Set<Long> seen = new HashSet<>();
        for (WarehouseTransferItemRequest line : request.getItems()) {
            if (line == null) throw new IllegalArgumentException("Dòng hàng không được trống");
            Product product = resolveProduct(line);
            if (!Boolean.TRUE.equals(product.getActive())) throw new IllegalArgumentException("Sản phẩm đã ngừng hoạt động");
            if (!seen.add(product.getId())) throw new IllegalArgumentException("Sản phẩm trùng dòng; vui lòng gộp số lượng");
            String unit = text(line.getUnit(), 50, "Đơn vị");
            if (unit == null) unit = product.getUnit();
            BigDecimal qty = quantity(line.getQuantity(), false), factor = conversions.getConversionFactor(product, unit);
            if (factor == null || factor.signum() <= 0) throw new IllegalArgumentException("Hệ số quy đổi không hợp lệ");
            BigDecimal base = quantity(qty.multiply(factor), false);
            transfer.addItem(WarehouseTransferItem.builder().product(product).unit(unit).quantity(qty).conversionFactor(factor)
                    .baseUnit(product.getUnit()).baseQuantity(base).note(text(line.getNote(), 255, "Ghi chú dòng")).build());
        }
        // Deterministic product + warehouse ordering also cooperates with order inventory locks.
        transfer.getItems().sort(Comparator.comparing(i -> i.getProduct().getId()));
        for (WarehouseTransferItem item : transfer.getItems()) {
            Map<Long, WarehouseInventory> stock = lockStock(locked, item);
            WarehouseInventory source = stock.get(fromId), target = stock.get(toId);
            if (source.getAvailableQuantity().compareTo(item.getBaseQuantity()) < 0)
                throw new IllegalArgumentException("Kho đi không đủ tồn khả dụng cho " + item.getProduct().getCode()
                        + " (khả dụng: " + source.getAvailableQuantity().toPlainString() + " " + item.getBaseUnit() + ")");
            if (!Objects.equals(source.getBaseUnit(), item.getBaseUnit()) || !Objects.equals(target.getBaseUnit(), item.getBaseUnit()))
                throw new IllegalArgumentException("Đơn vị cơ sở trong tồn kho không khớp sản phẩm; cần kiểm tra trước khi chuyển");
            BigDecimal before = source.getBaseQuantity();
            source.subtractBaseQuantity(item.getBaseQuantity());
            target.setInTransitQuantity(quantity(target.getInTransitQuantity().add(item.getBaseQuantity()), true));
            inventories.save(source); inventories.save(target);
            audit.logAction("INVENTORY", transfer.getTransferCode() + ":" + item.getProduct().getId(),
                    "TRANSFER_DISPATCH", before.toPlainString(), source.getBaseQuantity().toPlainString(), username);
        }
        return response(transfers.saveAndFlush(transfer), access);
    }

    @Transactional
    public WarehouseTransferResponse receive(Long id, WarehouseTransferReceiveRequest request, String username) {
        Scope access = scope(true);
        WarehouseTransfer transfer = lockedTransfer(id);
        access.require(transfer.getToWarehouse().getId());
        requireInTransit(transfer);
        Map<Long, Warehouse> locked = lockWarehouses(transfer.getFromWarehouse().getId(), transfer.getToWarehouse().getId());
        Map<Long, WarehouseTransferReceiveItemRequest> received = receiveLines(transfer, request);
        for (WarehouseTransferItem item : sortedItems(transfer)) {
            var line = received.get(item.getId());
            BigDecimal qty = line == null ? item.getQuantity() : quantity(line.getReceivedQuantity(), true);
            BigDecimal base = quantity(qty.multiply(item.getConversionFactor()), true);
            BigDecimal discrepancy = item.getBaseQuantity().subtract(base);
            String reason = line == null ? null : text(line.getDiscrepancyReason(), 500, "Lý do chênh lệch");
            if (discrepancy.signum() != 0 && reason == null) throw new IllegalArgumentException("Chênh lệch khi nhận "
                    + item.getProduct().getCode() + " bắt buộc nhập lý do");
            WarehouseInventory target = lockStock(locked, item).get(transfer.getToWarehouse().getId());
            removeTransit(target, item.getBaseQuantity());
            BigDecimal before = target.getBaseQuantity();
            target.setBaseQuantity(quantity(before.add(base), true));
            inventories.save(target);
            item.setReceivedQuantity(qty); item.setReceivedBaseQuantity(base);
            item.setDiscrepancyQuantity(item.getQuantity().subtract(qty)); item.setDiscrepancyBaseQuantity(discrepancy);
            item.setDiscrepancyReason(reason);
            audit.logAction("INVENTORY", transfer.getTransferCode() + ":" + item.getProduct().getId(),
                    "TRANSFER_RECEIVE", before.toPlainString(), target.getBaseQuantity().toPlainString()
                            + (reason == null ? "" : " (" + reason + ")"), username);
        }
        transfer.setReceiveNote(request == null ? null : text(request.getNote(), 500, "Ghi chú nhận"));
        transfer.setStatus(WarehouseTransferStatus.COMPLETED); transfer.setReceivedBy(username); transfer.setReceivedAt(LocalDateTime.now());
        return response(transfers.saveAndFlush(transfer), access);
    }

    @Transactional
    public WarehouseTransferResponse cancel(Long id, String reason, String username) {
        Scope access = scope(true);
        reason = text(reason, 500, "Lý do hủy");
        if (reason == null) throw new IllegalArgumentException("Vui lòng nhập lý do hủy phiếu chuyển");
        WarehouseTransfer transfer = lockedTransfer(id);
        access.require(transfer.getFromWarehouse().getId());
        requireInTransit(transfer);
        Map<Long, Warehouse> locked = lockWarehouses(transfer.getFromWarehouse().getId(), transfer.getToWarehouse().getId());
        for (WarehouseTransferItem item : sortedItems(transfer)) {
            Map<Long, WarehouseInventory> stock = lockStock(locked, item);
            WarehouseInventory source = stock.get(transfer.getFromWarehouse().getId()), target = stock.get(transfer.getToWarehouse().getId());
            removeTransit(target, item.getBaseQuantity());
            BigDecimal before = source.getBaseQuantity();
            source.setBaseQuantity(quantity(before.add(item.getBaseQuantity()), true));
            inventories.save(source); inventories.save(target);
            audit.logAction("INVENTORY", transfer.getTransferCode() + ":" + item.getProduct().getId(),
                    "TRANSFER_CANCEL", before.toPlainString(), source.getBaseQuantity().toPlainString() + " (" + reason + ")", username);
        }
        transfer.setStatus(WarehouseTransferStatus.CANCELLED); transfer.setCancelReason(reason);
        transfer.setCancelledBy(username); transfer.setCancelledAt(LocalDateTime.now());
        return response(transfers.saveAndFlush(transfer), access);
    }

    private WarehouseTransfer lockedTransfer(Long id) {
        return transfers.findByIdForUpdate(id).orElseThrow(() -> new IllegalArgumentException("Không tìm thấy phiếu chuyển kho"));
    }

    private void requireInTransit(WarehouseTransfer transfer) {
        if (transfer.getStatus() != WarehouseTransferStatus.IN_TRANSIT) throw new IllegalStateException("Phiếu không còn đang chuyển, không thể nhận/hủy lần nữa");
    }

    private Map<Long, Warehouse> lockWarehouses(Long first, Long second) {
        Map<Long, Warehouse> result = new LinkedHashMap<>();
        for (Long id : new TreeSet<>(List.of(first, second))) result.put(id, warehouses.findByIdForUpdate(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy kho " + id)));
        return result;
    }

    private Map<Long, WarehouseInventory> lockStock(Map<Long, Warehouse> locked, WarehouseTransferItem item) {
        Map<Long, WarehouseInventory> result = new HashMap<>();
        for (Warehouse warehouse : locked.values()) {
            WarehouseInventory stock = inventories.findByWarehouseIdAndProductIdForUpdate(warehouse.getId(), item.getProduct().getId())
                    .orElseGet(() -> inventories.saveAndFlush(WarehouseInventory.builder().warehouse(warehouse).product(item.getProduct())
                            .baseUnit(item.getBaseUnit()).baseQuantity(BigDecimal.ZERO).build()));
            if (!Objects.equals(stock.getBaseUnit(), item.getBaseUnit()))
                throw new IllegalStateException("Đơn vị tồn kho không khớp đơn vị đóng băng trên phiếu chuyển");
            result.put(warehouse.getId(), stock);
        }
        return result;
    }

    private void removeTransit(WarehouseInventory stock, BigDecimal quantity) {
        if (stock.getInTransitQuantity().compareTo(quantity) < 0)
            throw new IllegalStateException("Lượng hàng đang chuyển không khớp phiếu; cần kiểm tra số liệu");
        stock.setInTransitQuantity(stock.getInTransitQuantity().subtract(quantity));
    }

    private Map<Long, WarehouseTransferReceiveItemRequest> receiveLines(WarehouseTransfer transfer, WarehouseTransferReceiveRequest request) {
        Map<Long, WarehouseTransferReceiveItemRequest> result = new HashMap<>();
        if (request == null || request.getItems() == null || request.getItems().isEmpty()) return result;
        for (var line : request.getItems()) {
            if (line == null || (line.getItemId() == null && line.getProductId() == null)) throw new IllegalArgumentException("Thiếu mã dòng nhận hàng");
            var matches = transfer.getItems().stream().filter(i -> line.getItemId() != null
                    ? i.getId().equals(line.getItemId()) : i.getProduct().getId().equals(line.getProductId())).toList();
            if (matches.size() != 1) throw new IllegalArgumentException("Dòng nhận hàng không thuộc phiếu hoặc bị trùng SKU");
            var item = matches.get(0);
            if (line.getProductId() != null && !item.getProduct().getId().equals(line.getProductId()))
                throw new IllegalArgumentException("Mã sản phẩm không khớp dòng phiếu");
            if (result.put(item.getId(), line) != null) throw new IllegalArgumentException("Dòng nhận hàng bị lặp");
            quantity(line.getReceivedQuantity(), true);
        }
        if (result.size() != transfer.getItems().size()) throw new IllegalArgumentException("Vui lòng nhập thực nhận cho tất cả dòng hàng");
        return result;
    }

    private void requireVisible(Scope access, WarehouseTransfer transfer) {
        if (!access.contains(transfer.getFromWarehouse().getId()) && !access.contains(transfer.getToWarehouse().getId()))
            throw new AccessDeniedException("Phiếu không thuộc phạm vi kho được giao");
    }
    private List<WarehouseTransferItem> sortedItems(WarehouseTransfer transfer) {
        return transfer.getItems().stream().sorted(Comparator.comparing(i -> i.getProduct().getId())).toList();
    }
    private Product resolveProduct(WarehouseTransferItemRequest line) {
        Product product = line.getProductId() != null ? products.findById(line.getProductId()).orElse(null)
                : line.getProductCode() == null ? null : products.findByCode(line.getProductCode().trim().toUpperCase(Locale.ROOT)).orElse(null);
        if (product == null) throw new IllegalArgumentException("Không tìm thấy sản phẩm chuyển kho");
        return product;
    }
    private static BigDecimal quantity(BigDecimal value, boolean allowZero) {
        if (value == null || value.signum() < 0 || (!allowZero && value.signum() == 0)
                || value.stripTrailingZeros().scale() > 4 || value.compareTo(MAX_QUANTITY) > 0)
            throw new IllegalArgumentException("Số lượng không hợp lệ (tối đa 4 chữ số thập phân)");
        return value.setScale(4, RoundingMode.UNNECESSARY);
    }
    private static String text(String value, int max, String label) {
        if (value == null || value.isBlank()) return null;
        value = value.trim();
        if (value.length() > max) throw new IllegalArgumentException(label + " tối đa " + max + " ký tự");
        return value;
    }
    private static String term(String keyword) {
        String value = text(keyword, 150, "Từ khóa");
        return value == null ? null : "%" + value.toLowerCase(Locale.ROOT).replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
    }
    private static PageRequest paging(int page, int size) {
        if (page < 0 || size < 1 || size > 200) throw new IllegalArgumentException("Trang phải không âm; mỗi trang từ 1 đến 200 dòng");
        return PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id"));
    }
    private NamedResourceResponse named(Warehouse w) { return new NamedResourceResponse(w.getId(), w.getCode(), w.getName()); }
    private String statusLabel(WarehouseTransferStatus status) {
        return switch (status) { case IN_TRANSIT -> "Đang trên đường"; case COMPLETED -> "Đã nhận hàng"; case CANCELLED -> "Đã hủy"; };
    }
    private WarehouseTransferResponse response(WarehouseTransfer t, Scope access) {
        boolean pending = t.getStatus() == WarehouseTransferStatus.IN_TRANSIT;
        return WarehouseTransferResponse.builder().id(t.getId()).transferCode(t.getTransferCode())
                .fromWarehouseId(t.getFromWarehouse().getId()).fromWarehouseCode(t.getFromWarehouse().getCode()).fromWarehouseName(t.getFromWarehouse().getName())
                .toWarehouseId(t.getToWarehouse().getId()).toWarehouseCode(t.getToWarehouse().getCode()).toWarehouseName(t.getToWarehouse().getName())
                .status(t.getStatus()).statusDescription(statusLabel(t.getStatus()))
                .canReceive(pending && access.write() && access.contains(t.getToWarehouse().getId()))
                .canCancel(pending && access.write() && access.contains(t.getFromWarehouse().getId()))
                .note(t.getNote()).receiveNote(t.getReceiveNote()).cancelReason(t.getCancelReason()).createdBy(t.getCreatedBy())
                .createdAt(t.getCreatedAt()).dispatchedAt(t.getDispatchedAt()).receivedBy(t.getReceivedBy()).receivedAt(t.getReceivedAt())
                .cancelledBy(t.getCancelledBy()).cancelledAt(t.getCancelledAt()).items(t.getItems().stream().map(i ->
                    WarehouseTransferItemResponse.builder().id(i.getId()).productId(i.getProduct().getId()).productCode(i.getProduct().getCode())
                    .productName(i.getProduct().getName()).unit(i.getUnit()).quantity(i.getQuantity()).conversionFactor(i.getConversionFactor())
                    .baseUnit(i.getBaseUnit()).baseQuantity(i.getBaseQuantity()).receivedQuantity(i.getReceivedQuantity())
                    .receivedBaseQuantity(i.getReceivedBaseQuantity()).discrepancyQuantity(i.getDiscrepancyQuantity())
                    .discrepancyBaseQuantity(i.getDiscrepancyBaseQuantity()).discrepancyReason(i.getDiscrepancyReason()).note(i.getNote()).build()).toList()).build();
    }
}
