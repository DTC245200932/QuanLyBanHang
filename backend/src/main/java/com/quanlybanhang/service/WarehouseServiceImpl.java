package com.quanlybanhang.service;

import com.quanlybanhang.dto.request.ProductLocationRequest;
import com.quanlybanhang.dto.request.WarehouseLocationRequest;
import com.quanlybanhang.dto.request.WarehouseRequest;
import com.quanlybanhang.dto.request.WarehouseVoucherItemRequest;
import com.quanlybanhang.dto.request.WarehouseVoucherRequest;
import com.quanlybanhang.dto.response.*;
import com.quanlybanhang.model.*;
import com.quanlybanhang.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WarehouseServiceImpl implements WarehouseService {

    private final WarehouseRepository warehouseRepository;
    private final WarehouseLocationRepository warehouseLocationRepository;
    private final WarehouseLocationProductRepository locationProductRepository;
    private final ProductRepository productRepository;
    private final UnitConversionRepository unitConversionRepository;
    private final UnitConversionService unitConversionService;
    private final WarehouseVoucherRepository voucherRepository;
    private final WarehouseInventoryRepository inventoryRepository;
    private final UserRepository userRepository;
    private final DealerRepository dealerRepository;
    private final OrderRepository orderRepository;
    private final SupplierRepository supplierRepository;
    private final WarehouseLotInventoryRepository lotInventoryRepository;
    private final AuditLogService auditLogService;

    // ═══════════════════════════════════════════════════════════════
    // 1. OPTIONS & DANH SÁCH NHANH
    // ═══════════════════════════════════════════════════════════════

    @Override
    @Transactional(readOnly = true)
    public List<NamedResourceResponse> getWarehouseOptions() {
        return warehouseRepository.findAllByActiveTrueOrderByNameAsc().stream()
                .map(warehouse -> new NamedResourceResponse(
                        warehouse.getId(), warehouse.getCode(), warehouse.getName()))
                .toList();
    }

    // ═══════════════════════════════════════════════════════════════
    // 2. QUẢN LÝ KHO HÀNG (S5-03)
    // ═══════════════════════════════════════════════════════════════

    @Override
    @Transactional(readOnly = true)
    public List<WarehouseResponse> getAllWarehouses(String keyword, Boolean active) {
        List<Warehouse> list = warehouseRepository.findAllByOrderByCodeAsc();

        String kw = (keyword != null && !keyword.isBlank()) ? keyword.trim().toLowerCase() : null;

        return list.stream()
                .filter(w -> {
                    if (active != null && !active.equals(w.getActive())) {
                        return false;
                    }
                    if (kw != null) {
                        boolean matchCode = w.getCode() != null && w.getCode().toLowerCase().contains(kw);
                        boolean matchName = w.getName() != null && w.getName().toLowerCase().contains(kw);
                        boolean matchAddress = w.getAddress() != null && w.getAddress().toLowerCase().contains(kw);
                        boolean matchManager = (w.getManagerName() != null && w.getManagerName().toLowerCase().contains(kw))
                                || (w.getManager() != null && (
                                (w.getManager().getFullName() != null && w.getManager().getFullName().toLowerCase().contains(kw))
                                || (w.getManager().getUsername() != null && w.getManager().getUsername().toLowerCase().contains(kw))));
                        return matchCode || matchName || matchAddress || matchManager;
                    }
                    return true;
                })
                .map(this::mapToWarehouseResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public WarehouseResponse getWarehouseById(Long id) {
        Warehouse warehouse = warehouseRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy kho với ID: " + id));

        WarehouseResponse response = mapToWarehouseResponse(warehouse);

        // Nạp danh sách vị trí lưu trực thuộc kho
        List<WarehouseLocation> locations = warehouseLocationRepository.findByWarehouseIdOrderByCodeAsc(id);
        response.setLocations(locations.stream().map(this::mapToLocationResponse).collect(Collectors.toList()));

        return response;
    }

    @Override
    @Transactional
    public WarehouseResponse createWarehouse(WarehouseRequest request, String username) {
        if (request == null) {
            throw new IllegalArgumentException("Dữ liệu kho không được để trống");
        }
        if (request.getCode() == null || request.getCode().isBlank()) {
            throw new IllegalArgumentException("Mã kho không được để trống");
        }
        if (request.getName() == null || request.getName().isBlank()) {
            throw new IllegalArgumentException("Tên kho không được để trống");
        }

        String code = request.getCode().trim().toUpperCase();
        if (warehouseRepository.existsByCode(code)) {
            throw new IllegalArgumentException("Mã kho '" + code + "' đã tồn tại trong hệ thống");
        }

        User manager = null;
        if (request.getManagerUserId() != null) {
            manager = userRepository.findById(request.getManagerUserId())
                    .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy người dùng phụ trách với ID: " + request.getManagerUserId()));
        }

        String managerName = request.getManagerName() != null && !request.getManagerName().isBlank()
                ? request.getManagerName().trim()
                : (manager != null ? manager.getFullName() : null);

        Warehouse warehouse = Warehouse.builder()
                .code(code)
                .name(request.getName().trim())
                .address(request.getAddress() != null ? request.getAddress().trim() : null)
                .manager(manager)
                .managerName(managerName)
                .active(request.getActive() != null ? request.getActive() : true)
                .build();

        Warehouse saved = warehouseRepository.save(warehouse);

        if (auditLogService != null) {
            auditLogService.logAction(
                    "WAREHOUSE",
                    saved.getId().toString(),
                    "CREATE_WAREHOUSE",
                    null,
                    "Tạo mới kho '" + saved.getName() + "' (" + saved.getCode() + ")",
                    username != null ? username : "system"
            );
        }

        return mapToWarehouseResponse(saved);
    }

    @Override
    @Transactional
    public WarehouseResponse updateWarehouse(Long id, WarehouseRequest request, String username) {
        if (id == null) {
            throw new IllegalArgumentException("ID kho không được để trống");
        }
        if (request == null) {
            throw new IllegalArgumentException("Dữ liệu cập nhật kho không được để trống");
        }

        Warehouse warehouse = warehouseRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy kho với ID: " + id));

        if (request.getCode() != null && !request.getCode().isBlank()) {
            String newCode = request.getCode().trim().toUpperCase();
            if (!newCode.equalsIgnoreCase(warehouse.getCode())) {
                if (warehouseRepository.existsByCodeAndIdNot(newCode, id)) {
                    throw new IllegalArgumentException("Mã kho '" + newCode + "' đã được sử dụng bởi kho khác");
                }
                warehouse.setCode(newCode);
            }
        }

        if (request.getName() != null && !request.getName().isBlank()) {
            warehouse.setName(request.getName().trim());
        }

        if (request.getAddress() != null) {
            warehouse.setAddress(request.getAddress().trim());
        }

        if (request.getManagerUserId() != null) {
            if (request.getManagerUserId() <= 0) {
                warehouse.setManager(null);
            } else {
                User manager = userRepository.findById(request.getManagerUserId())
                        .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy người dùng phụ trách với ID: " + request.getManagerUserId()));
                warehouse.setManager(manager);
            }
        }

        if (request.getManagerName() != null) {
            warehouse.setManagerName(request.getManagerName().trim());
        } else if (warehouse.getManager() != null && (warehouse.getManagerName() == null || warehouse.getManagerName().isBlank())) {
            warehouse.setManagerName(warehouse.getManager().getFullName());
        }

        if (request.getActive() != null) {
            warehouse.setActive(request.getActive());
        }

        Warehouse saved = warehouseRepository.save(warehouse);

        if (auditLogService != null) {
            auditLogService.logAction(
                    "WAREHOUSE",
                    saved.getId().toString(),
                    "UPDATE_WAREHOUSE",
                    null,
                    "Cập nhật thông tin kho '" + saved.getName() + "' (" + saved.getCode() + ")",
                    username != null ? username : "system"
            );
        }

        return mapToWarehouseResponse(saved);
    }

    @Override
    @Transactional
    public void deleteWarehouse(Long id, String username) {
        Warehouse warehouse = warehouseRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy kho với ID: " + id));

        // Kiểm tra ràng buộc phiếu kho
        List<WarehouseVoucher> vouchers = voucherRepository.findByWarehouseIdOrderByRecordedAtDesc(id);
        if (vouchers != null && !vouchers.isEmpty()) {
            // Có phiếu kho -> chỉ chuyển trạng thái ngừng hoạt động
            warehouse.setActive(false);
            warehouseRepository.save(warehouse);
            if (auditLogService != null) {
                auditLogService.logAction(
                        "WAREHOUSE",
                        warehouse.getId().toString(),
                        "DEACTIVATE_WAREHOUSE",
                        null,
                        "Ngừng hoạt động kho '" + warehouse.getName() + "' do đã phát sinh giao dịch/phiếu kho",
                        username != null ? username : "system"
                );
            }
            return;
        }

        // Kiểm tra vị trí kho
        long locCount = warehouseLocationRepository.countByWarehouseId(id);
        if (locCount > 0) {
            warehouse.setActive(false);
            warehouseRepository.save(warehouse);
            if (auditLogService != null) {
                auditLogService.logAction(
                        "WAREHOUSE",
                        warehouse.getId().toString(),
                        "DEACTIVATE_WAREHOUSE",
                        null,
                        "Ngừng hoạt động kho '" + warehouse.getName() + "' do đang có " + locCount + " vị trí lưu trực thuộc",
                        username != null ? username : "system"
                );
            }
            return;
        }

        warehouseRepository.delete(warehouse);
        if (auditLogService != null) {
            auditLogService.logAction(
                    "WAREHOUSE",
                    id.toString(),
                    "DELETE_WAREHOUSE",
                    null,
                    "Xóa kho '" + warehouse.getName() + "' (" + warehouse.getCode() + ")",
                    username != null ? username : "system"
            );
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // 3. QUẢN LÝ VỊ TRÍ LƯU TRONG KHO (S5-03: KỆ HOẶC KHU)
    // ═══════════════════════════════════════════════════════════════

    @Override
    @Transactional(readOnly = true)
    public List<WarehouseLocationResponse> getLocations(Long warehouseId, LocationType type, Long parentId, Boolean active, String keyword) {
        List<WarehouseLocation> locations;

        if (warehouseId != null) {
            locations = warehouseLocationRepository.findByWarehouseIdOrderByCodeAsc(warehouseId);
        } else {
            locations = warehouseLocationRepository.findAll();
        }

        String kw = (keyword != null && !keyword.isBlank()) ? keyword.trim().toLowerCase() : null;

        return locations.stream()
                .filter(loc -> {
                    if (type != null && loc.getLocationType() != type) {
                        return false;
                    }
                    if (parentId != null) {
                        if (loc.getParent() == null || !parentId.equals(loc.getParent().getId())) {
                            return false;
                        }
                    }
                    if (active != null && !active.equals(loc.getActive())) {
                        return false;
                    }
                    if (kw != null) {
                        boolean matchCode = loc.getCode() != null && loc.getCode().toLowerCase().contains(kw);
                        boolean matchName = loc.getName() != null && loc.getName().toLowerCase().contains(kw);
                        boolean matchDesc = loc.getDescription() != null && loc.getDescription().toLowerCase().contains(kw);
                        return matchCode || matchName || matchDesc;
                    }
                    return true;
                })
                .map(this::mapToLocationResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public WarehouseLocationResponse getLocationById(Long id) {
        WarehouseLocation location = warehouseLocationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy vị trí lưu kho với ID: " + id));

        WarehouseLocationResponse response = mapToLocationResponse(location);

        // Nạp danh sách sản phẩm đang lưu tại vị trí này
        List<WarehouseLocationProduct> products = locationProductRepository.findByLocationId(id);
        response.setProducts(products.stream().map(this::mapToProductLocationResponse).collect(Collectors.toList()));

        // Nạp danh sách kệ con nếu là Khu (ZONE)
        if (location.getLocationType() == LocationType.ZONE) {
            List<WarehouseLocation> childShelves = warehouseLocationRepository.findByParentIdOrderByCodeAsc(id);
            response.setChildShelves(childShelves.stream().map(this::mapToLocationResponse).collect(Collectors.toList()));
        }

        return response;
    }

    @Override
    @Transactional
    public WarehouseLocationResponse createLocation(Long warehouseId, WarehouseLocationRequest request, String username) {
        if (request == null) {
            throw new IllegalArgumentException("Dữ liệu vị trí kho không được để trống");
        }

        Long targetWarehouseId = warehouseId != null ? warehouseId : request.getWarehouseId();
        if (targetWarehouseId == null) {
            throw new IllegalArgumentException("ID kho không được để trống");
        }

        Warehouse warehouse = warehouseRepository.findById(targetWarehouseId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy kho với ID: " + targetWarehouseId));

        if (request.getCode() == null || request.getCode().isBlank()) {
            throw new IllegalArgumentException("Mã vị trí không được để trống");
        }
        if (request.getName() == null || request.getName().isBlank()) {
            throw new IllegalArgumentException("Tên vị trí không được để trống");
        }

        String code = request.getCode().trim().toUpperCase();
        if (warehouseLocationRepository.existsByWarehouseIdAndCode(targetWarehouseId, code)) {
            throw new IllegalArgumentException("Mã vị trí '" + code + "' đã tồn tại trong kho " + warehouse.getName());
        }

        LocationType locationType = request.getLocationType() != null ? request.getLocationType() : LocationType.SHELF;

        WarehouseLocation parent = null;
        if (request.getParentLocationId() != null) {
            parent = warehouseLocationRepository.findById(request.getParentLocationId())
                    .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy vị trí cha với ID: " + request.getParentLocationId()));

            if (!parent.getWarehouse().getId().equals(targetWarehouseId)) {
                throw new IllegalArgumentException("Vị trí cha phải thuộc cùng một kho (" + warehouse.getName() + ")");
            }
            if (parent.getLocationType() != LocationType.ZONE) {
                throw new IllegalArgumentException("Vị trí cha phải là Khu vực (ZONE)");
            }
        }

        WarehouseLocation location = WarehouseLocation.builder()
                .warehouse(warehouse)
                .code(code)
                .name(request.getName().trim())
                .locationType(locationType)
                .parent(parent)
                .description(request.getDescription() != null ? request.getDescription().trim() : null)
                .active(request.getActive() != null ? request.getActive() : true)
                .build();

        WarehouseLocation saved = warehouseLocationRepository.save(location);

        if (auditLogService != null) {
            auditLogService.logAction(
                    "WAREHOUSE_LOCATION",
                    saved.getId().toString(),
                    "CREATE_LOCATION",
                    null,
                    "Khai báo vị trí " + saved.getLocationType().getDisplayName() + " '" + saved.getName() +
                            "' (" + saved.getCode() + ") trong kho " + warehouse.getName(),
                    username != null ? username : "system"
            );
        }

        return mapToLocationResponse(saved);
    }

    @Override
    @Transactional
    public WarehouseLocationResponse updateLocation(Long id, WarehouseLocationRequest request, String username) {
        if (id == null) {
            throw new IllegalArgumentException("ID vị trí kho không được để trống");
        }
        if (request == null) {
            throw new IllegalArgumentException("Dữ liệu cập nhật vị trí không được để trống");
        }

        WarehouseLocation location = warehouseLocationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy vị trí kho với ID: " + id));

        if (request.getCode() != null && !request.getCode().isBlank()) {
            String newCode = request.getCode().trim().toUpperCase();
            if (!newCode.equalsIgnoreCase(location.getCode())) {
                if (warehouseLocationRepository.existsByWarehouseIdAndCodeAndIdNot(location.getWarehouse().getId(), newCode, id)) {
                    throw new IllegalArgumentException("Mã vị trí '" + newCode + "' đã được sử dụng trong kho này");
                }
                location.setCode(newCode);
            }
        }

        if (request.getName() != null && !request.getName().isBlank()) {
            location.setName(request.getName().trim());
        }

        if (request.getLocationType() != null) {
            location.setLocationType(request.getLocationType());
        }

        if (request.getParentLocationId() != null) {
            if (request.getParentLocationId().equals(id)) {
                throw new IllegalArgumentException("Vị trí không thể là cha của chính nó");
            }
            WarehouseLocation parent = warehouseLocationRepository.findById(request.getParentLocationId())
                    .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy vị trí cha với ID: " + request.getParentLocationId()));

            if (!parent.getWarehouse().getId().equals(location.getWarehouse().getId())) {
                throw new IllegalArgumentException("Vị trí cha phải thuộc cùng một kho");
            }
            if (parent.getLocationType() != LocationType.ZONE) {
                throw new IllegalArgumentException("Vị trí cha phải là Khu vực (ZONE)");
            }
            location.setParent(parent);
        } else if (request.getParentLocationId() != null && request.getParentLocationId() <= 0) {
            location.setParent(null);
        }

        if (request.getDescription() != null) {
            location.setDescription(request.getDescription().trim());
        }

        if (request.getActive() != null) {
            location.setActive(request.getActive());
        }

        WarehouseLocation saved = warehouseLocationRepository.save(location);

        if (auditLogService != null) {
            auditLogService.logAction(
                    "WAREHOUSE_LOCATION",
                    saved.getId().toString(),
                    "UPDATE_LOCATION",
                    null,
                    "Cập nhật vị trí " + saved.getLocationType().getDisplayName() + " '" + saved.getName() + "' (" + saved.getCode() + ")",
                    username != null ? username : "system"
            );
        }

        return mapToLocationResponse(saved);
    }

    @Override
    @Transactional
    public void deleteLocation(Long id, String username) {
        WarehouseLocation location = warehouseLocationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy vị trí kho với ID: " + id));

        long childCount = warehouseLocationRepository.countByParentId(id);
        if (childCount > 0) {
            throw new IllegalArgumentException("Không thể xoá vì vị trí này đang chứa " + childCount + " kệ/khu trực thuộc");
        }

        long productCount = locationProductRepository.countByLocationId(id);
        if (productCount > 0) {
            throw new IllegalArgumentException("Không thể xoá vì vị trí này đang có " + productCount + " sản phẩm được gán lưu trữ");
        }

        warehouseLocationRepository.delete(location);

        if (auditLogService != null) {
            auditLogService.logAction(
                    "WAREHOUSE_LOCATION",
                    id.toString(),
                    "DELETE_LOCATION",
                    null,
                    "Xóa vị trí " + location.getLocationType().getDisplayName() + " '" + location.getName() + "' (" + location.getCode() + ")",
                    username != null ? username : "system"
            );
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // 4. GÁN SẢN PHẨM VÀO VỊ TRÍ KỆ / KHU (S5-03)
    // ═══════════════════════════════════════════════════════════════

    @Override
    @Transactional
    public ProductLocationResponse assignProductToLocation(Long locationId, ProductLocationRequest request, String username) {
        if (locationId == null) {
            throw new IllegalArgumentException("ID vị trí không được để trống");
        }
        if (request == null || request.getProductId() == null) {
            throw new IllegalArgumentException("ID sản phẩm không được để trống");
        }

        WarehouseLocation location = warehouseLocationRepository.findById(locationId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy vị trí kho với ID: " + locationId));

        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy sản phẩm với ID: " + request.getProductId()));

        boolean isDefault = request.getIsDefault() != null ? request.getIsDefault() : true;

        // Nếu đặt làm vị trí mặc định trong kho, xóa cờ mặc định cũ của sản phẩm tại kho này
        if (isDefault) {
            List<WarehouseLocationProduct> existingWarehouseMappings =
                    locationProductRepository.findByWarehouseIdAndProductId(location.getWarehouse().getId(), product.getId());
            for (WarehouseLocationProduct m : existingWarehouseMappings) {
                if (Boolean.TRUE.equals(m.getIsDefault()) && !m.getLocation().getId().equals(locationId)) {
                    m.setIsDefault(false);
                    locationProductRepository.save(m);
                }
            }
        }

        WarehouseLocationProduct mapping = locationProductRepository.findByLocationIdAndProductId(locationId, product.getId())
                .orElseGet(() -> WarehouseLocationProduct.builder()
                        .location(location)
                        .product(product)
                        .build());

        mapping.setIsDefault(isDefault);
        if (request.getNote() != null) {
            mapping.setNote(request.getNote().trim());
        }

        WarehouseLocationProduct saved = locationProductRepository.save(mapping);

        if (auditLogService != null) {
            auditLogService.logAction(
                    "WAREHOUSE_LOCATION_PRODUCT",
                    saved.getId() != null ? saved.getId().toString() : "N/A",
                    "ASSIGN_PRODUCT_LOCATION",
                    null,
                    "Gán sản phẩm '" + product.getName() + "' (" + product.getCode() + ") vào vị trí '" +
                            location.getName() + "' (" + location.getCode() + ") trong kho " + location.getWarehouse().getName(),
                    username != null ? username : "system"
            );
        }

        return mapToProductLocationResponse(saved);
    }

    @Override
    @Transactional
    public void removeProductFromLocation(Long locationId, Long productId, String username) {
        if (locationId == null || productId == null) {
            throw new IllegalArgumentException("ID vị trí và ID sản phẩm không được để trống");
        }

        WarehouseLocationProduct mapping = locationProductRepository.findByLocationIdAndProductId(locationId, productId)
                .orElseThrow(() -> new IllegalArgumentException("Sản phẩm chưa được gán vào vị trí này"));

        locationProductRepository.delete(mapping);

        if (auditLogService != null) {
            auditLogService.logAction(
                    "WAREHOUSE_LOCATION_PRODUCT",
                    mapping.getId().toString(),
                    "REMOVE_PRODUCT_LOCATION",
                    null,
                    "Bỏ gán sản phẩm ID " + productId + " khỏi vị trí ID " + locationId,
                    username != null ? username : "system"
            );
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductLocationResponse> getProductsByLocation(Long locationId) {
        return locationProductRepository.findByLocationId(locationId).stream()
                .map(this::mapToProductLocationResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductLocationResponse> getLocationsByProduct(Long productId, Long warehouseId) {
        List<WarehouseLocationProduct> list;
        if (warehouseId != null) {
            list = locationProductRepository.findByWarehouseIdAndProductId(warehouseId, productId);
        } else {
            list = locationProductRepository.findByProductId(productId);
        }
        return list.stream().map(this::mapToProductLocationResponse).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public ProductLocationResponse getProductLocationInWarehouse(Long warehouseId, Long productId) {
        if (warehouseId == null || productId == null) return null;
        List<WarehouseLocationProduct> list = locationProductRepository.findByWarehouseIdAndProductId(warehouseId, productId);
        if (list.isEmpty()) return null;
        return mapToProductLocationResponse(list.get(0));
    }

    // ═══════════════════════════════════════════════════════════════
    // 5. SOẠN ĐƠN HÀNG KÈM VỊ TRÍ KỆ CHÍNH XÁC (S5-03)
    // ═══════════════════════════════════════════════════════════════

    @Override
    @Transactional(readOnly = true)
    public OrderPickingListResponse getOrderPickingList(Long orderId) {
        if (orderId == null) {
            throw new IllegalArgumentException("ID đơn hàng không được để trống");
        }

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn hàng với ID: " + orderId));

        Warehouse warehouse = order.getWarehouse();
        if (warehouse == null && order.getDealer() != null) {
            warehouse = order.getDealer().getWarehouse();
        }

        List<OrderPickingItemResponse> itemResponses = new ArrayList<>();

        if (order.getItems() != null) {
            for (OrderItem item : order.getItems()) {
                Product p = item.getProduct();
                if (p == null) continue;

                // Tìm vị trí kệ trong kho xuất hàng
                WarehouseLocation loc = null;
                if (warehouse != null) {
                    List<WarehouseLocationProduct> mappings =
                            locationProductRepository.findByWarehouseIdAndProductId(warehouse.getId(), p.getId());
                    if (!mappings.isEmpty()) {
                        loc = mappings.get(0).getLocation();
                    }
                }

                // Lấy tồn khả dụng tại kho
                BigDecimal availableQty = BigDecimal.ZERO;
                if (warehouse != null) {
                    Optional<WarehouseInventory> invOpt =
                            inventoryRepository.findByWarehouseIdAndProductId(warehouse.getId(), p.getId());
                    if (invOpt.isPresent()) {
                        availableQty = invOpt.get().getAvailableQuantity();
                    }
                }

                String zoneName = null;
                String shelfName = null;
                String locDisplay = "Chưa gán vị trí";

                if (loc != null) {
                    if (loc.getParent() != null) {
                        zoneName = loc.getParent().getName();
                        shelfName = loc.getName();
                        locDisplay = loc.getParent().getName() + " > " + loc.getName();
                    } else if (loc.getLocationType() == LocationType.ZONE) {
                        zoneName = loc.getName();
                        locDisplay = loc.getName();
                    } else {
                        shelfName = loc.getName();
                        locDisplay = loc.getName();
                    }
                }

                itemResponses.add(OrderPickingItemResponse.builder()
                        .orderItemId(item.getId())
                        .productId(p.getId())
                        .productCode(p.getCode())
                        .productName(p.getName())
                        .quantity(item.getQuantity())
                        .unit(item.getUnit())
                        .baseQuantity(item.getBaseQuantity())
                        .baseUnit(item.getBaseUnit())
                        .warehouseId(warehouse != null ? warehouse.getId() : null)
                        .warehouseCode(warehouse != null ? warehouse.getCode() : null)
                        .warehouseName(warehouse != null ? warehouse.getName() : null)
                        .locationId(loc != null ? loc.getId() : null)
                        .locationCode(loc != null ? loc.getCode() : null)
                        .locationName(loc != null ? loc.getName() : null)
                        .locationType(loc != null ? loc.getLocationType() : null)
                        .locationTypeDisplayName(loc != null && loc.getLocationType() != null ? loc.getLocationType().getDisplayName() : null)
                        .zoneName(zoneName)
                        .shelfName(shelfName)
                        .locationDisplay(locDisplay)
                        .availableQuantity(availableQty)
                        .build());
            }
        }

        return OrderPickingListResponse.builder()
                .orderId(order.getId())
                .orderCode(order.getOrderCode())
                .orderStatus(order.getStatus())
                .expectedDeliveryDate(order.getExpectedDeliveryDate())
                .dealerId(order.getDealer() != null ? order.getDealer().getId() : null)
                .dealerCode(order.getDealer() != null ? order.getDealer().getCode() : null)
                .dealerName(order.getDealer() != null ? order.getDealer().getName() : null)
                .warehouseId(warehouse != null ? warehouse.getId() : null)
                .warehouseCode(warehouse != null ? warehouse.getCode() : null)
                .warehouseName(warehouse != null ? warehouse.getName() : null)
                .warehouseAddress(warehouse != null ? warehouse.getAddress() : null)
                .items(itemResponses)
                .build();
    }

    // ═══════════════════════════════════════════════════════════════
    // 6. NHẬP / XUẤT / ĐIỀU CHỈNH / TỒN KHO (S1-05, S2-07, S4-03)
    // ═══════════════════════════════════════════════════════════════

    @Override
    @Transactional
    public WarehouseVoucherResponse importGoods(WarehouseVoucherRequest request, String username) {
        return recordVoucher(request, WarehouseVoucherType.IMPORT, username);
    }

    @Override
    @Transactional
    public WarehouseVoucherResponse exportGoods(WarehouseVoucherRequest request, String username) {
        return recordVoucher(request, WarehouseVoucherType.EXPORT, username);
    }

    @Override
    @Transactional
    public WarehouseVoucherResponse adjustInventory(WarehouseVoucherRequest request, String username) {
        return recordVoucher(request, WarehouseVoucherType.ADJUSTMENT, username);
    }

    @Transactional
    public WarehouseVoucherResponse recordVoucher(WarehouseVoucherRequest request, WarehouseVoucherType type, String username) {
        if (request == null) {
            throw new IllegalArgumentException("Không được để trống nội dung phiếu kho");
        }

        // Chuẩn hóa danh sách items
        List<WarehouseVoucherItemRequest> itemRequests = normalizeItemRequests(request, type);
        if (itemRequests.isEmpty()) {
            throw new IllegalArgumentException("Phiếu kho phải có ít nhất 1 sản phẩm");
        }
        Warehouse warehouse = resolveWarehouse(request.getWarehouseId());

        String voucherCode = request.getVoucherCode();
        if (voucherCode == null || voucherCode.isBlank()) {
            String prefix = switch (type) {
                case IMPORT -> "PNK";
                case EXPORT -> "PXK";
                case ADJUSTMENT -> "PDC";
            };
            voucherCode = prefix + "-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"));
        }

        // Kiểm tra nhà cung cấp nếu có cung cấp
        Supplier supplier = null;
        if (request.getSupplierId() != null || (request.getSupplierCode() != null && !request.getSupplierCode().isBlank())) {
            supplier = resolveSupplier(request.getSupplierId(), request.getSupplierCode());
            if (supplier != null && Boolean.FALSE.equals(supplier.getActive())) {
                throw new IllegalArgumentException("Nhà cung cấp '" + supplier.getName() + "' đã ngừng hoạt động");
            }
        }

        boolean isDraft = "DRAFT".equalsIgnoreCase(request.getStatus()) || Boolean.TRUE.equals(request.getDraft());
        String initialStatus = isDraft ? "DRAFT" : "RECORDED";
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime voucherDate = request.getVoucherDate() != null ? request.getVoucherDate() : now;

        WarehouseVoucher voucher = WarehouseVoucher.builder()
                .voucherCode(voucherCode)
                .documentNumber(request.getDocumentNumber())
                .voucherDate(voucherDate)
                .type(type)
                .warehouse(warehouse)
                .supplier(supplier)
                .note(request.getNote())
                .status(initialStatus)
                .createdBy(username != null ? username : "system")
                .recordedAt(isDraft ? null : now)
                .referenceVoucherId(request.getReferenceVoucherId())
                .referenceVoucherCode(request.getReferenceVoucherCode())
                .adjustmentReason(request.getAdjustmentReason())
                .items(new ArrayList<>())
                .build();

        for (WarehouseVoucherItemRequest itemReq : itemRequests) {
            if (itemReq == null) {
                throw new IllegalArgumentException("Dòng sản phẩm trong phiếu kho không được để trống");
            }
            Product product = resolveProduct(itemReq);
            if (!Boolean.TRUE.equals(product.getActive())) {
                throw new IllegalArgumentException("Sản phẩm '" + product.getName() + "' đã ngừng kinh doanh");
            }

            BigDecimal qty = itemReq.getQuantity();
            if (qty == null) {
                throw new IllegalArgumentException("Số lượng cho sản phẩm '" + product.getName() + "' không được để trống");
            }
            if (qty.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Số lượng cho sản phẩm '" + product.getName() + "' phải lớn hơn 0");
            }

            String inputUnit = itemReq.getUnit();
            if (inputUnit == null || inputUnit.isBlank()) {
                inputUnit = product.getUnit();
            }

            // Quy đổi sang đơn vị cơ sở
            BigDecimal factor = null;
            if (unitConversionService != null) {
                try {
                    factor = unitConversionService.getConversionFactor(product, inputUnit);
                } catch (Exception ignored) {
                }
            }
            if (factor == null) {
                factor = BigDecimal.ONE;
            }

            // Lấy hệ số quy đổi tại thời điểm lập phiếu (S2-07)
            BigDecimal factor = unitConversionService.getConversionFactor(product, inputUnit);

            // Quy đổi về đơn vị cơ sở: baseQuantity = quantity * factor
            BigDecimal baseQty = qty.multiply(factor);

            // Quản lý số lô và hạn sử dụng (S5-04)
            boolean isLotManaged = Boolean.TRUE.equals(product.getManageLot());
            String lotNumber = itemReq.getLotNumber() != null ? itemReq.getLotNumber().trim() : null;
            LocalDate expiryDate = itemReq.getExpiryDate();
            LocalDate mfgDate = itemReq.getManufacturingDate();

            if (isLotManaged) {
                if (lotNumber == null || lotNumber.isBlank()) {
                    throw new IllegalArgumentException("Sản phẩm '" + product.getCode() + " - " + product.getName() + "' có quản lý lô, bắt buộc phải nhập số lô");
                }
                if (expiryDate == null) {
                    throw new IllegalArgumentException("Sản phẩm '" + product.getCode() + " - " + product.getName() + "' có quản lý lô, bắt buộc phải nhập hạn sử dụng");
                }
                if (mfgDate != null && expiryDate.isBefore(mfgDate)) {
                    throw new IllegalArgumentException("Hạn sử dụng không được trước ngày sản xuất cho lô '" + lotNumber + "'");
                }
            }

            BigDecimal price = itemReq.getPrice() != null ? itemReq.getPrice() : product.getPrice();
            BigDecimal totalAmount = price != null ? price.multiply(qty) : BigDecimal.ZERO;

            // ĐÓNG BĂNG thông tin đơn vị, hệ số và số lượng cơ sở vào dòng phiếu
            WarehouseVoucherItem item = WarehouseVoucherItem.builder()
                    .voucher(voucher)
                    .product(product)
                    .unit(inputUnit)
                    .quantity(qty)
                    .conversionFactor(factor)
                    .baseUnit(product.getUnit())
                    .baseQuantity(baseQty)
                    .price(price)
                    .totalAmount(totalAmount)
                    .lotNumber(lotNumber)
                    .expiryDate(expiryDate)
                    .manufacturingDate(mfgDate)
                    .note(itemReq.getNote())
                    .build();
            voucher.getItems().add(item);

            voucher.addItem(item);

            // CHỈ CỘNG TỒN KHO KHI XÁC NHẬN / GHI SỔ, PHIẾU NHÁP KHÔNG ẢNH HƯỞNG TỒN (S5-04)
            if (!isDraft) {
                WarehouseInventory inventory = inventoryRepository
                        .findByWarehouseIdAndProductId(warehouse.getId(), product.getId())
                        .orElseGet(() -> WarehouseInventory.builder()
                                .warehouse(warehouse)
                                .product(product)
                                .baseQuantity(BigDecimal.ZERO)
                                .baseUnit(product.getUnit())
                                .build());
                BigDecimal quantityBefore = inventory.getBaseQuantity() == null
                        ? BigDecimal.ZERO : inventory.getBaseQuantity();

                if (type == WarehouseVoucherType.IMPORT) {
                    inventory.addBaseQuantity(baseQty);
                } else if (type == WarehouseVoucherType.EXPORT) {
                    if (inventory.getBaseQuantity().compareTo(baseQty) < 0) {
                        throw new IllegalArgumentException("Không đủ tồn kho để xuất cho sản phẩm '" + product.getName() +
                                "'. Tồn hiện tại: " + inventory.getBaseQuantity() + " " + product.getUnit() +
                                ", yêu cầu xuất: " + baseQty + " " + product.getUnit() + " (" + qty + " " + inputUnit + ")");
                    }
                    inventory.subtractBaseQuantity(baseQty);
                } else if (type == WarehouseVoucherType.ADJUSTMENT) {
                    if (request.getDelta() != null) {
                        BigDecimal deltaBase = request.getDelta().multiply(factor);
                        inventory.addBaseQuantity(deltaBase);
                    } else {
                        inventory.setBaseQuantity(baseQty);
                    }
                }

                inventoryRepository.save(inventory);

                // Cập nhật tồn kho theo lô nếu có lotNumber
                if (lotNumber != null && !lotNumber.isBlank()) {
                    WarehouseLotInventory lotInv = lotInventoryRepository
                            .findByWarehouseIdAndProductIdAndLotNumber(warehouse.getId(), product.getId(), lotNumber)
                            .orElseGet(() -> WarehouseLotInventory.builder()
                                    .warehouse(warehouse)
                                    .product(product)
                                    .lotNumber(lotNumber)
                                    .expiryDate(expiryDate)
                                    .manufacturingDate(mfgDate)
                                    .baseQuantity(BigDecimal.ZERO)
                                    .baseUnit(product.getUnit())
                                    .build());
                    if (type == WarehouseVoucherType.IMPORT) {
                        lotInv.addBaseQuantity(baseQty);
                    } else if (type == WarehouseVoucherType.EXPORT) {
                        lotInv.subtractBaseQuantity(baseQty);
                    }
                    lotInventoryRepository.save(lotInv);
                }

                auditLogService.logAction(
                        "INVENTORY",
                        warehouse.getCode() + ":" + product.getCode(),
                        type.name(),
                        quantityBefore.toPlainString() + " " + product.getUnit(),
                        inventory.getBaseQuantity().toPlainString() + " " + product.getUnit(),
                        username != null ? username : "system");
            }
        }

        WarehouseVoucher savedVoucher = voucherRepository.save(voucher);
        return mapToVoucherResponse(savedVoucher);
    }

    @Override
    @Transactional
    public WarehouseVoucherResponse confirmVoucher(Long id, String username) {
        WarehouseVoucher voucher = voucherRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy phiếu kho với ID: " + id));

        if (!"DRAFT".equalsIgnoreCase(voucher.getStatus())) {
            throw new IllegalStateException("Chỉ có thể xác nhận phiếu ở trạng thái Nháp (DRAFT). Trạng thái hiện tại: " + voucher.getStatus());
        }

        Warehouse warehouse = voucher.getWarehouse();
        if (warehouse == null || !Boolean.TRUE.equals(warehouse.getActive())) {
            throw new IllegalArgumentException("Kho của phiếu nhập đã ngừng hoạt động hoặc không tồn tại");
        }

        if (voucher.getItems() == null || voucher.getItems().isEmpty()) {
            throw new IllegalArgumentException("Phiếu kho không có sản phẩm để xác nhận");
        }

        for (WarehouseVoucherItem item : voucher.getItems()) {
            Product product = item.getProduct();
            if (product == null || !Boolean.TRUE.equals(product.getActive())) {
                throw new IllegalArgumentException("Sản phẩm '" + (product != null ? product.getName() : "") + "' đã ngừng kinh doanh");
            }

            // Kiểm tra ràng buộc lô
            if (Boolean.TRUE.equals(product.getManageLot())) {
                if (item.getLotNumber() == null || item.getLotNumber().isBlank()) {
                    throw new IllegalArgumentException("Sản phẩm '" + product.getCode() + " - " + product.getName() + "' có quản lý lô, bắt buộc phải nhập số lô");
                }
                if (item.getExpiryDate() == null) {
                    throw new IllegalArgumentException("Sản phẩm '" + product.getCode() + " - " + product.getName() + "' có quản lý lô, bắt buộc phải nhập hạn sử dụng");
                }
            }

            BigDecimal baseQty = item.getBaseQuantity();

            // Cập nhật sổ sách tồn kho tổng
            WarehouseInventory inventory = inventoryRepository
                    .findByWarehouseIdAndProductId(warehouse.getId(), product.getId())
                    .orElseGet(() -> WarehouseInventory.builder()
                            .warehouse(warehouse)
                            .product(product)
                            .baseQuantity(BigDecimal.ZERO)
                            .baseUnit(product.getUnit())
                            .build());

            BigDecimal quantityBefore = inventory.getBaseQuantity() == null ? BigDecimal.ZERO : inventory.getBaseQuantity();

            if (voucher.getType() == WarehouseVoucherType.IMPORT) {
                inventory.addBaseQuantity(baseQty);
            } else if (voucher.getType() == WarehouseVoucherType.EXPORT) {
                if (inventory.getBaseQuantity().compareTo(baseQty) < 0) {
                    throw new IllegalArgumentException("Không đủ tồn kho để xuất cho sản phẩm '" + product.getName() +
                            "'. Tồn hiện tại: " + inventory.getBaseQuantity() + " " + product.getUnit() +
                            ", yêu cầu: " + baseQty + " " + product.getUnit());
                }
                inventory.subtractBaseQuantity(baseQty);
            }

            inventoryRepository.save(inventory);

            // Cập nhật tồn kho theo lô
            if (item.getLotNumber() != null && !item.getLotNumber().isBlank()) {
                WarehouseLotInventory lotInv = lotInventoryRepository
                        .findByWarehouseIdAndProductIdAndLotNumber(warehouse.getId(), product.getId(), item.getLotNumber())
                        .orElseGet(() -> WarehouseLotInventory.builder()
                                .warehouse(warehouse)
                                .product(product)
                                .lotNumber(item.getLotNumber())
                                .expiryDate(item.getExpiryDate())
                                .manufacturingDate(item.getManufacturingDate())
                                .baseQuantity(BigDecimal.ZERO)
                                .baseUnit(product.getUnit())
                                .build());
                if (voucher.getType() == WarehouseVoucherType.IMPORT) {
                    lotInv.addBaseQuantity(baseQty);
                } else if (voucher.getType() == WarehouseVoucherType.EXPORT) {
                    lotInv.subtractBaseQuantity(baseQty);
                }
                lotInventoryRepository.save(lotInv);
            }

            auditLogService.logAction(
                    "INVENTORY",
                    warehouse.getCode() + ":" + product.getCode(),
                    voucher.getType().name() + "_CONFIRM",
                    quantityBefore.toPlainString() + " " + product.getUnit(),
                    inventory.getBaseQuantity().toPlainString() + " " + product.getUnit(),
                    username != null ? username : "system");
        }

        voucher.setStatus("CONFIRMED");
        voucher.setRecordedAt(LocalDateTime.now());
        WarehouseVoucher saved = voucherRepository.save(voucher);
        return mapToVoucherResponse(saved);
    }

    @Override
    @Transactional
    public WarehouseVoucherResponse updateVoucher(Long id, WarehouseVoucherRequest request, String username) {
        WarehouseVoucher voucher = voucherRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy phiếu kho với ID: " + id));

        // Phiếu đã xác nhận không sửa được, chỉ lập phiếu điều chỉnh (S5-04)
        if ("CONFIRMED".equalsIgnoreCase(voucher.getStatus()) || "RECORDED".equalsIgnoreCase(voucher.getStatus()) || "ADJUSTED".equalsIgnoreCase(voucher.getStatus())) {
            throw new IllegalStateException("Phiếu đã xác nhận không sửa được, chỉ lập phiếu điều chỉnh");
        }
        if (!"DRAFT".equalsIgnoreCase(voucher.getStatus())) {
            throw new IllegalStateException("Chỉ được sửa phiếu ở trạng thái Nháp (DRAFT). Trạng thái hiện tại: " + voucher.getStatus());
        }

        if (request.getWarehouseId() != null) {
            voucher.setWarehouse(resolveWarehouse(request.getWarehouseId()));
        }

        if (request.getSupplierId() != null || (request.getSupplierCode() != null && !request.getSupplierCode().isBlank())) {
            Supplier supplier = resolveSupplier(request.getSupplierId(), request.getSupplierCode());
            if (supplier != null && Boolean.FALSE.equals(supplier.getActive())) {
                throw new IllegalArgumentException("Nhà cung cấp '" + supplier.getName() + "' đã ngừng hoạt động");
            }
            voucher.setSupplier(supplier);
        }

        if (request.getDocumentNumber() != null) {
            voucher.setDocumentNumber(request.getDocumentNumber());
        }
        if (request.getVoucherDate() != null) {
            voucher.setVoucherDate(request.getVoucherDate());
        }
        if (request.getNote() != null) {
            voucher.setNote(request.getNote());
        }

        // Cập nhật danh sách items nếu có truyền
        List<WarehouseVoucherItemRequest> itemRequests = normalizeItemRequests(request, voucher.getType());
        if (!itemRequests.isEmpty()) {
            voucher.getItems().clear();
            for (WarehouseVoucherItemRequest itemReq : itemRequests) {
                Product product = resolveProduct(itemReq);
                if (!Boolean.TRUE.equals(product.getActive())) {
                    throw new IllegalArgumentException("Sản phẩm '" + product.getName() + "' đã ngừng kinh doanh");
                }

                String inputUnit = itemReq.getUnit();
                if (inputUnit == null || inputUnit.isBlank()) {
                    inputUnit = product.getUnit();
                } else {
                    inputUnit = inputUnit.trim();
                }

                BigDecimal qty = itemReq.getQuantity();
                if (qty == null || qty.compareTo(BigDecimal.ZERO) <= 0) {
                    throw new IllegalArgumentException("Số lượng cho sản phẩm '" + product.getCode() + "' phải lớn hơn 0");
                }

                BigDecimal factor = unitConversionService.getConversionFactor(product, inputUnit);
                BigDecimal baseQty = qty.multiply(factor);

                boolean isLotManaged = Boolean.TRUE.equals(product.getManageLot());
                String lotNumber = itemReq.getLotNumber() != null ? itemReq.getLotNumber().trim() : null;
                LocalDate expiryDate = itemReq.getExpiryDate();
                LocalDate mfgDate = itemReq.getManufacturingDate();

                if (isLotManaged) {
                    if (lotNumber == null || lotNumber.isBlank()) {
                        throw new IllegalArgumentException("Sản phẩm '" + product.getCode() + " - " + product.getName() + "' có quản lý lô, bắt buộc phải nhập số lô");
                    }
                    if (expiryDate == null) {
                        throw new IllegalArgumentException("Sản phẩm '" + product.getCode() + " - " + product.getName() + "' có quản lý lô, bắt buộc phải nhập hạn sử dụng");
                    }
                    if (mfgDate != null && expiryDate.isBefore(mfgDate)) {
                        throw new IllegalArgumentException("Hạn sử dụng không được trước ngày sản xuất cho lô '" + lotNumber + "'");
                    }
                }

                BigDecimal price = itemReq.getPrice() != null ? itemReq.getPrice() : product.getPrice();
                BigDecimal totalAmount = price != null ? price.multiply(qty) : BigDecimal.ZERO;

                WarehouseVoucherItem item = WarehouseVoucherItem.builder()
                        .voucher(voucher)
                        .product(product)
                        .unit(inputUnit)
                        .quantity(qty)
                        .conversionFactor(factor)
                        .baseUnit(product.getUnit())
                        .baseQuantity(baseQty)
                        .price(price)
                        .totalAmount(totalAmount)
                        .lotNumber(lotNumber)
                        .expiryDate(expiryDate)
                        .manufacturingDate(mfgDate)
                        .note(itemReq.getNote())
                        .build();

                voucher.addItem(item);
            }
        }

        WarehouseVoucher saved = voucherRepository.save(voucher);
        return mapToVoucherResponse(saved);
    }

    @Override
    @Transactional
    public void deleteVoucher(Long id, String username) {
        WarehouseVoucher voucher = voucherRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy phiếu kho với ID: " + id));

        // Phiếu đã xác nhận không thể xóa, chỉ lập phiếu điều chỉnh (S5-04)
        if ("CONFIRMED".equalsIgnoreCase(voucher.getStatus()) || "RECORDED".equalsIgnoreCase(voucher.getStatus()) || "ADJUSTED".equalsIgnoreCase(voucher.getStatus())) {
            throw new IllegalStateException("Phiếu đã xác nhận không thể xóa, chỉ lập phiếu điều chỉnh");
        }

        voucherRepository.delete(voucher);
        auditLogService.logAction(
                "WAREHOUSE_VOUCHER",
                voucher.getVoucherCode(),
                "DELETE_DRAFT",
                voucher.getStatus(),
                "DELETED",
                username != null ? username : "system");
    }

    @Override
    @Transactional
    public WarehouseVoucherResponse adjustVoucher(Long id, WarehouseVoucherRequest request, String username) {
        WarehouseVoucher originalVoucher = voucherRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy phiếu kho gốc với ID: " + id));

        if (!"CONFIRMED".equalsIgnoreCase(originalVoucher.getStatus()) && !"RECORDED".equalsIgnoreCase(originalVoucher.getStatus()) && !"ADJUSTED".equalsIgnoreCase(originalVoucher.getStatus())) {
            throw new IllegalStateException("Chỉ có thể lập phiếu điều chỉnh cho phiếu kho đã xác nhận. Trạng thái hiện tại: " + originalVoucher.getStatus());
        }

        if (request == null) {
            throw new IllegalArgumentException("Không được để trống nội dung phiếu điều chỉnh");
        }

        Warehouse warehouse = request.getWarehouseId() != null
                ? resolveWarehouse(request.getWarehouseId())
                : originalVoucher.getWarehouse();

        String voucherCode = request.getVoucherCode();
        if (voucherCode == null || voucherCode.isBlank()) {
            voucherCode = "PDC-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"));
        }

        String reason = request.getAdjustmentReason() != null && !request.getAdjustmentReason().isBlank()
                ? request.getAdjustmentReason()
                : (request.getNote() != null ? request.getNote() : "Điều chỉnh cho phiếu " + originalVoucher.getVoucherCode());

        WarehouseVoucher adjustmentVoucher = WarehouseVoucher.builder()
                .voucherCode(voucherCode)
                .documentNumber(request.getDocumentNumber())
                .type(WarehouseVoucherType.ADJUSTMENT)
                .warehouse(warehouse)
                .supplier(originalVoucher.getSupplier())
                .referenceVoucherId(originalVoucher.getId())
                .referenceVoucherCode(originalVoucher.getVoucherCode())
                .adjustmentReason(reason)
                .note(request.getNote())
                .status("CONFIRMED")
                .createdBy(username != null ? username : "system")
                .voucherDate(request.getVoucherDate() != null ? request.getVoucherDate() : LocalDateTime.now())
                .recordedAt(LocalDateTime.now())
                .items(new ArrayList<>())
                .build();

        List<WarehouseVoucherItemRequest> itemRequests = normalizeItemRequests(request, WarehouseVoucherType.ADJUSTMENT);
        if (itemRequests.isEmpty()) {
            throw new IllegalArgumentException("Phiếu điều chỉnh phải có ít nhất 1 sản phẩm");
        }

        for (WarehouseVoucherItemRequest itemReq : itemRequests) {
            Product product = resolveProduct(itemReq);

            String inputUnit = itemReq.getUnit();
            if (inputUnit == null || inputUnit.isBlank()) {
                inputUnit = product.getUnit();
            } else {
                inputUnit = inputUnit.trim();
            }

            BigDecimal qty = itemReq.getQuantity() != null ? itemReq.getQuantity() : BigDecimal.ZERO;
            BigDecimal factor = unitConversionService.getConversionFactor(product, inputUnit);
            BigDecimal baseQty = qty.multiply(factor);

            String lotNumber = itemReq.getLotNumber() != null ? itemReq.getLotNumber().trim() : null;
            LocalDate expiryDate = itemReq.getExpiryDate();
            LocalDate mfgDate = itemReq.getManufacturingDate();

            BigDecimal price = itemReq.getPrice() != null ? itemReq.getPrice() : product.getPrice();
            BigDecimal totalAmount = price != null ? price.multiply(qty) : BigDecimal.ZERO;

            WarehouseVoucherItem item = WarehouseVoucherItem.builder()
                    .voucher(adjustmentVoucher)
                    .product(product)
                    .unit(inputUnit)
                    .quantity(qty)
                    .conversionFactor(factor)
                    .baseUnit(product.getUnit())
                    .baseQuantity(baseQty)
                    .price(price)
                    .totalAmount(totalAmount)
                    .lotNumber(lotNumber)
                    .expiryDate(expiryDate)
                    .manufacturingDate(mfgDate)
                    .note(itemReq.getNote())
                    .build();

            adjustmentVoucher.addItem(item);

            // Cập nhật tồn kho theo delta hoặc số lượng điều chỉnh
            WarehouseInventory inventory = inventoryRepository
                    .findByWarehouseIdAndProductId(warehouse.getId(), product.getId())
                    .orElseGet(() -> WarehouseInventory.builder()
                            .warehouse(warehouse)
                            .product(product)
                            .baseQuantity(BigDecimal.ZERO)
                            .baseUnit(product.getUnit())
                            .build());

            BigDecimal quantityBefore = inventory.getBaseQuantity() == null ? BigDecimal.ZERO : inventory.getBaseQuantity();

            if (request.getDelta() != null) {
                BigDecimal deltaBase = request.getDelta().multiply(factor);
                inventory.addBaseQuantity(deltaBase);
            } else {
                inventory.setBaseQuantity(baseQty);
            }

            inventoryRepository.save(inventory);

            // Cập nhật tồn kho lô nếu có
            if (lotNumber != null && !lotNumber.isBlank()) {
                WarehouseLotInventory lotInv = lotInventoryRepository
                        .findByWarehouseIdAndProductIdAndLotNumber(warehouse.getId(), product.getId(), lotNumber)
                        .orElseGet(() -> WarehouseLotInventory.builder()
                                .warehouse(warehouse)
                                .product(product)
                                .lotNumber(lotNumber)
                                .expiryDate(expiryDate)
                                .manufacturingDate(mfgDate)
                                .baseQuantity(BigDecimal.ZERO)
                                .baseUnit(product.getUnit())
                                .build());
                if (request.getDelta() != null) {
                    BigDecimal deltaBase = request.getDelta().multiply(factor);
                    lotInv.addBaseQuantity(deltaBase);
                } else {
                    lotInv.setBaseQuantity(baseQty);
                }
                lotInventoryRepository.save(lotInv);
            }

            auditLogService.logAction(
                    "INVENTORY",
                    warehouse.getCode() + ":" + product.getCode(),
                    "ADJUSTMENT",
                    quantityBefore.toPlainString() + " " + product.getUnit(),
                    inventory.getBaseQuantity().toPlainString() + " " + product.getUnit(),
                    username != null ? username : "system");
        }

        originalVoucher.setStatus("ADJUSTED");
        voucherRepository.save(originalVoucher);

        WarehouseVoucher saved = voucherRepository.save(adjustmentVoucher);
        return mapToVoucherResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<WarehouseLotInventoryResponse> getLotInventory(Long warehouseId, Long productId) {
        List<WarehouseLotInventory> list;
        if (warehouseId != null && productId != null) {
            list = lotInventoryRepository.findByWarehouseIdAndProductId(warehouseId, productId);
        } else if (warehouseId != null) {
            list = lotInventoryRepository.findByWarehouseId(warehouseId);
        } else if (productId != null) {
            list = lotInventoryRepository.findByProductId(productId);
        } else {
            list = lotInventoryRepository.findAllByOrderByUpdatedAtDesc();
        }

        return list.stream().map(lot -> WarehouseLotInventoryResponse.builder()
                .id(lot.getId())
                .warehouseId(lot.getWarehouse() != null ? lot.getWarehouse().getId() : null)
                .warehouseCode(lot.getWarehouse() != null ? lot.getWarehouse().getCode() : null)
                .warehouseName(lot.getWarehouse() != null ? lot.getWarehouse().getName() : null)
                .productId(lot.getProduct() != null ? lot.getProduct().getId() : null)
                .productCode(lot.getProduct() != null ? lot.getProduct().getCode() : null)
                .productName(lot.getProduct() != null ? lot.getProduct().getName() : null)
                .lotNumber(lot.getLotNumber())
                .expiryDate(lot.getExpiryDate())
                .manufacturingDate(lot.getManufacturingDate())
                .baseQuantity(lot.getBaseQuantity())
                .baseUnit(lot.getBaseUnit())
                .updatedAt(lot.getUpdatedAt())
                .build()).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<WarehouseInventoryResponse> getInventory(Long warehouseId) {
        List<WarehouseInventory> list;
        if (warehouseId != null) {
            list = inventoryRepository.findByWarehouseId(warehouseId);
        } else {
            list = inventoryRepository.findAll();
        }

        return list.stream().map(this::mapToInventoryResponse).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<WarehouseVoucherResponse> getVouchers(Long warehouseId, WarehouseVoucherType type) {
        return getVouchers(warehouseId, type, null, null);
    }

    @Override
    @Transactional(readOnly = true)
    public List<WarehouseVoucherResponse> getVouchers(Long warehouseId, WarehouseVoucherType type, String status, Long supplierId) {
        List<WarehouseVoucher> vouchers = voucherRepository.searchVouchers(warehouseId, type, status, supplierId);
        return vouchers.stream().map(this::mapToVoucherResponse).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public WarehouseVoucherResponse getVoucherById(Long id) {
        WarehouseVoucher voucher = voucherRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy phiếu kho với ID: " + id));
        return mapToVoucherResponse(voucher);
    }

    // ═══════════════════════════════════════════════════════════════
    // HELPER & MAPPING METHODS
    // ═══════════════════════════════════════════════════════════════

    private Warehouse resolveWarehouse(Long warehouseId) {
        if (warehouseId != null) {
            Warehouse warehouse = warehouseRepository.findById(warehouseId)
                    .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy kho với ID: " + warehouseId));
            if (!Boolean.TRUE.equals(warehouse.getActive())) {
                throw new IllegalArgumentException("Kho đã ngừng hoạt động");
            }
            return warehouse;
        }
        List<Warehouse> warehouses = warehouseRepository.findAllByActiveTrueOrderByNameAsc();
        if (warehouses.isEmpty()) {
            throw new IllegalArgumentException("Chưa có kho đang hoạt động. Vui lòng cấu hình kho thực tế trước khi ghi phiếu.");
        }
        if (warehouses.size() > 1) {
            throw new IllegalArgumentException("Vui lòng chọn kho (warehouseId) để ghi phiếu");
        }
        return warehouses.get(0);
    }

    private Supplier resolveSupplier(Long supplierId, String supplierCode) {
        if (supplierId != null) {
            return supplierRepository.findById(supplierId)
                    .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy nhà cung cấp với ID: " + supplierId));
        }
        if (supplierCode != null && !supplierCode.isBlank()) {
            return supplierRepository.findByCode(supplierCode.trim())
                    .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy nhà cung cấp với mã: " + supplierCode));
        }
        return null;
    }

    private Product resolveProduct(WarehouseVoucherItemRequest itemReq) {
        if (itemReq.getProductId() != null) {
            return productRepository.findById(itemReq.getProductId())
                    .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy sản phẩm với ID: " + itemReq.getProductId()));
        }
        if (itemReq.getProductCode() != null && !itemReq.getProductCode().isBlank()) {
            return productRepository.findByCode(itemReq.getProductCode().trim().toUpperCase())
                    .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy sản phẩm với mã: " + itemReq.getProductCode()));
        }
        throw new IllegalArgumentException("Cần cung cấp productId hoặc productCode");
    }

    private List<WarehouseVoucherItemRequest> normalizeItemRequests(WarehouseVoucherRequest request, WarehouseVoucherType type) {
        if (request.getItems() != null && !request.getItems().isEmpty()) {
            return request.getItems();
        }
        // Trường hợp payload đơn giản {"productId": 1, "quantity": 50, "unit": "thùng"}
        if (request.getProductId() != null) {
            BigDecimal qty = request.getQuantity();
            if (qty == null && type == WarehouseVoucherType.ADJUSTMENT && request.getDelta() != null) {
                qty = request.getDelta().abs();
            }
            if (qty == null) {
                throw new IllegalArgumentException("Số lượng không được để trống");
            }
            return List.of(WarehouseVoucherItemRequest.builder()
                    .productId(request.getProductId())
                    .quantity(qty)
                    .unit(request.getUnit())
                    .build());
        }
        return Collections.emptyList();
    }

    private WarehouseResponse mapToWarehouseResponse(Warehouse warehouse) {
        int locCount = (int) warehouseLocationRepository.countByWarehouseId(warehouse.getId());
        int defaultDealerCount = (int) dealerRepository.countByWarehouseId(warehouse.getId());

        User manager = warehouse.getManager();
        Long managerUserId = manager != null ? manager.getId() : null;
        String managerUsername = manager != null ? manager.getUsername() : null;
        String managerFullName = manager != null ? manager.getFullName() : null;

        String managerName = warehouse.getManagerName();
        if ((managerName == null || managerName.isBlank()) && managerFullName != null) {
            managerName = managerFullName;
        }

        return WarehouseResponse.builder()
                .id(warehouse.getId())
                .code(warehouse.getCode())
                .name(warehouse.getName())
                .address(warehouse.getAddress())
                .managerUserId(managerUserId)
                .managerUsername(managerUsername)
                .managerFullName(managerFullName)
                .managerName(managerName)
                .active(warehouse.getActive())
                .locationCount(locCount)
                .defaultDealerCount(defaultDealerCount)
                .createdAt(warehouse.getCreatedAt())
                .updatedAt(warehouse.getUpdatedAt())
                .build();
    }

    private WarehouseLocationResponse mapToLocationResponse(WarehouseLocation loc) {
        String parentCode = loc.getParent() != null ? loc.getParent().getCode() : null;
        String parentName = loc.getParent() != null ? loc.getParent().getName() : null;
        Long parentId = loc.getParent() != null ? loc.getParent().getId() : null;

        String fullDisplay = (loc.getParent() != null)
                ? loc.getParent().getName() + " > " + loc.getName()
                : loc.getName();

        int productCount = (int) locationProductRepository.countByLocationId(loc.getId());

        return WarehouseLocationResponse.builder()
                .id(loc.getId())
                .warehouseId(loc.getWarehouse() != null ? loc.getWarehouse().getId() : null)
                .warehouseCode(loc.getWarehouse() != null ? loc.getWarehouse().getCode() : null)
                .warehouseName(loc.getWarehouse() != null ? loc.getWarehouse().getName() : null)
                .code(loc.getCode())
                .name(loc.getName())
                .locationType(loc.getLocationType())
                .locationTypeDisplayName(loc.getLocationType() != null ? loc.getLocationType().getDisplayName() : null)
                .parentLocationId(parentId)
                .parentLocationCode(parentCode)
                .parentLocationName(parentName)
                .description(loc.getDescription())
                .active(loc.getActive())
                .fullDisplayName(fullDisplay)
                .productCount(productCount)
                .createdAt(loc.getCreatedAt())
                .updatedAt(loc.getUpdatedAt())
                .build();
    }

    private ProductLocationResponse mapToProductLocationResponse(WarehouseLocationProduct wlp) {
        WarehouseLocation loc = wlp.getLocation();
        Product p = wlp.getProduct();

        String parentZoneCode = loc.getParent() != null ? loc.getParent().getCode() : null;
        String parentZoneName = loc.getParent() != null ? loc.getParent().getName() : null;
        Long parentZoneId = loc.getParent() != null ? loc.getParent().getId() : null;

        String locDisplay = (loc.getParent() != null)
                ? loc.getParent().getName() + " > " + loc.getName()
                : loc.getName();

        return ProductLocationResponse.builder()
                .id(wlp.getId())
                .warehouseId(loc.getWarehouse() != null ? loc.getWarehouse().getId() : null)
                .warehouseCode(loc.getWarehouse() != null ? loc.getWarehouse().getCode() : null)
                .warehouseName(loc.getWarehouse() != null ? loc.getWarehouse().getName() : null)
                .locationId(loc.getId())
                .locationCode(loc.getCode())
                .locationName(loc.getName())
                .locationType(loc.getLocationType())
                .locationTypeDisplayName(loc.getLocationType() != null ? loc.getLocationType().getDisplayName() : null)
                .parentZoneId(parentZoneId)
                .parentZoneCode(parentZoneCode)
                .parentZoneName(parentZoneName)
                .locationDisplay(locDisplay)
                .productId(p != null ? p.getId() : null)
                .productCode(p != null ? p.getCode() : null)
                .productName(p != null ? p.getName() : null)
                .baseUnit(p != null ? p.getUnit() : null)
                .isDefault(wlp.getIsDefault())
                .note(wlp.getNote())
                .build();
    }

    private WarehouseVoucherResponse mapToVoucherResponse(WarehouseVoucher voucher) {
        List<WarehouseVoucherItemResponse> items = voucher.getItems() != null
                ? voucher.getItems().stream().map(this::mapToVoucherItemResponse).collect(Collectors.toList())
                : Collections.emptyList();

        return WarehouseVoucherResponse.builder()
                .id(voucher.getId())
                .voucherCode(voucher.getVoucherCode())
                .documentNumber(voucher.getDocumentNumber())
                .voucherDate(voucher.getVoucherDate())
                .type(voucher.getType())
                .warehouseId(voucher.getWarehouse() != null ? voucher.getWarehouse().getId() : null)
                .warehouseCode(voucher.getWarehouse() != null ? voucher.getWarehouse().getCode() : null)
                .warehouseName(voucher.getWarehouse() != null ? voucher.getWarehouse().getName() : null)
                .supplierId(voucher.getSupplier() != null ? voucher.getSupplier().getId() : null)
                .supplierCode(voucher.getSupplier() != null ? voucher.getSupplier().getCode() : null)
                .supplierName(voucher.getSupplier() != null ? voucher.getSupplier().getName() : null)
                .status(voucher.getStatus())
                .referenceVoucherId(voucher.getReferenceVoucherId())
                .referenceVoucherCode(voucher.getReferenceVoucherCode())
                .adjustmentReason(voucher.getAdjustmentReason())
                .note(voucher.getNote())
                .createdBy(voucher.getCreatedBy())
                .recordedAt(voucher.getRecordedAt())
                .items(items)
                .build();
    }

    private WarehouseVoucherItemResponse mapToVoucherItemResponse(WarehouseVoucherItem item) {
        return WarehouseVoucherItemResponse.builder()
                .id(item.getId())
                .productId(item.getProduct() != null ? item.getProduct().getId() : null)
                .productCode(item.getProduct() != null ? item.getProduct().getCode() : null)
                .productName(item.getProduct() != null ? item.getProduct().getName() : null)
                .manageLot(item.getProduct() != null ? Boolean.TRUE.equals(item.getProduct().getManageLot()) : false)
                .unit(item.getUnit())
                .quantity(item.getQuantity())
                .conversionFactor(item.getConversionFactor())
                .baseUnit(item.getBaseUnit())
                .baseQuantity(item.getBaseQuantity())
                .price(item.getPrice())
                .totalAmount(item.getTotalAmount())
                .lotNumber(item.getLotNumber())
                .expiryDate(item.getExpiryDate())
                .manufacturingDate(item.getManufacturingDate())
                .note(item.getNote())
                .build();
    }

    private WarehouseInventoryResponse mapToInventoryResponse(WarehouseInventory inv) {
        Product p = inv.getProduct();
        BigDecimal physical = inv.getBaseQuantity() != null ? inv.getBaseQuantity() : BigDecimal.ZERO;
        BigDecimal reserved = inv.getReservedQuantity() != null ? inv.getReservedQuantity() : BigDecimal.ZERO;
        BigDecimal available = physical.subtract(reserved).max(BigDecimal.ZERO);

        Map<String, BigDecimal> converted = new LinkedHashMap<>();
        Map<String, BigDecimal> convertedAvailable = new LinkedHashMap<>();

        // Luôn có đơn vị cơ sở
        converted.put(inv.getBaseUnit(), physical);
        convertedAvailable.put(inv.getBaseUnit(), available);

        // Tính tương đương theo các đơn vị quy đổi đã khai báo của SKU
        if (p != null) {
            List<UnitConversion> conversions = unitConversionRepository.findByProductIdOrderByConversionFactorAsc(p.getId());
            for (UnitConversion uc : conversions) {
                if (uc.getConversionFactor() != null && uc.getConversionFactor().compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal qtyInUnit = physical.divide(uc.getConversionFactor(), 2, RoundingMode.HALF_UP);
                    converted.put(uc.getUnitName(), qtyInUnit);

                    BigDecimal availInUnit = available.divide(uc.getConversionFactor(), 0, RoundingMode.FLOOR);
                    convertedAvailable.put(uc.getUnitName(), availInUnit);
                }
            }
        }

        // Tìm vị trí kệ trong kho lưu trữ
        Long locationId = null;
        String locationCode = null;
        String locationName = null;
        String locationType = null;
        String zoneName = null;
        String shelfName = null;
        String locationDisplay = null;

        if (inv.getWarehouse() != null && p != null) {
            List<WarehouseLocationProduct> mappings =
                    locationProductRepository.findByWarehouseIdAndProductId(inv.getWarehouse().getId(), p.getId());
            if (!mappings.isEmpty()) {
                WarehouseLocation loc = mappings.get(0).getLocation();
                locationId = loc.getId();
                locationCode = loc.getCode();
                locationName = loc.getName();
                locationType = loc.getLocationType() != null ? loc.getLocationType().name() : null;
                if (loc.getParent() != null) {
                    zoneName = loc.getParent().getName();
                    shelfName = loc.getName();
                    locationDisplay = loc.getParent().getName() + " > " + loc.getName();
                } else if (loc.getLocationType() == LocationType.ZONE) {
                    zoneName = loc.getName();
                    locationDisplay = loc.getName();
                } else {
                    shelfName = loc.getName();
                    locationDisplay = loc.getName();
                }
            }
        }

        return WarehouseInventoryResponse.builder()
                .id(inv.getId())
                .warehouseId(inv.getWarehouse() != null ? inv.getWarehouse().getId() : null)
                .warehouseCode(inv.getWarehouse() != null ? inv.getWarehouse().getCode() : null)
                .warehouseName(inv.getWarehouse() != null ? inv.getWarehouse().getName() : null)
                .productId(p != null ? p.getId() : null)
                .productCode(p != null ? p.getCode() : null)
                .productName(p != null ? p.getName() : null)
                .baseQuantity(physical)
                .baseUnit(inv.getBaseUnit())
                .reservedQuantity(reserved)
                .availableQuantity(available)
                .convertedViews(converted)
                .convertedAvailableViews(convertedAvailable)
                .locationId(locationId)
                .locationCode(locationCode)
                .locationName(locationName)
                .locationType(locationType)
                .zoneName(zoneName)
                .shelfName(shelfName)
                .locationDisplay(locationDisplay)
                .updatedAt(inv.getUpdatedAt())
                .build();
    }
}
