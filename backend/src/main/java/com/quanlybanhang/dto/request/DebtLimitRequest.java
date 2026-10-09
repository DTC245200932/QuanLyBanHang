package com.quanlybanhang.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Dữ liệu khai báo hạn mức công nợ và số ngày nợ cho phép của đại lý (S3-05).
 * - Mỗi đại lý có hạn mức tiền tối đa và số ngày nợ tối đa
 * - Thay đổi hạn mức bắt buộc nhập lý do và được ghi nhật ký
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DebtLimitRequest {

    @NotNull(message = "Hạn mức công nợ không được để trống")
    @DecimalMin(value = "0.0", inclusive = true, message = "Hạn mức công nợ không được âm")
    private BigDecimal debtLimit;

    @Min(value = 0, message = "Số ngày nợ tối đa không được âm")
    private Integer maxDebtDays;

    @NotBlank(message = "Lý do thay đổi hạn mức không được để trống")
    private String reason;
}
