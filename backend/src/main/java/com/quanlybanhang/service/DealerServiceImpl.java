package com.quanlybanhang.service;

import com.quanlybanhang.dto.request.DealerRequest;
import com.quanlybanhang.dto.response.DealerOptionsResponse;
import com.quanlybanhang.dto.response.DealerResponse;
import com.quanlybanhang.dto.response.NamedResourceResponse;
import com.quanlybanhang.dto.response.OrderItemResponse;
import com.quanlybanhang.dto.response.OrderResponse;
import com.quanlybanhang.exception.DealerHasTransactionsException;
import com.quanlybanhang.model.CustomerGroup;
import com.quanlybanhang.model.Dealer;
import com.quanlybanhang.model.Order;
import com.quanlybanhang.model.OrderItem;
import com.quanlybanhang.model.Territory;
import com.quanlybanhang.model.User;
import com.quanlybanhang.repository.DealerRepository;
import com.quanlybanhang.repository.OrderRepository;
import com.quanlybanhang.repository.TerritoryRepository;
import com.quanlybanhang.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DealerServiceImpl implements DealerService {

    private final DealerRepository dealerRepository;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final TerritoryRepository territoryRepository;
    private final AuditLogService auditLogService;

    @Override
    @Transactional(readOnly = true)
    public Page<DealerResponse> getAllDealers(
            String keyword,
            CustomerGroup customerGroup,
            Long territoryId,
            Long assignedUserId,
            Boolean active,
            Boolean locked,
            Pageable pageable) {

        String trimmedKeyword = (keyword != null && !keyword.trim().isEmpty()) ? keyword.trim() : null;
        Page<Dealer> page = dealerRepository.searchDealers(
                trimmedKeyword,
                customerGroup,
                territoryId,
                assignedUserId,
                active,
                locked,
                pageable);

        return page.map(this::mapToResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DealerResponse> getActiveDealers() {
        return dealerRepository.findByActiveTrueOrderByNameAsc().stream()
                .filter(d -> !Boolean.TRUE.equals(d.getLocked()))
                .map(this::mapToResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public DealerResponse getDealerById(Long id) {
        Dealer dealer = dealerRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đại lý với ID: " + id));
        return mapToResponse(dealer);
    }

    @Override
    @Transactional
    public DealerResponse createDealer(DealerRequest request, String currentUsername) {
        String code = request.getCode().trim().toUpperCase();
        if (dealerRepository.existsByCodeIgnoreCase(code)) {
            throw new IllegalArgumentException("Mã đại lý '" + code + "' đã tồn tại");
        }

        User assignedUser = null;
        if (request.getAssignedUserId() != null) {
            assignedUser = userRepository.findById(request.getAssignedUserId())
                    .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy người phụ trách với ID: " + request.getAssignedUserId()));
        }

        Territory territory = null;
        if (request.getTerritoryId() != null) {
            territory = territoryRepository.findById(request.getTerritoryId())
                    .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy khu vực với ID: " + request.getTerritoryId()));
        }

        Dealer dealer = Dealer.builder()
                .code(code)
                .name(request.getName().trim())
                .taxCode(request.getTaxCode() != null ? request.getTaxCode().trim() : null)
                .customerGroup(request.getCustomerGroup() != null ? request.getCustomerGroup() : CustomerGroup.DAI_LY_CAP_1)
                .assignedUser(assignedUser)
                .territory(territory)
                .active(request.getActive() != null ? request.getActive() : true)
                .handoverRequired(false)
                .debtLimit(request.getDebtLimit() != null ? request.getDebtLimit() : BigDecimal.ZERO)
                .phone(request.getPhone() != null ? request.getPhone().trim() : null)
                .email(request.getEmail() != null ? request.getEmail().trim() : null)
                .address(request.getAddress() != null ? request.getAddress().trim() : null)
                .build();

        Dealer saved = dealerRepository.save(dealer);
        auditLogService.logAction("DEALER", saved.getId().toString(), "CREATE_DEALER", null, saved.getCode(), currentUsername);
        return mapToResponse(saved);
    }

    @Override
    @Transactional
    public DealerResponse updateDealer(Long id, DealerRequest request, String currentUsername) {
        Dealer dealer = dealerRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đại lý với ID: " + id));

        String newCode = request.getCode().trim().toUpperCase();
        if (dealerRepository.existsByCodeIgnoreCaseAndIdNot(newCode, id)) {
            throw new IllegalArgumentException("Mã đại lý '" + newCode + "' đã tồn tại");
        }

        User assignedUser = null;
        if (request.getAssignedUserId() != null) {
            assignedUser = userRepository.findById(request.getAssignedUserId())
                    .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy người phụ trách với ID: " + request.getAssignedUserId()));
        }

        Territory territory = null;
        if (request.getTerritoryId() != null) {
            territory = territoryRepository.findById(request.getTerritoryId())
                    .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy khu vực với ID: " + request.getTerritoryId()));
        }

        dealer.setCode(newCode);
        dealer.setName(request.getName().trim());
        dealer.setTaxCode(request.getTaxCode() != null ? request.getTaxCode().trim() : null);
        dealer.setCustomerGroup(request.getCustomerGroup() != null ? request.getCustomerGroup() : CustomerGroup.DAI_LY_CAP_1);
        dealer.setAssignedUser(assignedUser);
        dealer.setTerritory(territory);
        if (request.getActive() != null) {
            dealer.setActive(request.getActive());
        }
        if (request.getDebtLimit() != null) {
            dealer.setDebtLimit(request.getDebtLimit());
        }
        dealer.setPhone(request.getPhone() != null ? request.getPhone().trim() : null);
        dealer.setEmail(request.getEmail() != null ? request.getEmail().trim() : null);
        dealer.setAddress(request.getAddress() != null ? request.getAddress().trim() : null);

        Dealer updated = dealerRepository.save(dealer);
        auditLogService.logAction("DEALER", updated.getId().toString(), "UPDATE_DEALER", null, updated.getCode(), currentUsername);
        return mapToResponse(updated);
    }

    @Override
    @Transactional
    public DealerResponse updateStatus(Long id, boolean active, String currentUsername) {
        Dealer dealer = dealerRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đại lý với ID: " + id));

        dealer.setActive(active);
        Dealer saved = dealerRepository.save(dealer);
        auditLogService.logAction(
                "DEALER",
                saved.getId().toString(),
                active ? "ACTIVATE_DEALER" : "DEACTIVATE_DEALER",
                null,
                saved.getCode(),
                currentUsername);

        return mapToResponse(saved);
    }

    @Override
    @Transactional
    public DealerResponse lockDealer(Long id, String reason, String currentUsername) {
        if (reason == null || reason.trim().isEmpty()) {
            throw new IllegalArgumentException("Bắt buộc nhập lý do khoá");
        }
        String normalizedReason = reason.trim();
        if (normalizedReason.length() > 500) {
            throw new IllegalArgumentException("Lý do khoá tối đa 500 ký tự");
        }

        Dealer dealer = dealerRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đại lý với ID: " + id));

        dealer.setLocked(true);
        dealer.setLockReason(normalizedReason);
        dealer.setLockedAt(java.time.LocalDateTime.now());
        dealer.setLockedBy(currentUsername != null ? currentUsername : "system");

        Dealer saved = dealerRepository.save(dealer);
        auditLogService.logAction(
                "DEALER",
                saved.getId().toString(),
                "LOCK_DEALER",
                null,
                "Khoá giao dịch đại lý: " + saved.getCode() + ". Lý do: " + normalizedReason,
                currentUsername != null ? currentUsername : "system");

        return mapToResponse(saved);
    }

    @Override
    @Transactional
    public DealerResponse unlockDealer(Long id, String currentUsername) {
        Dealer dealer = dealerRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đại lý với ID: " + id));

        String oldReason = dealer.getLockReason();
        dealer.setLocked(false);
        dealer.setLockReason(null);
        dealer.setLockedAt(null);
        dealer.setLockedBy(null);

        Dealer saved = dealerRepository.save(dealer);
        auditLogService.logAction(
                "DEALER",
                saved.getId().toString(),
                "UNLOCK_DEALER",
                oldReason,
                "Mở khoá giao dịch cho đại lý: " + saved.getCode(),
                currentUsername != null ? currentUsername : "system");

        return mapToResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderResponse> getInProgressOrders(Long dealerId) {
        if (!dealerRepository.existsById(dealerId)) {
            throw new IllegalArgumentException("Không tìm thấy đại lý với ID: " + dealerId);
        }
        return orderRepository.findByDealerIdAndStatusNotInOrderByCreatedAtDesc(
                        dealerId, List.of("CANCELLED", "REJECTED"))
                .stream()
                .map(this::mapOrderToResponse)
                .toList();
    }

    @Override
    @Transactional
    public void deleteDealer(Long id, String currentUsername) {
        Dealer dealer = dealerRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đại lý với ID: " + id));

        // Quy tắc S3-03: Đại lý đã phát sinh giao dịch thì không xoá được, chỉ ngừng giao dịch
        if (orderRepository.existsByDealerId(id)) {
            throw new DealerHasTransactionsException(dealer.getCode(), dealer.getName());
        }

        dealerRepository.delete(dealer);
        auditLogService.logAction("DEALER", id.toString(), "DELETE_DEALER", dealer.getCode(), null, currentUsername);
    }

    @Override
    @Transactional(readOnly = true)
    public DealerOptionsResponse getDealerOptions() {
        List<DealerOptionsResponse.CustomerGroupOption> customerGroups = Arrays.stream(CustomerGroup.values())
                .map(g -> new DealerOptionsResponse.CustomerGroupOption(g.name(), g.getDisplayName()))
                .toList();

        List<NamedResourceResponse> territories = territoryRepository.findAllByActiveTrueOrderByNameAsc().stream()
                .map(t -> new NamedResourceResponse(t.getId(), t.getCode(), t.getName()))
                .toList();

        List<DealerOptionsResponse.AssignedUserOption> assignedUsers = userRepository.findAll().stream()
                .filter(u -> Boolean.TRUE.equals(u.getActive()))
                .map(u -> new DealerOptionsResponse.AssignedUserOption(u.getId(), u.getUsername(), u.getFullName()))
                .toList();

        return DealerOptionsResponse.builder()
                .customerGroups(customerGroups)
                .territories(territories)
                .assignedUsers(assignedUsers)
                .build();
    }

    private DealerResponse mapToResponse(Dealer dealer) {
        boolean hasTransactions = orderRepository.existsByDealerId(dealer.getId());
        long inProgressOrderCount = orderRepository.countByDealerIdAndStatusNotIn(
                dealer.getId(), List.of("CANCELLED", "REJECTED"));

        return DealerResponse.builder()
                .id(dealer.getId())
                .code(dealer.getCode())
                .name(dealer.getName())
                .taxCode(dealer.getTaxCode())
                .customerGroup(dealer.getCustomerGroup())
                .customerGroupDisplayName(dealer.getCustomerGroup() != null ? dealer.getCustomerGroup().getDisplayName() : null)
                .territoryId(dealer.getTerritory() != null ? dealer.getTerritory().getId() : null)
                .territoryCode(dealer.getTerritory() != null ? dealer.getTerritory().getCode() : null)
                .territoryName(dealer.getTerritory() != null ? dealer.getTerritory().getName() : null)
                .assignedUserId(dealer.getAssignedUser() != null ? dealer.getAssignedUser().getId() : null)
                .assignedUsername(dealer.getAssignedUser() != null ? dealer.getAssignedUser().getUsername() : null)
                .assignedUserFullName(dealer.getAssignedUser() != null ? dealer.getAssignedUser().getFullName() : null)
                .active(dealer.getActive())
                .handoverRequired(dealer.getHandoverRequired())
                .debtLimit(dealer.getDebtLimit())
                .locked(dealer.getLocked())
                .lockReason(dealer.getLockReason())
                .lockedAt(dealer.getLockedAt())
                .lockedBy(dealer.getLockedBy())
                .inProgressOrderCount(inProgressOrderCount)
                .phone(dealer.getPhone())
                .email(dealer.getEmail())
                .address(dealer.getAddress())
                .hasTransactions(hasTransactions)
                .createdAt(dealer.getCreatedAt())
                .updatedAt(dealer.getUpdatedAt())
                .build();
    }

    private OrderResponse mapOrderToResponse(Order order) {
        List<OrderItemResponse> items = order.getItems() != null
                ? order.getItems().stream().map(this::mapOrderItemToResponse).toList()
                : java.util.Collections.emptyList();

        Dealer dealer = order.getDealer();
        boolean dealerLocked = dealer != null && Boolean.TRUE.equals(dealer.getLocked());
        String dealerLockWarning = dealerLocked
                ? String.format("Cảnh báo: Đại lý '%s' (%s) đang bị khoá giao dịch. Lý do: %s. Đơn hàng đang dở dang vẫn được xử lý nhưng cần theo dõi thu hồi công nợ.",
                        dealer.getName(), dealer.getCode(), dealer.getLockReason() != null ? dealer.getLockReason() : "Không nêu rõ")
                : null;

        return OrderResponse.builder()
                .id(order.getId())
                .orderCode(order.getOrderCode())
                .customerName(order.getCustomerName())
                .customerGroup(order.getCustomerGroup())
                .dealerId(order.getDealer() != null ? order.getDealer().getId() : null)
                .deliveryPointId(order.getDeliveryPoint() != null ? order.getDeliveryPoint().getId() : null)
                .priceListId(order.getPriceList() != null ? order.getPriceList().getId() : null)
                .priceListCode(order.getPriceList() != null ? order.getPriceList().getCode() : null)
                .status(order.getStatus())
                .requiresApproval(order.getRequiresApproval())
                .approvalReason(order.getApprovalReason())
                .approvedBy(order.getApprovedBy())
                .approvedAt(order.getApprovedAt())
                .totalAmount(order.getTotalAmount())
                .note(order.getNote())
                .createdBy(order.getCreatedBy())
                .createdAt(order.getCreatedAt())
                .recordedAt(order.getRecordedAt())
                .items(items)
                .dealerLocked(dealerLocked)
                .dealerLockWarning(dealerLockWarning)
                .warning(dealerLockWarning)
                .build();
    }

    private OrderItemResponse mapOrderItemToResponse(OrderItem item) {
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
                .totalAmount(item.getTotalAmount())
                .note(item.getNote())
                .build();
    }
}

