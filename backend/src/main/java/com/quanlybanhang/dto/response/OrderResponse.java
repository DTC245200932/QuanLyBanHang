package com.quanlybanhang.dto.response;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderResponse {

    private Long id;
    private String orderCode;
    private String customerName;
    private String status;
    private BigDecimal totalAmount;
    private String note;
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime recordedAt;
    private List<OrderItemResponse> items;
}
