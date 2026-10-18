package com.quanlybanhang.service;

import com.quanlybanhang.dto.request.OrderCalculateRequest;
import com.quanlybanhang.dto.request.OrderInventoryCheckRequest;
import com.quanlybanhang.dto.request.OrderItemRequest;
import com.quanlybanhang.dto.request.OrderLineItemPricingRequest;
import com.quanlybanhang.dto.request.OrderRequest;
import com.quanlybanhang.dto.response.*;
import com.quanlybanhang.exception.DealerLockedException;
import com.quanlybanhang.exception.OrderCannotBeCancelledException;
import com.quanlybanhang.model.*;
import com.quanlybanhang.repository.DealerRepository;
import com.quanlybanhang.repository.DealerDeliveryPointRepository;
import com.quanlybanhang.repository.OrderRepository;
import com.quanlybanhang.repository.PriceListRepository;
import com.quanlybanhang.repository.ProductRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.quanlybanhang.dto.request.OrderFilterRequest;
import com.quanlybanhang.dto.response.OrderFilterResult;
import com.quanlybanhang.model.OrderDraft;
import com.quanlybanhang.repository.OrderDraftRepository;
import com.quanlybanhang.repository.UserRepository;
import com.quanlybanhang.repository.WarehouseLocationProductRepository;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import com.quanlybanhang.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import com.quanlybanhang.dto.request.OrderInventoryCheckRequest;
import com.quanlybanhang.repository.WarehouseRepository;
import com.quanlybanhang.repository.WarehouseInventoryRepository;
import java.math.RoundingMode;

@Service
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final DealerRepository dealerRepository;
    private final DealerDeliveryPointRepository deliveryPointRepository;
    private final PriceListRepository priceListRepository;
    private final WarehouseRepository warehouseRepository;
    private final WarehouseInventoryRepository inventoryRepository;
    private final UnitConversionService unitConversionService;
    private final AuditLogService auditLogService;
    private final VolumeDiscountPolicyService volumeDiscountPolicyService;
    private final DealerDebtService dealerDebtService;
    private final OrderStatusHistoryRepository orderStatusHistoryRepository;

    @Autowired
    public OrderServiceImpl(
            OrderRepository orderRepository,
            ProductRepository productRepository,
            DealerRepository dealerRepository,
            DealerDeliveryPointRepository deliveryPointRepository,
            PriceListRepository priceListRepository,
            UnitConversionService unitConversionService,
            AuditLogService auditLogService,
            VolumeDiscountPolicyService volumeDiscountPolicyService,
            DealerDebtService dealerDebtService,
            WarehouseRepository warehouseRepository,
            WarehouseInventoryRepository inventoryRepository,
            OrderStatusHistoryRepository orderStatusHistoryRepository,
            UserRepository userRepository) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.dealerRepository = dealerRepository;
        this.deliveryPointRepository = deliveryPointRepository;
        this.priceListRepository = priceListRepository;
        this.unitConversionService = unitConversionService;
        this.auditLogService = auditLogService;
        this.volumeDiscountPolicyService = volumeDiscountPolicyService;
        this.dealerDebtService = dealerDebtService;
        this.warehouseRepository = warehouseRepository;
        this.inventoryRepository = inventoryRepository;
        this.orderStatusHistoryRepository = orderStatusHistoryRepository;
        this.userRepository = userRepository;
    }

    /**
     * Constructor tương thích cho các unit test cũ chỉ mock 9 bean ban đầu.
     */
    public OrderServiceImpl(
            OrderRepository orderRepository,
            ProductRepository productRepository,
            DealerRepository dealerRepository,
            DealerDeliveryPointRepository deliveryPointRepository,
            PriceListRepository priceListRepository,
            UnitConversionService unitConversionService,
            AuditLogService auditLogService,
            VolumeDiscountPolicyService volumeDiscountPolicyService,
            DealerDebtService dealerDebtService) {
        this(orderRepository, productRepository, dealerRepository, deliveryPointRepository,
                priceListRepository, unitConversionService, auditLogService,
                volumeDiscountPolicyService, dealerDebtService, null, null, null, null);
    }

    @Autowired(required = false)
    private UserRepository userRepository;

    @Autowired(required = false)
    public void setUserRepository(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Autowired(required = false)
    private OrderDraftRepository orderDraftRepository;

    @Autowired(required = false)
    private ObjectMapper objectMapper;

    @Autowired(required = false)
    private WarehouseLocationProductRepository locationProductRepository;

    @Autowired(required = false)
    public void setLocationProductRepository(WarehouseLocationProductRepository locationProductRepository) {
        this.locationProductRepository = locationProductRepository;
    }

    private record ItemPreData(
            Product product,
            String unit,
            BigDecimal quantity,
            BigDecimal conversionFactor,
            BigDecimal baseQuantity,
            BigDecimal price,
            BigDecimal floorPrice,
            boolean isBelowFloor,
            BigDecimal subtotal,
            String note
    ) {}

    @Override
    @Transactional
    public OrderResponse createOrder(OrderRequest request, String username) {
        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new IllegalArgumentException("Đơn hàng phải có ít nhất 1 sản phẩm");
        }

        String orderCode = request.getOrderCode();
        if (orderCode == null || orderCode.isBlank()) {
            orderCode = "DH-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"));
        } else if (orderRepository.existsByOrderCode(orderCode.trim())) {
            throw new IllegalArgumentException("Mã đơn hàng '" + orderCode + "' đã tồn tại");
        }

        // 1. Xác định đại lý và nhóm khách hàng (S2-10)
        Dealer dealer = null;
        CustomerGroup customerGroup = request.getCustomerGroup();

        if (request.getDealerId() != null) {
            dealer = dealerRepository.findById(request.getDealerId())
                    .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đại lý với ID: " + request.getDealerId()));
            // S3-07: Đại lý bị khoá không tạo được đơn mới, kể cả trên cổng đặt hàng
            if (Boolean.TRUE.equals(dealer.getLocked())) {
                throw new DealerLockedException(dealer.getCode(), dealer.getName(), dealer.getLockReason());
            }
            if (Boolean.FALSE.equals(dealer.getActive())) {
                throw new IllegalArgumentException(String.format("Đại lý '%s' (%s) đã ngừng giao dịch, không thể tạo đơn hàng mới.",
                        dealer.getName(), dealer.getCode()));
            }
            // S4-02: Đại lý có khoản quá hạn quá số ngày cho phép thì chặn tạo đơn hoàn toàn
            if (dealerDebtService != null) {
                dealerDebtService.validateDealerCanCreateOrder(dealer);
            }
            // S4-01: Bảng giá lấy theo nhóm khách hàng mà đại lý thuộc về
            customerGroup = dealer.getCustomerGroup();
        }

        // 2. Tự động tìm bảng giá hiệu lực phù hợp (S2-10, S4-01)
        PriceList priceList = null;
        if (request.getPriceListId() != null) {
            priceList = priceListRepository.findById(request.getPriceListId())
                    .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy bảng giá với ID: " + request.getPriceListId()));
            if (customerGroup == null) {
                customerGroup = priceList.getCustomerGroup();
            }
        } else if (customerGroup != null) {
            LocalDate queryDate = request.getExpectedDeliveryDate() != null ? request.getExpectedDeliveryDate() : LocalDate.now();
            List<PriceList> effectivePriceLists = priceListRepository.findEffectivePriceLists(customerGroup, queryDate);
            if (!effectivePriceLists.isEmpty()) {
                priceList = effectivePriceLists.get(0);
            }
        }

        // 2b. Lấy thông tin điểm giao hàng (S3-04)
        DealerDeliveryPoint deliveryPoint = null;
        if (request.getDeliveryPointId() != null) {
            deliveryPoint = deliveryPointRepository.findById(request.getDeliveryPointId())
                    .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy điểm giao hàng với ID: " + request.getDeliveryPointId()));
            if (dealer != null && !deliveryPoint.getDealer().getId().equals(dealer.getId())) {
                throw new IllegalArgumentException("Điểm giao hàng không thuộc đại lý đã chọn");
            }
        } else if (dealer != null) {
            // Lấy điểm giao hàng mặc định nếu có
            Optional<DealerDeliveryPoint> defaultPoint = deliveryPointRepository.findByDealerIdAndIsDefaultTrue(dealer.getId());
            if (defaultPoint.isPresent()) {
                deliveryPoint = defaultPoint.get();
            }
        }

        // 2c. Xác định kho phục vụ đại lý / xuất hàng (S4-03)
        Warehouse warehouse = null;
        if (request.getWarehouseId() != null && warehouseRepository != null) {
            warehouse = warehouseRepository.findById(request.getWarehouseId())
                    .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy kho với ID: " + request.getWarehouseId()));
            if (!Boolean.TRUE.equals(warehouse.getActive())) {
                throw new IllegalArgumentException("Kho đã ngừng hoạt động: " + warehouse.getName());
            }
        } else if (dealer != null && dealer.getWarehouse() != null) {
            warehouse = dealer.getWarehouse();
        } else if (warehouseRepository != null) {
            List<Warehouse> activeWarehouses = warehouseRepository.findAllByActiveTrueOrderByNameAsc();
            if (!activeWarehouses.isEmpty()) {
                warehouse = activeWarehouses.get(0);
            }
        }

        // Nếu bảng giá được sử dụng, đánh dấu đã phát sinh đơn (hasOrders = true)
        if (priceList != null) {
            priceList.setHasOrders(true);
            priceListRepository.save(priceList);
        }

        // Tạo map tra cứu dòng giá từ bảng giá: productId -> PriceListItem
        Map<Long, PriceListItem> priceItemMap = new HashMap<>();
        if (priceList != null && priceList.getItems() != null) {
            for (PriceListItem pli : priceList.getItems()) {
                if (pli.getProduct() != null) {
                    priceItemMap.put(pli.getProduct().getId(), pli);
                }
            }
        }

        Order order = Order.builder()
                .orderCode(orderCode.trim())
                .customerName(request.getCustomerName() != null ? request.getCustomerName() : (dealer != null ? dealer.getName() : null))
                .customerGroup(customerGroup)
                .dealer(dealer)
                .deliveryPoint(deliveryPoint)
                .warehouse(warehouse)
                .expectedDeliveryDate(request.getExpectedDeliveryDate())
                .priceList(priceList)
                .note(request.getNote())
                .status("RECORDED")
                .requiresApproval(false)
                .inventoryReserved(false)
                .createdBy(username != null ? username : "system")
                .recordedAt(LocalDateTime.now())
                .items(new ArrayList<>())
                .build();

        boolean orderRequiresApproval = false;
        List<String> belowFloorReasons = new ArrayList<>();
        List<VolumeDiscountPolicyService.OrderItemDiscountInput> discountInputs = new ArrayList<>();
        List<ItemPreData> preDataList = new ArrayList<>();

        for (int i = 0; i < request.getItems().size(); i++) {
            OrderItemRequest itemReq = request.getItems().get(i);
            Product product = resolveProduct(itemReq);

            // S4-01: Chặn thêm dòng hàng nếu không có bảng giá hiệu lực cho SKU đó
            if (customerGroup != null) {
                if (priceList == null) {
                    throw new IllegalArgumentException(String.format("Không tìm thấy bảng giá hiệu lực cho nhóm khách hàng '%s'. Không thể thêm dòng hàng cho sản phẩm '%s'.",
                            customerGroup.getDisplayName(), product.getName()));
                }
                PriceListItem pli = priceItemMap.get(product.getId());
                if (pli == null) {
                    throw new IllegalArgumentException(String.format("Sản phẩm '%s' (mã: %s) chưa có trong bảng giá hiệu lực của nhóm khách hàng '%s'. Không thể thêm dòng hàng.",
                            product.getName(), product.getCode(), customerGroup.getDisplayName()));
                }
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

            // Lấy hệ số quy đổi tại thời điểm ghi sổ đơn hàng (S2-07)
            BigDecimal factor = unitConversionService.getConversionFactor(product, inputUnit);

            // Quy đổi về đơn vị cơ sở: baseQuantity = quantity * factor
            BigDecimal baseQty = qty.multiply(factor);

            // 3. Tự động nhận đúng giá theo bảng giá hoặc kiểm tra giá sàn (S2-10, S4-01)
            PriceListItem priceListItem = priceItemMap.get(product.getId());

            BigDecimal unitSellingPrice = null;
            BigDecimal unitFloorPrice = null;

            if (priceListItem != null) {
                // Giá bảng giá tính theo đơn vị cơ sở quy đổi theo hệ số
                unitSellingPrice = priceListItem.getSellingPrice().multiply(factor);
                unitFloorPrice = priceListItem.getFloorPrice().multiply(factor);
            }

            BigDecimal price;
            if (itemReq.getPrice() != null) {
                price = itemReq.getPrice();
            } else if (unitSellingPrice != null) {
                // Đại lý tự động nhận đúng giá bán của mình từ bảng giá
                price = unitSellingPrice;
            } else {
                price = product.getPrice() != null ? product.getPrice().multiply(factor) : BigDecimal.ZERO;
            }

            // Kiểm tra bán dưới giá sàn — bán dưới giá sàn sẽ phải qua Quản lý kinh doanh duyệt
            boolean isBelowFloor = false;
            if (unitFloorPrice != null && price.compareTo(unitFloorPrice) < 0) {
                isBelowFloor = true;
                orderRequiresApproval = true;
                belowFloorReasons.add(String.format("Sản phẩm '%s' bán giá %s < giá sàn %s (%s)",
                        product.getName(), price.toPlainString(), unitFloorPrice.toPlainString(), inputUnit));
            }

            BigDecimal subtotalAmount = price.multiply(qty);

            discountInputs.add(VolumeDiscountPolicyService.OrderItemDiscountInput.builder()
                    .index(i)
                    .product(product)
                    .unit(inputUnit)
                    .quantity(qty)
                    .conversionFactor(factor)
                    .baseQuantity(baseQty)
                    .baseUnit(product.getUnit())
                    .price(price)
                    .subtotalAmount(subtotalAmount)
                    .build());

            preDataList.add(new ItemPreData(product, inputUnit, qty, factor, baseQty, price, unitFloorPrice, isBelowFloor, subtotalAmount, itemReq.getNote()));
        }

        // 3b. Giữ chỗ tồn kho (S5-06): Thực hiện khi đơn chuyển sang Đã duyệt (RECORDED).
        // Nếu đơn cần duyệt (PENDING_APPROVAL), việc giữ chỗ sẽ thực hiện khi Quản lý duyệt đơn.

        // 4. Áp dụng chính sách chiết khấu theo sản lượng (S3-01)
        VolumeDiscountPolicyService.OrderDiscountSummary discountSummary = null;
        if (volumeDiscountPolicyService != null) {
            discountSummary = volumeDiscountPolicyService.calculateOrderDiscount(discountInputs, customerGroup, LocalDate.now());
        }

        BigDecimal grandSubtotal = BigDecimal.ZERO;
        BigDecimal grandDiscount = BigDecimal.ZERO;

        for (int i = 0; i < preDataList.size(); i++) {
            ItemPreData d = preDataList.get(i);
            BigDecimal discountAmount = BigDecimal.ZERO;
            BigDecimal discountPercent = null;
            Long appliedPolicyId = null;
            String appliedPolicyCode = null;
            String appliedPolicyName = null;

            if (discountSummary != null && discountSummary.getItemResults() != null) {
                VolumeDiscountPolicyService.ItemDiscountResult res = discountSummary.getItemResults().get(i);
                if (res != null) {
                    discountAmount = res.getDiscountAmount() != null ? res.getDiscountAmount() : BigDecimal.ZERO;
                    discountPercent = res.getDiscountPercent();
                    appliedPolicyId = res.getAppliedPolicyId();
                    appliedPolicyCode = res.getAppliedPolicyCode();
                    appliedPolicyName = res.getAppliedPolicyName();
                }
            }

            BigDecimal lineTotal = d.subtotal.subtract(discountAmount).max(BigDecimal.ZERO);
            grandSubtotal = grandSubtotal.add(d.subtotal);
            grandDiscount = grandDiscount.add(discountAmount);

            // ĐÓNG BĂNG thông tin đơn vị, hệ số, giá sàn, số lượng cơ sở và chiết khấu vào dòng đơn hàng
            OrderItem item = OrderItem.builder()
                    .order(order)
                    .product(d.product)
                    .unit(d.unit)
                    .quantity(d.quantity)
                    .conversionFactor(d.conversionFactor)
                    .baseUnit(d.product.getUnit())
                    .baseQuantity(d.baseQuantity)
                    .price(d.price)
                    .floorPrice(d.floorPrice)
                    .belowFloorPrice(d.isBelowFloor)
                    .subtotalAmount(d.subtotal)
                    .discountAmount(discountAmount)
                    .discountPercent(discountPercent)
                    .appliedPolicyId(appliedPolicyId)
                    .appliedPolicyCode(appliedPolicyCode)
                    .appliedPolicyName(appliedPolicyName)
                    .totalAmount(lineTotal)
                    .note(d.note)
                    .build();

            order.addItem(item);
        }

        order.setSubtotalAmount(grandSubtotal);
        order.setDiscountAmount(grandDiscount);
        BigDecimal orderTotal = grandSubtotal.subtract(grandDiscount).max(BigDecimal.ZERO);
        order.setTotalAmount(orderTotal);

        // 5. Kiểm tra hạn mức công nợ của đại lý (S4-02):
        // Tổng đơn cộng công nợ hiện tại vượt hạn mức thì đơn bị đánh dấu cần duyệt
        if (dealer != null && dealerDebtService != null) {
            DealerDebtResponse debtInfo = dealerDebtService.getDealerDebtInfo(dealer);
            BigDecimal currentDebt = debtInfo != null && debtInfo.getCurrentDebt() != null
                    ? debtInfo.getCurrentDebt()
                    : BigDecimal.ZERO;
            BigDecimal debtLimit = debtInfo != null && debtInfo.getDebtLimit() != null
                    ? debtInfo.getDebtLimit()
                    : (dealer.getDebtLimit() != null ? dealer.getDebtLimit() : BigDecimal.ZERO);

            if (dealerDebtService.checkProjectedDebtExceedsLimit(dealer, orderTotal, currentDebt)) {
                orderRequiresApproval = true;
                BigDecimal projectedDebt = currentDebt.add(orderTotal);
                belowFloorReasons.add(String.format(
                        "Vượt hạn mức công nợ: Công nợ hiện tại (%s) + Đơn mới (%s) = %s vượt hạn mức (%s)",
                        formatMoney(currentDebt),
                        formatMoney(orderTotal),
                        formatMoney(projectedDebt),
                        formatMoney(debtLimit)));
            }
        }

        // Nếu có sản phẩm bán dưới giá sàn hoặc đơn vượt hạn mức công nợ -> chuyển sang trạng thái CHỜ DUYỆT (PENDING_APPROVAL)
        if (orderRequiresApproval) {
            order.setStatus("PENDING_APPROVAL");
            order.setRequiresApproval(true);
            order.setApprovalReason(String.join("; ", belowFloorReasons));
            order.setInventoryReserved(false);
        } else {
            order.setStatus("RECORDED");
            order.setRequiresApproval(false);
            // S5-06: Đơn không cần duyệt -> Đã duyệt ngay khi tạo -> thực hiện giữ chỗ tồn kho
            reserveInventoryForOrder(order, request.getReservationHours(), request.getReservationExpiresAt());
        }

        Order saved = orderRepository.save(order);
        if (orderRequiresApproval) {
            recordStatusHistory(saved, null, "PENDING_APPROVAL", "Đơn hàng chờ Quản lý kinh doanh duyệt: " + saved.getApprovalReason(), username);
        } else {
            recordStatusHistory(saved, null, "RECORDED", "Đơn hàng đã được duyệt/ghi nhận, đã giữ chỗ tồn kho", username);
        }
        String auditSuffix = orderRequiresApproval ? " (Chờ duyệt: " + order.getApprovalReason() + ")" : "";
        auditLogService.logAction("INVOICE", saved.getOrderCode(), "CREATE", null,
                "Tổng tiền: " + saved.getTotalAmount().toPlainString() + auditSuffix,
                username != null ? username : "system");

        return mapToResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public OrderFilterResult getOrders(OrderFilterRequest filter) {
        if (filter == null) {
            filter = new OrderFilterRequest();
        }

        final OrderFilterRequest f = filter;
        final boolean salesOnly = isSalesOnlyUser();
        final User currentUser = salesOnly ? getCurrentUser() : null;
        final Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        final String currentUsername = auth != null ? auth.getName() : null;

        Specification<Order> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // 1. Phân quyền dữ liệu (RLS): Nhân viên kinh doanh chỉ thấy đơn của đại lý mình phụ trách (S4-07)
            if (salesOnly) {
                Join<Order, Dealer> dealerJoin = root.join("dealer", JoinType.INNER);
                if (currentUser != null && currentUser.getId() != null) {
                    predicates.add(cb.equal(dealerJoin.get("assignedUser").get("id"), currentUser.getId()));
                } else if (currentUsername != null && !currentUsername.isBlank()) {
                    predicates.add(cb.equal(dealerJoin.get("assignedUser").get("username"), currentUsername));
                } else {
                    predicates.add(cb.disjunction());
                }
            }

            // 2. Lọc theo trạng thái đơn hàng (status)
            if (f.getStatus() != null && !f.getStatus().isBlank()) {
                predicates.add(cb.equal(cb.upper(root.get("status")), f.getStatus().trim().toUpperCase()));
            }

            // 3. Lọc theo đại lý (dealerId)
            if (f.getDealerId() != null) {
                predicates.add(cb.equal(root.get("dealer").get("id"), f.getDealerId()));
            }

            // 4. Lọc theo nhân viên kinh doanh phụ trách (salesUserId)
            if (!salesOnly && f.getResolvedSalesUserId() != null) {
                Join<Order, Dealer> dealerJoin = root.join("dealer", JoinType.INNER);
                predicates.add(cb.equal(dealerJoin.get("assignedUser").get("id"), f.getResolvedSalesUserId()));
            }

            // 5. Lọc theo khu vực / địa bàn (territoryId)
            if (f.getTerritoryId() != null) {
                Join<Order, Dealer> dealerJoin = root.join("dealer", JoinType.INNER);
                predicates.add(cb.equal(dealerJoin.get("territory").get("id"), f.getTerritoryId()));
            }

            // 6. Lọc theo khoảng thời gian tạo đơn
            if (f.getResolvedStartDate() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), f.getResolvedStartDate().atStartOfDay()));
            }
            if (f.getResolvedEndDate() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), f.getResolvedEndDate().atTime(LocalTime.MAX)));
            }

            // 7. Lọc theo điều kiện phê duyệt
            if (f.getApprovalRequired() != null) {
                predicates.add(cb.equal(root.get("requiresApproval"), f.getApprovalRequired()));
            }
            if (f.getApprovalReason() != null && !f.getApprovalReason().isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("approvalReason")), "%" + f.getApprovalReason().trim().toLowerCase() + "%"));
            }

            // 8. Tìm kiếm nhanh từ khóa (mã đơn, tên khách, tên/mã đại lý)
            String kw = f.getResolvedKeyword();
            if (kw != null && !kw.isBlank()) {
                String pattern = "%" + kw.toLowerCase() + "%";
                Join<Order, Dealer> dealerJoin = root.join("dealer", JoinType.LEFT);
                Predicate kwPredicate = cb.or(
                        cb.like(cb.lower(root.get("orderCode")), pattern),
                        cb.like(cb.lower(root.get("customerName")), pattern),
                        cb.like(cb.lower(dealerJoin.get("name")), pattern),
                        cb.like(cb.lower(dealerJoin.get("code")), pattern)
                );
                predicates.add(kwPredicate);
            }

            return predicates.isEmpty() ? cb.conjunction() : cb.and(predicates.toArray(new Predicate[0]));
        };

        List<Order> orders = orderRepository.findAll(spec, Sort.by(Sort.Direction.DESC, "createdAt"));

        // Tính tổng tiền của tập kết quả đang lọc (S4-07)
        BigDecimal totalAmount = orders.stream()
                .map(Order::getTotalAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<OrderResponse> responses = orders.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());

        return OrderFilterResult.builder()
                .orders(responses)
                .totalAmount(totalAmount)
                .totalOrders((long) responses.size())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderResponse> getAllOrders() {
        return getOrders(new OrderFilterRequest()).getOrders();
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse getOrderById(Long id) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth != null ? auth.getName() : null;
        return getOrderById(id, username);
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse getOrderById(Long id, String username) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn hàng với ID: " + id));
        validateSalesViewAccess(order, username);
        return mapToResponse(order);
    }

    @Override
    @Transactional
    public OrderResponse cancelOrder(Long id, String reason, String username) {
        if (reason == null || reason.trim().isBlank()) {
            throw new IllegalArgumentException("Lý do huỷ đơn hàng không được để trống");
        }
        String trimmedReason = reason.trim();

        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn hàng với ID: " + id));

        // RLS kiểm tra quyền hạn cho SALES: chỉ huỷ được đơn của mình hoặc đại lý phụ trách
        checkSalesAuthorizationForOrder(order, "huỷ");

        String oldStatus = order.getStatus();
        if ("CANCELLED".equalsIgnoreCase(oldStatus)) {
            throw new IllegalArgumentException("Đơn hàng này đã bị huỷ trước đó");
        }

        // Đơn đã xuất kho thì không huỷ được, phải đi đường trả hàng (S4-06)
        if (OrderStatus.isExportedOrLater(oldStatus)) {
            throw new OrderCannotBeCancelledException("Đơn đã xuất kho thì không huỷ được, phải đi đường trả hàng");
        }

        // Tự động nhả tồn kho đang giữ chỗ (S4-06)
        releaseReservedInventory(order);

        order.setStatus("CANCELLED");
        order.setCancelReason(trimmedReason);
        order.setCancelledBy(username != null ? username : "system");
        order.setCancelledAt(LocalDateTime.now());
        if (order.getNote() == null || order.getNote().isBlank()) {
            order.setNote("Huỷ: " + trimmedReason);
        } else {
            order.setNote(order.getNote() + " | Huỷ: " + trimmedReason);
        }

        Order saved = orderRepository.save(order);
        recordStatusHistory(saved, oldStatus, "CANCELLED", "Huỷ đơn hàng. Lý do: " + trimmedReason, username);
        auditLogService.logAction("INVOICE", saved.getOrderCode(), "CANCEL", oldStatus,
                "Huỷ đơn: " + trimmedReason, username != null ? username : "system");

        return mapToResponse(saved);
    }

    @Override
    @Transactional
    public OrderResponse cancelOrder(Long id, String username) {
        return cancelOrder(id, "Huỷ bởi người dùng " + (username != null ? username : "system"), username);
    }

    @Override
    @Transactional
    public OrderResponse updateOrderStatus(Long id, String status, String note, String username) {
        if (status == null || status.isBlank()) {
            throw new IllegalArgumentException("Trạng thái mới không được để trống");
        }
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn hàng với ID: " + id));

        String oldStatus = order.getStatus();
        String targetStatus = status.trim().toUpperCase();

        if ("CANCELLED".equalsIgnoreCase(oldStatus)) {
            throw new IllegalArgumentException("Không thể cập nhật trạng thái của đơn hàng đã bị huỷ");
        }
        if ("CANCELLED".equalsIgnoreCase(targetStatus)) {
            throw new IllegalArgumentException("Vui lòng sử dụng chức năng huỷ đơn hàng kèm lý do huỷ");
        }

        try {
            OrderStatus.valueOf(targetStatus);
        } catch (IllegalArgumentException e) {
            if (!"PROCESSING".equals(targetStatus) && !"SHIPPED".equals(targetStatus) && !"COMPLETED".equals(targetStatus)) {
                throw new IllegalArgumentException("Trạng thái đơn hàng không hợp lệ: " + status);
            }
        }

        // Khi chuyển sang EXPORTED (Đã xuất): trừ tồn kho thực tế và nhả giữ chỗ (S4-06)
        if (("EXPORTED".equals(targetStatus) || "SHIPPED".equals(targetStatus))
                && !OrderStatus.isExportedOrLater(oldStatus)) {
            deductInventoryOnExport(order);
        }

        order.setStatus(targetStatus);
        if (note != null && !note.isBlank()) {
            order.setNote((order.getNote() != null ? order.getNote() + " | " : "") + note.trim());
        }

        Order saved = orderRepository.save(order);
        recordStatusHistory(saved, oldStatus, targetStatus, note, username);
        auditLogService.logAction("INVOICE", saved.getOrderCode(), "UPDATE_STATUS", oldStatus,
                "Chuyển trạng thái sang: " + OrderStatus.getDisplayName(targetStatus) + (note != null ? " (" + note + ")" : ""),
                username != null ? username : "system");

        return mapToResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public OrderTrackingResponse getOrderTracking(Long id, String username) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn hàng với ID: " + id));

        checkSalesAuthorizationForOrder(order, "theo dõi");

        List<OrderStatusHistory> historyEntities = orderStatusHistoryRepository != null
                ? orderStatusHistoryRepository.findByOrderIdOrderByChangedAtAsc(order.getId())
                : Collections.emptyList();

        List<OrderStatusHistoryResponse> historyList = historyEntities.stream()
                .map(h -> OrderStatusHistoryResponse.builder()
                        .id(h.getId())
                        .orderId(h.getOrder().getId())
                        .previousStatus(h.getPreviousStatus())
                        .newStatus(h.getNewStatus())
                        .statusDisplayName(h.getStatusDisplayName())
                        .note(h.getNote())
                        .changedBy(h.getChangedBy())
                        .changedAt(h.getChangedAt())
                        .build())
                .collect(Collectors.toList());

        String currentStatus = order.getStatus();
        boolean isCancelled = "CANCELLED".equalsIgnoreCase(currentStatus);
        boolean isRejected = "REJECTED".equalsIgnoreCase(currentStatus);
        boolean wasApprovalRequired = Boolean.TRUE.equals(order.getRequiresApproval());

        Map<String, OrderStatusHistory> historyByStatus = new HashMap<>();
        for (OrderStatusHistory h : historyEntities) {
            historyByStatus.put(h.getNewStatus(), h);
        }

        List<OrderLifecycleStepResponse> steps = new ArrayList<>();
        int currentStepOrder = resolveStepOrder(currentStatus);

        // Step 1: DRAFT
        steps.add(buildStep(1, "DRAFT", "Nháp", "Đơn hàng đang ở trạng thái nháp",
                resolveStepState(1, currentStepOrder, isCancelled, isRejected, false),
                historyByStatus.get("DRAFT"), order.getCreatedAt(), order.getCreatedBy()));

        // Step 2: PENDING_APPROVAL
        boolean skippedApproval = !wasApprovalRequired && !historyByStatus.containsKey("PENDING_APPROVAL");
        steps.add(buildStep(2, "PENDING_APPROVAL", "Chờ duyệt", "Chờ Quản lý kinh doanh duyệt (giá sàn / công nợ)",
                skippedApproval ? "SKIPPED" : resolveStepState(2, currentStepOrder, isCancelled, isRejected, false),
                historyByStatus.get("PENDING_APPROVAL"), null, null));

        // Step 3: APPROVED
        OrderStatusHistory approvedHist = historyByStatus.get("RECORDED");
        if (approvedHist == null) approvedHist = historyByStatus.get("APPROVED");
        steps.add(buildStep(3, "APPROVED", "Đã duyệt", "Đơn hàng đã được duyệt, sẵn sàng soạn hàng",
                resolveStepState(3, currentStepOrder, isCancelled, isRejected, false),
                approvedHist, order.getRecordedAt(), order.getApprovedBy() != null ? order.getApprovedBy() : order.getCreatedBy()));

        // Step 4: PICKING
        OrderStatusHistory pickingHist = historyByStatus.get("PICKING");
        if (pickingHist == null) pickingHist = historyByStatus.get("PROCESSING");
        steps.add(buildStep(4, "PICKING", "Đang soạn hàng", "Kho đang thực hiện soạn hàng và đóng gói",
                resolveStepState(4, currentStepOrder, isCancelled, isRejected, false),
                pickingHist, null, null));

        // Step 5: EXPORTED
        OrderStatusHistory exportedHist = historyByStatus.get("EXPORTED");
        if (exportedHist == null) exportedHist = historyByStatus.get("SHIPPED");
        steps.add(buildStep(5, "EXPORTED", "Đã xuất", "Đã xuất kho, đang vận chuyển đến đại lý",
                resolveStepState(5, currentStepOrder, isCancelled, isRejected, false),
                exportedHist, null, null));

        // Step 6: DELIVERED
        steps.add(buildStep(6, "DELIVERED", "Đã giao", "Đã giao hàng thành công đến đại lý",
                resolveStepState(6, currentStepOrder, isCancelled, isRejected, false),
                historyByStatus.get("DELIVERED"), null, null));

        // Step 7: CLOSED
        OrderStatusHistory closedHist = historyByStatus.get("CLOSED");
        if (closedHist == null) closedHist = historyByStatus.get("COMPLETED");
        steps.add(buildStep(7, "CLOSED", "Đóng", "Đơn hàng hoàn tất toàn bộ chu trình và đóng",
                resolveStepState(7, currentStepOrder, isCancelled, isRejected, false),
                closedHist, null, null));

        return OrderTrackingResponse.builder()
                .orderId(order.getId())
                .orderCode(order.getOrderCode())
                .customerName(order.getCustomerName())
                .dealerId(order.getDealer() != null ? order.getDealer().getId() : null)
                .dealerName(order.getDealer() != null ? order.getDealer().getName() : null)
                .dealerCode(order.getDealer() != null ? order.getDealer().getCode() : null)
                .warehouseId(order.getWarehouse() != null ? order.getWarehouse().getId() : null)
                .warehouseCode(order.getWarehouse() != null ? order.getWarehouse().getCode() : null)
                .warehouseName(order.getWarehouse() != null ? order.getWarehouse().getName() : null)
                .currentStatus(currentStatus)
                .currentStatusDisplayName(OrderStatus.getDisplayName(currentStatus))
                .currentStep(currentStepOrder)
                .canCancel(OrderStatus.canCancel(currentStatus))
                .isExported(OrderStatus.isExportedOrLater(currentStatus))
                .inventoryReserved(order.getInventoryReserved())
                .cancelReason(order.getCancelReason())
                .cancelledBy(order.getCancelledBy())
                .cancelledAt(order.getCancelledAt())
                .expectedDeliveryDate(order.getExpectedDeliveryDate())
                .createdAt(order.getCreatedAt())
                .recordedAt(order.getRecordedAt())
                .totalAmount(order.getTotalAmount())
                .steps(steps)
                .history(historyList)
                .build();
    }

    @Override
    @Transactional
    public OrderResponse approveOrder(Long id, String username) {
        return approveOrder(id, null, username);
    }

    @Override
    @Transactional
    public OrderResponse approveOrder(Long id, Integer reservationHours, String username) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn hàng với ID: " + id));

        if (!"PENDING_APPROVAL".equalsIgnoreCase(order.getStatus())) {
            throw new IllegalArgumentException("Chỉ có thể phê duyệt đơn hàng đang ở trạng thái Chờ duyệt (PENDING_APPROVAL)");
        }

        // S5-06: Đơn chuyển sang Đã duyệt thì tồn tương ứng được giữ chỗ ngay trong cùng giao dịch (@Transactional)
        reserveInventoryForOrder(order, reservationHours, null);

        order.setStatus("RECORDED");
        order.setApprovedBy(username != null ? username : "system");
        order.setApprovedAt(LocalDateTime.now());
        if (order.getWarehouse() != null) {
            reserveInventory(order);
        }

        Order saved = orderRepository.save(order);
        recordStatusHistory(saved, "PENDING_APPROVAL", "RECORDED", "Quản lý kinh doanh phê duyệt đơn hàng, đã giữ chỗ tồn kho", username);

        Dealer dealer = order.getDealer();
        boolean dealerLocked = dealer != null && Boolean.TRUE.equals(dealer.getLocked());
        String auditDetail = "Quản lý kinh doanh duyệt đơn hàng (Giữ chỗ tồn: " +
                (Boolean.TRUE.equals(saved.getInventoryReserved()) ? "Thành công" : "Không áp dụng") + ")" +
                (dealerLocked ? " [Cảnh báo: Đại lý '" + dealer.getName() + "' đang bị khoá giao dịch. Lý do: " + dealer.getLockReason() + "]" : "");
        auditLogService.logAction("INVOICE", saved.getOrderCode(), "APPROVE", "PENDING_APPROVAL",
                auditDetail, username != null ? username : "system");
        return mapToResponse(saved);
    }

    @Override
    @Transactional
    public int releaseExpiredReservations() {
        LocalDateTime now = LocalDateTime.now();
        List<Order> expiredOrders = orderRepository.findExpiredReservations(now);
        int count = 0;
        for (Order order : expiredOrders) {
            String oldStatus = order.getStatus();
            releaseReservedInventory(order);
            order.setStatus("EXPIRED");
            String expiryTimeStr = order.getReservationExpiresAt() != null
                    ? order.getReservationExpiresAt().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"))
                    : now.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"));
            String noteMsg = "[Hệ thống tự động nhả tồn]: Đơn hàng quá hạn giữ chỗ lúc " + expiryTimeStr;
            order.setNote((order.getNote() != null && !order.getNote().isBlank())
                    ? order.getNote() + " | " + noteMsg
                    : noteMsg);
            orderRepository.save(order);
            auditLogService.logAction("INVOICE", order.getOrderCode(), "EXPIRE_RESERVATION", oldStatus,
                    "Quá hạn giữ chỗ - tự động nhả tồn kho", "system");
            count++;
        }
        return count;
    }

    @Override
    @Transactional
    public OrderResponse rejectOrder(Long id, String reason, String username) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn hàng với ID: " + id));

        if (!"PENDING_APPROVAL".equalsIgnoreCase(order.getStatus())) {
            throw new IllegalArgumentException("Chỉ có thể từ chối đơn hàng đang ở trạng thái Chờ duyệt (PENDING_APPROVAL)");
        }

        releaseReservedInventory(order);
        order.setStatus("REJECTED");
        order.setApprovedBy(username != null ? username : "system");
        order.setApprovedAt(LocalDateTime.now());
        if (reason != null && !reason.isBlank()) {
            order.setNote((order.getNote() != null ? order.getNote() + " | " : "") + "Lý do từ chối: " + reason.trim());
        }

        Order saved = orderRepository.save(order);
        recordStatusHistory(saved, "PENDING_APPROVAL", "REJECTED", "Từ chối duyệt: " + (reason != null ? reason : ""), username);
        auditLogService.logAction("INVOICE", saved.getOrderCode(), "REJECT", "PENDING_APPROVAL",
                "Từ chối đơn bán dưới giá sàn: " + (reason != null ? reason : ""), username != null ? username : "system");
        return mapToResponse(saved);
    }

    private void reserveInventoryForOrder(Order order, Integer reservationHours, LocalDateTime explicitExpiry) {
        if (order == null || order.getWarehouse() == null || inventoryRepository == null
                || order.getItems() == null || order.getItems().isEmpty()) {
            return;
        }

        if (Boolean.TRUE.equals(order.getInventoryReserved())) {
            return;
        }

        Warehouse warehouse = order.getWarehouse();
        List<OrderItem> items = order.getItems();

        Map<Long, BigDecimal> requestedBaseQtyByProduct = new HashMap<>();
        for (OrderItem item : items) {
            if (item.getProduct() != null && item.getBaseQuantity() != null) {
                requestedBaseQtyByProduct.merge(item.getProduct().getId(), item.getBaseQuantity(), BigDecimal::add);
            }
        }

        if (requestedBaseQtyByProduct.isEmpty()) {
            return;
        }

        List<Long> sortedProductIds = requestedBaseQtyByProduct.keySet().stream().sorted().toList();
        List<WarehouseInventory> inventories = inventoryRepository
                .findByWarehouseIdAndProductIdInForUpdate(warehouse.getId(), sortedProductIds);
        Map<Long, WarehouseInventory> inventoryMap = inventories.stream()
                .collect(Collectors.toMap(inv -> inv.getProduct().getId(), inv -> inv));

        for (Map.Entry<Long, BigDecimal> entry : requestedBaseQtyByProduct.entrySet()) {
            Long pId = entry.getKey();
            BigDecimal totalRequestedBase = entry.getValue();
            WarehouseInventory inv = inventoryMap.get(pId);

            BigDecimal physical = (inv != null && inv.getBaseQuantity() != null) ? inv.getBaseQuantity() : BigDecimal.ZERO;
            BigDecimal reserved = (inv != null && inv.getReservedQuantity() != null) ? inv.getReservedQuantity() : BigDecimal.ZERO;
            BigDecimal available = physical.subtract(reserved).max(BigDecimal.ZERO);

            if (totalRequestedBase.compareTo(available) > 0) {
                OrderItem repItem = items.stream()
                        .filter(it -> it.getProduct() != null && pId.equals(it.getProduct().getId()))
                        .findFirst()
                        .orElse(null);
                Product product = repItem != null ? repItem.getProduct() : (inv != null ? inv.getProduct() : null);
                String prodName = product != null ? product.getName() : ("ID " + pId);
                String prodCode = product != null ? product.getCode() : String.valueOf(pId);
                String baseUnit = product != null ? product.getUnit() : "đơn vị";
                String itemUnit = repItem != null && repItem.getUnit() != null ? repItem.getUnit() : baseUnit;
                BigDecimal factor = repItem != null && repItem.getConversionFactor() != null && repItem.getConversionFactor().compareTo(BigDecimal.ZERO) > 0
                        ? repItem.getConversionFactor()
                        : BigDecimal.ONE;
                BigDecimal maxAllowedInUnit = available.divide(factor, 0, RoundingMode.FLOOR);

                if (available.compareTo(BigDecimal.ZERO) <= 0) {
                    throw new IllegalArgumentException(String.format(
                            "Mặt hàng '%s' (mã: %s) đã hết tồn khả dụng trong kho phục vụ '%s'. Tồn khả dụng hiện tại: 0 %s. Số lượng tối đa có thể đặt là 0 %s.",
                            prodName, prodCode, warehouse.getName(), baseUnit, itemUnit
                    ));
                } else {
                    BigDecimal reqQtyInUnit = repItem != null && repItem.getQuantity() != null ? repItem.getQuantity() : totalRequestedBase;
                    throw new IllegalArgumentException(String.format(
                            "Vượt tồn khả dụng cho sản phẩm '%s' (mã: %s) tại kho '%s'. Tồn khả dụng hiện tại: %s %s. Yêu cầu đặt: %s %s (%s %s). Số lượng tối đa có thể đặt là %s %s.",
                            prodName, prodCode, warehouse.getName(),
                            available.stripTrailingZeros().toPlainString(), baseUnit,
                            reqQtyInUnit.stripTrailingZeros().toPlainString(), itemUnit,
                            totalRequestedBase.stripTrailingZeros().toPlainString(), baseUnit,
                            maxAllowedInUnit.stripTrailingZeros().toPlainString(), itemUnit
                    ));
                }
            }
        }

        // Đủ tồn khả dụng -> Thực hiện giữ chỗ
        for (Map.Entry<Long, BigDecimal> entry : requestedBaseQtyByProduct.entrySet()) {
            Long pId = entry.getKey();
            BigDecimal reqBase = entry.getValue();
            WarehouseInventory inv = inventoryMap.get(pId);
            if (inv != null) {
                inv.addReservedQuantity(reqBase);
                inventoryRepository.save(inv);
            }
        }

        order.setInventoryReserved(true);
        order.setReservedAt(LocalDateTime.now());
        order.setReservationExpiresAt(calculateReservationExpiry(order, reservationHours, explicitExpiry));
    }

    private LocalDateTime calculateReservationExpiry(Order order, Integer reservationHours, LocalDateTime explicitExpiry) {
        if (explicitExpiry != null) {
            return explicitExpiry;
        }
        if (reservationHours != null && reservationHours > 0) {
            return LocalDateTime.now().plusHours(reservationHours);
        }
        if (order != null && order.getExpectedDeliveryDate() != null) {
            LocalDateTime deliveryEnd = order.getExpectedDeliveryDate().atTime(23, 59, 59);
            if (deliveryEnd.isAfter(LocalDateTime.now())) {
                return deliveryEnd;
            }
        }
        return LocalDateTime.now().plusHours(48);
    }

    private void releaseReservedInventory(Order order) {
        if (Boolean.TRUE.equals(order.getInventoryReserved()) && order.getWarehouse() != null && inventoryRepository != null && order.getItems() != null && !order.getItems().isEmpty()) {
            Map<Long, BigDecimal> baseQtyByProduct = new HashMap<>();
            for (OrderItem item : order.getItems()) {
                if (item.getProduct() != null && item.getBaseQuantity() != null) {
                    baseQtyByProduct.merge(item.getProduct().getId(), item.getBaseQuantity(), BigDecimal::add);
                }
            }

            if (!baseQtyByProduct.isEmpty()) {
                List<Long> sortedProductIds = baseQtyByProduct.keySet().stream().sorted().toList();
                List<WarehouseInventory> inventories = inventoryRepository.findByWarehouseIdAndProductIdInForUpdate(order.getWarehouse().getId(), sortedProductIds);
                Map<Long, WarehouseInventory> inventoryMap = inventories.stream()
                        .collect(Collectors.toMap(inv -> inv.getProduct().getId(), inv -> inv));

                for (Map.Entry<Long, BigDecimal> entry : baseQtyByProduct.entrySet()) {
                    WarehouseInventory inv = inventoryMap.get(entry.getKey());
                    if (inv != null) {
                        inv.subtractReservedQuantity(entry.getValue());
                        inventoryRepository.save(inv);
                    }
                }
            }
            order.setInventoryReserved(false);
            order.setReservationExpiresAt(null);
        }
    }

    private Product resolveProduct(OrderItemRequest itemReq) {
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

    private OrderResponse mapToResponse(Order order) {
        Warehouse orderWh = order.getWarehouse() != null
                ? order.getWarehouse()
                : (order.getDealer() != null ? order.getDealer().getWarehouse() : null);

        List<OrderItemResponse> items = order.getItems() != null
                ? order.getItems().stream().map(it -> mapToItemResponse(it, orderWh)).collect(Collectors.toList())
                : Collections.emptyList();

        Dealer dealer = order.getDealer();
        boolean dealerLocked = dealer != null && Boolean.TRUE.equals(dealer.getLocked());
        String dealerLockWarning = dealerLocked
                ? String.format("Cảnh báo: Đại lý '%s' (%s) đang bị khoá giao dịch. Lý do: %s. Đơn hàng đang dở dang vẫn được xử lý nhưng cần theo dõi thu hồi công nợ.",
                        dealer.getName(), dealer.getCode(), dealer.getLockReason() != null ? dealer.getLockReason() : "Không nêu rõ")
                : null;

        boolean exceedsDebt = order.getApprovalReason() != null && order.getApprovalReason().contains("Vượt hạn mức công nợ");
        BigDecimal currentDebt = null;
        BigDecimal debtLimit = null;
        BigDecimal remainingLimit = null;

        if (dealer != null) {
            debtLimit = dealer.getDebtLimit();
            if (dealerDebtService != null) {
                try {
                    DealerDebtResponse dInfo = dealerDebtService.getDealerDebtInfo(dealer);
                    currentDebt = dInfo.getCurrentDebt();
                    remainingLimit = dInfo.getRemainingLimit();
                } catch (Exception ignored) {
                }
            }
        }

        String warning = dealerLockWarning;
        if (warning == null && Boolean.TRUE.equals(order.getRequiresApproval())) {
            warning = order.getApprovalReason();
        }

        String dealerCode = dealer != null ? dealer.getCode() : null;
        String dealerName = dealer != null ? dealer.getName() : null;
        User assignedUser = dealer != null ? dealer.getAssignedUser() : null;
        Long salesUserId = assignedUser != null ? assignedUser.getId() : null;
        String salesUserName = assignedUser != null ? assignedUser.getUsername() : null;
        String salesUserFullName = assignedUser != null ? assignedUser.getFullName() : null;
        Territory territory = dealer != null ? dealer.getTerritory() : null;
        Long territoryId = territory != null ? territory.getId() : null;
        String territoryCode = territory != null ? territory.getCode() : null;
        String territoryName = territory != null ? territory.getName() : null;

        return OrderResponse.builder()
                .id(order.getId())
                .orderCode(order.getOrderCode())
                .customerName(order.getCustomerName())
                .customerGroup(order.getCustomerGroup())
                .dealerId(dealer != null ? dealer.getId() : null)
                .dealerCode(dealerCode)
                .dealerName(dealerName)
                .salesUserId(salesUserId)
                .salesUserName(salesUserName)
                .salesUserFullName(salesUserFullName)
                .territoryId(territoryId)
                .territoryCode(territoryCode)
                .territoryName(territoryName)
                .deliveryPointId(order.getDeliveryPoint() != null ? order.getDeliveryPoint().getId() : null)
                .warehouseId(order.getWarehouse() != null ? order.getWarehouse().getId() : null)
                .warehouseCode(order.getWarehouse() != null ? order.getWarehouse().getCode() : null)
                .warehouseName(order.getWarehouse() != null ? order.getWarehouse().getName() : null)
                .expectedDeliveryDate(order.getExpectedDeliveryDate())
                .priceListId(order.getPriceList() != null ? order.getPriceList().getId() : null)
                .priceListCode(order.getPriceList() != null ? order.getPriceList().getCode() : null)
                .warehouseId(order.getWarehouse() != null ? order.getWarehouse().getId() : null)
                .warehouseCode(order.getWarehouse() != null ? order.getWarehouse().getCode() : null)
                .warehouseName(order.getWarehouse() != null ? order.getWarehouse().getName() : null)
                .status(order.getStatus())
                .statusDisplayName(OrderStatus.getDisplayName(order.getStatus()))
                .canCancel(OrderStatus.canCancel(order.getStatus()))
                .isExported(OrderStatus.isExportedOrLater(order.getStatus()))
                .inventoryReserved(order.getInventoryReserved())
                .cancelReason(order.getCancelReason())
                .cancelledBy(order.getCancelledBy())
                .cancelledAt(order.getCancelledAt())
                .reservedAt(order.getReservedAt())
                .reservationExpiresAt(order.getReservationExpiresAt())
                .requiresApproval(order.getRequiresApproval())
                .approvalReason(order.getApprovalReason())
                .approvedBy(order.getApprovedBy())
                .approvedAt(order.getApprovedAt())
                .subtotalAmount(order.getSubtotalAmount() != null ? order.getSubtotalAmount() : order.getTotalAmount())
                .discountAmount(order.getDiscountAmount() != null ? order.getDiscountAmount() : BigDecimal.ZERO)
                .totalAmount(order.getTotalAmount())
                .note(order.getNote())
                .createdBy(order.getCreatedBy())
                .createdAt(order.getCreatedAt())
                .recordedAt(order.getRecordedAt())
                .items(items)
                .dealerLocked(dealerLocked)
                .dealerLockWarning(dealerLockWarning)
                .warning(warning)
                .exceedsDebtLimit(exceedsDebt)
                .currentDebt(currentDebt)
                .debtLimit(debtLimit)
                .remainingDebtLimit(remainingLimit)
                .build();
    }

    private String formatMoney(BigDecimal amount) {
        if (amount == null) return "0 đ";
        return new java.text.DecimalFormat("#,### đ").format(amount);
    }

    private OrderItemResponse mapToItemResponse(OrderItem item) {
        return mapToItemResponse(item, item.getOrder() != null ? item.getOrder().getWarehouse() : null);
    }

    private OrderItemResponse mapToItemResponse(OrderItem item, Warehouse warehouse) {
        Long locationId = null;
        String locationCode = null;
        String locationName = null;
        String locationType = null;
        String zoneName = null;
        String shelfName = null;
        String locationDisplay = null;

        if (locationProductRepository != null && warehouse != null && item.getProduct() != null) {
            List<WarehouseLocationProduct> mappings =
                    locationProductRepository.findByWarehouseIdAndProductId(warehouse.getId(), item.getProduct().getId());
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

        return OrderItemResponse.builder()
                .id(item.getId())
                .productId(item.getProduct() != null ? item.getProduct().getId() : null)
                .productCode(item.getProduct() != null ? item.getProduct().getCode() : null)
                .productName(item.getProduct() != null ? item.getProduct().getName() : null)
                .unit(item.getUnit())
                .quantity(item.getQuantity())
                .conversionFactor(item.getConversionFactor())
                .baseUnit(item.getBaseUnit())
                .baseQuantity(item.getBaseQuantity())
                .price(item.getPrice())
                .floorPrice(item.getFloorPrice())
                .belowFloorPrice(item.getBelowFloorPrice())
                .subtotalAmount(item.getSubtotalAmount() != null ? item.getSubtotalAmount() : item.getTotalAmount())
                .discountAmount(item.getDiscountAmount() != null ? item.getDiscountAmount() : BigDecimal.ZERO)
                .discountPercent(item.getDiscountPercent())
                .appliedPolicyId(item.getAppliedPolicyId())
                .appliedPolicyCode(item.getAppliedPolicyCode())
                .appliedPolicyName(item.getAppliedPolicyName())
                .totalAmount(item.getTotalAmount())
                .note(item.getNote())
                .locationId(locationId)
                .locationCode(locationCode)
                .locationName(locationName)
                .locationType(locationType)
                .zoneName(zoneName)
                .shelfName(shelfName)
                .locationDisplay(locationDisplay)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public OrderInventoryCheckResponse checkInventory(OrderInventoryCheckRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Dữ liệu kiểm tra tồn kho không được để trống");
        }
        if (request.getProductId() == null) {
            throw new IllegalArgumentException("ID sản phẩm không được để trống");
        }
        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy sản phẩm với ID: " + request.getProductId()));

        Warehouse warehouse = null;
        if (request.getWarehouseId() != null) {
            warehouse = warehouseRepository.findById(request.getWarehouseId())
                    .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy kho hàng với ID: " + request.getWarehouseId()));
        } else if (request.getDealerId() != null) {
            Dealer dealer = dealerRepository.findById(request.getDealerId()).orElse(null);
            if (dealer != null && dealer.getWarehouse() != null) {
                warehouse = dealer.getWarehouse();
            }
        }

        if (warehouse == null && warehouseRepository != null) {
            List<Warehouse> activeWarehouses = warehouseRepository.findAllByActiveTrueOrderByNameAsc();
            if (!activeWarehouses.isEmpty()) {
                warehouse = activeWarehouses.get(0);
            }
        }

        if (warehouse == null) {
            throw new IllegalArgumentException("Hệ thống chưa có kho hàng nào đang hoạt động");
        }

        String inputUnit = request.getUnit();
        if (inputUnit == null || inputUnit.isBlank()) {
            inputUnit = product.getUnit();
        } else {
            inputUnit = inputUnit.trim();
        }

        BigDecimal qty = request.getQuantity() != null ? request.getQuantity() : BigDecimal.ZERO;
        BigDecimal factor = unitConversionService.getConversionFactor(product, inputUnit);
        BigDecimal baseQty = qty.multiply(factor);

        WarehouseInventory inv = inventoryRepository != null
                ? inventoryRepository.findByWarehouseIdAndProductId(warehouse.getId(), product.getId()).orElse(null)
                : null;

        BigDecimal physical = (inv != null && inv.getBaseQuantity() != null) ? inv.getBaseQuantity() : BigDecimal.ZERO;
        BigDecimal reserved = (inv != null && inv.getReservedQuantity() != null) ? inv.getReservedQuantity() : BigDecimal.ZERO;
        BigDecimal available = physical.subtract(reserved).max(BigDecimal.ZERO);

        BigDecimal effectiveFactor = (factor != null && factor.compareTo(BigDecimal.ZERO) > 0) ? factor : BigDecimal.ONE;
        BigDecimal availableInUnit = available.divide(effectiveFactor, 2, RoundingMode.HALF_UP);
        BigDecimal maxAllowedInUnit = available.divide(effectiveFactor, 0, RoundingMode.FLOOR);

        boolean valid = baseQty.compareTo(available) <= 0;
        String message;
        if (valid) {
            message = String.format("Còn đủ tồn khả dụng trong kho '%s'. Tồn khả dụng: %s %s (%s %s).",
                    warehouse.getName(),
                    availableInUnit.stripTrailingZeros().toPlainString(), inputUnit,
                    available.stripTrailingZeros().toPlainString(), product.getUnit());
        } else {
            if (available.compareTo(BigDecimal.ZERO) <= 0) {
                message = String.format("Mặt hàng '%s' (mã: %s) đã hết tồn khả dụng tại kho phục vụ '%s'. Tối đa có thể đặt: 0 %s.",
                        product.getName(), product.getCode(), warehouse.getName(), inputUnit);
            } else {
                message = String.format("Vượt tồn khả dụng cho sản phẩm '%s' (mã: %s) tại kho '%s'. Tồn khả dụng hiện tại: %s %s. Số lượng tối đa có thể đặt là %s %s.",
                        product.getName(), product.getCode(), warehouse.getName(),
                        available.stripTrailingZeros().toPlainString(), product.getUnit(),
                        maxAllowedInUnit.stripTrailingZeros().toPlainString(), inputUnit);
            }
        }

        return OrderInventoryCheckResponse.builder()
                .warehouseId(warehouse.getId())
                .warehouseCode(warehouse.getCode())
                .warehouseName(warehouse.getName())
                .productId(product.getId())
                .productCode(product.getCode())
                .productName(product.getName())
                .unit(inputUnit)
                .quantity(qty)
                .conversionFactor(factor)
                .baseUnit(product.getUnit())
                .baseQuantity(baseQty)
                .physicalQuantity(physical)
                .reservedQuantity(reserved)
                .availableQuantity(available)
                .availableInUnit(availableInUnit)
                .maxAllowedInUnit(maxAllowedInUnit)
                .valid(valid)
                .message(message)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public OrderLineItemPricingResponse calculateLineItemPricing(OrderLineItemPricingRequest request) {
        if (request.getProductId() == null && (request.getProductCode() == null || request.getProductCode().isBlank())) {
            throw new IllegalArgumentException("Cần cung cấp productId hoặc productCode");
        }

        Dealer dealer = null;
        CustomerGroup customerGroup = request.getCustomerGroup();

        if (request.getDealerId() != null) {
            dealer = dealerRepository.findById(request.getDealerId())
                    .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đại lý với ID: " + request.getDealerId()));
            if (Boolean.TRUE.equals(dealer.getLocked())) {
                throw new DealerLockedException(dealer.getCode(), dealer.getName(), dealer.getLockReason());
            }
            if (Boolean.FALSE.equals(dealer.getActive())) {
                throw new IllegalArgumentException(String.format("Đại lý '%s' (%s) đã ngừng giao dịch.",
                        dealer.getName(), dealer.getCode()));
            }
            customerGroup = dealer.getCustomerGroup();
        }

        if (customerGroup == null) {
            throw new IllegalArgumentException("Không xác định được nhóm khách hàng của đại lý để tra cứu bảng giá");
        }

        LocalDate orderDate = request.getOrderDate() != null ? request.getOrderDate() : LocalDate.now();

        // 1. Tra cứu bảng giá hiệu lực của nhóm khách hàng mà đại lý thuộc về
        List<PriceList> effectivePriceLists = priceListRepository.findEffectivePriceLists(customerGroup, orderDate);
        if (effectivePriceLists.isEmpty()) {
            throw new IllegalArgumentException(String.format("Không tìm thấy bảng giá hiệu lực cho nhóm khách hàng '%s'. Không thể thêm dòng hàng.",
                    customerGroup.getDisplayName()));
        }
        PriceList priceList = effectivePriceLists.get(0);

        // 2. Tra cứu sản phẩm
        Product product;
        if (request.getProductId() != null) {
            product = productRepository.findById(request.getProductId())
                    .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy sản phẩm với ID: " + request.getProductId()));
        } else {
            product = productRepository.findByCode(request.getProductCode().trim().toUpperCase())
                    .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy sản phẩm với mã: " + request.getProductCode()));
        }

        // 3. Kiểm tra SKU có trong bảng giá hiệu lực hay không - nếu không có thì chặn thêm dòng hàng kèm thông báo rõ lý do
        PriceListItem priceListItem = null;
        if (priceList.getItems() != null) {
            for (PriceListItem pli : priceList.getItems()) {
                if (pli.getProduct() != null && pli.getProduct().getId().equals(product.getId())) {
                    priceListItem = pli;
                    break;
                }
            }
        }
        if (priceListItem == null) {
            throw new IllegalArgumentException(String.format("Sản phẩm '%s' (mã: %s) chưa có trong bảng giá hiệu lực của nhóm khách hàng '%s'. Không thể thêm dòng hàng.",
                    product.getName(), product.getCode(), customerGroup.getDisplayName()));
        }

        // 4. Quy đổi đơn vị và giá
        String unit = request.getUnit();
        if (unit == null || unit.isBlank()) {
            unit = product.getUnit();
        } else {
            unit = unit.trim();
        }

        BigDecimal factor = unitConversionService.getConversionFactor(product, unit);
        BigDecimal qty = request.getQuantity() != null && request.getQuantity().compareTo(BigDecimal.ZERO) > 0
                ? request.getQuantity() : BigDecimal.ONE;
        BigDecimal baseQty = qty.multiply(factor);

        BigDecimal priceListSellingPrice = priceListItem.getSellingPrice().multiply(factor);
        BigDecimal floorPrice = priceListItem.getFloorPrice().multiply(factor);

        boolean isManualPrice = request.getManualPrice() != null;
        BigDecimal appliedPrice = isManualPrice ? request.getManualPrice() : priceListSellingPrice;

        boolean isBelowFloor = appliedPrice.compareTo(floorPrice) < 0;
        String approvalReason = isBelowFloor
                ? String.format("Sản phẩm '%s' bán giá %s < giá sàn %s (%s)",
                        product.getName(), appliedPrice.toPlainString(), floorPrice.toPlainString(), unit)
                : null;

        BigDecimal subtotal = appliedPrice.multiply(qty);

        // 5. Chiết khấu theo sản lượng tính lại tự động mỗi khi số lượng thay đổi
        BigDecimal discountAmount = BigDecimal.ZERO;
        BigDecimal discountPercent = null;
        Long appliedPolicyId = null;
        String appliedPolicyCode = null;
        String appliedPolicyName = null;
        String discountReason = null;

        if (volumeDiscountPolicyService != null) {
            VolumeDiscountPolicyService.OrderItemDiscountInput discountInput = VolumeDiscountPolicyService.OrderItemDiscountInput.builder()
                    .index(0)
                    .product(product)
                    .unit(unit)
                    .quantity(qty)
                    .conversionFactor(factor)
                    .baseQuantity(baseQty)
                    .baseUnit(product.getUnit())
                    .price(appliedPrice)
                    .subtotalAmount(subtotal)
                    .build();

            VolumeDiscountPolicyService.OrderDiscountSummary summary = volumeDiscountPolicyService.calculateOrderDiscount(
                    List.of(discountInput), customerGroup, orderDate);

            if (summary != null && summary.getItemResults() != null) {
                VolumeDiscountPolicyService.ItemDiscountResult res = summary.getItemResults().get(0);
                if (res != null) {
                    discountAmount = res.getDiscountAmount() != null ? res.getDiscountAmount() : BigDecimal.ZERO;
                    discountPercent = res.getDiscountPercent();
                    appliedPolicyId = res.getAppliedPolicyId();
                    appliedPolicyCode = res.getAppliedPolicyCode();
                    appliedPolicyName = res.getAppliedPolicyName();
                    discountReason = res.getBestPolicyReason();
                }
            }
        }

        BigDecimal totalAmount = subtotal.subtract(discountAmount).max(BigDecimal.ZERO);

        return OrderLineItemPricingResponse.builder()
                .productId(product.getId())
                .productCode(product.getCode())
                .productName(product.getName())
                .categoryId(product.getCategory() != null ? product.getCategory().getId() : null)
                .categoryName(product.getCategory() != null ? product.getCategory().getName() : null)
                .dealerId(dealer != null ? dealer.getId() : null)
                .dealerName(dealer != null ? dealer.getName() : null)
                .customerGroup(customerGroup)
                .customerGroupName(customerGroup.getDisplayName())
                .priceListId(priceList.getId())
                .priceListCode(priceList.getCode())
                .priceListName(priceList.getName())
                .unit(unit)
                .conversionFactor(factor)
                .baseUnit(product.getUnit())
                .quantity(qty)
                .baseQuantity(baseQty)
                .priceListSellingPrice(priceListSellingPrice)
                .floorPrice(floorPrice)
                .unitPrice(appliedPrice)
                .isManualPrice(isManualPrice)
                .belowFloorPrice(isBelowFloor)
                .requiresApproval(isBelowFloor)
                .approvalReason(approvalReason)
                .subtotalAmount(subtotal)
                .discountAmount(discountAmount)
                .discountPercent(discountPercent)
                .appliedPolicyId(appliedPolicyId)
                .appliedPolicyCode(appliedPolicyCode)
                .appliedPolicyName(appliedPolicyName)
                .discountReason(discountReason)
                .totalAmount(totalAmount)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public OrderCalculateResponse calculateOrder(OrderCalculateRequest request) {
        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new IllegalArgumentException("Đơn hàng phải có ít nhất 1 sản phẩm để tính toán");
        }

        Dealer dealer = null;
        CustomerGroup customerGroup = request.getCustomerGroup();

        if (request.getDealerId() != null) {
            dealer = dealerRepository.findById(request.getDealerId())
                    .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đại lý với ID: " + request.getDealerId()));
            if (Boolean.TRUE.equals(dealer.getLocked())) {
                throw new DealerLockedException(dealer.getCode(), dealer.getName(), dealer.getLockReason());
            }
            if (Boolean.FALSE.equals(dealer.getActive())) {
                throw new IllegalArgumentException(String.format("Đại lý '%s' (%s) đã ngừng giao dịch.",
                        dealer.getName(), dealer.getCode()));
            }
            customerGroup = dealer.getCustomerGroup();
        }

        PriceList priceList = null;
        LocalDate orderDate = request.getOrderDate() != null ? request.getOrderDate() : LocalDate.now();

        if (request.getPriceListId() != null) {
            priceList = priceListRepository.findById(request.getPriceListId())
                    .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy bảng giá với ID: " + request.getPriceListId()));
            if (customerGroup == null) {
                customerGroup = priceList.getCustomerGroup();
            }
        } else if (customerGroup != null) {
            List<PriceList> effectivePriceLists = priceListRepository.findEffectivePriceLists(customerGroup, orderDate);
            if (!effectivePriceLists.isEmpty()) {
                priceList = effectivePriceLists.get(0);
            } else {
                throw new IllegalArgumentException(String.format("Không tìm thấy bảng giá hiệu lực cho nhóm khách hàng '%s'. Không thể thêm dòng hàng.",
                        customerGroup.getDisplayName()));
            }
        }

        Map<Long, PriceListItem> priceItemMap = new HashMap<>();
        if (priceList != null && priceList.getItems() != null) {
            for (PriceListItem pli : priceList.getItems()) {
                if (pli.getProduct() != null) {
                    priceItemMap.put(pli.getProduct().getId(), pli);
                }
            }
        }

        boolean orderRequiresApproval = false;
        List<String> belowFloorReasons = new ArrayList<>();
        List<VolumeDiscountPolicyService.OrderItemDiscountInput> discountInputs = new ArrayList<>();
        List<ItemPreData> preDataList = new ArrayList<>();

        for (int i = 0; i < request.getItems().size(); i++) {
            OrderItemRequest itemReq = request.getItems().get(i);
            Product product = resolveProduct(itemReq);

            if (customerGroup != null) {
                PriceListItem pli = priceItemMap.get(product.getId());
                if (pli == null) {
                    throw new IllegalArgumentException(String.format("Sản phẩm '%s' (mã: %s) chưa có trong bảng giá hiệu lực của nhóm khách hàng '%s'. Không thể thêm dòng hàng.",
                            product.getName(), product.getCode(), customerGroup.getDisplayName()));
                }
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

            PriceListItem priceListItem = priceItemMap.get(product.getId());
            BigDecimal unitSellingPrice = null;
            BigDecimal unitFloorPrice = null;

            if (priceListItem != null) {
                unitSellingPrice = priceListItem.getSellingPrice().multiply(factor);
                unitFloorPrice = priceListItem.getFloorPrice().multiply(factor);
            }

            boolean isManual = itemReq.getPrice() != null;
            BigDecimal price;
            if (isManual) {
                price = itemReq.getPrice();
            } else if (unitSellingPrice != null) {
                price = unitSellingPrice;
            } else {
                price = product.getPrice() != null ? product.getPrice().multiply(factor) : BigDecimal.ZERO;
            }

            boolean isBelowFloor = false;
            if (unitFloorPrice != null && price.compareTo(unitFloorPrice) < 0) {
                isBelowFloor = true;
                orderRequiresApproval = true;
                belowFloorReasons.add(String.format("Sản phẩm '%s' bán giá %s < giá sàn %s (%s)",
                        product.getName(), price.toPlainString(), unitFloorPrice.toPlainString(), inputUnit));
            }

            BigDecimal subtotalAmount = price.multiply(qty);

            discountInputs.add(VolumeDiscountPolicyService.OrderItemDiscountInput.builder()
                    .index(i)
                    .product(product)
                    .unit(inputUnit)
                    .quantity(qty)
                    .conversionFactor(factor)
                    .baseQuantity(baseQty)
                    .baseUnit(product.getUnit())
                    .price(price)
                    .subtotalAmount(subtotalAmount)
                    .build());

            preDataList.add(new ItemPreData(product, inputUnit, qty, factor, baseQty, price, unitFloorPrice, isBelowFloor, subtotalAmount, itemReq.getNote()));
        }

        VolumeDiscountPolicyService.OrderDiscountSummary discountSummary = null;
        if (volumeDiscountPolicyService != null) {
            discountSummary = volumeDiscountPolicyService.calculateOrderDiscount(discountInputs, customerGroup, orderDate);
        }

        // Tra cứu tồn kho phục vụ nếu có
        Warehouse warehouse = null;
        if (request.getWarehouseId() != null) {
            warehouse = warehouseRepository.findById(request.getWarehouseId()).orElse(null);
        } else if (dealer != null && dealer.getWarehouse() != null) {
            warehouse = dealer.getWarehouse();
        }
        if (warehouse == null && warehouseRepository != null) {
            List<Warehouse> activeWarehouses = warehouseRepository.findAllByActiveTrueOrderByNameAsc();
            if (!activeWarehouses.isEmpty()) {
                warehouse = activeWarehouses.get(0);
            }
        }

        Map<Long, WarehouseInventory> inventoryMap = new HashMap<>();
        if (warehouse != null && inventoryRepository != null) {
            List<Long> productIds = preDataList.stream().map(d -> d.product().getId()).distinct().toList();
            List<WarehouseInventory> inventories = inventoryRepository.findByWarehouseIdAndProductIdIn(warehouse.getId(), productIds);
            for (WarehouseInventory inv : inventories) {
                if (inv.getProduct() != null) {
                    inventoryMap.put(inv.getProduct().getId(), inv);
                }
            }
        }

        BigDecimal grandSubtotal = BigDecimal.ZERO;
        BigDecimal grandDiscount = BigDecimal.ZERO;
        List<OrderCalculateItemResponse> calculatedItems = new ArrayList<>();

        for (int i = 0; i < preDataList.size(); i++) {
            ItemPreData d = preDataList.get(i);
            BigDecimal discountAmount = BigDecimal.ZERO;
            BigDecimal discountPercent = null;
            Long appliedPolicyId = null;
            String appliedPolicyCode = null;
            String appliedPolicyName = null;
            String reason = null;

            if (discountSummary != null && discountSummary.getItemResults() != null) {
                VolumeDiscountPolicyService.ItemDiscountResult res = discountSummary.getItemResults().get(i);
                if (res != null) {
                    discountAmount = res.getDiscountAmount() != null ? res.getDiscountAmount() : BigDecimal.ZERO;
                    discountPercent = res.getDiscountPercent();
                    appliedPolicyId = res.getAppliedPolicyId();
                    appliedPolicyCode = res.getAppliedPolicyCode();
                    appliedPolicyName = res.getAppliedPolicyName();
                    reason = res.getBestPolicyReason();
                }
            }

            BigDecimal lineTotal = d.subtotal().subtract(discountAmount).max(BigDecimal.ZERO);
            grandSubtotal = grandSubtotal.add(d.subtotal());
            grandDiscount = grandDiscount.add(discountAmount);

            PriceListItem priceListItem = priceItemMap.get(d.product().getId());
            BigDecimal priceListSelling = priceListItem != null ? priceListItem.getSellingPrice().multiply(d.conversionFactor()) : null;

            BigDecimal physical = BigDecimal.ZERO;
            BigDecimal reserved = BigDecimal.ZERO;
            BigDecimal available = BigDecimal.ZERO;
            BigDecimal availableInUnit = BigDecimal.ZERO;
            BigDecimal maxAllowedInUnit = BigDecimal.ZERO;
            Boolean exceedsStock = false;

            if (warehouse != null) {
                WarehouseInventory inv = inventoryMap.get(d.product().getId());
                physical = (inv != null && inv.getBaseQuantity() != null) ? inv.getBaseQuantity() : BigDecimal.ZERO;
                reserved = (inv != null && inv.getReservedQuantity() != null) ? inv.getReservedQuantity() : BigDecimal.ZERO;
                available = physical.subtract(reserved).max(BigDecimal.ZERO);
                BigDecimal factor = (d.conversionFactor() != null && d.conversionFactor().compareTo(BigDecimal.ZERO) > 0)
                        ? d.conversionFactor()
                        : BigDecimal.ONE;
                availableInUnit = available.divide(factor, 2, RoundingMode.HALF_UP);
                maxAllowedInUnit = available.divide(factor, 0, RoundingMode.FLOOR);
                exceedsStock = d.baseQuantity().compareTo(available) > 0;
            }

            calculatedItems.add(OrderCalculateItemResponse.builder()
                    .productId(d.product().getId())
                    .productCode(d.product().getCode())
                    .productName(d.product().getName())
                    .unit(d.unit())
                    .quantity(d.quantity())
                    .conversionFactor(d.conversionFactor())
                    .baseUnit(d.product().getUnit())
                    .baseQuantity(d.baseQuantity())
                    .priceListSellingPrice(priceListSelling)
                    .floorPrice(d.floorPrice())
                    .price(d.price())
                    .isManualPrice(request.getItems().get(i).getPrice() != null)
                    .belowFloorPrice(d.isBelowFloor())
                    .subtotalAmount(d.subtotal())
                    .discountAmount(discountAmount)
                    .discountPercent(discountPercent)
                    .appliedPolicyId(appliedPolicyId)
                    .appliedPolicyCode(appliedPolicyCode)
                    .appliedPolicyName(appliedPolicyName)
                    .selectionReason(reason)
                    .totalAmount(lineTotal)
                    .physicalQuantity(physical)
                    .reservedQuantity(reserved)
                    .availableQuantity(available)
                    .availableInUnit(availableInUnit)
                    .maxAllowedInUnit(maxAllowedInUnit)
                    .exceedsStock(exceedsStock)
                    .build());
        }

        return OrderCalculateResponse.builder()
                .dealerId(dealer != null ? dealer.getId() : null)
                .dealerName(dealer != null ? dealer.getName() : null)
                .warehouseId(warehouse != null ? warehouse.getId() : null)
                .warehouseCode(warehouse != null ? warehouse.getCode() : null)
                .warehouseName(warehouse != null ? warehouse.getName() : null)
                .customerGroup(customerGroup)
                .customerGroupName(customerGroup != null ? customerGroup.getDisplayName() : null)
                .priceListId(priceList != null ? priceList.getId() : null)
                .priceListCode(priceList != null ? priceList.getCode() : null)
                .priceListName(priceList != null ? priceList.getName() : null)
                .subtotalAmount(grandSubtotal)
                .discountAmount(grandDiscount)
                .totalAmount(grandSubtotal.subtract(grandDiscount).max(BigDecimal.ZERO))
                .requiresApproval(orderRequiresApproval)
                .approvalReason(orderRequiresApproval ? String.join("; ", belowFloorReasons) : null)
                .items(calculatedItems)
                .build();
    }

    /**
     * S4-09: Sao chép một đơn cũ thành đơn mới ở trạng thái Nháp (DRAFT).
     * Toàn bộ dòng hàng của đơn đã chọn được sao chép.
     * Giá và chiết khấu được áp lại theo bảng giá và chính sách hiện hành, không kế thừa giá cũ.
     * Bản sao luôn bắt đầu ở trạng thái Nháp.
     */
    @Override
    @Transactional
    public OrderResponse copyOrder(Long id, String username) {
        Order sourceOrder = orderRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn hàng với ID: " + id));

        validateSalesAccess(sourceOrder, username);

        if (sourceOrder.getItems() == null || sourceOrder.getItems().isEmpty()) {
            throw new IllegalArgumentException("Đơn hàng không có dòng hàng nào để sao chép");
        }

        Dealer dealer = sourceOrder.getDealer();
        if (dealer != null) {
            if (Boolean.TRUE.equals(dealer.getLocked())) {
                throw new DealerLockedException(dealer.getCode(), dealer.getName(), dealer.getLockReason());
            }
            if (Boolean.FALSE.equals(dealer.getActive())) {
                throw new IllegalArgumentException(String.format("Đại lý '%s' (%s) đã ngừng giao dịch, không thể sao chép đơn hàng.",
                        dealer.getName(), dealer.getCode()));
            }
        }

        CustomerGroup customerGroup = sourceOrder.getCustomerGroup();
        if (customerGroup == null && dealer != null) {
            customerGroup = dealer.getCustomerGroup();
        }

        LocalDate today = LocalDate.now();
        PriceList priceList = null;
        if (customerGroup != null) {
            List<PriceList> effectivePriceLists = priceListRepository.findEffectivePriceLists(customerGroup, today);
            if (!effectivePriceLists.isEmpty()) {
                priceList = effectivePriceLists.get(0);
            } else {
                throw new IllegalArgumentException(String.format("Không tìm thấy bảng giá hiệu lực cho nhóm khách hàng '%s' tại thời điểm %s. Không thể tính giá cho đơn sao chép.",
                        customerGroup.getDisplayName(), today));
            }
        }

        Map<Long, PriceListItem> priceItemMap = new HashMap<>();
        if (priceList != null && priceList.getItems() != null) {
            for (PriceListItem pli : priceList.getItems()) {
                if (pli.getProduct() != null) {
                    priceItemMap.put(pli.getProduct().getId(), pli);
                }
            }
        }

        String newOrderCode = "DH-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"));

        Order newOrder = Order.builder()
                .orderCode(newOrderCode)
                .customerName(sourceOrder.getCustomerName())
                .customerGroup(customerGroup)
                .dealer(dealer)
                .deliveryPoint(sourceOrder.getDeliveryPoint())
                .expectedDeliveryDate(today.plusDays(3))
                .priceList(priceList)
                .note(sourceOrder.getNote() != null ? sourceOrder.getNote() : "")
                .status("DRAFT")
                .requiresApproval(false)
                .createdBy(username != null ? username : "system")
                .createdAt(LocalDateTime.now())
                .recordedAt(null)
                .items(new ArrayList<>())
                .build();

        List<VolumeDiscountPolicyService.OrderItemDiscountInput> discountInputs = new ArrayList<>();
        List<ItemPreData> preDataList = new ArrayList<>();

        for (int i = 0; i < sourceOrder.getItems().size(); i++) {
            OrderItem sourceItem = sourceOrder.getItems().get(i);
            Product product = sourceItem.getProduct();
            if (product == null && sourceItem.getId() != null) {
                continue;
            }

            if (customerGroup != null) {
                PriceListItem pli = priceItemMap.get(product.getId());
                if (pli == null) {
                    throw new IllegalArgumentException(String.format("Sản phẩm '%s' (mã: %s) chưa có trong bảng giá hiệu lực hiện hành của nhóm khách hàng '%s'. Không thể sao chép dòng hàng.",
                            product.getName(), product.getCode(), customerGroup.getDisplayName()));
                }
            }

            String inputUnit = sourceItem.getUnit();
            if (inputUnit == null || inputUnit.isBlank()) {
                inputUnit = product.getUnit();
            } else {
                inputUnit = inputUnit.trim();
            }

            BigDecimal qty = sourceItem.getQuantity();
            if (qty == null || qty.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Số lượng cho sản phẩm '" + product.getCode() + "' phải lớn hơn 0");
            }

            BigDecimal factor = unitConversionService.getConversionFactor(product, inputUnit);
            BigDecimal baseQty = qty.multiply(factor);

            PriceListItem priceListItem = priceItemMap.get(product.getId());
            BigDecimal unitSellingPrice = null;
            BigDecimal unitFloorPrice = null;
            if (priceListItem != null) {
                unitSellingPrice = priceListItem.getSellingPrice().multiply(factor);
                unitFloorPrice = priceListItem.getFloorPrice().multiply(factor);
            }

            BigDecimal price;
            if (unitSellingPrice != null) {
                price = unitSellingPrice;
            } else {
                price = product.getPrice() != null ? product.getPrice().multiply(factor) : BigDecimal.ZERO;
            }

            BigDecimal subtotalAmount = price.multiply(qty);

            discountInputs.add(VolumeDiscountPolicyService.OrderItemDiscountInput.builder()
                    .index(i)
                    .product(product)
                    .unit(inputUnit)
                    .quantity(qty)
                    .conversionFactor(factor)
                    .baseQuantity(baseQty)
                    .baseUnit(product.getUnit())
                    .price(price)
                    .subtotalAmount(subtotalAmount)
                    .build());

            preDataList.add(new ItemPreData(product, inputUnit, qty, factor, baseQty, price, unitFloorPrice, false, subtotalAmount, sourceItem.getNote()));
        }

        VolumeDiscountPolicyService.OrderDiscountSummary discountSummary = null;
        if (volumeDiscountPolicyService != null) {
            discountSummary = volumeDiscountPolicyService.calculateOrderDiscount(discountInputs, customerGroup, today);
        }

        BigDecimal grandSubtotal = BigDecimal.ZERO;
        BigDecimal grandDiscount = BigDecimal.ZERO;

        for (int i = 0; i < preDataList.size(); i++) {
            ItemPreData pre = preDataList.get(i);
            BigDecimal itemDiscount = BigDecimal.ZERO;
            BigDecimal itemDiscountPercent = BigDecimal.ZERO;
            Long appliedPolicyId = null;
            String appliedPolicyCode = null;
            String appliedPolicyName = null;

            if (discountSummary != null && discountSummary.getItemResults() != null) {
                VolumeDiscountPolicyService.ItemDiscountResult disRes = discountSummary.getItemResults().get(i);
                if (disRes != null) {
                    itemDiscount = disRes.getDiscountAmount() != null ? disRes.getDiscountAmount() : BigDecimal.ZERO;
                    itemDiscountPercent = disRes.getDiscountPercent() != null ? disRes.getDiscountPercent() : BigDecimal.ZERO;
                    appliedPolicyId = disRes.getAppliedPolicyId();
                    appliedPolicyCode = disRes.getAppliedPolicyCode();
                    appliedPolicyName = disRes.getAppliedPolicyName();
                }
            }

            BigDecimal itemTotal = pre.subtotal().subtract(itemDiscount);
            if (itemTotal.compareTo(BigDecimal.ZERO) < 0) {
                itemTotal = BigDecimal.ZERO;
            }

            OrderItem orderItem = OrderItem.builder()
                    .order(newOrder)
                    .product(pre.product())
                    .unit(pre.unit())
                    .quantity(pre.quantity())
                    .conversionFactor(pre.conversionFactor())
                    .baseUnit(pre.product().getUnit())
                    .baseQuantity(pre.baseQuantity())
                    .price(pre.price())
                    .floorPrice(pre.floorPrice())
                    .belowFloorPrice(false)
                    .subtotalAmount(pre.subtotal())
                    .discountAmount(itemDiscount)
                    .discountPercent(itemDiscountPercent)
                    .appliedPolicyId(appliedPolicyId)
                    .appliedPolicyCode(appliedPolicyCode)
                    .appliedPolicyName(appliedPolicyName)
                    .totalAmount(itemTotal)
                    .note(pre.note())
                    .build();

            newOrder.getItems().add(orderItem);
            grandSubtotal = grandSubtotal.add(pre.subtotal());
            grandDiscount = grandDiscount.add(itemDiscount);
        }

        newOrder.setSubtotalAmount(grandSubtotal);
        newOrder.setDiscountAmount(grandDiscount);
        newOrder.setTotalAmount(grandSubtotal.subtract(grandDiscount));

        Order saved = orderRepository.save(newOrder);

        auditLogService.logAction("INVOICE", saved.getOrderCode(), "COPY_FROM",
                sourceOrder.getOrderCode(), saved.getStatus(), username != null ? username : "system");

        syncToDraft(saved, username);

        return mapToResponse(saved);
    }

    /**
     * S4-09: Sao chép các dòng hàng của đơn cũ trực tiếp vào bản nháp tạo đơn của người dùng.
     */
    @Override
    @Transactional
    public JsonNode copyOrderToDraft(Long id, String username) {
        Order sourceOrder = orderRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn hàng với ID: " + id));

        validateSalesAccess(sourceOrder, username);

        if (sourceOrder.getItems() == null || sourceOrder.getItems().isEmpty()) {
            throw new IllegalArgumentException("Đơn hàng không có dòng hàng để sao chép.");
        }

        ObjectMapper mapper = this.objectMapper != null ? this.objectMapper : new ObjectMapper();
        ObjectNode draft = mapper.createObjectNode();
        draft.put("dealerId", sourceOrder.getDealer() != null ? sourceOrder.getDealer().getId() : null);
        draft.put("deliveryPointId", sourceOrder.getDeliveryPoint() != null ? sourceOrder.getDeliveryPoint().getId() : null);
        draft.putNull("deliveryDate");
        draft.put("note", "");

        ArrayNode itemsNode = draft.putArray("items");
        int seq = 0;
        for (int i = 0; i < sourceOrder.getItems().size(); i++) {
            OrderItem item = sourceOrder.getItems().get(i);
            if (item.getProduct() == null || item.getQuantity() == null || item.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            ObjectNode itemNode = itemsNode.addObject();
            itemNode.put("_key", "copy-" + sourceOrder.getId() + "-" + i);
            itemNode.put("productId", item.getProduct().getId());
            itemNode.put("selectedUnitId", item.getUnit() != null ? item.getUnit() : item.getProduct().getUnit());
            itemNode.put("qty", item.getQuantity());
            itemNode.put("basePrice", 0);
            itemNode.put("unitPrice", 0);
            itemNode.put("floorPrice", 0);
            itemNode.put("manualPrice", false);
            itemNode.put("discountAmount", 0);
            seq++;
        }
        draft.put("_seq", seq);

        if (seq == 0) {
            throw new IllegalArgumentException("Đơn hàng không có dòng hàng hợp lệ để sao chép.");
        }

        if (orderDraftRepository != null && username != null && !username.isBlank()) {
            OrderDraft orderDraft = orderDraftRepository.findByUsername(username)
                    .orElseGet(() -> OrderDraft.builder().username(username).build());
            orderDraft.setDraftData(draft.toString());
            orderDraftRepository.save(orderDraft);
        }

        return draft;
    }

    private void validateSalesAccess(Order order, String username) {
        if (username == null || username.isBlank() || "system".equalsIgnoreCase(username) || "admin".equalsIgnoreCase(username)) {
            return;
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return;
        }
        boolean isAdminOrManager = auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()) || "ROLE_SALES_MANAGER".equals(a.getAuthority()));
        if (isAdminOrManager) {
            return;
        }

        boolean isSales = auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_SALES".equals(a.getAuthority()));
        if (!isSales) {
            return;
        }

        if (order.getCreatedBy() != null && order.getCreatedBy().equalsIgnoreCase(username)) {
            return;
        }

        Dealer dealer = order.getDealer();
        if (dealer != null && dealer.getAssignedUser() != null
                && dealer.getAssignedUser().getUsername() != null
                && dealer.getAssignedUser().getUsername().equalsIgnoreCase(username)) {
            return;
        }

        throw new AccessDeniedException("Bạn không có quyền sao chép đơn hàng này");
    }

    private void syncToDraft(Order saved, String username) {
        if (orderDraftRepository == null || username == null || username.isBlank()) {
            return;
        }
        try {
            ObjectMapper mapper = this.objectMapper != null ? this.objectMapper : new ObjectMapper();
            ObjectNode draft = mapper.createObjectNode();
            draft.put("dealerId", saved.getDealer() != null ? saved.getDealer().getId() : null);
            draft.put("deliveryPointId", saved.getDeliveryPoint() != null ? saved.getDeliveryPoint().getId() : null);
            draft.putNull("deliveryDate");
            draft.put("note", saved.getNote() != null ? saved.getNote() : "");

            ArrayNode itemsNode = draft.putArray("items");
            if (saved.getItems() != null) {
                for (int i = 0; i < saved.getItems().size(); i++) {
                    OrderItem item = saved.getItems().get(i);
                    ObjectNode itemNode = itemsNode.addObject();
                    itemNode.put("_key", "copy-" + saved.getId() + "-" + i);
                    itemNode.put("productId", item.getProduct() != null ? item.getProduct().getId() : null);
                    itemNode.put("selectedUnitId", item.getUnit());
                    itemNode.put("qty", item.getQuantity());
                    itemNode.put("basePrice", 0);
                    itemNode.put("unitPrice", item.getPrice());
                    itemNode.put("floorPrice", item.getFloorPrice());
                    itemNode.put("manualPrice", false);
                    itemNode.put("discountAmount", item.getDiscountAmount());
                }
                draft.put("_seq", saved.getItems().size());
            }
            OrderDraft orderDraft = orderDraftRepository.findByUsername(username)
                    .orElseGet(() -> OrderDraft.builder().username(username).build());
            orderDraft.setDraftData(draft.toString());
            orderDraftRepository.save(orderDraft);
        } catch (Exception ignored) {
        }
    }

    private boolean isSalesOnlyUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return false;
        boolean hasSales = auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_SALES".equals(a.getAuthority()));
        boolean hasElevated = auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority())
                        || "ROLE_SALES_MANAGER".equals(a.getAuthority())
                        || "ROLE_ACCOUNTANT".equals(a.getAuthority())
                        || "ROLE_VIEWER".equals(a.getAuthority())
                        || "ROLE_WAREHOUSE_MANAGER".equals(a.getAuthority())
                        || "ROLE_WAREHOUSE_STAFF".equals(a.getAuthority()));
        return hasSales && !hasElevated;
    }

    private User getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || userRepository == null) return null;
        return userRepository.findByUsername(auth.getName()).orElse(null);
    }

    private void validateSalesViewAccess(Order order, String username) {
        if (username == null || username.isBlank() || "system".equalsIgnoreCase(username) || "admin".equalsIgnoreCase(username)) {
            return;
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return;
        }
        boolean isElevated = auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority())
                        || "ROLE_SALES_MANAGER".equals(a.getAuthority())
                        || "ROLE_ACCOUNTANT".equals(a.getAuthority())
                        || "ROLE_VIEWER".equals(a.getAuthority())
                        || "ROLE_WAREHOUSE_MANAGER".equals(a.getAuthority())
                        || "ROLE_WAREHOUSE_STAFF".equals(a.getAuthority()));
        if (isElevated) {
            return;
        }

        boolean isSales = auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_SALES".equals(a.getAuthority()));
        if (!isSales) {
            return;
        }

        if (order.getCreatedBy() != null && order.getCreatedBy().equalsIgnoreCase(username)) {
            return;
        }

        Dealer dealer = order.getDealer();
        if (dealer != null && dealer.getAssignedUser() != null
                && dealer.getAssignedUser().getUsername() != null
                && dealer.getAssignedUser().getUsername().equalsIgnoreCase(username)) {
            return;
        }

        throw new AccessDeniedException("Bạn chỉ có quyền xem đơn hàng của đại lý do mình phụ trách");
    }

    private void checkSalesAuthorizationForOrder(Order order, String action) {
        if (!isSalesOnlyUser()) {
            return;
        }
        User currentUser = getCurrentUser();
        if (currentUser == null) {
            return;
        }

        boolean isCreator = order.getCreatedBy() != null && order.getCreatedBy().equalsIgnoreCase(currentUser.getUsername());
        boolean isAssignedDealer = order.getDealer() != null
                && order.getDealer().getAssignedUser() != null
                && order.getDealer().getAssignedUser().getId().equals(currentUser.getId());

        if (!isCreator && !isAssignedDealer) {
            throw new AccessDeniedException(
                    "Bạn chỉ có quyền " + action + " đơn hàng do mình tạo hoặc thuộc đại lý do mình phụ trách");
        }
    }

    private Warehouse resolveDefaultWarehouse(Long warehouseId) {
        if (warehouseRepository == null) {
            return null;
        }
        if (warehouseId != null) {
            return warehouseRepository.findById(warehouseId)
                    .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy kho hàng với ID: " + warehouseId));
        }
        List<Warehouse> active = warehouseRepository.findAllByActiveTrueOrderByNameAsc();
        if (active != null && !active.isEmpty()) {
            return active.get(0);
        }
        return warehouseRepository.findAll().stream().findFirst().orElse(null);
    }

    private void reserveInventory(Order order) {
        if (Boolean.TRUE.equals(order.getInventoryReserved())) {
            return;
        }
        if (warehouseRepository == null || inventoryRepository == null) {
            order.setInventoryReserved(true);
            return;
        }
        Warehouse warehouse = order.getWarehouse();
        if (warehouse == null) {
            return;
        }
        if (warehouse != null && order.getItems() != null) {
            for (OrderItem item : order.getItems()) {
                if (item.getProduct() != null && item.getBaseQuantity() != null && item.getBaseQuantity().compareTo(BigDecimal.ZERO) > 0) {
                    Product product = item.getProduct();
                    BigDecimal baseQty = item.getBaseQuantity();
                    WarehouseInventory inventory = inventoryRepository
                            .findByWarehouseIdAndProductId(warehouse.getId(), product.getId())
                            .orElseGet(() -> WarehouseInventory.builder()
                                    .warehouse(order.getWarehouse())
                                    .product(product)
                                    .baseQuantity(BigDecimal.ZERO)
                                    .reservedQuantity(BigDecimal.ZERO)
                                    .baseUnit(product.getUnit())
                                    .build());
                    inventory.addReservedQuantity(baseQty);
                    inventoryRepository.save(inventory);
                }
            }
            order.setInventoryReserved(true);
        }
    }

    private void deductInventoryOnExport(Order order) {
        if (warehouseRepository == null || inventoryRepository == null) {
            order.setInventoryReserved(false);
            return;
        }
        Warehouse warehouse = order.getWarehouse();
        if (warehouse == null) {
            warehouse = resolveDefaultWarehouse(null);
            if (warehouse != null) {
                order.setWarehouse(warehouse);
            }
        }
        if (warehouse != null && order.getItems() != null) {
            for (OrderItem item : order.getItems()) {
                if (item.getProduct() != null && item.getBaseQuantity() != null && item.getBaseQuantity().compareTo(BigDecimal.ZERO) > 0) {
                    Product product = item.getProduct();
                    BigDecimal baseQty = item.getBaseQuantity();
                    WarehouseInventory inv = inventoryRepository
                            .findByWarehouseIdAndProductId(warehouse.getId(), product.getId())
                            .orElseGet(() -> WarehouseInventory.builder()
                                    .warehouse(order.getWarehouse())
                                    .product(product)
                                    .baseQuantity(BigDecimal.ZERO)
                                    .reservedQuantity(BigDecimal.ZERO)
                                    .baseUnit(product.getUnit())
                                    .build());
                    if (Boolean.TRUE.equals(order.getInventoryReserved())) {
                        inv.subtractReservedQuantity(baseQty);
                    }
                    inv.subtractBaseQuantity(baseQty);
                    inventoryRepository.save(inv);
                }
            }
        }
        order.setInventoryReserved(false);
    }

    private void recordStatusHistory(Order order, String prevStatus, String newStatus, String note, String changedBy) {
        if (orderStatusHistoryRepository == null || order == null) {
            return;
        }
        OrderStatusHistory history = OrderStatusHistory.builder()
                .order(order)
                .previousStatus(prevStatus)
                .newStatus(newStatus)
                .statusDisplayName(OrderStatus.getDisplayName(newStatus))
                .note(note)
                .changedBy(changedBy != null ? changedBy : "system")
                .changedAt(LocalDateTime.now())
                .build();
        orderStatusHistoryRepository.save(history);
    }

    private int resolveStepOrder(String status) {
        if (status == null) return 0;
        String s = status.trim().toUpperCase();
        return switch (s) {
            case "DRAFT" -> 1;
            case "PENDING_APPROVAL" -> 2;
            case "APPROVED", "RECORDED" -> 3;
            case "PICKING", "PROCESSING" -> 4;
            case "EXPORTED", "SHIPPED" -> 5;
            case "DELIVERED" -> 6;
            case "CLOSED", "COMPLETED" -> 7;
            default -> 0;
        };
    }

    private String resolveStepState(int stepOrder, int currentStepOrder, boolean isCancelled, boolean isRejected, boolean isSkipped) {
        if (isSkipped) return "SKIPPED";
        if (isCancelled) {
            if (stepOrder < currentStepOrder) return "COMPLETED";
            if (stepOrder == currentStepOrder) return "CANCELLED";
            return "SKIPPED";
        }
        if (isRejected && stepOrder == 2) {
            return "REJECTED";
        }
        if (stepOrder < currentStepOrder) return "COMPLETED";
        if (stepOrder == currentStepOrder) return "CURRENT";
        return "UPCOMING";
    }

    private OrderLifecycleStepResponse buildStep(
            int stepOrder, String status, String displayName, String description,
            String state, OrderStatusHistory history, LocalDateTime fallbackTime, String fallbackOperator) {
        LocalDateTime timestamp = history != null ? history.getChangedAt() : fallbackTime;
        String operator = history != null ? history.getChangedBy() : fallbackOperator;
        String note = history != null ? history.getNote() : null;

        return OrderLifecycleStepResponse.builder()
                .stepOrder(stepOrder)
                .status(status)
                .displayName(displayName)
                .description(description)
                .state(state)
                .timestamp(timestamp)
                .operator(operator)
                .note(note)
                .build();
    }
}
