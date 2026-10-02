package com.quanlybanhang.service;

import com.quanlybanhang.dto.request.PriceListItemRequest;
import com.quanlybanhang.dto.request.PriceListNewVersionRequest;
import com.quanlybanhang.dto.request.PriceListRequest;
import com.quanlybanhang.dto.response.PriceListResponse;
import com.quanlybanhang.exception.PriceListLockedException;
import com.quanlybanhang.model.CustomerGroup;
import com.quanlybanhang.model.PriceList;
import com.quanlybanhang.model.PriceListItem;
import com.quanlybanhang.model.Product;
import com.quanlybanhang.repository.OrderRepository;
import com.quanlybanhang.repository.PriceListItemRepository;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PriceListService - Khai báo bảng giá theo nhóm khách hàng & thời gian hiệu lực (S2-10)")
class PriceListServiceTest {

    @Mock
    private PriceListRepository priceListRepository;

    @Mock
    private PriceListItemRepository priceListItemRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private PriceListServiceImpl priceListService;

    private Product product;
    private PriceList existingPriceList;

    @BeforeEach
    void setUp() {
        product = Product.builder()
                .id(1L)
                .code("SP-KEN330")
                .name("Bia Heineken Sleek lon 330ml")
                .unit("lon")
                .price(new BigDecimal("19000"))
                .build();

        existingPriceList = PriceList.builder()
                .id(100L)
                .code("BG-DLC1-2026")
                .name("Bảng giá Đại lý cấp 1 năm 2026")
                .customerGroup(CustomerGroup.DAI_LY_CAP_1)
                .startDate(LocalDate.of(2026, 1, 1))
                .endDate(LocalDate.of(2026, 12, 31))
                .version(1)
                .active(true)
                .hasOrders(false)
                .items(new ArrayList<>())
                .build();

        existingPriceList.addItem(PriceListItem.builder()
                .product(product)
                .sellingPrice(new BigDecimal("16500"))
                .floorPrice(new BigDecimal("15500"))
                .note("Giá đại lý C1")
                .build());
    }

    @Test
    @DisplayName("Tạo bảng giá thành công cho Đại lý cấp 1 kèm giá bán và giá sàn")
    void createPriceList_Success() {
        PriceListRequest req = PriceListRequest.builder()
                .code("BG-DLC1-2026")
                .name("Bảng giá Đại lý cấp 1 năm 2026")
                .customerGroup(CustomerGroup.DAI_LY_CAP_1)
                .startDate(LocalDate.of(2026, 1, 1))
                .endDate(LocalDate.of(2026, 12, 31))
                .active(true)
                .items(List.of(PriceListItemRequest.builder()
                        .productId(1L)
                        .sellingPrice(new BigDecimal("16500"))
                        .floorPrice(new BigDecimal("15500"))
                        .build()))
                .build();

        when(priceListRepository.existsByCode("BG-DLC1-2026")).thenReturn(false);
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(priceListRepository.save(any(PriceList.class))).thenAnswer(invocation -> {
            PriceList p = invocation.getArgument(0);
            p.setId(100L);
            return p;
        });

        PriceListResponse resp = priceListService.createPriceList(req, "sales_mgr");

        assertThat(resp).isNotNull();
        assertThat(resp.getCode()).isEqualTo("BG-DLC1-2026");
        assertThat(resp.getCustomerGroup()).isEqualTo(CustomerGroup.DAI_LY_CAP_1);
        assertThat(resp.getCustomerGroupName()).isEqualTo("Đại lý cấp 1");
        assertThat(resp.getVersion()).isEqualTo(1);
        assertThat(resp.getHasOrders()).isFalse();
        assertThat(resp.getItems()).hasSize(1);
        assertThat(resp.getItems().get(0).getSellingPrice()).isEqualByComparingTo(new BigDecimal("16500"));
        assertThat(resp.getItems().get(0).getFloorPrice()).isEqualByComparingTo(new BigDecimal("15500"));
    }

    @Test
    @DisplayName("Từ chối tạo bảng giá nếu giá sàn lớn hơn giá bán")
    void createPriceList_RejectsFloorPriceHigherThanSellingPrice() {
        PriceListRequest req = PriceListRequest.builder()
                .code("BG-DLC1-2026")
                .name("Bảng giá lỗi")
                .customerGroup(CustomerGroup.DAI_LY_CAP_1)
                .startDate(LocalDate.of(2026, 1, 1))
                .endDate(LocalDate.of(2026, 12, 31))
                .items(List.of(PriceListItemRequest.builder()
                        .productId(1L)
                        .sellingPrice(new BigDecimal("15000"))
                        .floorPrice(new BigDecimal("16000")) // Lớn hơn giá bán
                        .build()))
                .build();

        when(priceListRepository.existsByCode("BG-DLC1-2026")).thenReturn(false);
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> priceListService.createPriceList(req, "sales_mgr"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Giá sàn không được lớn hơn giá bán");
    }

    @Test
    @DisplayName("Từ chối tạo bảng giá nếu ngày bắt đầu sau ngày kết thúc")
    void createPriceList_RejectsInvalidDateRange() {
        PriceListRequest req = PriceListRequest.builder()
                .code("BG-TEST")
                .name("Bảng giá ngày lỗi")
                .customerGroup(CustomerGroup.DAI_LY_CAP_2)
                .startDate(LocalDate.of(2026, 12, 31))
                .endDate(LocalDate.of(2026, 1, 1))
                .build();

        assertThatThrownBy(() -> priceListService.createPriceList(req, "sales_mgr"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Ngày bắt đầu hiệu lực không được sau ngày kết thúc hiệu lực");
    }

    @Test
    @DisplayName("Bảng giá ĐÃ PHÁT SINH ĐƠN thì KHÔNG SỬA, chỉ tạo phiên bản mới -> Ném PriceListLockedException")
    void updatePriceList_ThrowsPriceListLockedException_WhenOrdersGenerated() {
        existingPriceList.setHasOrders(true);

        when(priceListRepository.findById(100L)).thenReturn(Optional.of(existingPriceList));

        PriceListRequest updateReq = PriceListRequest.builder()
                .code("BG-DLC1-2026")
                .name("Sửa tên bảng giá")
                .customerGroup(CustomerGroup.DAI_LY_CAP_1)
                .startDate(LocalDate.of(2026, 1, 1))
                .endDate(LocalDate.of(2026, 12, 31))
                .build();

        assertThatThrownBy(() -> priceListService.updatePriceList(100L, updateReq, "sales_mgr"))
                .isInstanceOf(PriceListLockedException.class)
                .hasMessageContaining("Bảng giá đã phát sinh đơn hàng, không được phép chỉnh sửa. Vui lòng tạo phiên bản mới.");
    }

    @Test
    @DisplayName("Bảng giá đã liên kết đơn trong DB thì KHÔNG SỬA dù flag hasOrders là false -> Ném PriceListLockedException")
    void updatePriceList_ThrowsPriceListLockedException_WhenOrderExistsInDB() {
        existingPriceList.setHasOrders(false);

        when(priceListRepository.findById(100L)).thenReturn(Optional.of(existingPriceList));
        when(orderRepository.existsByPriceListId(100L)).thenReturn(true); // Có đơn trong DB

        PriceListRequest updateReq = PriceListRequest.builder()
                .code("BG-DLC1-2026")
                .name("Sửa tên bảng giá")
                .customerGroup(CustomerGroup.DAI_LY_CAP_1)
                .startDate(LocalDate.of(2026, 1, 1))
                .endDate(LocalDate.of(2026, 12, 31))
                .build();

        assertThatThrownBy(() -> priceListService.updatePriceList(100L, updateReq, "sales_mgr"))
                .isInstanceOf(PriceListLockedException.class)
                .hasMessageContaining("Bảng giá đã phát sinh đơn hàng, không được phép chỉnh sửa. Vui lòng tạo phiên bản mới.");
    }

    @Test
    @DisplayName("Bảng giá ĐÃ PHÁT SINH ĐƠN thì KHÔNG XOÁ -> Ném PriceListLockedException")
    void deletePriceList_ThrowsPriceListLockedException_WhenOrdersExist() {
        existingPriceList.setHasOrders(true);

        when(priceListRepository.findById(100L)).thenReturn(Optional.of(existingPriceList));

        assertThatThrownBy(() -> priceListService.deletePriceList(100L, "sales_mgr"))
                .isInstanceOf(PriceListLockedException.class)
                .hasMessageContaining("Bảng giá đã phát sinh đơn hàng, không được phép xóa.");
    }

    @Test
    @DisplayName("Tạo phiên bản mới từ bảng giá cũ thành công (version tăng lên 2, hasOrders=false)")
    void createNewVersion_Success() {
        existingPriceList.setHasOrders(true);

        when(priceListRepository.findById(100L)).thenReturn(Optional.of(existingPriceList));
        when(priceListRepository.existsByCode("BG-DLC1-2026-V2")).thenReturn(false);
        when(priceListRepository.save(any(PriceList.class))).thenAnswer(invocation -> {
            PriceList p = invocation.getArgument(0);
            p.setId(101L);
            return p;
        });

        PriceListNewVersionRequest newVerReq = PriceListNewVersionRequest.builder()
                .build(); // Tự động sinh mã và sao chép toàn bộ dòng giá từ bản cha

        PriceListResponse resp = priceListService.createNewVersion(100L, newVerReq, "sales_mgr");

        assertThat(resp).isNotNull();
        assertThat(resp.getCode()).isEqualTo("BG-DLC1-2026-V2");
        assertThat(resp.getVersion()).isEqualTo(2);
        assertThat(resp.getParentPriceListId()).isEqualTo(100L);
        assertThat(resp.getHasOrders()).isFalse(); // Phiên bản mới chưa phát sinh đơn
        assertThat(resp.getItems()).hasSize(1);
        assertThat(resp.getItems().get(0).getSellingPrice()).isEqualByComparingTo(new BigDecimal("16500"));
    }

    @Test
    @DisplayName("Tìm bảng giá hiệu lực cho Đại lý cấp 1 tại ngày cụ thể")
    void getEffectivePriceList_ReturnsActivePriceList() {
        when(priceListRepository.findEffectivePriceLists(CustomerGroup.DAI_LY_CAP_1, LocalDate.of(2026, 5, 1)))
                .thenReturn(List.of(existingPriceList));

        PriceListResponse resp = priceListService.getEffectivePriceList(CustomerGroup.DAI_LY_CAP_1, LocalDate.of(2026, 5, 1));

        assertThat(resp).isNotNull();
        assertThat(resp.getCode()).isEqualTo("BG-DLC1-2026");
        assertThat(resp.getCustomerGroup()).isEqualTo(CustomerGroup.DAI_LY_CAP_1);
    }
}
