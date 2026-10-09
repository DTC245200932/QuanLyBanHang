package com.quanlybanhang.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Response kết quả chuyển giao địa bàn và đại lý hàng loạt (S3-06).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkHandoverResponse {

    private int totalTransferred;

    private Long fromUserId;
    private String fromUsername;
    private String fromUserFullName;

    private Long toUserId;
    private String toUsername;
    private String toUserFullName;

    private Long territoryId;
    private String territoryName;

    private List<Long> transferredDealerIds;
    private List<String> transferredDealerNames;

    private String reason;
    private LocalDateTime transferredAt;
    private String performedByUsername;
}

