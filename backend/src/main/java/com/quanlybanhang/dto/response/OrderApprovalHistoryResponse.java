package com.quanlybanhang.dto.response;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderApprovalHistoryResponse {

    private Long id;
    private Long orderId;
    private String orderCode;

    /**
     * PENDING_APPROVAL, APPROVED, REJECTED, RETURNED
     */
    private String action;

    private String comment;
    private String performedBy;
    private String username;

    private LocalDateTime createdAt;
    private LocalDateTime timestamp;
}
