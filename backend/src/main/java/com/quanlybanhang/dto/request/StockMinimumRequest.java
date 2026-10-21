package com.quanlybanhang.dto.request;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record StockMinimumRequest(
        @NotNull @DecimalMin("0") @Digits(integer = 11, fraction = 4) BigDecimal minimumQuantity,
        @NotBlank @Size(max = 50) String baseUnit) {}
