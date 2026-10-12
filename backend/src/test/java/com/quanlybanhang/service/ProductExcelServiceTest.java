package com.quanlybanhang.service;

import com.quanlybanhang.dto.request.ProductImportRowRequest;
import com.quanlybanhang.model.Category;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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
            assertThat(rows.get(0).getName()).isEqualTo("Sản phẩm nhập từ tệp");
            assertThat(rows.get(0).getCategory()).isEqualTo("TEST-CATEGORY");
        }
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {"18.000|18000", "18,000|18000", "12.50|12.50", "12,50|12.50",
            "1.234,56|1234.56", "1,234.56|1234.56", "18 000 vnđ|18000", "18000 ₫|18000"})
    void preservesDecimalValuesAndUnderstandsCurrencyGrouping(String input, String expected) throws Exception {
        var row = parseSingleRow(input, "Đang kinh doanh");
        assertThat(row.getPrice()).isEqualByComparingTo(expected);
        assertThat(row.getParseErrors()).isEmpty();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {"abc|Giá bán", "1,2,3|Giá bán", "NaN|Giá bán"})
    void invalidMoneyProducesRowErrorInsteadOfBecomingZero(String input, String expectedError) throws Exception {
        var row = parseSingleRow(input, "Đang kinh doanh");
        assertThat(row.getPrice()).isNull();
        assertThat(row.getParseErrors()).anyMatch(error -> error.contains(expectedError));
    }

    @Test
    void invalidStatusIsReportedAndVietnameseLockedStatusIsRecognized() throws Exception {
        assertThat(parseSingleRow("100", "Đã khóa").getActive()).isFalse();
        assertThat(parseSingleRow("100", "không rõ").getParseErrors()).contains("Trạng thái không hợp lệ");
    }

    @Test
    void blankOptionalCellsRemainMissingForUpdatePreservation() throws Exception {
        var row = parseSingleRow("", "");
        assertThat(row.getPrice()).isNull();
        assertThat(row.getUnit()).isNull();
        assertThat(row.getActive()).isNull();
    }

    @Test
    void formulaWithoutCachedResultIsRejectedInsteadOfImportedAsZero() throws Exception {
        byte[] template = excelService.generateTemplate(List.of());
        try (var workbook = WorkbookFactory.create(new ByteArrayInputStream(template));
             var out = new ByteArrayOutputStream()) {
            Row row = workbook.getSheetAt(0).createRow(1);
            row.createCell(0).setCellValue("FORMULA");
            row.createCell(1).setCellValue("Công thức chưa tính");
            row.createCell(2).setCellValue("CAT");
            row.createCell(5).setCellFormula("1+2");
            workbook.write(out);
            var parsed = excelService.parseExcel(new ByteArrayInputStream(out.toByteArray())).get(0);
            assertThat(parsed.getParseErrors()).contains("Giá bán không phải số hợp lệ");
            assertThat(parsed.getPrice()).isNull();
        }
    }

    @Test
    void readsGenuineLegacyXlsWorkbook() throws Exception {
        try (var workbook = new org.apache.poi.hssf.usermodel.HSSFWorkbook();
             var out = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("Sản phẩm");
            var header = sheet.createRow(0);
            String[] headers = {"Mã SKU", "Tên sản phẩm", "Mã/Tên nhóm hàng", "Giá bán"};
            for (int i = 0; i < headers.length; i++) header.createCell(i).setCellValue(headers[i]);
            var row = sheet.createRow(1);
            row.createCell(0).setCellValue("XLS-001");
            row.createCell(1).setCellValue("Sản phẩm XLS");
            row.createCell(2).setCellValue("CAT");
            row.createCell(3).setCellValue(12345);
            workbook.write(out);
            var parsed = excelService.parseExcel(new ByteArrayInputStream(out.toByteArray())).get(0);
            assertThat(parsed.getSku()).isEqualTo("XLS-001");
            assertThat(parsed.getCategory()).isEqualTo("CAT");
            assertThat(parsed.getPrice()).isEqualByComparingTo("12345");
        }
    }

    @Test
    void englishCostPriceHeaderDoesNotOverwriteSellingPriceHeader() throws Exception {
        byte[] template = excelService.generateTemplate(List.of());
        try (var workbook = WorkbookFactory.create(new ByteArrayInputStream(template));
             var out = new ByteArrayOutputStream()) {
            Row header = workbook.getSheetAt(0).getRow(0);
            header.getCell(5).setCellValue("Price");
            header.getCell(6).setCellValue("Cost Price");
            Row row = workbook.getSheetAt(0).createRow(1);
            row.createCell(0).setCellValue("S1");
            row.createCell(1).setCellValue("Tên");
            row.createCell(2).setCellValue("Nhóm");
            row.createCell(5).setCellValue(12500);
            row.createCell(6).setCellValue(9000);
            workbook.write(out);
            var parsed = excelService.parseExcel(new ByteArrayInputStream(out.toByteArray())).get(0);
            assertThat(parsed.getPrice()).isEqualByComparingTo("12500");
            assertThat(parsed.getCostPrice()).isEqualByComparingTo("9000");
        }
    }

    private ProductImportRowRequest parseSingleRow(String price, String status) throws Exception {
        byte[] template = excelService.generateTemplate(List.of());
        try (var workbook = WorkbookFactory.create(new ByteArrayInputStream(template));
             var out = new ByteArrayOutputStream()) {
            Row row = workbook.getSheetAt(0).createRow(1);
            row.createCell(0).setCellValue("TEST-SKU");
            row.createCell(1).setCellValue("Sản phẩm");
            row.createCell(2).setCellValue("TEST-CATEGORY");
            row.createCell(5).setCellValue(price);
            row.createCell(7).setCellValue(status);
            workbook.write(out);
            return excelService.parseExcel(new ByteArrayInputStream(out.toByteArray())).get(0);
        }
    }
}

