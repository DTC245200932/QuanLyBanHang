package com.quanlybanhang.service;

import com.quanlybanhang.dto.request.OrderItemRequest;
import com.quanlybanhang.dto.request.OrderRequest;
import com.quanlybanhang.dto.response.OrderResponse;
import com.quanlybanhang.model.*;
import com.quanlybanhang.repository.DealerRepository;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderService - Tự động nhận đúng giá theo nhóm đại lý & Kiểm soát giá sàn (S2-10)")
class OrderPriceListTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private DealerRepository dealerRepository;

    @Mock
    private PriceListRepository priceListRepository;

    @Mock
    private UnitConversionService unitConversionService;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private com.quanlybanhang.repository.DealerDeliveryPointRepository deliveryPointRepository;

    @InjectMocks
    private OrderServiceImpl orderService;

    private Product biaHeineken;
    private Dealer dealerCap1;
    private Dealer dealerCap2;
    private PriceList priceListDl1;
    private PriceList priceListDl2;

    @BeforeEach
    void setUp() {
        biaHeineken = Product.builder()
                .id(10L)
                .code("SP-KEN330")
                .name("Bia Heineken Sleek lon 330ml")
                .unit("lon")
                .price(new BigDecimal("19000"))
                .build();

        dealerCap1 = Dealer.builder()
                .id(1L)
                .name("Đại lý Cấp 1 Minh Phát")
                .customerGroup(CustomerGroup.DAI_LY_CAP_1)
                .build();

        dealerCap2 = Dealer.builder()
                .id(2L)
                .name("Đại lý Cấp 2 Tân Bình")
                .customerGroup(CustomerGroup.DAI_LY_CAP_2)
                .build();

        // Bảng giá Đại lý cấp 1: Giá bán 16,500 / lon, Giá sàn 15,500 / lon
        priceListDl1 = PriceList.builder()
                .id(101L)
                .code("BG-DLC1-2026")
                .name("Bảng giá Đại lý cấp 1")
                .customerGroup(CustomerGroup.DAI_LY_CAP_1)
                .startDate(LocalDate.of(2026, 1, 1))
                .endDate(LocalDate.of(2026, 12, 31))
                .version(1)
                .active(true)
                .hasOrders(false)
                .items(new ArrayList<>())
                .build();
        priceListDl1.addItem(PriceListItem.builder()
                .product(biaHeineken)
                .sellingPrice(new BigDecimal("16500"))
                .floorPrice(new BigDecimal("15500"))
                .build());

        // Bảng giá Đại lý cấp 2: Giá bán 17,500 / lon, Giá sàn 16,500 / lon
        priceListDl2 = PriceList.builder()
                .id(102L)
                .code("BG-DLC2-2026")
                .name("Bảng giá Đại lý cấp 2")
                .customerGroup(CustomerGroup.DAI_LY_CAP_2)
                .startDate(LocalDate.of(2026, 1, 1))
                .endDate(LocalDate.of(2026, 12, 31))
                .version(1)
                .active(true)
                .hasOrders(false)
                .items(new ArrayList<>())
                .build();
        priceListDl2.addItem(PriceListItem.builder()
                .product(biaHeineken)
                .sellingPrice(new BigDecimal("17500"))
                .floorPrice(new BigDecimal("16500"))
                .build());
    }

    @Test
    @DisplayName("Đại lý cấp 1 tự động nhận đúng giá bán 16,500đ/lon theo bảng giá cấp 1")
    void createOrder_DealerCap1_AutoReceivesPriceListCap1() {
        OrderRequest request = OrderRequest.builder()
                .dealerId(1L)
                .items(List.of(OrderItemRequest.builder()
                        .productId(10L)
                        .unit("lon")
                        .quantity(new BigDecimal("100"))
                        // không truyền price -> tự động lấy từ bảng giá cấp 1
                        .build()))
                .build();

        when(dealerRepository.findById(1L)).thenReturn(Optional.of(dealerCap1));
        when(priceListRepository.findEffectivePriceLists(eq(CustomerGroup.DAI_LY_CAP_1), any(LocalDate.class)))
                .thenReturn(List.of(priceListDl1));
        when(productRepository.findById(10L)).thenReturn(Optional.of(biaHeineken));
        when(unitConversionService.getConversionFactor(biaHeineken, "lon")).thenReturn(BigDecimal.ONE);
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrderResponse response = orderService.createOrder(request, "sales01");

        assertThat(response).isNotNull();
        assertThat(response.getCustomerGroup()).isEqualTo(CustomerGroup.DAI_LY_CAP_1);
        assertThat(response.getPriceListCode()).isEqualTo("BG-DLC1-2026");
        assertThat(response.getStatus()).isEqualTo("RECORDED");
        assertThat(response.getRequiresApproval()).isFalse();

        // Kiểm tra giá tự động nhận: 16,500đ/lon
        assertThat(response.getItems().get(0).getPrice()).isEqualByComparingTo(new BigDecimal("16500"));
        assertThat(response.getItems().get(0).getFloorPrice()).isEqualByComparingTo(new BigDecimal("15500"));
        assertThat(response.getTotalAmount()).isEqualByComparingTo(new BigDecimal("1650000")); // 100 * 16,500

        // Kiểm tra bảng giá được đánh dấu đã phát sinh đơn (hasOrders = true)
        assertThat(priceListDl1.getHasOrders()).isTrue();
        verify(priceListRepository).save(priceListDl1);
    }

    @Test
    @DisplayName("Đại lý cấp 2 tự động nhận đúng giá bán 17,500đ/lon theo bảng giá cấp 2")
    void createOrder_DealerCap2_AutoReceivesPriceListCap2() {
        OrderRequest request = OrderRequest.builder()
                .dealerId(2L)
                .items(List.of(OrderItemRequest.builder()
                        .productId(10L)
                        .unit("lon")
                        .quantity(new BigDecimal("100"))
                        // không truyền price -> tự động lấy từ bảng giá cấp 2
                        .build()))
                .build();

        when(dealerRepository.findById(2L)).thenReturn(Optional.of(dealerCap2));
        when(priceListRepository.findEffectivePriceLists(eq(CustomerGroup.DAI_LY_CAP_2), any(LocalDate.class)))
                .thenReturn(List.of(priceListDl2));
        when(productRepository.findById(10L)).thenReturn(Optional.of(biaHeineken));
        when(unitConversionService.getConversionFactor(biaHeineken, "lon")).thenReturn(BigDecimal.ONE);
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrderResponse response = orderService.createOrder(request, "sales01");

        assertThat(response).isNotNull();
        assertThat(response.getCustomerGroup()).isEqualTo(CustomerGroup.DAI_LY_CAP_2);
        assertThat(response.getPriceListCode()).isEqualTo("BG-DLC2-2026");

        // Kiểm tra giá tự động nhận: 17,500đ/lon
        assertThat(response.getItems().get(0).getPrice()).isEqualByComparingTo(new BigDecimal("17500"));
        assertThat(response.getItems().get(0).getFloorPrice()).isEqualByComparingTo(new BigDecimal("16500"));
        assertThat(response.getTotalAmount()).isEqualByComparingTo(new BigDecimal("1750000")); // 100 * 17,500
    }

    @Test
    @DisplayName("Bán DƯỚI GIÁ SÀN -> Đơn hàng chuyển sang PENDING_APPROVAL và cần Quản lý duyệt")
    void createOrder_BelowFloorPrice_RequiresApproval() {
        // Giá sàn Đại lý cấp 1 là 15,500đ; nhân viên nhập giá bán 14,000đ (dưới giá sàn)
        OrderRequest request = OrderRequest.builder()
                .customerGroup(CustomerGroup.DAI_LY_CAP_1)
                .customerName("Đại lý Cấp 1 Minh Phát")
                .items(List.of(OrderItemRequest.builder()
                        .productId(10L)
                        .unit("lon")
                        .quantity(new BigDecimal("50"))
                        .price(new BigDecimal("14000")) // < Giá sàn 15,500
                        .build()))
                .build();

        when(priceListRepository.findEffectivePriceLists(eq(CustomerGroup.DAI_LY_CAP_1), any(LocalDate.class)))
                .thenReturn(List.of(priceListDl1));
        when(productRepository.findById(10L)).thenReturn(Optional.of(biaHeineken));
        when(unitConversionService.getConversionFactor(biaHeineken, "lon")).thenReturn(BigDecimal.ONE);
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrderResponse response = orderService.createOrder(request, "sales01");

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo("PENDING_APPROVAL");
        assertThat(response.getRequiresApproval()).isTrue();
        assertThat(response.getApprovalReason()).contains("bán giá 14000 < giá sàn 15500");
        assertThat(response.getItems().get(0).getBelowFloorPrice()).isTrue();
    }

    @Test
    @DisplayName("Đặt theo THÙNG (hệ số 24) bán dưới giá sàn quy đổi -> Chờ duyệt")
    void createOrder_ByCarton_BelowFloorPrice_RequiresApproval() {
        // Giá sàn 1 lon = 15,500 -> Giá sàn 1 thùng (24 lon) = 15,500 * 24 = 372,000
        // Nhân viên bán giá 360,000 / thùng (< 372,000)
        OrderRequest request = OrderRequest.builder()
                .customerGroup(CustomerGroup.DAI_LY_CAP_1)
                .items(List.of(OrderItemRequest.builder()
                        .productId(10L)
                        .unit("thùng")
                        .quantity(new BigDecimal("5"))
                        .price(new BigDecimal("360000")) // < 372,000
                        .build()))
                .build();

        when(priceListRepository.findEffectivePriceLists(eq(CustomerGroup.DAI_LY_CAP_1), any(LocalDate.class)))
                .thenReturn(List.of(priceListDl1));
        when(productRepository.findById(10L)).thenReturn(Optional.of(biaHeineken));
        when(unitConversionService.getConversionFactor(biaHeineken, "thùng")).thenReturn(new BigDecimal("24.0000"));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrderResponse response = orderService.createOrder(request, "sales01");

        assertThat(response.getStatus()).isEqualTo("PENDING_APPROVAL");
        assertThat(response.getRequiresApproval()).isTrue();
        assertThat(response.getItems().get(0).getFloorPrice()).isEqualByComparingTo(new BigDecimal("372000.0000"));
        assertThat(response.getItems().get(0).getBelowFloorPrice()).isTrue();
    }

    @Test
    @DisplayName("Quản lý kinh doanh phê duyệt đơn hàng bán dưới giá sàn thành công -> RECORDED")
    void approveOrder_Success() {
        Order pendingOrder = Order.builder()
                .id(200L)
                .orderCode("DH-BELOW-FLOOR")
                .status("PENDING_APPROVAL")
                .requiresApproval(true)
                .approvalReason("Bán dưới giá sàn")
                .build();

        when(orderRepository.findById(200L)).thenReturn(Optional.of(pendingOrder));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrderResponse resp = orderService.approveOrder(200L, "sales.manager");

        assertThat(resp.getStatus()).isEqualTo("RECORDED");
        assertThat(resp.getApprovedBy()).isEqualTo("sales.manager");
        assertThat(resp.getApprovedAt()).isNotNull();
    }

    @Test
    @DisplayName("Quản lý kinh doanh từ chối đơn hàng bán dưới giá sàn -> REJECTED")
    void rejectOrder_Success() {
        Order pendingOrder = Order.builder()
                .id(200L)
                .orderCode("DH-BELOW-FLOOR")
                .status("PENDING_APPROVAL")
                .requiresApproval(true)
                .approvalReason("Bán dưới giá sàn")
                .build();

        when(orderRepository.findById(200L)).thenReturn(Optional.of(pendingOrder));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrderResponse resp = orderService.rejectOrder(200L, "Lỗ quá mức cho phép", "sales.manager");

        assertThat(resp.getStatus()).isEqualTo("REJECTED");
        assertThat(resp.getApprovedBy()).isEqualTo("sales.manager");
        assertThat(resp.getNote()).contains("Lý do từ chối: Lỗ quá mức cho phép");
    }
}
