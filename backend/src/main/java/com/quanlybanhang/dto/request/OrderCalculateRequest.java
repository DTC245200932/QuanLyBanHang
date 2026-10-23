package com.quanlybanhang.dto.request;

import com.quanlybanhang.model.CustomerGroup;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.*;

import java.time.LocalDate;
import java.util.List;

/**
 * Request tính toán lại toàn bộ giá và chiết khấu cho đơn hàng (S4-01).
 * Dùng khi thêm/sửa/xóa dòng hàng hoặc thay đổi số lượng, sửa giá thủ công.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderCalculateRequest {

    private Long dealerId;

    private Long warehouseId;

    private CustomerGroup customerGroup;

    private Long priceListId;

    private LocalDate orderDate;

    @NotEmpty(message = "Danh sách dòng hàng không được để trống")
    @Valid
    private List<OrderItemRequest> items;
}

