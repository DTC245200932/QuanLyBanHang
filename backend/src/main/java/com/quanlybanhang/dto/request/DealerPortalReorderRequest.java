package com.quanlybanhang.dto.request;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/**
 * Request đặt lại đơn hàng cũ từ Cổng Đại lý (S5-02).
 * Hỗ trợ đặt lại toàn bộ (items rỗng hoặc null) hoặc một phần dòng hàng.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DealerPortalReorderRequest {

    private Long deliveryPointId;

    private LocalDate expectedDeliveryDate;

    private String note;

    @Valid
    private List<DealerPortalReorderItemRequest> items;
}
