package com.quanlybanhang.dto.request;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockCountCancelRequest {
    private String reason;
}
