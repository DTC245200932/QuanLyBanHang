package com.quanlybanhang.service;

import com.quanlybanhang.dto.request.CreateInvoiceAdjustmentRequest;
import com.quanlybanhang.dto.request.CreateInvoiceRequest;
import com.quanlybanhang.dto.request.InvoicePreviewRequest;
import com.quanlybanhang.dto.response.*;
import com.quanlybanhang.model.InvoiceStatus;
import org.springframework.data.domain.Page;

import java.time.LocalDate;
import java.util.List;

/**
 * Service Quản lý Hóa đơn bán hàng và Chứng từ điều chỉnh (S7-01).
 */
public interface InvoiceService {

    /**
     * Xem trước hóa đơn trước khi phát hành.
     */
    InvoicePreviewResponse previewInvoice(InvoicePreviewRequest request, String username);

    /**
     * Phát hành hóa đơn từ các phiếu giao hàng đã chọn.
     */
    InvoiceResponse issueInvoice(CreateInvoiceRequest request, String username);

    /**
     * Lấy chi tiết hóa đơn theo ID.
     */
    InvoiceResponse getById(Long id);

    /**
     * Tìm kiếm và phân trang danh sách hóa đơn.
     */
    Page<InvoiceResponse> search(Long dealerId, InvoiceStatus status, LocalDate startDate, LocalDate endDate, String keyword, int page, int size);

    /**
     * Thử chỉnh sửa hóa đơn (luôn bị từ chối với 400 Bad Request theo tiêu chí S7-01).
     */
    void updateInvoice(Long id);

    /**
     * Thử xóa hóa đơn (luôn bị từ chối với 400 Bad Request theo tiêu chí S7-01).
     */
    void deleteInvoice(Long id);

    /**
     * Lập chứng từ điều chỉnh cho hóa đơn đã phát hành.
     */
    InvoiceAdjustmentResponse createAdjustment(Long invoiceId, CreateInvoiceAdjustmentRequest request, String username);

    /**
     * Lấy danh sách chứng từ điều chỉnh của một hóa đơn.
     */
    List<InvoiceAdjustmentResponse> getAdjustments(Long invoiceId);

    /**
     * Lấy danh sách các phiếu giao hàng hợp lệ sẵn sàng xuất hóa đơn của một đại lý trong kỳ.
     */
    List<EligibleDeliveryOrderResponse> getEligibleOrders(Long dealerId, LocalDate startDate, LocalDate endDate);

    /**
     * Lấy danh sách đại lý có phiếu giao hàng chưa xuất hóa đơn.
     */
    List<EligibleDealerResponse> getDealersWithEligibleOrders();
}

