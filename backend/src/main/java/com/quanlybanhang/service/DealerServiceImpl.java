package com.quanlybanhang.service;

import com.quanlybanhang.dto.request.DealerRequest;
import com.quanlybanhang.dto.response.DealerOptionsResponse;
import com.quanlybanhang.dto.response.DealerResponse;
import com.quanlybanhang.dto.response.NamedResourceResponse;
import com.quanlybanhang.exception.DealerHasTransactionsException;
import com.quanlybanhang.model.CustomerGroup;
import com.quanlybanhang.model.Dealer;
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
            Pageable pageable) {

        String trimmedKeyword = (keyword != null && !keyword.trim().isEmpty()) ? keyword.trim() : null;
        Page<Dealer> page = dealerRepository.searchDealers(
                trimmedKeyword,
                customerGroup,
                territoryId,
                assignedUserId,
                active,
                pageable);

        return page.map(this::mapToResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DealerResponse> getActiveDealers() {
        return dealerRepository.findByActiveTrueOrderByNameAsc().stream()
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
                .phone(dealer.getPhone())
                .email(dealer.getEmail())
                .address(dealer.getAddress())
                .hasTransactions(hasTransactions)
                .createdAt(dealer.getCreatedAt())
                .updatedAt(dealer.getUpdatedAt())
                .build();
    }
}

