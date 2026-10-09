package com.quanlybanhang.dto.response;

import com.quanlybanhang.model.CustomerGroup;
import com.quanlybanhang.model.DiscountTargetType;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VolumeDiscountPolicyResponse {

    private Long id;
    private String code;
    private String name;
    private String description;
    private DiscountTargetType targetType;

    // Chi tiết sản phẩm nếu targetType = PRODUCT
    private Long productId;
    private String productCode;
    private String productName;
    private String productUnit;

    // Chi tiết nhóm hàng nếu targetType = CATEGORY
    private Long categoryId;
    private String categoryCode;
    private String categoryName;

    private CustomerGroup customerGroup;
    private String customerGroupName;

    private LocalDate startDate;
    private LocalDate endDate;
    private Boolean active;

    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private List<VolumeDiscountTierResponse> tiers;
}

