package com.quanlybanhang.scheduler;

import com.quanlybanhang.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduler tự động kiểm tra và nhả tồn kho giữ chỗ cho các đơn hàng đã quá hạn giữ chỗ (S5-06).
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.order.reservation-scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class OrderReservationScheduler {

    private final OrderService orderService;

    /**
     * Chạy định kỳ mỗi phút một lần (mặc định) để quét và tự động nhả tồn giữ chỗ cho các đơn quá hạn.
     */
    @Scheduled(fixedDelayString = "${app.order.reservation-check-interval-ms:60000}")
    public void autoReleaseExpiredReservations() {
        try {
            int releasedCount = orderService.releaseExpiredReservations();
            if (releasedCount > 0) {
                log.info("OrderReservationScheduler: Đã tự động nhả tồn giữ chỗ cho {} đơn hàng quá hạn.", releasedCount);
            }
        } catch (Exception e) {
            log.error("OrderReservationScheduler: Lỗi trong quá trình quét và nhả tồn giữ chỗ quá hạn", e);
        }
    }
}
