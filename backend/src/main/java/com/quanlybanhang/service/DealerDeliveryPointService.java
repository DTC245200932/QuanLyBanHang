package com.quanlybanhang.service;

import com.quanlybanhang.dto.request.DealerDeliveryPointRequest;
import com.quanlybanhang.dto.response.DealerDeliveryPointResponse;

import java.util.List;

public interface DealerDeliveryPointService {
    List<DealerDeliveryPointResponse> getDeliveryPointsByDealer(Long dealerId);
    DealerDeliveryPointResponse getDeliveryPoint(Long id);
    DealerDeliveryPointResponse createDeliveryPoint(Long dealerId, DealerDeliveryPointRequest request);
    DealerDeliveryPointResponse updateDeliveryPoint(Long id, DealerDeliveryPointRequest request);
    void deleteDeliveryPoint(Long id);
    DealerDeliveryPointResponse setDefaultDeliveryPoint(Long dealerId, Long id);
}
