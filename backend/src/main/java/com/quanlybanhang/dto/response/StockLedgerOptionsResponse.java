package com.quanlybanhang.dto.response;

import java.util.List;

public record StockLedgerOptionsResponse(List<NamedResourceResponse> warehouses, List<NamedResourceResponse> categories,
        List<StatusOption> stockStatuses) {
    public record StatusOption(String value, String label) { }
}
