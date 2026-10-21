package com.quanlybanhang.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockCountCreateRequest {

    @NotNull(message = "Kho kiểm kê không được để trống")
    private Long warehouseId;

    /**
     * Tùy chọn: Nhóm hàng cần kiểm kê. Để trống/null nếu kiểm kê toàn bộ kho.
     */
    private Long categoryId;

    /**
     * Tùy chọn: Mã phiếu kiểm kê. Để trống hệ thống sẽ tự sinh.
     */
    private String code;

    /**
     * Ghi chú phiếu kiểm kê.
     */
    private String note;

    /**
     * Tùy chọn: Nhập sẵn số lượng thực tế ban đầu nếu có.
     */
    private List<StockCountItemUpdateRequest> items;
}
