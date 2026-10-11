package com.quanlybanhang.service;

import com.quanlybanhang.dto.request.ProductImportRowRequest;
import com.quanlybanhang.model.Category;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.ss.usermodel.Row;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ProductExcelService - Tạo mẫu và đọc Excel sản phẩm (S2-08)")
class ProductExcelServiceTest {

    private final ProductExcelService excelService = new ProductExcelService();

    @Test
    @DisplayName("Tệp nhập chỉ có tiêu đề; không tạo sản phẩm khi tải rồi nhập lại tệp trống")
    void generatedTemplateContainsNoBusinessRecords() throws Exception {
        Category cat1 = Category.builder().id(1L).code("BEV-BEER").name("Bia các loại").level(1).build();
        Category cat2 = Category.builder().id(2L).code("BEV-WATER").name("Nước khoáng").level(2).parent(cat1).build();

        byte[] excelBytes = excelService.generateTemplate(List.of(cat1, cat2));
        assertThat(excelBytes).isNotNull();
        assertThat(excelBytes.length).isGreaterThan(1000);

        // Đọc lại tệp mẫu vừa sinh ra
        List<ProductImportRowRequest> rows = excelService.parseExcel(new ByteArrayInputStream(excelBytes));

        assertThat(rows).isEmpty();
        try (var workbook = WorkbookFactory.create(new ByteArrayInputStream(excelBytes))) {
            assertThat(workbook.getSheetAt(0).getPhysicalNumberOfRows()).isEqualTo(1);
            assertThat(workbook.getSheetAt(1).getRow(1).getCell(0).getStringCellValue()).isEqualTo(cat1.getCode());
        }
    }

    @Test
    void parsesOnlyDataAddedByTheUser() throws Exception {
        byte[] template = excelService.generateTemplate(List.of());
        try (var workbook = WorkbookFactory.create(new ByteArrayInputStream(template));
             var out = new ByteArrayOutputStream()) {
            Row row = workbook.getSheetAt(0).createRow(1);
            row.createCell(0).setCellValue("TEST-SKU");
            row.createCell(1).setCellValue("Sản phẩm nhập từ tệp");
            row.createCell(2).setCellValue("TEST-CATEGORY");
            row.createCell(5).setCellValue(27500);
            workbook.write(out);
            List<ProductImportRowRequest> rows = excelService.parseExcel(new ByteArrayInputStream(out.toByteArray()));
            assertThat(rows).hasSize(1);
            assertThat(rows.get(0).getEffectiveSku()).isEqualTo("TEST-SKU");
            assertThat(rows.get(0).getPrice()).isEqualByComparingTo(new BigDecimal("27500"));
        }
    }
}

