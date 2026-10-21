package com.quanlybanhang.dto.response;

import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockCountOptionsResponse {

    private List<NamedOption> warehouses;
    private List<NamedOption> categories;
    private List<StatusOption> statuses;
    private boolean canAdjust;

    public record NamedOption(Long id, String code, String name) {}
    public record StatusOption(String code, String label) {}
}
