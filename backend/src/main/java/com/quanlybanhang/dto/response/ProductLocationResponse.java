package com.quanlybanhang.dto.response;

import com.quanlybanhang.model.LocationType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response thông tin vị trí lưu của sản phẩm (S5-03).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductLocationResponse {

    private Long id;

    // Kho
    private Long warehouseId;
    private String warehouseCode;
    private String warehouseName;

    // Vị trí lưu
    private Long locationId;
    private String locationCode;
    private String locationName;
    private LocationType locationType;
    private String locationTypeDisplayName;

    // Khu vực cha
    private Long parentZoneId;
    private String parentZoneCode;
    private String parentZoneName;

    /** Tên hiển thị vị trí chuẩn (vd: "Khu A > Kệ 01") */
    private String locationDisplay;

    // Sản phẩm
    private Long productId;
    private String productCode;
    private String productName;
    private String baseUnit;

    private Boolean isDefault;
    private String note;
}
