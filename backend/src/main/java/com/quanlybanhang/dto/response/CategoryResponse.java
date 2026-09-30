package com.quanlybanhang.dto.response;

import lombok.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Response DTO cho Category (Nhóm hàng nhiều cấp - cấu trúc cây).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CategoryResponse {

    private Long id;
    private String code;
    private String name;
    private String description;
    private Integer level;

    private Long parentId;
    private String parentName;
    private String parentCode;

    /**
     * Số lượng sản phẩm trực tiếp thuộc nhóm này.
     */
    @Builder.Default
    private Long directProductCount = 0L;

    /**
     * Tổng số lượng sản phẩm tính cả các nhóm con trong toàn bộ cây nhánh.
     */
    @Builder.Default
    private Long totalProductCount = 0L;

    /**
     * Doanh số bán hàng trực tiếp của nhóm này.
     */
    @Builder.Default
    private BigDecimal directRevenue = BigDecimal.ZERO;

    /**
     * Tổng doanh số của ngành hàng / nhóm hàng (bao gồm toàn bộ các nhóm con trong cây).
     * Phục vụ yêu cầu S2-06: "để xem được doanh số theo ngành hàng chứ không chỉ theo từng mã".
     */
    @Builder.Default
    private BigDecimal totalRevenue = BigDecimal.ZERO;

    /**
     * Danh sách các nhóm con (đệ quy cây nhiều cấp).
     */
    @Builder.Default
    private List<CategoryResponse> children = new ArrayList<>();
}
