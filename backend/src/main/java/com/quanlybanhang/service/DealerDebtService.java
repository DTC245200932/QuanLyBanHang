package com.quanlybanhang.service;

import com.quanlybanhang.dto.request.OrderDebtCheckRequest;
import com.quanlybanhang.dto.response.DealerDebtResponse;
import com.quanlybanhang.dto.response.OrderDebtCheckResponse;
import com.quanlybanhang.model.Dealer;

import java.math.BigDecimal;

/**
 * Service quản lý công nợ, hạn mức và kiểm soát rủi ro nợ của đại lý (S4-02).
 */
public interface DealerDebtService {

    /**
     * Lấy thông tin công nợ, hạn mức và nợ quá hạn của đại lý theo ID.
     * Áp dụng kiểm tra quyền cấp dòng nếu người gọi là nhân viên kinh doanh.
     */
    DealerDebtResponse getDealerDebtInfo(Long dealerId);

    /**
     * Lấy thông tin công nợ, hạn mức và nợ quá hạn của đối tượng đại lý đã nạp.
     */
    DealerDebtResponse getDealerDebtInfo(Dealer dealer);

    /**
     * Kiểm tra sơ bộ hạn mức và trạng thái nợ của đại lý khi đặt đơn mới.
     */
    OrderDebtCheckResponse checkOrderDebt(OrderDebtCheckRequest request);

    /**
     * Kiểm tra sơ bộ hạn mức và trạng thái nợ của đại lý theo ID và số tiền đơn mới.
     */
    OrderDebtCheckResponse checkOrderDebt(Long dealerId, BigDecimal orderAmount);

    /**
     * Kiểm tra đại lý có đủ điều kiện đặt đơn mới không.
     * Nếu đại lý có khoản nợ quá hạn vượt quá số ngày cho phép (maxDebtDays),
     * sẽ ném ra DealerDebtOverdueException để chặn tạo đơn hoàn toàn (S4-02).
     */
    void validateDealerCanCreateOrder(Dealer dealer);

    /**
     * Kiểm tra xem tổng đơn cộng công nợ hiện tại có vượt hạn mức công nợ hay không.
     */
    boolean checkProjectedDebtExceedsLimit(Dealer dealer, BigDecimal newOrderTotal, BigDecimal currentDebt);
}

