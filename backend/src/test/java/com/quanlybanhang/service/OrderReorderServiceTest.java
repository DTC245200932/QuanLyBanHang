package com.quanlybanhang.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quanlybanhang.dto.request.DealerPortalReorderItemRequest;
import com.quanlybanhang.dto.request.DealerPortalReorderRequest;
import com.quanlybanhang.dto.response.DealerPortalReorderPreviewResponse;
import com.quanlybanhang.dto.response.DealerPortalReorderResponse;
import com.quanlybanhang.dto.response.UnitConversionResponse;
import com.quanlybanhang.exception.DealerLockedException;
import com.quanlybanhang.model.*;
import com.quanlybanhang.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("S5-02: Đặt lại đơn hàng cũ dành cho Đại lý (Reorder)")
class OrderReorderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private UnitConversionService unitConversionService;

    @Mock
    private DealerRepository dealerRepository;

    @Mock
    private DealerDeliveryPointRepository deliveryPointRepository;

    @Mock
    private PriceListRepository priceListRepository;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private VolumeDiscountPolicyService volumeDiscountPolicyService;

    @Mock
    private DealerDebtService dealerDebtService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private OrderStatusHistoryRepository orderStatusHistoryRepository;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private OrderServiceImpl orderService;

    private Dealer dealer;
    private User dealerUser;
    private Product productActive;
    private Product productDiscontinued;
    private Product productNoPrice;
    private Order sourceOrder;
    private PriceList priceList;

    @BeforeEach
    void setUp() {
        Warehouse warehouse = Warehouse.builder()
                .id(1L)
                .code("WH01")
                .name("Kho Tổng")
                .active(true)
                .build();

        dealer = Dealer.builder()
                .id(1L)
                .code("DL01")
                .name("Đại lý Minh Phát")
                .customerGroup(CustomerGroup.DAI_LY_CAP_1)
                .warehouse(warehouse)
                .active(true)
                .locked(false)
                .build();

        dealerUser = User.builder()
                .id(10L)
                .username("dealer01")
                .fullName("Đại lý Minh Phát")
                .dealer(dealer)
                .active(true)
                .build();

        productActive = Product.builder()
                .id(100L)
                .code("SP-ACTIVE")
                .name("Bia Heineken Lon 330ml")
                .unit("lon")
                .price(new BigDecimal("18000"))
                .active(true)
                .build();

        productDiscontinued = Product.builder()
                .id(101L)
                .code("SP-DISCONTINUED")
                .name("Nước ngọt cũ ngừng bán")
                .unit("chai")
                .price(new BigDecimal("10000"))
                .active(false) // Ngừng kinh doanh
                .build();

        productNoPrice = Product.builder()
                .id(102L)
                .code("SP-NOPRICE")
                .name("Sản phẩm hết hiệu lực giá")
                .unit("hộp")
                .price(new BigDecimal("50000"))
                .active(true)
                .build();

        OrderItem itemActive = OrderItem.builder()
                .id(1001L)
                .product(productActive)
                .unit("thùng")
                .quantity(new BigDecimal("5"))
                .conversionFactor(new BigDecimal("24"))
                .baseUnit("lon")
                .baseQuantity(new BigDecimal("120"))
                .price(new BigDecimal("400000")) // Giá cũ
                .totalAmount(new BigDecimal("2000000"))
                .build();

        OrderItem itemDiscontinued = OrderItem.builder()
                .id(1002L)
                .product(productDiscontinued)
                .unit("két")
                .quantity(new BigDecimal("2"))
                .conversionFactor(new BigDecimal("20"))
                .baseUnit("chai")
                .baseQuantity(new BigDecimal("40"))
                .price(new BigDecimal("180000"))
                .totalAmount(new BigDecimal("360000"))
                .build();

        OrderItem itemNoPrice = OrderItem.builder()
                .id(1003L)
                .product(productNoPrice)
                .unit("hộp")
                .quantity(new BigDecimal("3"))
                .conversionFactor(BigDecimal.ONE)
                .baseUnit("hộp")
                .baseQuantity(new BigDecimal("3"))
                .price(new BigDecimal("50000"))
                .totalAmount(new BigDecimal("150000"))
                .build();

        sourceOrder = Order.builder()
                .id(50L)
                .orderCode("DH-SOURCE-001")
                .dealer(dealer)
                .customerName("Đại lý Minh Phát")
                .status("DELIVERED")
                .createdBy("dealer01")
                .items(new ArrayList<>(List.of(itemActive, itemDiscontinued, itemNoPrice)))
                .build();

        itemActive.setOrder(sourceOrder);
        itemDiscontinued.setOrder(sourceOrder);
        itemNoPrice.setOrder(sourceOrder);

        // Bảng giá hiệu lực hiện hành: chỉ chứa productActive với giá mới 432.000 / thùng (18.000 / lon)
        PriceListItem pliActive = PriceListItem.builder()
                .product(productActive)
                .sellingPrice(new BigDecimal("19000")) // Giá mới hiện hành (19.000/lon -> 456.000/thùng)
                .floorPrice(new BigDecimal("16000"))
                .build();

        priceList = PriceList.builder()
                .id(1L)
                .code("BG-CURRENT")
                .name("Bảng giá hiện hành")
                .customerGroup(CustomerGroup.DAI_LY_CAP_1)
                .startDate(LocalDate.now().minusDays(5))
                .endDate(LocalDate.now().plusDays(30))
                .active(true)
                .items(List.of(pliActive))
                .build();

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "dealer01", "pass",
                        List.of(new SimpleGrantedAuthority("ROLE_DEALER"), new SimpleGrantedAuthority("ORDER_VIEW"), new SimpleGrantedAuthority("ORDER_CREATE"))
                )
        );
        when(userRepository.findByUsername("dealer01")).thenReturn(Optional.of(dealerUser));
    }

    @Test
    @DisplayName("S5-02 Preview: Phân loại mặt hàng còn kinh doanh và ngừng kinh doanh, tính lại giá hiện hành")
    void getPortalReorderPreview_Success() {
        when(orderRepository.findById(50L)).thenReturn(Optional.of(sourceOrder));
        when(priceListRepository.findEffectivePriceLists(eq(CustomerGroup.DAI_LY_CAP_1), any(LocalDate.class)))
                .thenReturn(List.of(priceList));
        when(unitConversionService.getConversionFactor(productActive, "thùng")).thenReturn(new BigDecimal("24"));
        when(unitConversionService.getConversionsByProductId(100L)).thenReturn(List.of(
                UnitConversionResponse.builder().unitName("thùng").conversionFactor(new BigDecimal("24")).isBaseUnit(false).build()
        ));

        DealerPortalReorderPreviewResponse preview = orderService.getPortalReorderPreview(50L, "dealer01");

        assertThat(preview).isNotNull();
        assertThat(preview.getSourceOrderId()).isEqualTo(50L);
        assertThat(preview.isCanReorder()).isTrue();

        // 1 sản phẩm khả dụng: productActive
        assertThat(preview.getAvailableItems()).hasSize(1);
        var avail = preview.getAvailableItems().get(0);
        assertThat(avail.getProductCode()).isEqualTo("SP-ACTIVE");
        assertThat(avail.getUnit()).isEqualTo("thùng");
        assertThat(avail.getQuantity()).isEqualByComparingTo("5");
        assertThat(avail.getOldPrice()).isEqualByComparingTo("400000"); // Giá cũ
        assertThat(avail.getCurrentPrice()).isEqualByComparingTo("456000"); // 19000 * 24 = 456000
        assertThat(avail.getSubtotalAmount()).isEqualByComparingTo("2280000"); // 456000 * 5 = 2280000

        // 2 sản phẩm bị loại: 1 discontinued và 1 không có trong bảng giá
        assertThat(preview.getDiscontinuedItems()).hasSize(2);
        assertThat(preview.getDiscontinuedItems()).extracting("productCode")
                .containsExactlyInAnyOrder("SP-DISCONTINUED", "SP-NOPRICE");

        // Có thông báo cảnh báo rõ ràng
        assertThat(preview.getWarnings()).hasSize(2);
        assertThat(preview.getWarnings().stream().anyMatch(w -> w.contains("ngừng kinh doanh"))).isTrue();
        assertThat(preview.getWarnings().stream().anyMatch(w -> w.contains("không có trong bảng giá"))).isTrue();
    }

    @Test
    @DisplayName("S5-02 Preview: Đại lý bị khoá giao dịch -> ném DealerLockedException")
    void getPortalReorderPreview_DealerLocked_ThrowsException() {
        dealer.setLocked(true);
        dealer.setLockReason("Quá hạn nợ 45 ngày");
        when(orderRepository.findById(50L)).thenReturn(Optional.of(sourceOrder));

        assertThatThrownBy(() -> orderService.getPortalReorderPreview(50L, "dealer01"))
                .isInstanceOf(DealerLockedException.class);
    }

    @Test
    @DisplayName("S5-02 Preview: Đại lý khác truy cập đơn không thuộc về mình -> 403 AccessDeniedException")
    void getPortalReorderPreview_CrossDealerAccess_ThrowsAccessDenied() {
        Dealer otherDealer = Dealer.builder().id(999L).code("DL999").name("Đại lý Khác").build();
        User stranger = User.builder().id(99L).username("stranger").dealer(otherDealer).build();
        when(userRepository.findByUsername("dealer01")).thenReturn(Optional.of(stranger));
        when(orderRepository.findById(50L)).thenReturn(Optional.of(sourceOrder));

        assertThatThrownBy(() -> orderService.getPortalReorderPreview(50L, "dealer01"))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("S5-02 Preview: Khi tất cả mặt hàng đều ngừng kinh doanh -> canReorder = false")
    void getPortalReorderPreview_AllDiscontinued_CanReorderFalse() {
        // Chỉ có item ngừng kinh doanh
        sourceOrder.getItems().clear();
        sourceOrder.getItems().add(OrderItem.builder().id(1002L).product(productDiscontinued).quantity(BigDecimal.ONE).unit("chai").build());

        when(orderRepository.findById(50L)).thenReturn(Optional.of(sourceOrder));
        when(priceListRepository.findEffectivePriceLists(eq(CustomerGroup.DAI_LY_CAP_1), any(LocalDate.class)))
                .thenReturn(List.of(priceList));

        DealerPortalReorderPreviewResponse preview = orderService.getPortalReorderPreview(50L, "dealer01");

        assertThat(preview.isCanReorder()).isFalse();
        assertThat(preview.getAvailableItems()).isEmpty();
        assertThat(preview.getDiscontinuedItems()).hasSize(1);
    }

    @Test
    @DisplayName("S5-02 Reorder: Đặt lại toàn bộ (request items rỗng) -> tạo đơn mới PENDING_APPROVAL, loại bỏ hàng ngừng kinh doanh")
    void createPortalReorder_FullReorder_Success() {
        when(orderRepository.findById(50L)).thenReturn(Optional.of(sourceOrder));
        when(priceListRepository.findEffectivePriceLists(eq(CustomerGroup.DAI_LY_CAP_1), any(LocalDate.class)))
                .thenReturn(List.of(priceList));
        when(productRepository.findById(100L)).thenReturn(Optional.of(productActive));
        when(unitConversionService.getConversionFactor(productActive, "thùng")).thenReturn(new BigDecimal("24"));

        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            o.setId(888L);
            return o;
        });

        DealerPortalReorderResponse response = orderService.createPortalReorder(50L, null, "dealer01");

        assertThat(response).isNotNull();
        assertThat(response.getOrder()).isNotNull();
        assertThat(response.getOrder().getStatus()).isEqualTo("PENDING_APPROVAL");
        assertThat(response.getOrder().getRequiresApproval()).isTrue();

        // Kiểm tra loại bỏ mặt hàng ngừng kinh doanh / hết bảng giá
        assertThat(response.getExcludedItems()).hasSize(2);
        assertThat(response.getWarnings()).hasSize(2);

        // Ghi audit log
        verify(auditLogService, atLeastOnce()).logAction(eq("INVOICE"), any(), eq("REORDER_FROM"), eq("DH-SOURCE-001"), any(), eq("dealer01"));
    }

    @Test
    @DisplayName("S5-02 Reorder: Đặt lại một phần dòng hàng (partial reorder) -> chỉ đặt các dòng được chọn")
    void createPortalReorder_PartialReorder_Success() {
        when(orderRepository.findById(50L)).thenReturn(Optional.of(sourceOrder));
        when(priceListRepository.findEffectivePriceLists(eq(CustomerGroup.DAI_LY_CAP_1), any(LocalDate.class)))
                .thenReturn(List.of(priceList));
        when(productRepository.findById(100L)).thenReturn(Optional.of(productActive));
        when(unitConversionService.getConversionFactor(productActive, "thùng")).thenReturn(new BigDecimal("24"));

        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            o.setId(889L);
            return o;
        });

        DealerPortalReorderRequest request = DealerPortalReorderRequest.builder()
                .note("Đặt lại 3 thùng giao sáng thứ 2")
                .items(List.of(
                        DealerPortalReorderItemRequest.builder()
                                .productId(100L)
                                .unit("thùng")
                                .quantity(new BigDecimal("3"))
                                .build()
                ))
                .build();

        DealerPortalReorderResponse response = orderService.createPortalReorder(50L, request, "dealer01");

        assertThat(response).isNotNull();
        assertThat(response.getOrder()).isNotNull();
        assertThat(response.getOrder().getStatus()).isEqualTo("PENDING_APPROVAL");
        assertThat(response.getOrder().getItems()).hasSize(1);
        assertThat(response.getOrder().getItems().get(0).getQuantity()).isEqualByComparingTo("3");
    }

    @Test
    @DisplayName("S5-02 Reorder: Tất cả dòng hàng được chọn đều ngừng kinh doanh -> ném IllegalArgumentException")
    void createPortalReorder_AllSelectedDiscontinued_ThrowsException() {
        when(orderRepository.findById(50L)).thenReturn(Optional.of(sourceOrder));
        when(priceListRepository.findEffectivePriceLists(eq(CustomerGroup.DAI_LY_CAP_1), any(LocalDate.class)))
                .thenReturn(List.of(priceList));
        when(productRepository.findById(101L)).thenReturn(Optional.of(productDiscontinued));

        DealerPortalReorderRequest request = DealerPortalReorderRequest.builder()
                .items(List.of(
                        DealerPortalReorderItemRequest.builder()
                                .productId(101L)
                                .unit("két")
                                .quantity(BigDecimal.ONE)
                                .build()
                ))
                .build();

        assertThatThrownBy(() -> orderService.createPortalReorder(50L, request, "dealer01"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ngừng kinh doanh");
    }
}
