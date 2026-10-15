package com.quanlybanhang.dto.response;

import com.quanlybanhang.model.CustomerGroup;
import lombok.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Kết quả tính toán toàn bộ giá và chiết khấu cho đơn hàng (S4-01).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderCalculateResponse {

    private Long dealerId;
    private String dealerName;
    private Long warehouseId;
    private String warehouseCode;
    private String warehouseName;
    private CustomerGroup customerGroup;
    private String customerGroupName;

    private Long priceListId;
    private String priceListCode;
    private String priceListName;

    private BigDecimal subtotalAmount;
    private BigDecimal discountAmount;
    private BigDecimal totalAmount;

    /** Có ít nhất một dòng bán dưới giá sàn hay không -> cần duyệt. */
    private Boolean requiresApproval;

    /** Tổng hợp lý do cần duyệt. */
    private String approvalReason;

    @Builder.Default
    private List<OrderCalculateItemResponse> items = new ArrayList<>();
}

