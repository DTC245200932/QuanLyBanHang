package com.quanlybanhang.service;

import com.quanlybanhang.dto.request.OrderItemRequest;
import com.quanlybanhang.dto.request.OrderRequest;
import com.quanlybanhang.dto.response.OrderResponse;
import com.quanlybanhang.model.*;
import com.quanlybanhang.repository.DealerRepository;
import com.quanlybanhang.repository.DealerDeliveryPointRepository;
import com.quanlybanhang.repository.OrderRepository;
import com.quanlybanhang.repository.PriceListRepository;
import com.quanlybanhang.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderService - Tích hợp Chiết khấu sản lượng khi tạo đơn hàng (S3-01)")
class OrderVolumeDiscountIntegrationTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private DealerRepository dealerRepository;

    @Mock
    private DealerDeliveryPointRepository deliveryPointRepository;

    @Mock
    private PriceListRepository priceListRepository;

    @Mock
    private UnitConversionService unitConversionService;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private VolumeDiscountPolicyService volumeDiscountPolicyService;

    @Mock
    private DealerDebtService dealerDebtService;

    @InjectMocks
    private OrderServiceImpl orderService;

    private Product biaHeineken;
    private Dealer dealer;

    @BeforeEach
    void setUp() {
        biaHeineken = Product.builder()
                .id(10L)
                .code("SP-KEN330")
                .name("Bia Heineken Sleek lon 330ml")
                .unit("lon")
                .price(new BigDecimal("16500"))
                .build();

        dealer = Dealer.builder()
                .id(1L)
                .name("Đại lý Cấp 1 Minh Phát")
                .customerGroup(CustomerGroup.DAI_LY_CAP_1)
                .build();
    }

    @Test
    @DisplayName("Tạo đơn hàng tự động áp dụng chiết khấu sản lượng và lưu thông tin chính sách")
    void testCreateOrder_AppliesVolumeDiscount() {
        // Đặt 5 thùng bia Heineken (5 * 24 = 120 lon cơ sở)
        // Đơn giá thùng = 16,500 * 24 = 396,000 đ
        // Tổng tiền hàng trước chiết khấu = 5 * 396,000 = 1,980,000 đ
        // Chiết khấu sản lượng 5% (do đạt bậc 120 lon) = 99,000 đ
        // Tổng thanh toán sau chiết khấu = 1,881,000 đ

        OrderRequest request = OrderRequest.builder()
                .orderCode("DH-TEST-CK-01")
                .dealerId(1L)
                .items(Collections.singletonList(
                        OrderItemRequest.builder()
                                .productId(10L)
                                .unit("thùng")
                                .quantity(new BigDecimal("5"))
                                .build()
                ))
                .build();

        when(orderRepository.existsByOrderCode("DH-TEST-CK-01")).thenReturn(false);
        when(dealerRepository.findById(1L)).thenReturn(Optional.of(dealer));
        when(productRepository.findById(10L)).thenReturn(Optional.of(biaHeineken));
        when(unitConversionService.getConversionFactor(biaHeineken, "thùng")).thenReturn(new BigDecimal("24.0"));

        // Mock kết quả tính chiết khấu từ VolumeDiscountPolicyService
        Map<Integer, VolumeDiscountPolicyService.ItemDiscountResult> itemResults = new HashMap<>();
        itemResults.put(0, VolumeDiscountPolicyService.ItemDiscountResult.builder()
                .discountAmount(new BigDecimal("99000.00"))
                .discountPercent(new BigDecimal("5.0"))
                .appliedPolicyId(100L)
                .appliedPolicyCode("CK-KEN-2026")
                .appliedPolicyName("Chiết khấu sản lượng bia Heineken")
                .bestPolicyReason("Áp dụng chính sách 'CK-KEN-2026': giảm 5%")
                .build());

        VolumeDiscountPolicyService.OrderDiscountSummary discountSummary = VolumeDiscountPolicyService.OrderDiscountSummary.builder()
                .subtotalAmount(new BigDecimal("1980000.00"))
                .discountAmount(new BigDecimal("99000.00"))
                .totalAmount(new BigDecimal("1881000.00"))
                .itemResults(itemResults)
                .build();

        when(volumeDiscountPolicyService.calculateOrderDiscount(any(), eq(CustomerGroup.DAI_LY_CAP_1), any(LocalDate.class)))
                .thenReturn(discountSummary);

        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order o = invocation.getArgument(0);
            o.setId(999L);
            return o;
        });

        OrderResponse response = orderService.createOrder(request, "sales01");

        assertThat(response).isNotNull();
        assertThat(response.getSubtotalAmount()).isEqualByComparingTo("1980000.00");
        assertThat(response.getDiscountAmount()).isEqualByComparingTo("99000.00");
        assertThat(response.getTotalAmount()).isEqualByComparingTo("1881000.00");

        assertThat(response.getItems()).hasSize(1);
        var itemResp = response.getItems().get(0);
        assertThat(itemResp.getSubtotalAmount()).isEqualByComparingTo("1980000.00");
        assertThat(itemResp.getDiscountAmount()).isEqualByComparingTo("99000.00");
        assertThat(itemResp.getDiscountPercent()).isEqualByComparingTo("5.0");
        assertThat(itemResp.getAppliedPolicyCode()).isEqualTo("CK-KEN-2026");
        assertThat(itemResp.getTotalAmount()).isEqualByComparingTo("1881000.00");
    }
}

