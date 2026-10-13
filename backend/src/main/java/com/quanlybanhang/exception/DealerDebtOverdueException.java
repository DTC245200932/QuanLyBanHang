package com.quanlybanhang.exception;

import lombok.Getter;

import java.math.BigDecimal;

/**
 * Ngoại lệ ném ra khi đại lý có khoản nợ quá hạn vượt quá số ngày cho phép (S4-02).
 * Quy tắc nghiệp vụ: Đại lý có khoản quá hạn quá số ngày cho phép thì chặn tạo đơn hoàn toàn.
 */
@Getter
public class DealerDebtOverdueException extends RuntimeException {

    private final String dealerCode;
    private final String dealerName;
    private final int overdueDays;
    private final int maxDebtDays;
    private final BigDecimal overdueAmount;

    public DealerDebtOverdueException(String dealerCode, String dealerName, int overdueDays, int maxDebtDays, BigDecimal overdueAmount) {
        super(String.format("Đại lý '%s' (%s) có khoản nợ quá hạn %d ngày (vượt quá giới hạn cho phép %d ngày)%s, bị chặn tạo đơn hoàn toàn.",
                dealerName,
                dealerCode,
                overdueDays,
                maxDebtDays,
                overdueAmount != null ? String.format(" với tổng tiền %s đ", overdueAmount.toPlainString()) : ""));
        this.dealerCode = dealerCode;
        this.dealerName = dealerName;
        this.overdueDays = overdueDays;
        this.maxDebtDays = maxDebtDays;
        this.overdueAmount = overdueAmount;
    }
}

