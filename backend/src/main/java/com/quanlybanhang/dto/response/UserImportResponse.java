package com.quanlybanhang.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserImportResponse {
    private int totalRows;
    private int validRows;
    private int invalidRows;
    private int importedRows;
    @Builder.Default
    private List<UserImportRowResponse> rows = new ArrayList<>();
}
