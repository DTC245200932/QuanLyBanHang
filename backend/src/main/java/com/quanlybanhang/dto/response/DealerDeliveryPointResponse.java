package com.quanlybanhang.dto.response;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DealerDeliveryPointResponse {
    private Long id;
    private Long dealerId;
    private String receiverName;
    private String phone;
    private String address;
    private String directionNote;
    private Boolean isDefault;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
