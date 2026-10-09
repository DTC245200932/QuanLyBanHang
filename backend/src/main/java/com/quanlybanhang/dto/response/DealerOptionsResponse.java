package com.quanlybanhang.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Dữ liệu danh mục tham chiếu phục vụ tạo/sửa hồ sơ đại lý (S3-03).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DealerOptionsResponse {
    private List<CustomerGroupOption> customerGroups;
    private List<NamedResourceResponse> territories;
    private List<AssignedUserOption> assignedUsers;

    public record CustomerGroupOption(String value, String displayName) {}
    public record AssignedUserOption(Long id, String username, String fullName) {}
}

