package com.quanlybanhang.service;

import com.quanlybanhang.dto.request.AssignDealerRequest;
import com.quanlybanhang.dto.request.BulkHandoverRequest;
import com.quanlybanhang.dto.request.DealerRequest;
import com.quanlybanhang.dto.response.BulkHandoverResponse;
import com.quanlybanhang.dto.response.DealerAssignmentHistoryResponse;
import com.quanlybanhang.dto.response.DealerOptionsResponse;
import com.quanlybanhang.dto.response.DealerResponse;
import com.quanlybanhang.model.CustomerGroup;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;

public interface DealerService {

    Page<DealerResponse> getAllDealers(
            String keyword,
            CustomerGroup customerGroup,
            Long territoryId,
            Long assignedUserId,
            Boolean active,
            Pageable pageable);

    List<DealerResponse> getActiveDealers();

    DealerResponse getDealerById(Long id);

    DealerResponse createDealer(DealerRequest request, String currentUsername);

    DealerResponse updateDealer(Long id, DealerRequest request, String currentUsername);

    DealerResponse updateStatus(Long id, boolean active, String currentUsername);

    void deleteDealer(Long id, String currentUsername);

    DealerOptionsResponse getDealerOptions();

    // ── Phân công & Chuyển giao địa bàn (S3-06) ──

    DealerResponse assignDealer(Long id, AssignDealerRequest request, String currentUsername);

    BulkHandoverResponse bulkHandover(BulkHandoverRequest request, String currentUsername);

    List<DealerResponse> getPendingHandoverDealers();

    Page<DealerAssignmentHistoryResponse> getHandoverHistory(
            Long fromUserId,
            Long toUserId,
            Long dealerId,
            Long territoryId,
            LocalDateTime startDate,
            LocalDateTime endDate,
            Pageable pageable);

    List<DealerAssignmentHistoryResponse> getDealerAssignmentHistory(Long dealerId);
}
