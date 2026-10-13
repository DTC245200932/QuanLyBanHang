package com.quanlybanhang.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Yêu cầu kiểm tra sơ bộ hạn mức công nợ trước khi chốt đơn (S4-02).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderDebtCheckRequest {

    @NotNull(message = "ID đại lý không được để trống")
    private Long dealerId;

    /** Tổng giá trị dự kiến của đơn hàng mới cần kiểm tra. */
    @NotNull(message = "Số tiền đơn hàng không được để trống")
    private BigDecimal orderAmount;
}

