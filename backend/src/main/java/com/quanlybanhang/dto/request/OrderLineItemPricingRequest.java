package com.quanlybanhang.dto.request;

import com.quanlybanhang.model.CustomerGroup;
import jakarta.validation.constraints.DecimalMin;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Request tính giá và chiết khấu tự động khi thêm một dòng hàng (S4-01).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderLineItemPricingRequest {

    private Long dealerId;

    private CustomerGroup customerGroup;

    private Long productId;

    private String productCode;

    private String unit;

    @DecimalMin(value = "0.0001", message = "Số lượng phải lớn hơn 0")
    @Builder.Default
    private BigDecimal quantity = BigDecimal.ONE;

    /** Giá sửa thủ công (nếu người dùng tự nhập / điều chỉnh giá). */
    private BigDecimal manualPrice;

    private LocalDate orderDate;
}

