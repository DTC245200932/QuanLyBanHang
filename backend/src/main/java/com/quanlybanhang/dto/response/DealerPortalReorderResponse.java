package com.quanlybanhang.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Kết quả đặt lại đơn hàng (S5-02).
 * Bao gồm đơn hàng mới tạo ở trạng thái Chờ duyệt kèm danh sách và thông báo về sản phẩm ngừng kinh doanh bị loại bỏ.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DealerPortalReorderResponse {
    private OrderResponse order;

    @Builder.Default
    private List<DealerPortalDiscontinuedItemResponse> excludedItems = new ArrayList<>();

    @Builder.Default
    private List<String> warnings = new ArrayList<>();
}
