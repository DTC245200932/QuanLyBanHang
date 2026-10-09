package com.quanlybanhang.service;

import com.quanlybanhang.dto.request.AssignDealerRequest;
import com.quanlybanhang.dto.request.BulkHandoverRequest;
import com.quanlybanhang.dto.request.DealerRequest;
import com.quanlybanhang.dto.response.*;
import com.quanlybanhang.exception.DealerHasTransactionsException;
import com.quanlybanhang.model.*;
import com.quanlybanhang.repository.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DealerServiceTest {

    @Mock
    private DealerRepository dealerRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private TerritoryRepository territoryRepository;

    @Mock
    private DealerAssignmentHistoryRepository dealerAssignmentHistoryRepository;

    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private DealerServiceImpl dealerService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private User buildSalesUser(Long id, String username, String fullName) {
        Role salesRole = Role.builder().id(1L).name(ERole.ROLE_SALES).build();
        return User.builder()
                .id(id)
                .username(username)
                .fullName(fullName)
                .active(true)
                .roles(Set.of(salesRole))
                .build();
    }

    @Test
    @DisplayName("Tạo đại lý thành công với đầy đủ các trường thông tin theo S3-03")
    void createDealer_persistsAllFields() {
        DealerRequest request = DealerRequest.builder()
                .code("DL001")
                .name("Đại lý Cấp 1 Minh Phát")
                .taxCode("0101234567")
                .customerGroup(CustomerGroup.DAI_LY_CAP_1)
                .territoryId(10L)
                .assignedUserId(20L)
                .phone("0981234567")
                .email("minhphat@dealer.vn")
                .address("123 Lê Lợi, TP.HCM")
                .debtLimit(new BigDecimal("100000000"))
                .maxDebtDays(30)
                .active(true)
                .build();

        Territory territory = Territory.builder().id(10L).code("HCM-DONG").name("TP.HCM Đông").build();
        User user = buildSalesUser(20L, "sales.user", "Nguyễn Văn Bán");

        when(dealerRepository.existsByCodeIgnoreCase("DL001")).thenReturn(false);
        when(territoryRepository.findById(10L)).thenReturn(Optional.of(territory));
        when(userRepository.findById(20L)).thenReturn(Optional.of(user));
        when(dealerRepository.save(any(Dealer.class))).thenAnswer(invocation -> {
            Dealer d = invocation.getArgument(0);
            d.setId(1L);
            return d;
        });

        DealerResponse response = dealerService.createDealer(request, "accountant01");

        ArgumentCaptor<Dealer> captor = ArgumentCaptor.forClass(Dealer.class);
        verify(dealerRepository).save(captor.capture());
        Dealer saved = captor.getValue();

        assertThat(saved.getCode()).isEqualTo("DL001");
        assertThat(saved.getName()).isEqualTo("Đại lý Cấp 1 Minh Phát");
        assertThat(saved.getTaxCode()).isEqualTo("0101234567");
        assertThat(saved.getCustomerGroup()).isEqualTo(CustomerGroup.DAI_LY_CAP_1);
        assertThat(saved.getTerritory()).isEqualTo(territory);
        assertThat(saved.getAssignedUser()).isEqualTo(user);
        assertThat(saved.getDebtLimit()).isEqualTo(new BigDecimal("100000000"));
        assertThat(saved.getMaxDebtDays()).isEqualTo(30);
        assertThat(saved.getActive()).isTrue();

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getCustomerGroupDisplayName()).isEqualTo("Đại lý cấp 1");
        assertThat(response.getTerritoryName()).isEqualTo("TP.HCM Đông");
        assertThat(response.getAssignedUserFullName()).isEqualTo("Nguyễn Văn Bán");
        assertThat(response.getMaxDebtDays()).isEqualTo(30);

        verify(dealerAssignmentHistoryRepository).save(any(DealerAssignmentHistory.class));
        verify(auditLogService).logAction(eq("DEALER"), eq("1"), eq("CREATE_DEALER"), isNull(), eq("DL001"), eq("accountant01"));
    }

    @Test
    @DisplayName("Tạo đại lý với mã đã tồn tại phải bị từ chối")
    void createDealer_duplicateCode_throwsException() {
        DealerRequest request = DealerRequest.builder()
                .code("dl001")
                .name("Đại lý Trùng Mã")
                .customerGroup(CustomerGroup.DAI_LY_CAP_1)
                .build();

        when(dealerRepository.existsByCodeIgnoreCase("DL001")).thenReturn(true);

        assertThatThrownBy(() -> dealerService.createDealer(request, "accountant01"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Mã đại lý 'DL001' đã tồn tại");

        verify(dealerRepository, never()).save(any());
    }

    @Test
    @DisplayName("Cập nhật đại lý thành công và kiểm tra không trùng mã với đại lý khác")
    void updateDealer_success() {
        Dealer existing = Dealer.builder()
                .id(1L)
                .code("DL001")
                .name("Đại lý Cũ")
                .customerGroup(CustomerGroup.DAI_LY_CAP_1)
                .active(true)
                .build();

        DealerRequest updateReq = DealerRequest.builder()
                .code("DL001-NEW")
                .name("Đại lý Mới")
                .customerGroup(CustomerGroup.DAI_LY_CAP_2)
                .active(true)
                .build();

        when(dealerRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(dealerRepository.existsByCodeIgnoreCaseAndIdNot("DL001-NEW", 1L)).thenReturn(false);
        when(dealerRepository.save(any(Dealer.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DealerResponse res = dealerService.updateDealer(1L, updateReq, "admin");

        assertThat(res.getCode()).isEqualTo("DL001-NEW");
        assertThat(res.getName()).isEqualTo("Đại lý Mới");
        assertThat(res.getCustomerGroup()).isEqualTo(CustomerGroup.DAI_LY_CAP_2);
        assertThat(res.getCustomerGroupDisplayName()).isEqualTo("Đại lý cấp 2");
        verify(auditLogService).logAction(eq("DEALER"), eq("1"), eq("UPDATE_DEALER"), isNull(), eq("DL001-NEW"), eq("admin"));
    }

    @Test
    @DisplayName("Cập nhật đại lý với mã trùng với đại lý khác phải ném ngoại lệ")
    void updateDealer_duplicateCodeOtherDealer_throwsException() {
        Dealer existing = Dealer.builder()
                .id(1L)
                .code("DL001")
                .name("Đại lý A")
                .customerGroup(CustomerGroup.DAI_LY_CAP_1)
                .build();

        DealerRequest updateReq = DealerRequest.builder()
                .code("DL002")
                .name("Đại lý A Đổi Mã Trùng B")
                .customerGroup(CustomerGroup.DAI_LY_CAP_1)
                .build();

        when(dealerRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(dealerRepository.existsByCodeIgnoreCaseAndIdNot("DL002", 1L)).thenReturn(true);

        assertThatThrownBy(() -> dealerService.updateDealer(1L, updateReq, "admin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Mã đại lý 'DL002' đã tồn tại");

        verify(dealerRepository, never()).save(any());
    }

    @Test
    @DisplayName("Cập nhật trạng thái giao dịch (ngừng / khôi phục giao dịch)")
    void updateStatus_success() {
        Dealer existing = Dealer.builder()
                .id(1L)
                .code("DL001")
                .name("Đại lý Test")
                .active(true)
                .build();

        when(dealerRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(dealerRepository.save(any(Dealer.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DealerResponse res = dealerService.updateStatus(1L, false, "accountant01");
        assertThat(res.getActive()).isFalse();
        verify(auditLogService).logAction(eq("DEALER"), eq("1"), eq("DEACTIVATE_DEALER"), isNull(), eq("DL001"), eq("accountant01"));

        res = dealerService.updateStatus(1L, true, "accountant01");
        assertThat(res.getActive()).isTrue();
        verify(auditLogService).logAction(eq("DEALER"), eq("1"), eq("ACTIVATE_DEALER"), isNull(), eq("DL001"), eq("accountant01"));
    }

    @Test
    @DisplayName("Đại lý đã phát sinh giao dịch (đơn hàng) thì không xoá được, ném DealerHasTransactionsException")
    void deleteDealer_withTransactions_throwsDealerHasTransactionsException() {
        Dealer existing = Dealer.builder()
                .id(1L)
                .code("DL001")
                .name("Đại lý Minh Phát")
                .build();

        when(dealerRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(orderRepository.existsByDealerId(1L)).thenReturn(true);

        assertThatThrownBy(() -> dealerService.deleteDealer(1L, "accountant01"))
                .isInstanceOf(DealerHasTransactionsException.class)
                .hasMessageContaining("Đại lý 'Đại lý Minh Phát' (DL001) đã phát sinh giao dịch không thể xoá, chỉ có thể chuyển sang trạng thái ngừng giao dịch.");

        verify(dealerRepository, never()).delete(any());
    }

    @Test
    @DisplayName("Đại lý chưa có giao dịch thì được phép xóa khỏi cơ sở dữ liệu")
    void deleteDealer_withoutTransactions_succeeds() {
        Dealer existing = Dealer.builder()
                .id(1L)
                .code("DL001")
                .name("Đại lý Mới Chưa Bán Gì")
                .build();

        when(dealerRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(orderRepository.existsByDealerId(1L)).thenReturn(false);

        dealerService.deleteDealer(1L, "accountant01");

        verify(dealerRepository).delete(existing);
        verify(auditLogService).logAction(eq("DEALER"), eq("1"), eq("DELETE_DEALER"), eq("DL001"), isNull(), eq("accountant01"));
    }

    @Test
    @DisplayName("Lấy danh sách đại lý có phân trang và bộ lọc")
    void getAllDealers_withPagingAndFilters() {
        Dealer d1 = Dealer.builder().id(1L).code("DL001").name("Đại lý 1").customerGroup(CustomerGroup.DAI_LY_CAP_1).active(true).build();
        Page<Dealer> page = new PageImpl<>(List.of(d1));

        when(dealerRepository.searchDealers(eq("DL"), eq(CustomerGroup.DAI_LY_CAP_1), isNull(), isNull(), eq(true), isNull(), any()))
                .thenReturn(page);
        when(orderRepository.existsByDealerId(1L)).thenReturn(false);

        Page<DealerResponse> result = dealerService.getAllDealers(
                "DL",
                CustomerGroup.DAI_LY_CAP_1,
                null,
                null,
                true,
                PageRequest.of(0, 10));

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).getCode()).isEqualTo("DL001");
        assertThat(result.getContent().get(0).isHasTransactions()).isFalse();
    }

    @Test
    @DisplayName("Lấy danh mục tham chiếu cho hồ sơ đại lý chỉ trả về nhân viên có vai trò kinh doanh")
    void getDealerOptions_success() {
        Territory t1 = Territory.builder().id(1L).code("HCM").name("Hồ Chí Minh").build();
        User u1 = buildSalesUser(2L, "sales1", "Sales One");
        User u2 = User.builder().id(3L).username("accountant1").fullName("Accountant").active(true)
                .roles(Set.of(Role.builder().name(ERole.ROLE_ACCOUNTANT).build())).build();

        when(territoryRepository.findAllByActiveTrueOrderByNameAsc()).thenReturn(List.of(t1));
        when(userRepository.findAll()).thenReturn(List.of(u1, u2));

        DealerOptionsResponse options = dealerService.getDealerOptions();

        assertThat(options.getCustomerGroups()).hasSize(3);
        assertThat(options.getTerritories()).hasSize(1);
        assertThat(options.getTerritories().get(0).code()).isEqualTo("HCM");
        // Chỉ lấy user active và có vai trò sales
        assertThat(options.getAssignedUsers()).hasSize(1);
        assertThat(options.getAssignedUsers().get(0).username()).isEqualTo("sales1");
    }

    @Test
    @DisplayName("Khoá giao dịch đại lý thành công: cập nhật cờ locked, lý do, và ghi nhật ký (S3-07)")
    void lockDealer_success() {
        Dealer existing = Dealer.builder()
                .id(1L)
                .code("DL001")
                .name("Đại lý Minh Phát")
                .locked(false)
                .build();

        when(dealerRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(dealerRepository.save(any(Dealer.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orderRepository.countByDealerIdAndStatusNotIn(eq(1L), any())).thenReturn(2L);

        DealerResponse res = dealerService.lockDealer(1L, "Nợ quá hạn 90 ngày, có dấu hiệu mất khả năng thanh toán", "accountant01");

        assertThat(res.getLocked()).isTrue();
        assertThat(res.getLockReason()).isEqualTo("Nợ quá hạn 90 ngày, có dấu hiệu mất khả năng thanh toán");
        assertThat(res.getLockedBy()).isEqualTo("accountant01");
        assertThat(res.getLockedAt()).isNotNull();
        assertThat(res.getInProgressOrderCount()).isEqualTo(2L);

        verify(auditLogService).logAction(eq("DEALER"), eq("1"), eq("LOCK_DEALER"), isNull(),
                contains("Khoá giao dịch đại lý: DL001"), eq("accountant01"));
    }

    @Test
    @DisplayName("Khoá giao dịch không có lý do hoặc để trắng phải ném ngoại lệ (S3-07)")
    void lockDealer_withoutReason_throwsException() {
        assertThatThrownBy(() -> dealerService.lockDealer(1L, null, "accountant01"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Bắt buộc nhập lý do khoá");

        assertThatThrownBy(() -> dealerService.lockDealer(1L, "   ", "accountant01"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Bắt buộc nhập lý do khoá");

        verify(dealerRepository, never()).save(any());
    }

    @Test
    @DisplayName("Mở khoá giao dịch cho đại lý: reset trạng thái khoá và ghi nhật ký (S3-07)")
    void unlockDealer_success() {
        Dealer existing = Dealer.builder()
                .id(1L)
                .code("DL001")
                .name("Đại lý Minh Phát")
                .locked(true)
                .lockReason("Mất khả năng thanh toán")
                .build();

        when(dealerRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(dealerRepository.save(any(Dealer.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orderRepository.countByDealerIdAndStatusNotIn(eq(1L), any())).thenReturn(0L);

        DealerResponse res = dealerService.unlockDealer(1L, "accountant01");

        assertThat(res.getLocked()).isFalse();
        assertThat(res.getLockReason()).isNull();
        assertThat(res.getLockedAt()).isNull();
        assertThat(res.getLockedBy()).isNull();

        verify(auditLogService).logAction(eq("DEALER"), eq("1"), eq("UNLOCK_DEALER"), eq("Mất khả năng thanh toán"),
                contains("Mở khoá giao dịch cho đại lý: DL001"), eq("accountant01"));
    }

    @Test
    @DisplayName("Danh sách đại lý đang hoạt động (getActiveDealers) loại trừ các đại lý đã bị khoá giao dịch (S3-07)")
    void getActiveDealers_excludesLockedDealers() {
        Dealer d1 = Dealer.builder().id(1L).code("DL001").name("Đại lý Mở").active(true).locked(false).build();
        Dealer d2 = Dealer.builder().id(2L).code("DL002").name("Đại lý Khoá").active(true).locked(true).build();

        when(dealerRepository.findByActiveTrueOrderByNameAsc()).thenReturn(List.of(d1, d2));
        when(orderRepository.countByDealerIdAndStatusNotIn(eq(1L), any())).thenReturn(0L);

        List<DealerResponse> activeDealers = dealerService.getActiveDealers();

        assertThat(activeDealers).hasSize(1);
        assertThat(activeDealers.get(0).getCode()).isEqualTo("DL001");
    }

    @Test
    @DisplayName("Lấy danh sách đơn hàng đang dở dang của đại lý (S3-07)")
    void getInProgressOrders_success() {
        when(dealerRepository.existsById(1L)).thenReturn(true);
        com.quanlybanhang.model.Order pendingOrder = com.quanlybanhang.model.Order.builder()
                .id(100L)
                .orderCode("DH-100")
                .status("PENDING_APPROVAL")
                .dealer(Dealer.builder().id(1L).code("DL001").name("Đại lý Minh Phát").locked(true).lockReason("Nợ xấu").build())
                .items(new java.util.ArrayList<>())
                .build();

        when(orderRepository.findByDealerIdAndStatusNotInOrderByCreatedAtDesc(eq(1L), any()))
                .thenReturn(List.of(pendingOrder));

        List<com.quanlybanhang.dto.response.OrderResponse> inProgress = dealerService.getInProgressOrders(1L);

        assertThat(inProgress).hasSize(1);
        assertThat(inProgress.get(0).getOrderCode()).isEqualTo("DH-100");
        assertThat(inProgress.get(0).getDealerLocked()).isTrue();
        assertThat(inProgress.get(0).getDealerLockWarning()).contains("Cảnh báo: Đại lý 'Đại lý Minh Phát'");
    }

    // ═════════════════════════════════════════════════════════════════════
    // S3-06: PHÂN CÔNG & CHUYỂN GIAO ĐỊA BÀN / BẢO MẬT DỮ LIỆU SALES
    // ═════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("[S3-06] Phân công nhân viên phụ trách chính cho một đại lý thành công")
    void assignDealer_success() {
        User oldUser = buildSalesUser(10L, "sales.old", "Nhân viên cũ");
        User newUser = buildSalesUser(20L, "sales.new", "Nhân viên mới");
        Territory territory = Territory.builder().id(1L).name("Khu vực 1").build();

        Dealer dealer = Dealer.builder()
                .id(100L)
                .code("DL100")
                .name("Đại lý An Phú")
                .assignedUser(oldUser)
                .territory(territory)
                .handoverRequired(true)
                .build();

        AssignDealerRequest req = AssignDealerRequest.builder()
                .assignedUserId(20L)
                .reason("Bàn giao phụ trách chính khu vực Quận 2")
                .build();

        when(dealerRepository.findById(100L)).thenReturn(Optional.of(dealer));
        when(userRepository.findById(20L)).thenReturn(Optional.of(newUser));
        when(dealerRepository.save(any(Dealer.class))).thenAnswer(i -> i.getArgument(0));

        DealerResponse res = dealerService.assignDealer(100L, req, "sales.manager");

        assertThat(dealer.getAssignedUser()).isEqualTo(newUser);
        assertThat(dealer.getHandoverRequired()).isFalse();

        ArgumentCaptor<DealerAssignmentHistory> historyCaptor = ArgumentCaptor.forClass(DealerAssignmentHistory.class);
        verify(dealerAssignmentHistoryRepository).save(historyCaptor.capture());
        DealerAssignmentHistory h = historyCaptor.getValue();
        assertThat(h.getFromUser()).isEqualTo(oldUser);
        assertThat(h.getToUser()).isEqualTo(newUser);
        assertThat(h.getTransferType()).isEqualTo("INDIVIDUAL_ASSIGNMENT");
        assertThat(h.getReason()).isEqualTo("Bàn giao phụ trách chính khu vực Quận 2");

        verify(auditLogService).logAction(eq("DEALER_ASSIGN"), eq("100"), eq("ASSIGN_DEALER"), eq("sales.old"), eq("sales.new"), eq("sales.manager"));
    }

    @Test
    @DisplayName("[S3-06] Phân công đại lý cho tài khoản bị khóa phải ném lỗi")
    void assignDealer_toInactiveUser_throwsException() {
        Dealer dealer = Dealer.builder().id(100L).code("DL100").name("Đại lý").build();
        User lockedUser = User.builder().id(20L).username("locked.user").active(false).build();

        when(dealerRepository.findById(100L)).thenReturn(Optional.of(dealer));
        when(userRepository.findById(20L)).thenReturn(Optional.of(lockedUser));

        AssignDealerRequest req = AssignDealerRequest.builder().assignedUserId(20L).build();

        assertThatThrownBy(() -> dealerService.assignDealer(100L, req, "manager"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("đang bị khoá");

        verify(dealerRepository, never()).save(any());
    }

    @Test
    @DisplayName("[S3-06] Chuyển giao địa bàn và đại lý hàng loạt khi nhân viên nghỉ việc thành công")
    void bulkHandover_success() {
        User fromUser = buildSalesUser(10L, "sales.retiring", "Nhân viên sắp nghỉ");
        User toUser = buildSalesUser(20L, "sales.successor", "Nhân viên tiếp quản");
        Territory territory = Territory.builder().id(1L).code("HCM-DONG").name("TP.HCM Khu Đông").build();

        Dealer d1 = Dealer.builder().id(101L).code("DL101").name("Đại lý A").assignedUser(fromUser).territory(territory).handoverRequired(true).build();
        Dealer d2 = Dealer.builder().id(102L).code("DL102").name("Đại lý B").assignedUser(fromUser).territory(territory).handoverRequired(true).build();
        List<Dealer> candidates = List.of(d1, d2);

        BulkHandoverRequest req = BulkHandoverRequest.builder()
                .fromUserId(10L)
                .toUserId(20L)
                .reason("Nhân viên nghỉ việc theo thoả thuận")
                .notes("Đã chuyển giao toàn bộ công nợ")
                .build();

        when(userRepository.findById(10L)).thenReturn(Optional.of(fromUser));
        when(userRepository.findById(20L)).thenReturn(Optional.of(toUser));
        when(dealerRepository.findByAssignedUserId(10L)).thenReturn(candidates);

        BulkHandoverResponse res = dealerService.bulkHandover(req, "manager01");

        assertThat(res.getTotalTransferred()).isEqualTo(2);
        assertThat(res.getFromUsername()).isEqualTo("sales.retiring");
        assertThat(res.getToUsername()).isEqualTo("sales.successor");
        assertThat(res.getTransferredDealerIds()).containsExactlyInAnyOrder(101L, 102L);

        // Đảm bảo cả 2 đại lý đều được đổi người phụ trách và xoá cờ handoverRequired
        assertThat(d1.getAssignedUser()).isEqualTo(toUser);
        assertThat(d1.getHandoverRequired()).isFalse();
        assertThat(d2.getAssignedUser()).isEqualTo(toUser);
        assertThat(d2.getHandoverRequired()).isFalse();

        verify(dealerRepository).saveAll(candidates);
        verify(dealerAssignmentHistoryRepository).saveAll(any());
        verify(auditLogService).logAction(eq("DEALER_HANDOVER"), eq("10->20"), eq("BULK_HANDOVER"), anyString(), eq("Nhân viên nghỉ việc theo thoả thuận"), eq("manager01"));
    }

    @Test
    @DisplayName("[S3-06] Chuyển giao hàng loạt cùng 1 nhân viên phải ném ngoại lệ")
    void bulkHandover_sameUser_throwsException() {
        BulkHandoverRequest req = BulkHandoverRequest.builder()
                .fromUserId(10L)
                .toUserId(10L)
                .reason("Lỗi")
                .build();

        assertThatThrownBy(() -> dealerService.bulkHandover(req, "manager"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Nhân viên tiếp nhận phải khác nhân viên bàn giao");
    }

    @Test
    @DisplayName("[S3-06] Nhân viên kinh doanh chỉ nhìn thấy đại lý mình phụ trách trong danh sách")
    void getAllDealers_asSalesUser_restrictedToAssignedUser() {
        User salesUser = buildSalesUser(15L, "sales01", "Nhân viên 01");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("sales01", "pass", List.of(new SimpleGrantedAuthority("ROLE_SALES")))
        );

        when(userRepository.findByUsername("sales01")).thenReturn(Optional.of(salesUser));
        when(dealerRepository.searchDealers(isNull(), isNull(), isNull(), eq(15L), isNull(), isNull(), any()))
                .thenReturn(new PageImpl<>(List.of(Dealer.builder().id(1L).code("DL01").name("Đại lý").build())));

        Page<DealerResponse> res = dealerService.getAllDealers(null, null, null, 999L, null, PageRequest.of(0, 10));

        // Phải bị ép gán sang 15L (ID của sales01) thay vì 999L do client truyền
        verify(dealerRepository).searchDealers(isNull(), isNull(), isNull(), eq(15L), isNull(), isNull(), any());
        assertThat(res.getTotalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("[S3-06] Nhân viên kinh doanh cố xem đại lý của người khác bị ném AccessDeniedException")
    void getDealerById_asSalesUser_otherDealer_throwsAccessDeniedException() {
        User salesUser = buildSalesUser(15L, "sales01", "Nhân viên 01");
        User otherSales = buildSalesUser(16L, "sales02", "Nhân viên 02");

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("sales01", "pass", List.of(new SimpleGrantedAuthority("ROLE_SALES")))
        );

        Dealer otherDealer = Dealer.builder()
                .id(200L)
                .code("DL200")
                .name("Đại lý của người khác")
                .assignedUser(otherSales)
                .build();

        when(dealerRepository.findById(200L)).thenReturn(Optional.of(otherDealer));
        when(userRepository.findByUsername("sales01")).thenReturn(Optional.of(salesUser));

        assertThatThrownBy(() -> dealerService.getDealerById(200L))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Bạn chỉ có quyền xem đại lý do mình phụ trách");

        assertThatThrownBy(() -> dealerService.getInProgressOrders(200L))
                .isInstanceOf(AccessDeniedException.class);
        verify(orderRepository, never()).findByDealerIdAndStatusNotInOrderByCreatedAtDesc(any(), any());
    }

    @Test
    @DisplayName("[S3-06 & S3-07] Danh sách đặt hàng của sales loại bỏ đại lý đang khoá")
    void getActiveDealers_asSalesUser_excludesLockedDealers() {
        User salesUser = buildSalesUser(15L, "sales01", "Nhân viên 01");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("sales01", "pass", List.of(new SimpleGrantedAuthority("ROLE_SALES"))));
        Dealer open = Dealer.builder().id(1L).code("DL01").name("Đại lý đang mở").assignedUser(salesUser).build();
        Dealer locked = Dealer.builder().id(2L).code("DL02").name("Đại lý khoá").assignedUser(salesUser).locked(true).build();
        when(userRepository.findByUsername("sales01")).thenReturn(Optional.of(salesUser));
        when(dealerRepository.findByAssignedUserIdAndActiveTrueOrderByNameAsc(15L)).thenReturn(List.of(open, locked));

        assertThat(dealerService.getActiveDealers()).extracting(DealerResponse::getId).containsExactly(1L);
    }

    @Test
    @DisplayName("[S3-06] Nhân viên kinh doanh xem đại lý do chính mình phụ trách thành công")
    void getDealerById_asSalesUser_ownDealer_success() {
        User salesUser = buildSalesUser(15L, "sales01", "Nhân viên 01");

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("sales01", "pass", List.of(new SimpleGrantedAuthority("ROLE_SALES")))
        );

        Dealer ownDealer = Dealer.builder()
                .id(200L)
                .code("DL200")
                .name("Đại lý của tôi")
                .assignedUser(salesUser)
                .build();

        when(dealerRepository.findById(200L)).thenReturn(Optional.of(ownDealer));
        when(userRepository.findByUsername("sales01")).thenReturn(Optional.of(salesUser));

        DealerResponse res = dealerService.getDealerById(200L);
        assertThat(res.getId()).isEqualTo(200L);
        assertThat(res.getCode()).isEqualTo("DL200");
    }

    @Test
    @DisplayName("[S3-06] Lấy danh sách đại lý cần bàn giao")
    void getPendingHandoverDealers_success() {
        Dealer d1 = Dealer.builder().id(1L).code("DL01").name("Đại lý Cần Bàn Giao").handoverRequired(true).build();
        when(dealerRepository.findByHandoverRequiredTrueOrderByNameAsc()).thenReturn(List.of(d1));

        List<DealerResponse> pending = dealerService.getPendingHandoverDealers();
        assertThat(pending).hasSize(1);
        assertThat(pending.get(0).getCode()).isEqualTo("DL01");
    }
}
