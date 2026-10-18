package com.quanlybanhang.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/**
 * Danh sách soạn hàng theo đơn hàng kèm vị trí kệ trong kho (S5-03).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderPickingListResponse {

    private Long orderId;
    private String orderCode;
    private String orderStatus;
    private LocalDate expectedDeliveryDate;

    // Đại lý nhận hàng
    private Long dealerId;
    private String dealerCode;
    private String dealerName;

    // Kho soạn hàng
    private Long warehouseId;
    private String warehouseCode;
    private String warehouseName;
    private String warehouseAddress;

    // Danh sách dòng hàng cần soạn với vị trí kệ
    private List<OrderPickingItemResponse> items;
}
