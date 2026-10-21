package com.quanlybanhang.dto.response;

import com.quanlybanhang.model.StockCountStatus;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockCountResponse {
    private Long id;
    private String code;
    private Long warehouseId;
    private String warehouseCode;
    private String warehouseName;
    private Long categoryId;
    private String categoryName;
    private StockCountStatus status;
    private String statusLabel;
    private String note;
    private String adjustmentReason;
    private String cancelReason;
    private String createdBy;
    private LocalDateTime createdAt;
    private String completedBy;
    private LocalDateTime completedAt;
    private String cancelledBy;
    private LocalDateTime cancelledAt;
    private int totalItems;
    private int matchedItems;
    private int diffItems;
    private int uncountedItems;
    private boolean canAdjust;
    private boolean canEdit;
    private List<StockCountItemResponse> items;
}
