package com.quanlybanhang.service;

import com.quanlybanhang.dto.request.ProductImportRowRequest;
import com.quanlybanhang.model.Category;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ProductExcelService - Tạo mẫu và đọc Excel sản phẩm (S2-08)")
class ProductExcelServiceTest {

    private final ProductExcelService excelService = new ProductExcelService();

    @Test
    @DisplayName("Tạo tệp Excel mẫu thành công và đọc lại các dòng mẫu chính xác")
    void generateAndParseTemplate_Success() {
        Category cat1 = Category.builder().id(1L).code("BEV-BEER").name("Bia các loại").level(1).build();
        Category cat2 = Category.builder().id(2L).code("BEV-WATER").name("Nước khoáng").level(2).parent(cat1).build();

        byte[] excelBytes = excelService.generateTemplate(List.of(cat1, cat2));
        assertThat(excelBytes).isNotNull();
        assertThat(excelBytes.length).isGreaterThan(1000);

        // Đọc lại tệp mẫu vừa sinh ra
        List<ProductImportRowRequest> rows = excelService.parseExcel(new ByteArrayInputStream(excelBytes));

        assertThat(rows).isNotEmpty();
        assertThat(rows.size()).isGreaterThanOrEqualTo(2);

        ProductImportRowRequest firstRow = rows.get(0);
        assertThat(firstRow.getEffectiveSku()).isEqualTo("HEIN-330");
        assertThat(firstRow.getName()).contains("Heineken");
        assertThat(firstRow.getPrice()).isEqualByComparingTo(new BigDecimal("18000"));
        assertThat(firstRow.getCostPrice()).isEqualByComparingTo(new BigDecimal("14500"));
        assertThat(firstRow.getActive()).isTrue();
    }
}

