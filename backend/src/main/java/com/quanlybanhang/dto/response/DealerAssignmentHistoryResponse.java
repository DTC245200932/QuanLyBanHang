package com.quanlybanhang.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Response chi tiết bản ghi lịch sử phân công/chuyển giao đại lý (S3-06).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DealerAssignmentHistoryResponse {

    private Long id;

    private Long dealerId;
    private String dealerCode;
    private String dealerName;

    private Long fromUserId;
    private String fromUsername;
    private String fromUserFullName;

    private Long toUserId;
    private String toUsername;
    private String toUserFullName;

    private Long territoryId;
    private String territoryName;

    private String transferType;
    private String reason;

    private Long performedByUserId;
    private String performedByUsername;
    private String performedByFullName;

    private LocalDateTime transferredAt;
}

