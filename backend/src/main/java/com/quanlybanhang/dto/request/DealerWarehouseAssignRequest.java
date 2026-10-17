package com.quanlybanhang.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request gán kho phục vụ mặc định cho đại lý (S5-03).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DealerWarehouseAssignRequest {

    @NotNull(message = "ID kho phục vụ mặc định không được để trống")
    private Long warehouseId;
}

