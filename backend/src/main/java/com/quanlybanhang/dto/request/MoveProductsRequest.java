package com.quanlybanhang.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.List;

/**
 * Request chuyển sản phẩm giữa các nhóm hàng (S2-06).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MoveProductsRequest {

    /**
     * Danh sách ID sản phẩm cần chuyển nhóm.
     */
    @NotEmpty(message = "Danh sách sản phẩm cần chuyển không được để trống")
    private List<Long> productIds;

    /**
     * ID của nhóm hàng đích.
     */
    @NotNull(message = "Nhóm hàng đích không được để trống")
    private Long targetCategoryId;
}
