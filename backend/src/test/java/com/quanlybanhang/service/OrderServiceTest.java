package com.quanlybanhang.service;

import com.quanlybanhang.dto.request.OrderItemRequest;
import com.quanlybanhang.dto.request.OrderRequest;
import com.quanlybanhang.dto.response.DealerDebtResponse;
import com.quanlybanhang.dto.response.OrderResponse;
import com.quanlybanhang.exception.DealerDebtOverdueException;
import com.quanlybanhang.model.Dealer;
import com.quanlybanhang.model.Order;
import com.quanlybanhang.model.Product;
import com.quanlybanhang.repository.DealerRepository;
import com.quanlybanhang.repository.OrderRepository;
import com.quanlybanhang.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderService - Đơn hàng quy về đơn vị cơ sở khi ghi sổ (S2-07)")
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private UnitConversionService unitConversionService;

    @Mock
    private DealerRepository dealerRepository;

    @Mock
    private com.quanlybanhang.repository.DealerDeliveryPointRepository deliveryPointRepository;

    @Mock
    private com.quanlybanhang.repository.PriceListRepository priceListRepository;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private VolumeDiscountPolicyService volumeDiscountPolicyService;

    @Mock
    private DealerDebtService dealerDebtService;

    @InjectMocks
    private OrderServiceImpl orderService;

    private Product biaHeineken;

    @BeforeEach
    void setUp() {
        biaHeineken = Product.builder()
                .id(10L)
                .code("SP-KEN330")
                .name("Bia Heineken Sleek lon 330ml")
                .unit("lon")
                .price(new BigDecimal("19000"))
                .build();
    }

    @Test
    @DisplayName("Đặt 2 thùng bia (hệ số 24) -> chi tiết đơn hàng ghi đúng số lượng cơ sở 48 lon")
    void createOrder_ByCarton_RecordsInBaseCans() {
        OrderRequest request = OrderRequest.builder()
                .orderCode("DH-001")
                .customerName("Đại lý Minh Tâm")
                .items(List.of(OrderItemRequest.builder()
                        .productId(10L)
                        .unit("thùng")
                        .quantity(new BigDecimal("2"))
                        .price(new BigDecimal("435000"))
                        .build()))
                .build();

        when(productRepository.findById(10L)).thenReturn(Optional.of(biaHeineken));
        when(unitConversionService.getConversionFactor(biaHeineken, "thùng")).thenReturn(new BigDecimal("24.0000"));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrderResponse response = orderService.createOrder(request, "sales01");

        assertThat(response).isNotNull();
        assertThat(response.getItems()).hasSize(1);

        // Chi tiết dòng hàng
        assertThat(response.getItems().get(0).getUnit()).isEqualTo("thùng");
        assertThat(response.getItems().get(0).getQuantity()).isEqualByComparingTo(new BigDecimal("2"));
        assertThat(response.getItems().get(0).getConversionFactor()).isEqualByComparingTo(new BigDecimal("24.0000"));
        assertThat(response.getItems().get(0).getBaseUnit()).isEqualTo("lon");
        assertThat(response.getItems().get(0).getBaseQuantity()).isEqualByComparingTo(new BigDecimal("48.0000"));

        // Tổng tiền = 2 thùng * 435,000 = 870,000
        assertThat(response.getTotalAmount()).isEqualByComparingTo(new BigDecimal("870000"));
    }

    @Test
    @DisplayName("Đại lý bị khoá giao dịch thì KHÔNG tạo được đơn mới, ném DealerLockedException (S3-07)")
    void createOrder_DealerLocked_ThrowsDealerLockedException() {
        Dealer lockedDealer = Dealer.builder()
                .id(1L)
                .code("DL001")
                .name("Đại lý Minh Phát")
                .locked(true)
                .lockReason("Nợ quá hạn khó đòi")
                .build();

        OrderRequest request = OrderRequest.builder()
                .dealerId(1L)
                .items(List.of(OrderItemRequest.builder()
                        .productId(10L)
                        .quantity(BigDecimal.TEN)
                        .build()))
                .build();

        when(dealerRepository.findById(1L)).thenReturn(Optional.of(lockedDealer));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> orderService.createOrder(request, "sales01"))
                .isInstanceOf(com.quanlybanhang.exception.DealerLockedException.class)
                .hasMessageContaining("Đại lý 'Đại lý Minh Phát' (DL001) đã bị khoá giao dịch, không thể tạo đơn hàng mới. Lý do khoá: Nợ quá hạn khó đòi");

        verify(orderRepository, never()).save(any());
    }

    @Test
    @DisplayName("Đơn đang dở của đại lý bị khoá VẪN xử lý tiếp được (phê duyệt) nhưng có cảnh báo (S3-07)")
    void approveOrder_DealerLocked_SucceedsWithWarning() {
        Dealer lockedDealer = Dealer.builder()
                .id(1L)
                .code("DL001")
                .name("Đại lý Minh Phát")
                .locked(true)
                .lockReason("Nợ quá hạn khó đòi")
                .build();

        Order pendingOrder = Order.builder()
                .id(50L)
                .orderCode("DH-050")
                .status("PENDING_APPROVAL")
                .dealer(lockedDealer)
                .requiresApproval(true)
                .items(new java.util.ArrayList<>())
                .build();

        when(orderRepository.findById(50L)).thenReturn(Optional.of(pendingOrder));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrderResponse response = orderService.approveOrder(50L, "sales_mgr");

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo("RECORDED");
        assertThat(response.getApprovedBy()).isEqualTo("sales_mgr");
        assertThat(response.getDealerLocked()).isTrue();
        assertThat(response.getDealerLockWarning()).contains("Cảnh báo: Đại lý 'Đại lý Minh Phát'");
        assertThat(response.getDealerLockWarning()).contains("Nợ quá hạn khó đòi");

        verify(auditLogService).logAction(eq("INVOICE"), eq("DH-050"), eq("APPROVE"), eq("PENDING_APPROVAL"),
                contains("[Cảnh báo: Đại lý 'Đại lý Minh Phát' đang bị khoá giao dịch"), eq("sales_mgr"));
    }

    @Test
    @DisplayName("Đơn đang dở của đại lý bị khoá VẪN huỷ được bình thường và có cảnh báo (S3-07)")
    void cancelOrder_DealerLocked_SucceedsWithWarning() {
        Dealer lockedDealer = Dealer.builder()
                .id(1L)
                .code("DL001")
                .name("Đại lý Minh Phát")
                .locked(true)
                .lockReason("Mất khả năng thanh toán")
                .build();

        Order recordedOrder = Order.builder()
                .id(51L)
                .orderCode("DH-051")
                .status("RECORDED")
                .dealer(lockedDealer)
                .items(new java.util.ArrayList<>())
                .build();

        when(orderRepository.findById(51L)).thenReturn(Optional.of(recordedOrder));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrderResponse response = orderService.cancelOrder(51L, "sales01");

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo("CANCELLED");
        assertThat(response.getDealerLocked()).isTrue();
        assertThat(response.getDealerLockWarning()).contains("Cảnh báo: Đại lý 'Đại lý Minh Phát'");
    }

    @Test
    @DisplayName("S4-02: Đại lý có nợ quá hạn vượt quá số ngày cho phép -> Chặn tạo đơn hoàn toàn")
    void createOrder_DealerHasOverdueDebt_ThrowsDealerDebtOverdueException() {
        Dealer overdueDealer = Dealer.builder()
                .id(2L)
                .code("DL-OVERDUE")
                .name("Đại lý Nợ Quá Hạn")
                .debtLimit(new BigDecimal("100000000"))
                .maxDebtDays(30)
                .active(true)
                .locked(false)
                .build();

        OrderRequest request = OrderRequest.builder()
                .dealerId(2L)
                .items(List.of(OrderItemRequest.builder()
                        .productId(10L)
                        .quantity(BigDecimal.ONE)
                        .price(new BigDecimal("19000"))
                        .build()))
                .build();

        when(dealerRepository.findById(2L)).thenReturn(Optional.of(overdueDealer));
        org.mockito.Mockito.doThrow(new DealerDebtOverdueException("DL-OVERDUE", "Đại lý Nợ Quá Hạn", 45, 30, new BigDecimal("15000000")))
                .when(dealerDebtService).validateDealerCanCreateOrder(overdueDealer);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> orderService.createOrder(request, "sales01"))
                .isInstanceOf(DealerDebtOverdueException.class)
                .hasMessageContaining("Đại lý 'Đại lý Nợ Quá Hạn' (DL-OVERDUE) có khoản nợ quá hạn 45 ngày");

        verify(orderRepository, never()).save(any());
    }

    @Test
    @DisplayName("S4-02: Tổng đơn cộng công nợ hiện tại vượt hạn mức -> Đơn bị đánh dấu PENDING_APPROVAL")
    void createOrder_ExceedsDebtLimit_SetsPendingApproval() {
        Dealer dealerWithLimit = Dealer.builder()
                .id(3L)
                .code("DL-LIMIT")
                .name("Đại lý Hạn Mức")
                .debtLimit(new BigDecimal("10000000"))
                .maxDebtDays(30)
                .active(true)
                .locked(false)
                .build();

        OrderRequest request = OrderRequest.builder()
                .orderCode("DH-DEBT-01")
                .dealerId(3L)
                .items(List.of(OrderItemRequest.builder()
                        .productId(10L)
                        .unit("thùng")
                        .quantity(new BigDecimal("2"))
                        .price(new BigDecimal("435000"))
                        .build()))
                .build();

        when(dealerRepository.findById(3L)).thenReturn(Optional.of(dealerWithLimit));
        when(productRepository.findById(10L)).thenReturn(Optional.of(biaHeineken));
        when(unitConversionService.getConversionFactor(biaHeineken, "thùng")).thenReturn(new BigDecimal("24.0000"));

        when(dealerDebtService.getDealerDebtInfo(dealerWithLimit)).thenReturn(
                DealerDebtResponse.builder()
                        .dealerId(3L)
                        .dealerCode("DL-LIMIT")
                        .dealerName("Đại lý Hạn Mức")
                        .currentDebt(new BigDecimal("9500000"))
                        .debtLimit(new BigDecimal("10000000"))
                        .remainingLimit(new BigDecimal("500000"))
                        .build());
        when(dealerDebtService.checkProjectedDebtExceedsLimit(eq(dealerWithLimit), any(BigDecimal.class), eq(new BigDecimal("9500000"))))
                .thenReturn(true);
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrderResponse response = orderService.createOrder(request, "sales01");

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo("PENDING_APPROVAL");
        assertThat(response.getRequiresApproval()).isTrue();
        assertThat(response.getApprovalReason()).contains("Vượt hạn mức công nợ");
        assertThat(response.getExceedsDebtLimit()).isTrue();
        assertThat(response.getCurrentDebt()).isEqualByComparingTo(new BigDecimal("9500000"));
        assertThat(response.getDebtLimit()).isEqualByComparingTo(new BigDecimal("10000000"));
    }

    @Test
    @DisplayName("S4-02: Tổng đơn cộng công nợ hiện tại trong hạn mức -> Đơn ghi sổ RECORDED bình thường")
    void createOrder_WithinDebtLimit_SetsRecorded() {
        Dealer dealerWithLimit = Dealer.builder()
                .id(4L)
                .code("DL-OK")
                .name("Đại lý Chuẩn")
                .debtLimit(new BigDecimal("50000000"))
                .maxDebtDays(30)
                .active(true)
                .locked(false)
                .build();

        OrderRequest request = OrderRequest.builder()
                .orderCode("DH-DEBT-02")
                .dealerId(4L)
                .items(List.of(OrderItemRequest.builder()
                        .productId(10L)
                        .unit("thùng")
                        .quantity(new BigDecimal("2"))
                        .price(new BigDecimal("435000"))
                        .build()))
                .build();

        when(dealerRepository.findById(4L)).thenReturn(Optional.of(dealerWithLimit));
        when(productRepository.findById(10L)).thenReturn(Optional.of(biaHeineken));
        when(unitConversionService.getConversionFactor(biaHeineken, "thùng")).thenReturn(new BigDecimal("24.0000"));

        when(dealerDebtService.getDealerDebtInfo(dealerWithLimit)).thenReturn(
                DealerDebtResponse.builder()
                        .dealerId(4L)
                        .dealerCode("DL-OK")
                        .dealerName("Đại lý Chuẩn")
                        .currentDebt(new BigDecimal("5000000"))
                        .debtLimit(new BigDecimal("50000000"))
                        .remainingLimit(new BigDecimal("45000000"))
                        .build());
        when(dealerDebtService.checkProjectedDebtExceedsLimit(eq(dealerWithLimit), any(BigDecimal.class), eq(new BigDecimal("5000000"))))
                .thenReturn(false);
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrderResponse response = orderService.createOrder(request, "sales01");

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo("RECORDED");
        assertThat(response.getRequiresApproval()).isFalse();
        assertThat(response.getExceedsDebtLimit()).isFalse();
        assertThat(response.getCurrentDebt()).isEqualByComparingTo(new BigDecimal("5000000"));
        assertThat(response.getDebtLimit()).isEqualByComparingTo(new BigDecimal("50000000"));
    }
}
