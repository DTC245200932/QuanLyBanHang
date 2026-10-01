package com.quanlybanhang.service;

import com.quanlybanhang.dto.request.WarehouseVoucherItemRequest;
import com.quanlybanhang.dto.request.WarehouseVoucherRequest;
import com.quanlybanhang.dto.response.WarehouseInventoryResponse;
import com.quanlybanhang.dto.response.WarehouseVoucherResponse;
import com.quanlybanhang.model.*;
import com.quanlybanhang.repository.*;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("WarehouseService - Nhập xuất kho theo thùng/lốc, sổ sách ghi theo lon (S2-07)")
class WarehouseServiceTest {

    @Mock
    private WarehouseRepository warehouseRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private UnitConversionRepository unitConversionRepository;

    @Mock
    private UnitConversionService unitConversionService;

    @Mock
    private WarehouseVoucherRepository voucherRepository;

    @Mock
    private WarehouseInventoryRepository inventoryRepository;

    @InjectMocks
    private WarehouseServiceImpl warehouseService;

    private Warehouse khoHcm;
    private Product biaHeineken;

    @BeforeEach
    void setUp() {
        khoHcm = Warehouse.builder().id(1L).code("KHO-HCM").name("Kho HCM").build();
        biaHeineken = Product.builder()
                .id(10L)
                .code("SP-KEN330")
                .name("Bia Heineken Sleek lon 330ml")
                .unit("lon")
                .price(new BigDecimal("19000"))
                .costPrice(new BigDecimal("15000"))
                .build();
    }

    @Test
    @DisplayName("Thủ kho nhập 10 thùng (hệ số 24) -> sổ sách tồn kho ghi nhận 240 lon")
    void importGoods_ByCarton_LedgerRecordsInCans() {
        WarehouseVoucherRequest request = WarehouseVoucherRequest.builder()
                .warehouseId(1L)
                .voucherCode("PNK-001")
                .note("Nhập hàng theo thùng")
                .items(List.of(WarehouseVoucherItemRequest.builder()
                        .productId(10L)
                        .unit("thùng")
                        .quantity(new BigDecimal("10"))
                        .build()))
                .build();

        when(warehouseRepository.findById(1L)).thenReturn(Optional.of(khoHcm));
        when(productRepository.findById(10L)).thenReturn(Optional.of(biaHeineken));
        when(unitConversionService.getConversionFactor(biaHeineken, "thùng")).thenReturn(new BigDecimal("24.0000"));

        WarehouseInventory inventory = WarehouseInventory.builder()
                .id(50L)
                .warehouse(khoHcm)
                .product(biaHeineken)
                .baseQuantity(BigDecimal.ZERO)
                .baseUnit("lon")
                .build();

        when(inventoryRepository.findByWarehouseIdAndProductId(1L, 10L)).thenReturn(Optional.of(inventory));
        when(voucherRepository.save(any(WarehouseVoucher.class))).thenAnswer(invocation -> invocation.getArgument(0));

        WarehouseVoucherResponse response = warehouseService.importGoods(request, "thukho01");

        assertThat(response).isNotNull();
        assertThat(response.getItems()).hasSize(1);

        // Chi tiết phiếu: số lượng nhập = 10 thùng, hệ số = 24.0, số lượng cơ sở = 240 lon
        assertThat(response.getItems().get(0).getUnit()).isEqualTo("thùng");
        assertThat(response.getItems().get(0).getQuantity()).isEqualByComparingTo(new BigDecimal("10"));
        assertThat(response.getItems().get(0).getConversionFactor()).isEqualByComparingTo(new BigDecimal("24.0000"));
        assertThat(response.getItems().get(0).getBaseUnit()).isEqualTo("lon");
        assertThat(response.getItems().get(0).getBaseQuantity()).isEqualByComparingTo(new BigDecimal("240.0000"));

        // Sổ sách tồn kho được cộng đúng 240 lon
        assertThat(inventory.getBaseQuantity()).isEqualByComparingTo(new BigDecimal("240.0000"));
        verify(inventoryRepository).save(inventory);
    }

    @Test
    @DisplayName("Thủ kho xuất 5 lốc (hệ số 6 = 30 lon) -> sổ sách tồn kho trừ đúng 30 lon")
    void exportGoods_ByPack_LedgerDeductsInCans() {
        WarehouseVoucherRequest request = WarehouseVoucherRequest.builder()
                .warehouseId(1L)
                .voucherCode("PXK-001")
                .note("Xuất hàng theo lốc")
                .items(List.of(WarehouseVoucherItemRequest.builder()
                        .productId(10L)
                        .unit("lốc")
                        .quantity(new BigDecimal("5"))
                        .build()))
                .build();

        when(warehouseRepository.findById(1L)).thenReturn(Optional.of(khoHcm));
        when(productRepository.findById(10L)).thenReturn(Optional.of(biaHeineken));
        when(unitConversionService.getConversionFactor(biaHeineken, "lốc")).thenReturn(new BigDecimal("6.0000"));

        // Hiện tại trong kho đang có 100 lon
        WarehouseInventory inventory = WarehouseInventory.builder()
                .id(50L)
                .warehouse(khoHcm)
                .product(biaHeineken)
                .baseQuantity(new BigDecimal("100.0000"))
                .baseUnit("lon")
                .build();

        when(inventoryRepository.findByWarehouseIdAndProductId(1L, 10L)).thenReturn(Optional.of(inventory));
        when(voucherRepository.save(any(WarehouseVoucher.class))).thenAnswer(invocation -> invocation.getArgument(0));

        WarehouseVoucherResponse response = warehouseService.exportGoods(request, "thukho01");

        // Chi tiết phiếu: 5 lốc -> 30 lon
        assertThat(response.getItems().get(0).getBaseQuantity()).isEqualByComparingTo(new BigDecimal("30.0000"));

        // Tồn kho: 100 - 30 = 70 lon
        assertThat(inventory.getBaseQuantity()).isEqualByComparingTo(new BigDecimal("70.0000"));
    }

    @Test
    @DisplayName("Xuất hàng vượt quá tồn kho cơ sở -> ném ngoại lệ chặn xuất âm")
    void exportGoods_InsufficientBaseStock_ThrowsException() {
        WarehouseVoucherRequest request = WarehouseVoucherRequest.builder()
                .warehouseId(1L)
                .items(List.of(WarehouseVoucherItemRequest.builder()
                        .productId(10L)
                        .unit("thùng")
                        .quantity(new BigDecimal("10")) // 10 thùng = 240 lon
                        .build()))
                .build();

        when(warehouseRepository.findById(1L)).thenReturn(Optional.of(khoHcm));
        when(productRepository.findById(10L)).thenReturn(Optional.of(biaHeineken));
        when(unitConversionService.getConversionFactor(biaHeineken, "thùng")).thenReturn(new BigDecimal("24.0000"));

        // Tồn kho chỉ còn 50 lon
        WarehouseInventory inventory = WarehouseInventory.builder()
                .warehouse(khoHcm)
                .product(biaHeineken)
                .baseQuantity(new BigDecimal("50.0000"))
                .baseUnit("lon")
                .build();

        when(inventoryRepository.findByWarehouseIdAndProductId(1L, 10L)).thenReturn(Optional.of(inventory));

        assertThatThrownBy(() -> warehouseService.exportGoods(request, "thukho01"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Không đủ tồn kho để xuất");
    }

    @Test
    @DisplayName("Xem tồn kho: hiển thị số lon thực tế và quy đổi tương đương theo thùng, lốc")
    void getInventory_ShowsBaseQuantityAndConvertedViews() {
        WarehouseInventory inventory = WarehouseInventory.builder()
                .id(50L)
                .warehouse(khoHcm)
                .product(biaHeineken)
                .baseQuantity(new BigDecimal("240.0000"))
                .baseUnit("lon")
                .build();

        when(inventoryRepository.findByWarehouseId(1L)).thenReturn(List.of(inventory));

        UnitConversion loc = UnitConversion.builder().unitName("lốc").conversionFactor(new BigDecimal("6.0000")).build();
        UnitConversion thung = UnitConversion.builder().unitName("thùng").conversionFactor(new BigDecimal("24.0000")).build();
        when(unitConversionRepository.findByProductIdOrderByConversionFactorAsc(10L)).thenReturn(List.of(loc, thung));

        List<WarehouseInventoryResponse> responses = warehouseService.getInventory(1L);

        assertThat(responses).hasSize(1);
        WarehouseInventoryResponse item = responses.get(0);

        assertThat(item.getBaseQuantity()).isEqualByComparingTo(new BigDecimal("240.0000"));
        assertThat(item.getBaseUnit()).isEqualTo("lon");

        // Quy đổi tương đương: 240 lon = 10 thùng = 40 lốc
        assertThat(item.getConvertedViews().get("lon")).isEqualByComparingTo(new BigDecimal("240.0000"));
        assertThat(item.getConvertedViews().get("thùng")).isEqualByComparingTo(new BigDecimal("10.00"));
        assertThat(item.getConvertedViews().get("lốc")).isEqualByComparingTo(new BigDecimal("40.00"));
    }
}
