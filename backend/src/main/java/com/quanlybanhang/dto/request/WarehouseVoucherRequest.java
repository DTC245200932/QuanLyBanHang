package com.quanlybanhang.dto.request;

import com.quanlybanhang.model.WarehouseVoucherType;
import lombok.*;

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

    private List<WarehouseVoucherItemRequest> items;

    // Các trường tương thích ngược với payload đơn giản: {"productId": 1, "quantity": 50, "unit": "thùng"} hoặc {"delta": 10}
    private Long productId;
    private BigDecimal quantity;
    private String unit;
    private BigDecimal delta;
}
