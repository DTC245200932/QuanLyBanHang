package com.quanlybanhang.dto.response;

import com.quanlybanhang.model.CustomerGroup;
import lombok.*;

import java.math.BigDecimal;

/**
 * Response kết quả áp giá và chiết khấu tự động cho một dòng hàng (S4-01).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderLineItemPricingResponse {

    private Long productId;
    private String productCode;
    private String productName;
    private Long categoryId;
    private String categoryName;

    private Long dealerId;
    private String dealerName;
    private CustomerGroup customerGroup;
    private String customerGroupName;

    private Long priceListId;
    private String priceListCode;
    private String priceListName;

    private String unit;
    private BigDecimal conversionFactor;
    private String baseUnit;
    private BigDecimal quantity;
    private BigDecimal baseQuantity;

    /** Giá bán niêm yết từ bảng giá đang hiệu lực (đã nhân hệ số quy đổi). */
    private BigDecimal priceListSellingPrice;

    /** Giá sàn từ bảng giá đang hiệu lực (đã nhân hệ số quy đổi). */
    private BigDecimal floorPrice;

    /** Đơn giá được áp dụng (giá niêm yết hoặc giá sửa thủ công). */
    private BigDecimal unitPrice;

    /** Có sửa giá thủ công hay không. */
    private Boolean isManualPrice;

    /** Đơn giá bán có nằm dưới giá sàn hay không. */
    private Boolean belowFloorPrice;

    /** Đơn hàng có bị đánh dấu cần duyệt do bán dưới giá sàn hay không. */
    private Boolean requiresApproval;

    /** Lý do cần phê duyệt nếu bán dưới giá sàn. */
    private String approvalReason;

    /** Thành tiền trước chiết khấu (unitPrice * quantity). */
    private BigDecimal subtotalAmount;

    /** Số tiền chiết khấu sản lượng được tính tự động. */
    private BigDecimal discountAmount;

    /** Tỷ lệ % chiết khấu (nếu có). */
    private BigDecimal discountPercent;

    /** Thông tin chính sách chiết khấu được áp dụng. */
    private Long appliedPolicyId;
    private String appliedPolicyCode;
    private String appliedPolicyName;
    private String discountReason;

    /** Tổng thành tiền sau chiết khấu (subtotalAmount - discountAmount). */
    private BigDecimal totalAmount;
}

