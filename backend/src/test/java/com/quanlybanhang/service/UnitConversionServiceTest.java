package com.quanlybanhang.service;

import com.quanlybanhang.dto.request.UnitConversionRequest;
import com.quanlybanhang.dto.response.UnitConversionResponse;
import com.quanlybanhang.model.Category;
import com.quanlybanhang.model.Product;
import com.quanlybanhang.model.UnitConversion;
import com.quanlybanhang.repository.ProductRepository;
import com.quanlybanhang.repository.UnitConversionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UnitConversionService - Đơn vị tính quy đổi của SKU (S2-07)")
class UnitConversionServiceTest {

    @Mock
    private UnitConversionRepository unitConversionRepository;

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private UnitConversionServiceImpl unitConversionService;

    private Product biaHeineken;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        Category category = Category.builder().id(1L).code("CAT-BEER").name("Bia").build();
        biaHeineken = Product.builder()
                .id(10L)
                .code("SP-KEN330")
                .name("Bia Heineken lon 330ml")
                .unit("lon")
                .price(new BigDecimal("19000"))
                .category(category)
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Lấy danh sách đơn vị quy đổi: luôn có đơn vị cơ sở (lon) với hệ số 1.0 kèm các đơn vị khác (lốc, thùng)")
    void getConversions_IncludesBaseUnitAndSecondaryUnits() {
        UnitConversion loc = UnitConversion.builder()
                .id(101L)
                .product(biaHeineken)
                .unitName("lốc")
                .conversionFactor(new BigDecimal("6.0000"))
                .isBaseUnit(false)
                .build();

        UnitConversion thung = UnitConversion.builder()
                .id(102L)
                .product(biaHeineken)
                .unitName("thùng")
                .conversionFactor(new BigDecimal("24.0000"))
                .isBaseUnit(false)
                .build();

        when(productRepository.findById(10L)).thenReturn(Optional.of(biaHeineken));
        when(unitConversionRepository.findByProductIdOrderByConversionFactorAsc(10L)).thenReturn(List.of(loc, thung));

        List<UnitConversionResponse> list = unitConversionService.getConversionsByProductId(10L);

        assertThat(list).hasSize(3);
        // Đơn vị đầu tiên là đơn vị cơ sở
        assertThat(list.get(0).getUnitName()).isEqualTo("lon");
        assertThat(list.get(0).getConversionFactor()).isEqualByComparingTo(BigDecimal.ONE);
        assertThat(list.get(0).getIsBaseUnit()).isTrue();

        // 2 đơn vị tiếp theo là lốc và thùng
        assertThat(list.get(1).getUnitName()).isEqualTo("lốc");
        assertThat(list.get(1).getConversionFactor()).isEqualByComparingTo(new BigDecimal("6.0000"));

        assertThat(list.get(2).getUnitName()).isEqualTo("thùng");
        assertThat(list.get(2).getConversionFactor()).isEqualByComparingTo(new BigDecimal("24.0000"));
    }

    @Test
    @DisplayName("Nhân viên kho không được xem giá vốn của đơn vị cơ sở hoặc đơn vị quy đổi")
    void getConversions_WarehouseStaff_HidesCostPrice() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("warehouse.staff", "pass",
                        List.of(new SimpleGrantedAuthority("ROLE_WAREHOUSE_STAFF"))));
        biaHeineken.setCostPrice(new BigDecimal("15000"));
        UnitConversion thung = UnitConversion.builder()
                .id(102L)
                .product(biaHeineken)
                .unitName("thùng")
                .conversionFactor(new BigDecimal("24.0000"))
                .costPrice(new BigDecimal("355000"))
                .build();

        when(productRepository.findById(10L)).thenReturn(Optional.of(biaHeineken));
        when(unitConversionRepository.findByProductIdOrderByConversionFactorAsc(10L)).thenReturn(List.of(thung));

        List<UnitConversionResponse> list = unitConversionService.getConversionsByProductId(10L);

        assertThat(list).extracting(UnitConversionResponse::getCostPrice).containsOnlyNulls();
    }

    @Test
    @DisplayName("Khai báo đơn vị quy đổi mới (thùng = 24 lon) thành công")
    void addConversion_Success() {
        UnitConversionRequest request = UnitConversionRequest.builder()
                .unitName("thùng")
                .conversionFactor(new BigDecimal("24.0000"))
                .price(new BigDecimal("435000"))
                .description("1 thùng = 24 lon")
                .build();

        when(productRepository.findById(10L)).thenReturn(Optional.of(biaHeineken));
        when(unitConversionRepository.existsByProductIdAndUnitNameIgnoreCase(10L, "thùng")).thenReturn(false);
        when(unitConversionRepository.save(any(UnitConversion.class))).thenAnswer(invocation -> {
            UnitConversion uc = invocation.getArgument(0);
            uc.setId(201L);
            return uc;
        });

        UnitConversionResponse response = unitConversionService.addConversion(10L, request);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(201L);
        assertThat(response.getUnitName()).isEqualTo("thùng");
        assertThat(response.getConversionFactor()).isEqualByComparingTo(new BigDecimal("24.0000"));
        assertThat(response.getBaseUnit()).isEqualTo("lon");
    }

    @Test
    @DisplayName("Khai báo đơn vị trùng đơn vị cơ sở -> ném ngoại lệ")
    void addConversion_DuplicateBaseUnit_ThrowsException() {
        UnitConversionRequest request = UnitConversionRequest.builder()
                .unitName("lon")
                .conversionFactor(BigDecimal.ONE)
                .build();

        when(productRepository.findById(10L)).thenReturn(Optional.of(biaHeineken));

        assertThatThrownBy(() -> unitConversionService.addConversion(10L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("trùng với đơn vị cơ sở");
    }

    @Test
    @DisplayName("Khai báo hệ số quy đổi <= 0 -> ném ngoại lệ")
    void addConversion_InvalidFactor_ThrowsException() {
        UnitConversionRequest request = UnitConversionRequest.builder()
                .unitName("thùng")
                .conversionFactor(BigDecimal.ZERO)
                .build();

        when(productRepository.findById(10L)).thenReturn(Optional.of(biaHeineken));

        assertThatThrownBy(() -> unitConversionService.addConversion(10L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Hệ số quy đổi phải lớn hơn 0");
    }

    @Test
    @DisplayName("Quy đổi số lượng về đơn vị cơ sở (10 thùng -> 240 lon)")
    void convertToBaseQuantity_Success() {
        UnitConversion thung = UnitConversion.builder()
                .product(biaHeineken)
                .unitName("thùng")
                .conversionFactor(new BigDecimal("24.0000"))
                .build();

        when(unitConversionRepository.findByProductIdAndUnitNameIgnoreCase(10L, "thùng"))
                .thenReturn(Optional.of(thung));

        BigDecimal baseQty = unitConversionService.convertToBaseQuantity(biaHeineken, "thùng", new BigDecimal("10"));

        assertThat(baseQty).isEqualByComparingTo(new BigDecimal("240.0000"));
    }

    @Test
    @DisplayName("Nhập đơn vị cơ sở (50 lon) -> hệ số là 1.0, số lượng cơ sở là 50 lon")
    void convertToBaseQuantity_BaseUnit_ReturnsSameQuantity() {
        BigDecimal baseQty = unitConversionService.convertToBaseQuantity(biaHeineken, "lon", new BigDecimal("50"));
        assertThat(baseQty).isEqualByComparingTo(new BigDecimal("50"));
    }

    @Test
    @DisplayName("Nhập đơn vị chưa khai báo (két) -> ném ngoại lệ")
    void convertToBaseQuantity_UnregisteredUnit_ThrowsException() {
        when(unitConversionRepository.findByProductIdAndUnitNameIgnoreCase(10L, "két"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> unitConversionService.convertToBaseQuantity(biaHeineken, "két", new BigDecimal("5")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("không hợp lệ cho SKU");
    }
}
