package com.quanlybanhang.dto.request;

import com.quanlybanhang.model.WarehouseVoucherType;
import lombok.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WarehouseVoucherRequest {

    private String voucherCode;

    private Long warehouseId;

    private WarehouseVoucherType type;

    private String note;

    private List<@NotNull @Valid WarehouseVoucherItemRequest> items;

    // Các trường tương thích ngược với payload đơn giản: {"productId": 1, "quantity": 50, "unit": "thùng"} hoặc {"delta": 10}
    private Long productId;
    private BigDecimal quantity;
    private String unit;
    private BigDecimal delta;

    @JsonIgnore
    @AssertTrue(message = "Phiếu kho phải có ít nhất 1 sản phẩm")
    public boolean isProductSelectionValid() {
        return (items != null && !items.isEmpty()) || productId != null;
    }
}
