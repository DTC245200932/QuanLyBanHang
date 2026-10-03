package com.quanlybanhang.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/** Dữ liệu cập nhật hạn mức công nợ của một đại lý. */
@Data
public class DebtLimitRequest {

    @NotNull(message = "Hạn mức công nợ không được để trống")
    @DecimalMin(value = "0.0", inclusive = true, message = "Hạn mức công nợ không được âm")
    private BigDecimal debtLimit;
}
