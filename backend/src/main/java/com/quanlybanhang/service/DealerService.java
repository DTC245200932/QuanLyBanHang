package com.quanlybanhang.service;

import com.quanlybanhang.dto.request.DealerRequest;
import com.quanlybanhang.dto.response.DealerOptionsResponse;
import com.quanlybanhang.dto.response.DealerResponse;
import com.quanlybanhang.model.CustomerGroup;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface DealerService {

    Page<DealerResponse> getAllDealers(
            String keyword,
            CustomerGroup customerGroup,
            Long territoryId,
            Long assignedUserId,
            Boolean active,
            Boolean locked,
            Pageable pageable);

    default Page<DealerResponse> getAllDealers(
            String keyword,
            CustomerGroup customerGroup,
            Long territoryId,
            Long assignedUserId,
            Boolean active,
            Pageable pageable) {
        return getAllDealers(keyword, customerGroup, territoryId, assignedUserId, active, null, pageable);
    }

    List<DealerResponse> getActiveDealers();

    DealerResponse getDealerById(Long id);

    DealerResponse createDealer(DealerRequest request, String currentUsername);

    DealerResponse updateDealer(Long id, DealerRequest request, String currentUsername);

    DealerResponse updateStatus(Long id, boolean active, String currentUsername);

    /**
     * Khoá giao dịch với đại lý (S3-07).
     * Bắt buộc có lý do khoá.
     */
    DealerResponse lockDealer(Long id, String reason, String currentUsername);

    /**
     * Mở khoá giao dịch với đại lý (S3-07).
     */
    DealerResponse unlockDealer(Long id, String currentUsername);

    /**
     * Lấy danh sách đơn hàng đang dở dang của đại lý (S3-07).
     */
    List<com.quanlybanhang.dto.response.OrderResponse> getInProgressOrders(Long dealerId);

    void deleteDealer(Long id, String currentUsername);

    DealerOptionsResponse getDealerOptions();
}

