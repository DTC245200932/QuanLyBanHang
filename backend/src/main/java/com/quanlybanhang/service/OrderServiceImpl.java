package com.quanlybanhang.service;

import com.quanlybanhang.dto.request.OrderCalculateRequest;
import com.quanlybanhang.dto.request.OrderItemRequest;
import com.quanlybanhang.dto.request.OrderLineItemPricingRequest;
import com.quanlybanhang.dto.request.OrderRequest;
import com.quanlybanhang.dto.response.*;
import com.quanlybanhang.exception.DealerLockedException;
import com.quanlybanhang.model.*;
import com.quanlybanhang.dto.request.OrderStatusUpdateRequest;
import com.quanlybanhang.repository.DealerRepository;
import com.quanlybanhang.repository.DealerDeliveryPointRepository;
import com.quanlybanhang.repository.OrderApprovalHistoryRepository;
import com.quanlybanhang.repository.OrderRepository;
import com.quanlybanhang.repository.PriceListRepository;
import com.quanlybanhang.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final DealerRepository dealerRepository;
    private final DealerDeliveryPointRepository deliveryPointRepository;
    private final PriceListRepository priceListRepository;
    private final UnitConversionService unitConversionService;
    private final AuditLogService auditLogService;
    private final VolumeDiscountPolicyService volumeDiscountPolicyService;
    private final DealerDebtService dealerDebtService;

    private OrderApprovalHistoryRepository orderApprovalHistoryRepository;

    @Autowired(required = false)
    public void setOrderApprovalHistoryRepository(OrderApprovalHistoryRepository orderApprovalHistoryRepository) {
        this.orderApprovalHistoryRepository = orderApprovalHistoryRepository;
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
                .expectedDeliveryDate(request.getExpectedDeliveryDate())
                .priceList(priceList)
                .note(request.getNote())
                .status("RECORDED")
                .requiresApproval(false)
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
        } else {
            order.setStatus("RECORDED");
            order.setRequiresApproval(false);
        }

        Order saved = orderRepository.save(order);
        if (orderRequiresApproval && orderApprovalHistoryRepository != null) {
            try {
                OrderApprovalHistory history = OrderApprovalHistory.builder()
                        .order(saved)
                        .orderCode(saved.getOrderCode())
                        .action("PENDING_APPROVAL")
                        .comment(saved.getApprovalReason())
                        .performedBy(username != null ? username : "system")
                        .build();
                orderApprovalHistoryRepository.save(history);
            } catch (Exception ignored) {
            }
        }
        String auditSuffix = orderRequiresApproval ? " (Chờ duyệt: " + order.getApprovalReason() + ")" : "";
        auditLogService.logAction("INVOICE", saved.getOrderCode(), "CREATE", null,
                "Tổng tiền: " + saved.getTotalAmount().toPlainString() + auditSuffix,
                username != null ? username : "system");

        return mapToResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderResponse> getAllOrders() {
        return orderRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse getOrderById(Long id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn hàng với ID: " + id));
        return mapToResponse(order);
    }

    @Override
    @Transactional
    @Deprecated
    public OrderResponse cancelOrder(Long id, String username) {
        return cancelOrder(id, "Hủy bởi hệ thống", username);
    }

    @Override
    @Transactional
    public OrderResponse cancelOrder(Long id, String reason, String username) {
        if (reason == null || reason.trim().isEmpty()) {
            throw new IllegalArgumentException("Vui lòng nhập lý do huỷ đơn hàng");
        }

        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn hàng với ID: " + id));

        String currentStatus = order.getStatus();
        // S4-06: Không cho huỷ đơn đã được xuất kho hoặc đã giao
        if ("EXPORTED".equalsIgnoreCase(currentStatus) || "DELIVERED".equalsIgnoreCase(currentStatus)) {
            throw new IllegalArgumentException(
                "Không thể huỷ đơn hàng đã " + ("EXPORTED".equalsIgnoreCase(currentStatus) ? "xuất kho" : "giao hàng") +
                ". Trạng thái hiện tại: " + currentStatus);
        }
        if ("CANCELLED".equalsIgnoreCase(currentStatus)) {
            throw new IllegalArgumentException("Đơn hàng đã được huỷ trước đó.");
        }

        String cleanReason = reason.trim();
        order.setStatus("CANCELLED");
        order.setCancelReason(cleanReason);
        order.setCancelledBy(username != null ? username : "system");
        order.setCancelledAt(LocalDateTime.now());

        Order saved = orderRepository.save(order);
        auditLogService.logAction("INVOICE", saved.getOrderCode(), "CANCEL", currentStatus,
                "Lý do huỷ: " + cleanReason, username != null ? username : "system");
        return mapToResponse(saved);
    }

    @Override
    @Transactional
    public OrderResponse approveOrder(Long id, String username) {
        return approveOrder(id, null, username);
    }

    @Override
    @Transactional
    public OrderResponse approveOrder(Long id, String comment, String username) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn hàng với ID: " + id));

        if (!"PENDING_APPROVAL".equalsIgnoreCase(order.getStatus())) {
            throw new IllegalArgumentException("Chỉ có thể phê duyệt đơn hàng đang ở trạng thái Chờ duyệt (PENDING_APPROVAL)");
        }

        // Đơn được duyệt mới chuyển sang giữ chỗ tồn kho và chờ xuất kho (chuyển sang trạng thái RECORDED)
        order.setStatus("RECORDED");
        order.setApprovedBy(username != null ? username : "system");
        order.setApprovedAt(LocalDateTime.now());
        order.setRecordedAt(LocalDateTime.now());

        if (comment != null && !comment.isBlank()) {
            String note = order.getNote();
            order.setNote((note != null && !note.isBlank() ? note + " | " : "") + "Ý kiến duyệt: " + comment.trim());
        }

        Order saved = orderRepository.save(order);

        // Lưu lịch sử duyệt bất biến (S4-05)
        if (orderApprovalHistoryRepository != null) {
            try {
                OrderApprovalHistory history = OrderApprovalHistory.builder()
                        .order(saved)
                        .orderCode(saved.getOrderCode())
                        .action("APPROVED")
                        .comment(comment != null && !comment.isBlank() ? comment.trim() : "Đã duyệt đơn hàng")
                        .performedBy(username != null ? username : "system")
                        .build();
                orderApprovalHistoryRepository.save(history);
            } catch (Exception ignored) {
            }
        }

        Dealer dealer = order.getDealer();
        boolean dealerLocked = dealer != null && Boolean.TRUE.equals(dealer.getLocked());
        String auditDetail = "Quản lý kinh doanh duyệt đơn" +
                (comment != null && !comment.isBlank() ? " (Ý kiến: " + comment.trim() + ")" : "") +
                (dealerLocked ? " [Cảnh báo: Đại lý '" + dealer.getName() + "' đang bị khoá giao dịch. Lý do: " + dealer.getLockReason() + "]" : "");
        auditLogService.logAction("INVOICE", saved.getOrderCode(), "APPROVE", "PENDING_APPROVAL",
                auditDetail, username != null ? username : "system");
        return mapToResponse(saved);
    }

    @Override
    @Transactional
    public OrderResponse rejectOrder(Long id, String reason, String username) {
        if (reason == null || reason.trim().isEmpty()) {
            throw new IllegalArgumentException("Vui lòng nhập lý do/ý kiến từ chối đơn hàng");
        }

        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn hàng với ID: " + id));

        if (!"PENDING_APPROVAL".equalsIgnoreCase(order.getStatus())) {
            throw new IllegalArgumentException("Chỉ có thể từ chối đơn hàng đang ở trạng thái Chờ duyệt (PENDING_APPROVAL)");
        }

        String cleanReason = reason.trim();
        order.setStatus("REJECTED");
        order.setApprovedBy(username != null ? username : "system");
        order.setApprovedAt(LocalDateTime.now());
        order.setNote((order.getNote() != null && !order.getNote().isBlank() ? order.getNote() + " | " : "") + "Lý do từ chối: " + cleanReason);

        Order saved = orderRepository.save(order);

        // Lưu lịch sử duyệt bất biến (S4-05)
        if (orderApprovalHistoryRepository != null) {
            try {
                OrderApprovalHistory history = OrderApprovalHistory.builder()
                        .order(saved)
                        .orderCode(saved.getOrderCode())
                        .action("REJECTED")
                        .comment(cleanReason)
                        .performedBy(username != null ? username : "system")
                        .build();
                orderApprovalHistoryRepository.save(history);
            } catch (Exception ignored) {
            }
        }

        auditLogService.logAction("INVOICE", saved.getOrderCode(), "REJECT", "PENDING_APPROVAL",
                "Từ chối đơn: " + cleanReason, username != null ? username : "system");
        return mapToResponse(saved);
    }

    @Override
    @Transactional
    public OrderResponse returnOrder(Long id, String reason, String username) {
        if (reason == null || reason.trim().isEmpty()) {
            throw new IllegalArgumentException("Vui lòng nhập lý do/ý kiến trả lại sửa đơn hàng");
        }

        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn hàng với ID: " + id));

        if (!"PENDING_APPROVAL".equalsIgnoreCase(order.getStatus())) {
            throw new IllegalArgumentException("Chỉ có thể trả lại sửa đơn hàng đang ở trạng thái Chờ duyệt (PENDING_APPROVAL)");
        }

        String cleanReason = reason.trim();
        order.setStatus("RETURNED");
        order.setApprovedBy(username != null ? username : "system");
        order.setApprovedAt(LocalDateTime.now());
        order.setNote((order.getNote() != null && !order.getNote().isBlank() ? order.getNote() + " | " : "") + "Lý do trả lại sửa: " + cleanReason);

        Order saved = orderRepository.save(order);

        // Lưu lịch sử duyệt bất biến (S4-05)
        if (orderApprovalHistoryRepository != null) {
            try {
                OrderApprovalHistory history = OrderApprovalHistory.builder()
                        .order(saved)
                        .orderCode(saved.getOrderCode())
                        .action("RETURNED")
                        .comment(cleanReason)
                        .performedBy(username != null ? username : "system")
                        .build();
                orderApprovalHistoryRepository.save(history);
            } catch (Exception ignored) {
            }
        }

        auditLogService.logAction("INVOICE", saved.getOrderCode(), "RETURN", "PENDING_APPROVAL",
                "Trả lại sửa đơn: " + cleanReason, username != null ? username : "system");
        return mapToResponse(saved);
    }

    @Override
    @Transactional
    public OrderResponse updateOrderStatus(Long id, OrderStatusUpdateRequest request, String username) {
        if (request == null || request.getStatus() == null || request.getStatus().isBlank()) {
            throw new IllegalArgumentException("Trạng thái không được để trống");
        }
        String status = request.getStatus().trim().toUpperCase();
        String comment = request.getEffectiveComment();
        return switch (status) {
            case "APPROVED", "RECORDED" -> approveOrder(id, comment, username);
            case "REJECTED" -> rejectOrder(id, comment, username);
            case "RETURNED" -> returnOrder(id, comment, username);
            default -> throw new IllegalArgumentException("Trạng thái duyệt không hợp lệ: " + status + ". Chỉ chấp nhận APPROVED, REJECTED, RETURNED");
        };
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderApprovalHistoryResponse> getOrderApprovalHistory(Long orderId) {
        if (orderApprovalHistoryRepository != null) {
            List<OrderApprovalHistory> list = orderApprovalHistoryRepository.findByOrderIdOrderByCreatedAtAsc(orderId);
            if (!list.isEmpty()) {
                return list.stream().map(h -> OrderApprovalHistoryResponse.builder()
                        .id(h.getId())
                        .orderId(h.getOrder().getId())
                        .orderCode(h.getOrderCode())
                        .action(h.getAction())
                        .comment(h.getComment())
                        .performedBy(h.getPerformedBy())
                        .username(h.getPerformedBy())
                        .createdAt(h.getCreatedAt())
                        .timestamp(h.getCreatedAt())
                        .build()).toList();
            }
        }
        return Collections.emptyList();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<OrderResponse> getOrders(
            String status,
            String approvalReason,
            String search,
            Boolean approvalRequired,
            Long dealerId,
            String createdBy,
            Long territoryId,
            LocalDate dateFrom,
            LocalDate dateTo,
            Pageable pageable) {

        Specification<Order> spec = (root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();

            // Lọc theo trạng thái
            if (status != null && !status.isBlank()) {
                String cleanStatus = status.trim().toUpperCase();
                if ("APPROVED".equals(cleanStatus)) {
                    predicates.add(cb.or(
                            cb.equal(root.get("status"), "APPROVED"),
                            cb.and(cb.equal(root.get("status"), "RECORDED"), cb.isNotNull(root.get("approvedAt"))),
                            cb.and(cb.equal(root.get("status"), "RECORDED"), cb.isTrue(root.get("requiresApproval")))
                    ));
                } else {
                    predicates.add(cb.equal(cb.upper(root.get("status")), cleanStatus));
                }
            }

            // Lọc đơn chờ duyệt
            if (Boolean.TRUE.equals(approvalRequired)) {
                predicates.add(cb.or(
                        cb.isTrue(root.get("requiresApproval")),
                        cb.equal(root.get("status"), "PENDING_APPROVAL"),
                        cb.equal(root.get("status"), "RETURNED")
                ));
            }

            // Tìm kiếm theo mã đơn, tên, đại lý
            if (search != null && !search.isBlank()) {
                String kw = "%" + search.trim().toLowerCase() + "%";
                jakarta.persistence.criteria.Join<Order, Dealer> dealerJoin = root.join("dealer", jakarta.persistence.criteria.JoinType.LEFT);
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("orderCode")), kw),
                        cb.like(cb.lower(root.get("customerName")), kw),
                        cb.like(cb.lower(dealerJoin.get("name")), kw),
                        cb.like(cb.lower(dealerJoin.get("code")), kw)
                ));
            }

            // Lọc theo lý do cần duyệt
            if (approvalReason != null && !approvalReason.isBlank()) {
                String r = approvalReason.trim().toUpperCase();
                if ("OVER_DEBT_LIMIT".equals(r)) {
                    predicates.add(cb.or(
                            cb.like(cb.lower(root.get("approvalReason")), "%hạn mức%"),
                            cb.like(cb.lower(root.get("approvalReason")), "%công nợ%")
                    ));
                } else if ("BELOW_FLOOR_PRICE".equals(r)) {
                    predicates.add(cb.like(cb.lower(root.get("approvalReason")), "%giá sàn%"));
                } else {
                    predicates.add(cb.like(cb.lower(root.get("approvalReason")), "%" + approvalReason.trim().toLowerCase() + "%"));
                }
            }

            // S4-07: Lọc theo đại lý cụ thể
            if (dealerId != null) {
                predicates.add(cb.equal(root.get("dealer").get("id"), dealerId));
            }

            // S4-07: Lọc theo người tạo đơn (nhân viên kinh doanh)
            if (createdBy != null && !createdBy.isBlank()) {
                predicates.add(cb.equal(root.get("createdBy"), createdBy.trim()));
            }

            // S4-07: Lọc theo khu vực / địa bàn của đại lý
            if (territoryId != null) {
                jakarta.persistence.criteria.Join<Order, Dealer> dJoin = root.join("dealer", jakarta.persistence.criteria.JoinType.LEFT);
                predicates.add(cb.equal(dJoin.get("territory").get("id"), territoryId));
            }

            // S4-07: Lọc theo khoảng ngày tạo đơn
            if (dateFrom != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), dateFrom.atStartOfDay()));
            }
            if (dateTo != null) {
                predicates.add(cb.lessThan(root.get("createdAt"), dateTo.plusDays(1).atStartOfDay()));
            }

            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };

        return orderRepository.findAll(spec, pageable).map(this::mapToResponse);
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
        List<OrderItemResponse> items = order.getItems() != null
                ? order.getItems().stream().map(this::mapToItemResponse).collect(Collectors.toList())
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

        // Danh sách lý do cần duyệt và mức vi phạm (S4-05)
        List<String> approvalReasonsList = new ArrayList<>();
        List<OrderViolationResponse> violations = new ArrayList<>();
        BigDecimal debtExceeded = null;
        BigDecimal priceGap = BigDecimal.ZERO;

        if (order.getApprovalReason() != null && !order.getApprovalReason().isBlank()) {
            String reasonText = order.getApprovalReason();
            if (reasonText.contains("hạn mức") || reasonText.contains("công nợ") || exceedsDebt) {
                approvalReasonsList.add("OVER_DEBT_LIMIT");
                if (currentDebt != null && debtLimit != null) {
                    BigDecimal projected = currentDebt.add(order.getTotalAmount() != null ? order.getTotalAmount() : BigDecimal.ZERO);
                    if (projected.compareTo(debtLimit) > 0) {
                        debtExceeded = projected.subtract(debtLimit);
                    }
                }
                violations.add(OrderViolationResponse.builder()
                        .type("OVER_DEBT_LIMIT")
                        .title("Vượt hạn mức công nợ")
                        .description(reasonText)
                        .displayValue(debtExceeded != null ? "+" + formatMoney(debtExceeded) : "Vượt hạn mức")
                        .value(debtExceeded)
                        .build());
            }

            if (reasonText.contains("giá sàn")) {
                approvalReasonsList.add("BELOW_FLOOR_PRICE");
                BigDecimal totalGap = BigDecimal.ZERO;
                if (order.getItems() != null) {
                    for (OrderItem it : order.getItems()) {
                        if (Boolean.TRUE.equals(it.getBelowFloorPrice()) && it.getFloorPrice() != null && it.getPrice() != null) {
                            BigDecimal diff = it.getFloorPrice().subtract(it.getPrice());
                            if (diff.compareTo(BigDecimal.ZERO) > 0) {
                                BigDecimal lineGap = diff.multiply(it.getQuantity() != null ? it.getQuantity() : BigDecimal.ONE);
                                totalGap = totalGap.add(lineGap);
                            }
                        }
                    }
                }
                if (totalGap.compareTo(BigDecimal.ZERO) > 0) {
                    priceGap = totalGap;
                }
                violations.add(OrderViolationResponse.builder()
                        .type("BELOW_FLOOR_PRICE")
                        .title("Bán dưới giá sàn")
                        .description(reasonText)
                        .displayValue(priceGap != null && priceGap.compareTo(BigDecimal.ZERO) > 0 ? "-" + formatMoney(priceGap) : "Dưới giá sàn")
                        .value(priceGap)
                        .build());
            }
        }

        // Lấy lịch sử duyệt đơn hàng (S4-05) - bất biến, không sửa xoá
        List<OrderApprovalHistoryResponse> historyResponses = new ArrayList<>();
        if (orderApprovalHistoryRepository != null) {
            try {
                List<OrderApprovalHistory> historyEntities = orderApprovalHistoryRepository.findByOrderIdOrderByCreatedAtAsc(order.getId());
                for (OrderApprovalHistory h : historyEntities) {
                    historyResponses.add(OrderApprovalHistoryResponse.builder()
                            .id(h.getId())
                            .orderId(order.getId())
                            .orderCode(order.getOrderCode())
                            .action(h.getAction())
                            .comment(h.getComment())
                            .performedBy(h.getPerformedBy())
                            .username(h.getPerformedBy())
                            .createdAt(h.getCreatedAt())
                            .timestamp(h.getCreatedAt())
                            .build());
                }
            } catch (Exception ignored) {
            }
        }
        if (historyResponses.isEmpty() && (Boolean.TRUE.equals(order.getRequiresApproval()) || order.getApprovedAt() != null)) {
            historyResponses.add(OrderApprovalHistoryResponse.builder()
                    .orderId(order.getId())
                    .orderCode(order.getOrderCode())
                    .action("PENDING_APPROVAL")
                    .comment(order.getApprovalReason())
                    .performedBy(order.getCreatedBy() != null ? order.getCreatedBy() : "system")
                    .username(order.getCreatedBy() != null ? order.getCreatedBy() : "system")
                    .createdAt(order.getCreatedAt())
                    .timestamp(order.getCreatedAt())
                    .build());
            if (order.getApprovedAt() != null) {
                String action = "REJECTED".equalsIgnoreCase(order.getStatus()) ? "REJECTED"
                        : "RETURNED".equalsIgnoreCase(order.getStatus()) ? "RETURNED" : "APPROVED";
                historyResponses.add(OrderApprovalHistoryResponse.builder()
                        .orderId(order.getId())
                        .orderCode(order.getOrderCode())
                        .action(action)
                        .comment(order.getNote())
                        .performedBy(order.getApprovedBy() != null ? order.getApprovedBy() : "Quản lý kinh doanh")
                        .username(order.getApprovedBy() != null ? order.getApprovedBy() : "Quản lý kinh doanh")
                        .createdAt(order.getApprovedAt())
                        .timestamp(order.getApprovedAt())
                        .build());
            }
        }

        return OrderResponse.builder()
                .id(order.getId())
                .orderCode(order.getOrderCode())
                .customerName(order.getCustomerName())
                .customerGroup(order.getCustomerGroup())
                .dealerId(order.getDealer() != null ? order.getDealer().getId() : null)
                .dealerName(dealer != null ? dealer.getName() : null)
                .dealerCode(dealer != null ? dealer.getCode() : null)
                .deliveryPointId(order.getDeliveryPoint() != null ? order.getDeliveryPoint().getId() : null)
                .expectedDeliveryDate(order.getExpectedDeliveryDate())
                .priceListId(order.getPriceList() != null ? order.getPriceList().getId() : null)
                .priceListCode(order.getPriceList() != null ? order.getPriceList().getCode() : null)
                .status(order.getStatus())
                .requiresApproval(order.getRequiresApproval())
                .approvalReason(order.getApprovalReason())
                .approvalReasons(approvalReasonsList)
                .violations(violations)
                .debtExceeded(debtExceeded)
                .priceGap(priceGap)
                .approvalHistory(historyResponses)
                .approvedBy(order.getApprovedBy())
                .approvedAt(order.getApprovedAt())
                .subtotalAmount(order.getSubtotalAmount() != null ? order.getSubtotalAmount() : order.getTotalAmount())
                .discountAmount(order.getDiscountAmount() != null ? order.getDiscountAmount() : BigDecimal.ZERO)
                .totalAmount(order.getTotalAmount())
                .grandTotal(order.getTotalAmount())
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
                .cancelReason(order.getCancelReason())
                .cancelledBy(order.getCancelledBy())
                .cancelledAt(order.getCancelledAt())
                .build();
    }

    private String formatMoney(BigDecimal amount) {
        if (amount == null) return "0 đ";
        return new java.text.DecimalFormat("#,### đ").format(amount);
    }

    private OrderItemResponse mapToItemResponse(OrderItem item) {
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
                .unitPrice(item.getPrice())
                .floorPrice(item.getFloorPrice())
                .belowFloorPrice(item.getBelowFloorPrice())
                .subtotalAmount(item.getSubtotalAmount() != null ? item.getSubtotalAmount() : item.getTotalAmount())
                .discountAmount(item.getDiscountAmount() != null ? item.getDiscountAmount() : BigDecimal.ZERO)
                .discountPercent(item.getDiscountPercent())
                .appliedPolicyId(item.getAppliedPolicyId())
                .appliedPolicyCode(item.getAppliedPolicyCode())
                .appliedPolicyName(item.getAppliedPolicyName())
                .totalAmount(item.getTotalAmount())
                .lineTotal(item.getTotalAmount())
                .note(item.getNote())
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
                    .build());
        }

        return OrderCalculateResponse.builder()
                .dealerId(dealer != null ? dealer.getId() : null)
                .dealerName(dealer != null ? dealer.getName() : null)
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

    // ── S4-04: Lịch sử mua hàng đại lý ──

    @Override
    @Transactional(readOnly = true)
    public DealerPurchaseHistoryResult getDealerPurchaseHistory(Long dealerId, int months, String currentUsername) {
        Dealer dealer = dealerRepository.findById(dealerId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đại lý với ID: " + dealerId));

        LocalDate toDate = LocalDate.now();
        LocalDate fromDate = toDate.minusMonths(Math.max(1, Math.min(months, 24)));

        // Lấy các đơn hàng RECORDED của đại lý trong khoảng thời gian
        List<Order> orders = orderRepository.findAll((root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("dealer").get("id"), dealerId));
            predicates.add(cb.equal(root.get("status"), "RECORDED"));
            predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), fromDate.atStartOfDay()));
            predicates.add(cb.lessThan(root.get("createdAt"), toDate.plusDays(1).atStartOfDay()));
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        });

        // Tổng hợp theo sản phẩm
        Map<Long, ProductPurchaseSummary> productMap = new LinkedHashMap<>();
        for (Order order : orders) {
            if (order.getItems() == null) continue;
            for (OrderItem item : order.getItems()) {
                if (item.getProduct() == null) continue;
                Long pid = item.getProduct().getId();
                ProductPurchaseSummary existing = productMap.get(pid);
                BigDecimal baseQty = item.getBaseQuantity() != null ? item.getBaseQuantity() : BigDecimal.ZERO;
                int existingCount = existing != null ? existing.orderCount() : 0;
                BigDecimal existingQty = existing != null ? existing.totalBaseQuantity() : BigDecimal.ZERO;
                LocalDateTime lastAt = (existing == null || order.getCreatedAt().isAfter(existing.lastOrderAt() != null ? existing.lastOrderAt() : LocalDateTime.MIN))
                        ? order.getCreatedAt() : existing.lastOrderAt();
                BigDecimal lastPrice = (existing == null || order.getCreatedAt().isAfter(existing.lastOrderAt() != null ? existing.lastOrderAt() : LocalDateTime.MIN))
                        ? (item.getPrice() != null ? item.getPrice() : BigDecimal.ZERO) : existing.lastPrice();
                Product p = item.getProduct();
                String catName = p.getCategory() != null ? p.getCategory().getName() : null;
                BigDecimal totalQty = existingQty.add(baseQty);
                double avgMonthly = months > 0 ? totalQty.doubleValue() / months : 0;
                productMap.put(pid, new ProductPurchaseSummary(
                        pid, p.getCode(), p.getName(), catName,
                        totalQty, p.getUnit(),
                        BigDecimal.valueOf(avgMonthly).setScale(2, java.math.RoundingMode.HALF_UP),
                        existingCount + 1, lastPrice, lastAt));
            }
        }

        // Danh sách đơn gần nhất (tối đa 5)
        List<RecentOrderSummary> recentOrders = orders.stream()
                .sorted(Comparator.comparing(Order::getCreatedAt).reversed())
                .limit(5)
                .map(o -> {
                    List<String> names = o.getItems() != null
                            ? o.getItems().stream()
                                .filter(it -> it.getProduct() != null)
                                .map(it -> it.getProduct().getName())
                                .collect(Collectors.toList())
                            : Collections.emptyList();
                    return new RecentOrderSummary(o.getId(), o.getOrderCode(), o.getStatus(),
                            o.getTotalAmount(), o.getCreatedAt(), names);
                })
                .collect(Collectors.toList());

        List<ProductPurchaseSummary> products = new ArrayList<>(productMap.values());
        products.sort(Comparator.comparing(ProductPurchaseSummary::totalBaseQuantity).reversed());

        return new DealerPurchaseHistoryResult(dealerId, dealer.getName(), dealer.getCode(),
                months, fromDate, toDate, products, recentOrders);
    }

    // ── S4-09: Sao chép đơn hàng ──

    @Override
    @Transactional
    public OrderResponse copyOrder(Long sourceOrderId, String username) {
        Order source = orderRepository.findById(sourceOrderId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn hàng nguồn với ID: " + sourceOrderId));

        // Tạo mã đơn mới
        String newCode = "DH-COPY-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"));

        // Lấy thông tin đại lý và xác định bảng giá hiện hành
        Dealer dealer = source.getDealer();
        CustomerGroup customerGroup = source.getCustomerGroup();
        if (dealer != null) {
            if (Boolean.TRUE.equals(dealer.getLocked())) {
                throw new com.quanlybanhang.exception.DealerLockedException(
                        dealer.getCode(), dealer.getName(), dealer.getLockReason());
            }
            customerGroup = dealer.getCustomerGroup();
        }

        // Tra bảng giá hiện hành cho nhóm khách hàng
        PriceList currentPriceList = null;
        if (customerGroup != null) {
            List<PriceList> pl = priceListRepository.findEffectivePriceLists(customerGroup, LocalDate.now());
            if (!pl.isEmpty()) currentPriceList = pl.get(0);
        }

        Map<Long, PriceListItem> priceMap = new HashMap<>();
        if (currentPriceList != null && currentPriceList.getItems() != null) {
            for (PriceListItem pli : currentPriceList.getItems()) {
                if (pli.getProduct() != null) priceMap.put(pli.getProduct().getId(), pli);
            }
        }

        Order newOrder = Order.builder()
                .orderCode(newCode)
                .customerName(source.getCustomerName())
                .customerGroup(customerGroup)
                .dealer(dealer)
                .deliveryPoint(source.getDeliveryPoint())
                .priceList(currentPriceList)
                .status("RECORDED")
                .requiresApproval(false)
                .note("Sao chép từ đơn hàng: " + source.getOrderCode())
                .createdBy(username)
                .build();

        boolean requiresApproval = false;
        List<String> approvalReasonsList = new ArrayList<>();
        BigDecimal grandSubtotal = BigDecimal.ZERO;
        BigDecimal grandDiscount = BigDecimal.ZERO;

        if (source.getItems() != null) {
            for (OrderItem srcItem : source.getItems()) {
                Product product = srcItem.getProduct();
                if (product == null) continue;

                String unit = srcItem.getUnit() != null ? srcItem.getUnit() : product.getUnit();
                BigDecimal factor = unitConversionService.getConversionFactor(product, unit);
                BigDecimal qty = srcItem.getQuantity() != null ? srcItem.getQuantity() : BigDecimal.ONE;
                BigDecimal baseQty = qty.multiply(factor);

                // Áp giá từ bảng giá hiện hành nếu có
                PriceListItem pli = priceMap.get(product.getId());
                BigDecimal price;
                BigDecimal floorPrice = null;
                boolean belowFloor = false;
                if (pli != null) {
                    price = pli.getSellingPrice().multiply(factor);
                    floorPrice = pli.getFloorPrice().multiply(factor);
                    belowFloor = price.compareTo(floorPrice) < 0;
                } else {
                    // Dùng giá từ đơn gốc nếu SKU không còn trong bảng giá
                    price = srcItem.getPrice() != null ? srcItem.getPrice() : BigDecimal.ZERO;
                }

                if (belowFloor) {
                    requiresApproval = true;
                    approvalReasonsList.add(String.format("Sản phẩm '%s' bán giá %s < giá sàn %s",
                            product.getName(), price.toPlainString(),
                            floorPrice != null ? floorPrice.toPlainString() : "N/A"));
                }

                BigDecimal subtotal = price.multiply(qty);
                grandSubtotal = grandSubtotal.add(subtotal);

                OrderItem newItem = OrderItem.builder()
                        .product(product)
                        .unit(unit)
                        .quantity(qty)
                        .conversionFactor(factor)
                        .baseUnit(product.getUnit())
                        .baseQuantity(baseQty)
                        .price(price)
                        .floorPrice(floorPrice)
                        .belowFloorPrice(belowFloor)
                        .subtotalAmount(subtotal)
                        .discountAmount(BigDecimal.ZERO)
                        .totalAmount(subtotal)
                        .note(srcItem.getNote())
                        .build();
                newOrder.addItem(newItem);
            }
        }

        newOrder.setSubtotalAmount(grandSubtotal);
        newOrder.setDiscountAmount(grandDiscount);
        newOrder.setTotalAmount(grandSubtotal.subtract(grandDiscount).max(BigDecimal.ZERO));

        if (requiresApproval) {
            newOrder.setStatus("PENDING_APPROVAL");
            newOrder.setRequiresApproval(true);
            newOrder.setApprovalReason(String.join("; ", approvalReasonsList));
        }

        Order saved = orderRepository.save(newOrder);
        auditLogService.logAction("INVOICE", saved.getOrderCode(), "COPY", null,
                "Sao chép từ: " + source.getOrderCode(), username != null ? username : "system");
        return mapToResponse(saved);
    }

    // ── S4-08: Xuất HTML đơn hàng để in/PDF ──

    @Override
    @Transactional(readOnly = true)
    public String generateOrderPrintHtml(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn hàng với ID: " + orderId));

        Dealer dealer = order.getDealer();
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html lang='vi'><head><meta charset='UTF-8'>");
        sb.append("<title>Đơn hàng ").append(escapeHtml(order.getOrderCode())).append("</title>");
        sb.append("<style>");
        sb.append("body{font-family:Arial,sans-serif;font-size:13px;margin:20px;color:#222}");
        sb.append(".header{text-align:center;margin-bottom:16px}");
        sb.append(".header h2{margin:0;font-size:18px}");
        sb.append(".info-table{width:100%;margin-bottom:14px;border-collapse:collapse}");
        sb.append(".info-table td{padding:4px 8px;vertical-align:top}");
        sb.append(".info-table .label{font-weight:bold;width:160px}");
        sb.append("table.items{width:100%;border-collapse:collapse;margin-bottom:14px}");
        sb.append("table.items th,table.items td{border:1px solid #bbb;padding:5px 8px;text-align:left}");
        sb.append("table.items th{background:#f0f0f0;font-weight:bold}");
        sb.append("table.items td.num{text-align:right}");
        sb.append(".totals{text-align:right;margin-top:8px}");
        sb.append(".totals td{padding:3px 8px}");
        sb.append(".totals .label{font-weight:bold}");
        sb.append(".footer{margin-top:30px;display:flex;justify-content:space-between}");
        sb.append(".footer .sign{text-align:center;width:200px}");
        sb.append("</style></head><body>");

        // Header
        sb.append("<div class='header'>");
        sb.append("<h2>ĐƠN ĐẶT HÀNG</h2>");
        sb.append("<p>Mã đơn: <strong>").append(escapeHtml(order.getOrderCode())).append("</strong></p>");
        sb.append("</div>");

        // Thông tin đại lý và đơn hàng
        sb.append("<table class='info-table'><tr>");
        sb.append("<td><table class='info-table'>");
        sb.append("<tr><td class='label'>Đại lý:</td><td>").append(dealer != null ? escapeHtml(dealer.getName()) : "").append("</td></tr>");
        sb.append("<tr><td class='label'>Mã đại lý:</td><td>").append(dealer != null ? escapeHtml(dealer.getCode()) : "").append("</td></tr>");
        sb.append("<tr><td class='label'>Địa chỉ:</td><td>").append(dealer != null && dealer.getAddress() != null ? escapeHtml(dealer.getAddress()) : "").append("</td></tr>");
        sb.append("<tr><td class='label'>Điện thoại:</td><td>").append(dealer != null && dealer.getPhone() != null ? escapeHtml(dealer.getPhone()) : "").append("</td></tr>");
        sb.append("</table></td>");
        sb.append("<td><table class='info-table'>");
        sb.append("<tr><td class='label'>Ngày tạo:</td><td>").append(order.getCreatedAt() != null ? order.getCreatedAt().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")) : "").append("</td></tr>");
        sb.append("<tr><td class='label'>Ngày giao dự kiến:</td><td>").append(order.getExpectedDeliveryDate() != null ? order.getExpectedDeliveryDate().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) : "").append("</td></tr>");
        sb.append("<tr><td class='label'>Người tạo:</td><td>").append(order.getCreatedBy() != null ? escapeHtml(order.getCreatedBy()) : "").append("</td></tr>");
        sb.append("<tr><td class='label'>Trạng thái:</td><td>").append(escapeHtml(translateStatus(order.getStatus()))).append("</td></tr>");
        if (order.getPriceList() != null) {
            sb.append("<tr><td class='label'>Bảng giá:</td><td>").append(escapeHtml(order.getPriceList().getCode())).append("</td></tr>");
        }
        sb.append("</table></td>");
        sb.append("</tr></table>");

        // Bảng dòng hàng
        sb.append("<table class='items'><thead><tr>");
        sb.append("<th>STT</th><th>Mã SP</th><th>Tên sản phẩm</th><th>ĐVT</th><th class='num'>SL</th><th class='num'>Đơn giá</th><th class='num'>Chiết khấu</th><th class='num'>Thành tiền</th>");
        sb.append("</tr></thead><tbody>");

        java.text.DecimalFormat df = new java.text.DecimalFormat("#,###");
        int i = 1;
        if (order.getItems() != null) {
            for (OrderItem item : order.getItems()) {
                sb.append("<tr>");
                sb.append("<td>").append(i++).append("</td>");
                sb.append("<td>").append(item.getProduct() != null ? escapeHtml(item.getProduct().getCode()) : "").append("</td>");
                sb.append("<td>").append(item.getProduct() != null ? escapeHtml(item.getProduct().getName()) : "").append("</td>");
                sb.append("<td>").append(item.getUnit() != null ? escapeHtml(item.getUnit()) : "").append("</td>");
                sb.append("<td class='num'>").append(item.getQuantity() != null ? df.format(item.getQuantity()) : "").append("</td>");
                sb.append("<td class='num'>").append(item.getPrice() != null ? df.format(item.getPrice()) : "").append("</td>");
                sb.append("<td class='num'>").append(item.getDiscountAmount() != null && item.getDiscountAmount().compareTo(BigDecimal.ZERO) > 0 ? df.format(item.getDiscountAmount()) : "-").append("</td>");
                sb.append("<td class='num'>").append(item.getTotalAmount() != null ? df.format(item.getTotalAmount()) : "").append("</td>");
                sb.append("</tr>");
            }
        }
        sb.append("</tbody></table>");

        // Tổng tiền
        sb.append("<table class='totals' style='margin-left:auto'>");
        sb.append("<tr><td class='label'>Tổng tiền hàng:</td><td>").append(df.format(order.getSubtotalAmount() != null ? order.getSubtotalAmount() : BigDecimal.ZERO)).append(" đ</td></tr>");
        sb.append("<tr><td class='label'>Chiết khấu:</td><td>-").append(df.format(order.getDiscountAmount() != null ? order.getDiscountAmount() : BigDecimal.ZERO)).append(" đ</td></tr>");
        sb.append("<tr><td class='label' style='font-size:15px'>Tổng thanh toán:</td><td style='font-size:15px;font-weight:bold'>").append(df.format(order.getTotalAmount() != null ? order.getTotalAmount() : BigDecimal.ZERO)).append(" đ</td></tr>");
        sb.append("</table>");

        if (order.getNote() != null && !order.getNote().isBlank()) {
            sb.append("<p><strong>Ghi chú:</strong> ").append(escapeHtml(order.getNote())).append("</p>");
        }

        // Chữ ký
        sb.append("<div class='footer'>");
        sb.append("<div class='sign'><p>Người đặt hàng</p><br/><br/><p>(Ký, ghi rõ họ tên)</p></div>");
        sb.append("<div class='sign'><p>Người giao hàng</p><br/><br/><p>(Ký, ghi rõ họ tên)</p></div>");
        sb.append("<div class='sign'><p>Người nhận hàng</p><br/><br/><p>(Ký, ghi rõ họ tên)</p></div>");
        sb.append("</div>");

        sb.append("</body></html>");
        return sb.toString();
    }

    private String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private String translateStatus(String status) {
        if (status == null) return "";
        return switch (status.toUpperCase()) {
            case "RECORDED" -> "Đã ghi sổ";
            case "PENDING_APPROVAL" -> "Chờ duyệt";
            case "APPROVED" -> "Đã duyệt";
            case "REJECTED" -> "Từ chối";
            case "RETURNED" -> "Trả lại sửa";
            case "CANCELLED" -> "Đã huỷ";
            case "EXPORTED" -> "Đã xuất kho";
            case "DELIVERED" -> "Đã giao hàng";
            default -> status;
        };
    }
}
