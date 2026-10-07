package com.quanlybanhang.service;

import com.quanlybanhang.dto.request.DealerRequest;
import com.quanlybanhang.dto.response.DealerOptionsResponse;
import com.quanlybanhang.dto.response.DealerResponse;
import com.quanlybanhang.exception.DealerHasTransactionsException;
import com.quanlybanhang.model.CustomerGroup;
import com.quanlybanhang.model.Dealer;
import com.quanlybanhang.model.Territory;
import com.quanlybanhang.model.User;
import com.quanlybanhang.repository.DealerRepository;
import com.quanlybanhang.repository.OrderRepository;
import com.quanlybanhang.repository.TerritoryRepository;
import com.quanlybanhang.repository.UserRepository;
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

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

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
    private AuditLogService auditLogService;

    @InjectMocks
    private DealerServiceImpl dealerService;

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
                .active(true)
                .build();

        Territory territory = Territory.builder().id(10L).code("HCM-DONG").name("TP.HCM Đông").build();
        User user = User.builder().id(20L).username("sales.user").fullName("Nguyễn Văn Bán").build();

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
        assertThat(saved.getActive()).isTrue();
        assertThat(saved.getPhone()).isEqualTo("0981234567");
        assertThat(saved.getEmail()).isEqualTo("minhphat@dealer.vn");
        assertThat(saved.getAddress()).isEqualTo("123 Lê Lợi, TP.HCM");

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getCustomerGroupDisplayName()).isEqualTo("Đại lý cấp 1");
        assertThat(response.getTerritoryName()).isEqualTo("TP.HCM Đông");
        assertThat(response.getAssignedUserFullName()).isEqualTo("Nguyễn Văn Bán");

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

        // Ngừng giao dịch
        DealerResponse res = dealerService.updateStatus(1L, false, "accountant01");
        assertThat(res.getActive()).isFalse();
        verify(auditLogService).logAction(eq("DEALER"), eq("1"), eq("DEACTIVATE_DEALER"), isNull(), eq("DL001"), eq("accountant01"));

        // Khôi phục giao dịch
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

        when(dealerRepository.searchDealers(eq("DL"), eq(CustomerGroup.DAI_LY_CAP_1), isNull(), isNull(), eq(true), any()))
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
    @DisplayName("Lấy danh mục tham chiếu cho hồ sơ đại lý")
    void getDealerOptions_success() {
        Territory t1 = Territory.builder().id(1L).code("HCM").name("Hồ Chí Minh").build();
        User u1 = User.builder().id(2L).username("sales1").fullName("Sales One").active(true).build();
        User u2 = User.builder().id(3L).username("locked").fullName("Locked User").active(false).build();

        when(territoryRepository.findAllByActiveTrueOrderByNameAsc()).thenReturn(List.of(t1));
        when(userRepository.findAll()).thenReturn(List.of(u1, u2));

        DealerOptionsResponse options = dealerService.getDealerOptions();

        assertThat(options.getCustomerGroups()).hasSize(3);
        assertThat(options.getTerritories()).hasSize(1);
        assertThat(options.getTerritories().get(0).code()).isEqualTo("HCM");
        // Chỉ lấy user active
        assertThat(options.getAssignedUsers()).hasSize(1);
        assertThat(options.getAssignedUsers().get(0).username()).isEqualTo("sales1");
    }
}

