package com.quanlybanhang.service;

import com.quanlybanhang.dto.request.ProductImportRowRequest;
import com.quanlybanhang.model.Category;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Service xử lý tạo tệp Excel mẫu và đọc dữ liệu Excel nhập sản phẩm (S2-08).
 */
@Service
public class ProductExcelService {

    /**
     * Sinh tệp Excel mẫu (.xlsx) phục vụ nhập danh mục sản phẩm hàng loạt (S2-08).
     * Bao gồm:
     * - Sheet 1: "Sản phẩm mẫu" kèm các cột chuẩn và dòng dữ liệu ví dụ.
     * - Sheet 2: "Danh mục nhóm hàng" chứa danh sách nhóm hàng hiện có trong hệ thống để tiện tra cứu.
     * - Sheet 3: "Hướng dẫn" quy định rõ các quy tắc nghiệp vụ (SKU duy nhất, cập nhật thay vì tạo mới...).
     */
    public byte[] generateTemplate(List<Category> categories) {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            // --- STYLES ---
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerFont.setFontHeightInPoints((short) 11);

            CellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFillForegroundColor(IndexedColors.TEAL.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setFont(headerFont);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            headerStyle.setBorderBottom(BorderStyle.THIN);
            headerStyle.setBorderTop(BorderStyle.THIN);
            headerStyle.setBorderLeft(BorderStyle.THIN);
            headerStyle.setBorderRight(BorderStyle.THIN);

            CellStyle dataStyle = workbook.createCellStyle();
            dataStyle.setBorderBottom(BorderStyle.THIN);
            dataStyle.setBorderTop(BorderStyle.THIN);
            dataStyle.setBorderLeft(BorderStyle.THIN);
            dataStyle.setBorderRight(BorderStyle.THIN);

            CellStyle numberStyle = workbook.createCellStyle();
            numberStyle.cloneStyleFrom(dataStyle);
            DataFormat format = workbook.createDataFormat();
            numberStyle.setDataFormat(format.getFormat("#,##0"));

            // --- SHEET 1: SẢN PHẨM MẪU ---
            Sheet sheet = workbook.createSheet("Sản phẩm mẫu");
            String[] headers = {
                    "Mã SKU (*)",
                    "Tên sản phẩm (*)",
                    "Mã/Tên nhóm hàng (*)",
                    "Đơn vị tính cơ sở",
                    "Quy cách đóng gói",
                    "Giá bán",
                    "Giá vốn",
                    "Trạng thái",
                    "Mô tả"
            };

            Row headerRow = sheet.createRow(0);
            headerRow.setHeightInPoints(28);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            // Dữ liệu mẫu thực tế
            Object[][] sampleData = {
                    {"HEIN-330", "Bia Heineken lon 330ml", "BEV-BEER", "lon", "Thùng 24 lon", 18000, 14500, "Đang kinh doanh", "Bia cao cấp Hà Lan lon bạc 330ml"},
                    {"AQUA-500", "Nước suối Aquafina 500ml", "BEV-WATER", "chai", "Lốc 24 chai", 6000, 4200, "Đang kinh doanh", "Nước tinh khiết đóng chai 500ml"},
                    {"COCA-320", "Nước ngọt Coca-Cola lon 320ml", "BEV-SOFT", "lon", "Thùng 24 lon", 10500, 8500, "Đang kinh doanh", "Nước ngọt có gas vị truyền thống"}
            };

            // Nếu có categories thực tế thì dùng mã nhóm thực tế cho mẫu
            if (categories != null && !categories.isEmpty()) {
                String firstCatCode = categories.get(0).getCode();
                sampleData[0][2] = firstCatCode;
                if (categories.size() > 1) {
                    sampleData[1][2] = categories.get(1).getCode();
                }
                if (categories.size() > 2) {
                    sampleData[2][2] = categories.get(2).getCode();
                }
            }

            for (int r = 0; r < sampleData.length; r++) {
                Row row = sheet.createRow(r + 1);
                row.setHeightInPoints(20);
                for (int c = 0; c < sampleData[r].length; c++) {
                    Cell cell = row.createCell(c);
                    Object val = sampleData[r][c];
                    if (val instanceof Number) {
                        cell.setCellValue(((Number) val).doubleValue());
                        cell.setCellStyle(numberStyle);
                    } else {
                        cell.setCellValue(val != null ? val.toString() : "");
                        cell.setCellStyle(dataStyle);
                    }
                }
            }

            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
                sheet.setColumnWidth(i, Math.max(sheet.getColumnWidth(i) + 1200, 4200));
            }

            // --- SHEET 2: DANH MỤC NHÓM HÀNG ---
            Sheet catSheet = workbook.createSheet("Danh mục nhóm hàng");
            String[] catHeaders = {"Mã nhóm hàng", "Tên nhóm hàng", "Cấp độ", "Nhóm cha"};
            Row catHeaderRow = catSheet.createRow(0);
            catHeaderRow.setHeightInPoints(26);
            for (int i = 0; i < catHeaders.length; i++) {
                Cell cell = catHeaderRow.createCell(i);
                cell.setCellValue(catHeaders[i]);
                cell.setCellStyle(headerStyle);
            }

            if (categories != null) {
                int catRowIdx = 1;
                for (Category cat : categories) {
                    Row catRow = catSheet.createRow(catRowIdx++);
                    catRow.setHeightInPoints(19);

                    Cell c0 = catRow.createCell(0);
                    c0.setCellValue(cat.getCode() != null ? cat.getCode() : "");
                    c0.setCellStyle(dataStyle);

                    Cell c1 = catRow.createCell(1);
                    c1.setCellValue(cat.getName() != null ? cat.getName() : "");
                    c1.setCellStyle(dataStyle);

                    Cell c2 = catRow.createCell(2);
                    c2.setCellValue(cat.getLevel() != null ? "Cấp " + cat.getLevel() : "Cấp 1");
                    c2.setCellStyle(dataStyle);

                    Cell c3 = catRow.createCell(3);
                    c3.setCellValue(cat.getParent() != null ? cat.getParent().getName() : "(Ngành hàng gốc)");
                    c3.setCellStyle(dataStyle);
                }
            }

            for (int i = 0; i < catHeaders.length; i++) {
                catSheet.autoSizeColumn(i);
                catSheet.setColumnWidth(i, Math.max(catSheet.getColumnWidth(i) + 1000, 4500));
            }

            // --- SHEET 3: HƯỚNG DẪN SỬ DỤNG ---
            Sheet guideSheet = workbook.createSheet("Hướng dẫn");
            String[] guideLines = {
                    "QUY ĐỊNH NHẬP DỮ LIỆU SẢN PHẨM TỪ EXCEL (S2-08)",
                    "",
                    "1. Các cột có dấu (*) là bắt buộc: Mã SKU, Tên sản phẩm, Mã/Tên nhóm hàng.",
                    "2. Quy tắc mã SKU:",
                    "   - Mã SKU là duy nhất trên toàn hệ thống (không phân biệt chữ hoa, chữ thường).",
                    "   - NẾU MÃ SKU ĐÃ TỒN TẠI: Hệ thống sẽ tự động CẬP NHẬT sản phẩm thay vì tạo mới (có đánh dấu rõ 'UPDATE' trong bản xem trước).",
                    "   - NẾU MÃ SKU CHƯA TỒN TẠI: Hệ thống sẽ TẠO MỚI sản phẩm ('CREATE').",
                    "3. Cột Nhóm hàng:",
                    "   - Có thể điền Mã nhóm hàng (ví dụ: BEV-BEER) hoặc Tên nhóm hàng (ví dụ: Bia các loại).",
                    "   - Vui lòng tra cứu danh sách nhóm hàng tại Sheet 'Danh mục nhóm hàng'.",
                    "4. Cột Giá vốn (Bảo mật theo S1-05 & S2-05):",
                    "   - Chỉ Quản lý kinh doanh (ROLE_SALES_MANAGER) có quyền xem và cập nhật giá vốn.",
                    "   - Người dùng không có quyền khi nhập tệp sẽ không thay đổi giá vốn của sản phẩm.",
                    "5. Cột Trạng thái:",
                    "   - Điền 'Đang kinh doanh' hoặc 'Ngừng kinh doanh' (hoặc 1 / 0, true / false).",
                    "   - Nếu để trống, hệ thống mặc định là 'Đang kinh doanh'.",
                    "6. Cột Đơn vị tính cơ sở: Ví dụ lon, chai, hộp, cái... Nếu để trống, mặc định là 'cái'.",
                    "7. Hệ thống hỗ trợ xử lý mượt mà lên tới 5.000 dòng dữ liệu mỗi lần nhập."
            };

            for (int i = 0; i < guideLines.length; i++) {
                Row r = guideSheet.createRow(i);
                Cell c = r.createCell(0);
                c.setCellValue(guideLines[i]);
            }
            guideSheet.autoSizeColumn(0);

            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Không thể tạo tệp Excel mẫu: " + e.getMessage(), e);
        }
    }

    /**
     * Đọc tệp Excel (.xlsx hoặc .xls) thành danh sách ProductImportRowRequest.
     */
    public List<ProductImportRowRequest> parseExcel(InputStream inputStream) {
        List<ProductImportRowRequest> requests = new ArrayList<>();

        try (Workbook workbook = WorkbookFactory.create(inputStream)) {
            if (workbook.getNumberOfSheets() == 0) {
                return requests;
            }

            Sheet sheet = workbook.getSheetAt(0);
            if (sheet.getPhysicalNumberOfRows() == 0) {
                return requests;
            }

            // Tìm dòng tiêu đề (header)
            int headerRowIndex = -1;
            Map<String, Integer> colMap = new HashMap<>();

            for (int r = 0; r <= Math.min(sheet.getLastRowNum(), 10); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;

                Map<String, Integer> tempMap = mapHeaders(row);
                if (tempMap.containsKey("sku") || tempMap.containsKey("name")) {
                    headerRowIndex = r;
                    colMap = tempMap;
                    break;
                }
            }

            if (headerRowIndex == -1) {
                throw new IllegalArgumentException("Không tìm thấy dòng tiêu đề hợp lệ trong tệp Excel (cần có cột 'Mã SKU' và 'Tên sản phẩm')");
            }

            // Đọc từng dòng dữ liệu sau dòng header
            for (int r = headerRowIndex + 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null || isRowEmpty(row)) {
                    continue;
                }

                String sku = getStringValue(row, colMap.get("sku"));
                String name = getStringValue(row, colMap.get("name"));
                String category = getStringValue(row, colMap.get("category"));
                String unit = getStringValue(row, colMap.get("unit"));
                String packing = getStringValue(row, colMap.get("packing"));
                BigDecimal price = getBigDecimalValue(row, colMap.get("price"));
                BigDecimal costPrice = getBigDecimalValue(row, colMap.get("costPrice"));
                Boolean active = getBooleanValue(row, colMap.get("active"));
                String description = getStringValue(row, colMap.get("description"));

                // Nếu tất cả các trường chính đều rỗng thì bỏ qua
                if ((sku == null || sku.isBlank()) && (name == null || name.isBlank()) && (category == null || category.isBlank())) {
                    continue;
                }

                ProductImportRowRequest req = ProductImportRowRequest.builder()
                        .rowNumber(r + 1) // 1-indexed theo số dòng trong Excel
                        .sku(sku != null ? sku.trim() : null)
                        .code(sku != null ? sku.trim() : null)
                        .name(name != null ? name.trim() : null)
                        .category(category != null ? category.trim() : null)
                        .unit(unit != null && !unit.isBlank() ? unit.trim() : "cái")
                        .packingSpecification(packing != null ? packing.trim() : null)
                        .price(price)
                        .costPrice(costPrice)
                        .active(active)
                        .description(description != null ? description.trim() : null)
                        .build();

                requests.add(req);
            }

        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Lỗi đọc tệp Excel: " + e.getMessage(), e);
        }

        return requests;
    }

    private Map<String, Integer> mapHeaders(Row row) {
        Map<String, Integer> map = new HashMap<>();
        for (int c = 0; c < row.getLastCellNum(); c++) {
            Cell cell = row.getCell(c);
            if (cell == null) continue;
            String text = normalizeHeader(cell.getStringCellValue());
            if (text.isBlank()) continue;

            if (text.contains("sku") || text.equals("code") || text.contains("masanpham") || text.contains("masp") || text.equals("ma")) {
                map.putIfAbsent("sku", c);
            } else if (text.contains("tensanpham") || text.contains("tensp") || text.contains("ten") || text.equals("name")) {
                map.putIfAbsent("name", c);
            } else if (text.contains("nhomhang") || text.contains("manhom") || text.contains("tennhom") || text.contains("nhom") || text.contains("category")) {
                map.putIfAbsent("category", c);
            } else if (text.contains("donvitinh") || text.contains("dvt") || text.contains("donvi") || text.equals("unit")) {
                map.putIfAbsent("unit", c);
            } else if (text.contains("quycach") || text.contains("donggoi") || text.contains("packing")) {
                map.putIfAbsent("packing", c);
            } else if (text.contains("giaban") || text.contains("dongia") || text.equals("gia") || text.contains("price")) {
                map.putIfAbsent("price", c);
            } else if (text.contains("giavon") || text.contains("costprice") || text.contains("cost")) {
                map.putIfAbsent("costPrice", c);
            } else if (text.contains("trangthai") || text.contains("kinhdoanh") || text.contains("status") || text.equals("active")) {
                map.putIfAbsent("active", c);
            } else if (text.contains("mota") || text.contains("ghichu") || text.contains("description") || text.contains("note")) {
                map.putIfAbsent("description", c);
            }
        }
        return map;
    }

    private String normalizeHeader(String header) {
        if (header == null) return "";
        String normalized = Normalizer.normalize(header, Normalizer.Form.NFD);
        Pattern pattern = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");
        String noAccents = pattern.matcher(normalized).replaceAll("");
        return noAccents.toLowerCase(Locale.ROOT)
                .replaceAll("[đĐ]", "d")
                .replaceAll("[^a-z0-9]", "");
    }

    private boolean isRowEmpty(Row row) {
        for (int c = row.getFirstCellNum(); c < row.getLastCellNum(); c++) {
            Cell cell = row.getCell(c);
            if (cell != null && cell.getCellType() != CellType.BLANK) {
                if (cell.getCellType() == CellType.STRING && !cell.getStringCellValue().trim().isEmpty()) {
                    return false;
                }
                if (cell.getCellType() != CellType.STRING) {
                    return false;
                }
            }
        }
        return true;
    }

    private String getStringValue(Row row, Integer colIdx) {
        if (colIdx == null || colIdx < 0) return null;
        Cell cell = row.getCell(colIdx);
        if (cell == null) return null;

        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getDateCellValue().toString();
                }
                double num = cell.getNumericCellValue();
                if (num == Math.floor(num)) {
                    return String.format(Locale.ROOT, "%.0f", num);
                }
                return String.valueOf(num);
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                try {
                    return cell.getStringCellValue();
                } catch (Exception e) {
                    try {
                        return String.valueOf(cell.getNumericCellValue());
                    } catch (Exception ignored) {
                        return "";
                    }
                }
            default:
                return null;
        }
    }

    private BigDecimal getBigDecimalValue(Row row, Integer colIdx) {
        if (colIdx == null || colIdx < 0) return null;
        Cell cell = row.getCell(colIdx);
        if (cell == null) return null;

        try {
            switch (cell.getCellType()) {
                case NUMERIC:
                    return BigDecimal.valueOf(cell.getNumericCellValue());
                case STRING:
                    String text = cell.getStringCellValue().trim()
                            .replace(",", "")
                            .replace(".", "")
                            .replace(" ", "")
                            .replace("đ", "")
                            .replace("VND", "")
                            .replace("vnd", "");
                    if (text.isEmpty()) return null;
                    return new BigDecimal(text);
                case FORMULA:
                    return BigDecimal.valueOf(cell.getNumericCellValue());
                default:
                    return null;
            }
        } catch (Exception e) {
            return null;
        }
    }

    private Boolean getBooleanValue(Row row, Integer colIdx) {
        if (colIdx == null || colIdx < 0) return null;
        Cell cell = row.getCell(colIdx);
        if (cell == null) return null;

        switch (cell.getCellType()) {
            case BOOLEAN:
                return cell.getBooleanCellValue();
            case NUMERIC:
                return cell.getNumericCellValue() != 0;
            case STRING:
                String val = cell.getStringCellValue().trim().toLowerCase(Locale.ROOT);
                if (val.contains("đang") || val.contains("dang") || val.contains("hoat dong")
                        || val.equals("true") || val.equals("1") || val.equals("yes") || val.equals("co")) {
                    return true;
                }
                if (val.contains("ngừng") || val.contains("ngung") || val.contains("khoa")
                        || val.equals("false") || val.equals("0") || val.equals("no") || val.equals("khong")) {
                    return false;
                }
                return null;
            default:
                return null;
        }
    }
}

