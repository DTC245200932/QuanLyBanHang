package com.quanlybanhang.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Dữ liệu xem trước khi đặt lại đơn hàng cũ (S5-02).
 * Phân loại rõ ràng dòng hàng khả dụng và dòng hàng đã ngừng kinh doanh/bị loại bỏ.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DealerPortalReorderPreviewResponse {
    private Long sourceOrderId;
    private String sourceOrderCode;
    private Long dealerId;
    private String dealerCode;
    private String dealerName;
    private Long deliveryPointId;
    private String deliveryPointName;
    private String deliveryAddress;
    private LocalDate defaultExpectedDeliveryDate;

    @Builder.Default
    private List<DealerPortalReorderItemResponse> availableItems = new ArrayList<>();

    @Builder.Default
    private List<DealerPortalDiscontinuedItemResponse> discontinuedItems = new ArrayList<>();

    @Builder.Default
    private List<String> warnings = new ArrayList<>();

    private boolean canReorder;

    private OrderCalculateResponse pricing;
    private DealerDebtResponse debt;
}
