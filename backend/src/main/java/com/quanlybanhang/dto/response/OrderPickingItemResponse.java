package com.quanlybanhang.dto.response;

import com.quanlybanhang.model.LocationType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Thông tin một dòng sản phẩm cần soạn kèm vị trí kệ chính xác (S5-03).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderPickingItemResponse {

    private Long orderItemId;
    private Long productId;
    private String productCode;
    private String productName;

    /** Số lượng đặt theo đơn vị đặt hàng */
    private BigDecimal quantity;
    private String unit;

    /** Số lượng cơ sở cần soạn */
    private BigDecimal baseQuantity;
    private String baseUnit;

    // Kho xuất hàng
    private Long warehouseId;
    private String warehouseCode;
    private String warehouseName;

    // Vị trí lưu chính xác trong kho
    private Long locationId;
    private String locationCode;
    private String locationName;
    private LocationType locationType;
    private String locationTypeDisplayName;
    private String zoneName;
    private String shelfName;

    /** Vị trí soạn hàng hiển thị, vd: "Khu A > Kệ 01" hoặc "Kệ 02" */
    private String locationDisplay;

    /** Tồn khả dụng tại kho */
    private BigDecimal availableQuantity;
}
