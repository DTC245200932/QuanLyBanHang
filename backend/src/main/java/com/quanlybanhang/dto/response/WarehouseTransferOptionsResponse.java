package com.quanlybanhang.dto.response;

import java.util.List;

public record WarehouseTransferOptionsResponse(List<NamedResourceResponse> sourceWarehouses,
        List<NamedResourceResponse> destinationWarehouses, List<StatusOption> statuses, boolean canCreate) {
    public record StatusOption(String value, String label) {}
}
