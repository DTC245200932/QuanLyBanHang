package com.quanlybanhang.service.impl;

import com.quanlybanhang.dto.request.DealerDeliveryPointRequest;
import com.quanlybanhang.dto.response.DealerDeliveryPointResponse;
import com.quanlybanhang.model.Dealer;
import com.quanlybanhang.model.DealerDeliveryPoint;
import com.quanlybanhang.repository.DealerDeliveryPointRepository;
import com.quanlybanhang.repository.DealerRepository;
import com.quanlybanhang.service.DealerDeliveryPointService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DealerDeliveryPointServiceImpl implements DealerDeliveryPointService {

    private final DealerDeliveryPointRepository deliveryPointRepository;
    private final DealerRepository dealerRepository;

    @Override
    @Transactional(readOnly = true)
    public List<DealerDeliveryPointResponse> getDeliveryPointsByDealer(Long dealerId) {
        return deliveryPointRepository.findByDealerId(dealerId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public DealerDeliveryPointResponse getDeliveryPoint(Long id) {
        DealerDeliveryPoint point = deliveryPointRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy điểm giao hàng với id: " + id));
        return mapToResponse(point);
    }

    @Override
    @Transactional
    public DealerDeliveryPointResponse createDeliveryPoint(Long dealerId, DealerDeliveryPointRequest request) {
        Dealer dealer = dealerRepository.findById(dealerId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy đại lý với id: " + dealerId));

        boolean isDefault = request.getIsDefault() != null && request.getIsDefault();
        
        // Nếu là điểm giao hàng đầu tiên thì mặc định là true
        List<DealerDeliveryPoint> existingPoints = deliveryPointRepository.findByDealerId(dealerId);
        if (existingPoints.isEmpty()) {
            isDefault = true;
        } else if (isDefault) {
            // Nếu set true thì unset các điểm cũ
            for (DealerDeliveryPoint dp : existingPoints) {
                if (dp.getIsDefault()) {
                    dp.setIsDefault(false);
                    deliveryPointRepository.save(dp);
                }
            }
        }

        DealerDeliveryPoint point = DealerDeliveryPoint.builder()
                .dealer(dealer)
                .receiverName(request.getReceiverName())
                .phone(request.getPhone())
                .address(request.getAddress())
                .directionNote(request.getDirectionNote())
                .isDefault(isDefault)
                .build();

        return mapToResponse(deliveryPointRepository.save(point));
    }

    @Override
    @Transactional
    public DealerDeliveryPointResponse updateDeliveryPoint(Long id, DealerDeliveryPointRequest request) {
        DealerDeliveryPoint point = deliveryPointRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy điểm giao hàng với id: " + id));

        point.setReceiverName(request.getReceiverName());
        point.setPhone(request.getPhone());
        point.setAddress(request.getAddress());
        point.setDirectionNote(request.getDirectionNote());

        if (request.getIsDefault() != null && request.getIsDefault() && !point.getIsDefault()) {
            List<DealerDeliveryPoint> existingPoints = deliveryPointRepository.findByDealerId(point.getDealer().getId());
            for (DealerDeliveryPoint dp : existingPoints) {
                if (dp.getIsDefault() && !dp.getId().equals(id)) {
                    dp.setIsDefault(false);
                    deliveryPointRepository.save(dp);
                }
            }
            point.setIsDefault(true);
        } else if (request.getIsDefault() != null && !request.getIsDefault() && point.getIsDefault()) {
            // Không cho phép bỏ mặc định nếu không có điểm khác làm mặc định (có thể xử lý sau hoặc chặn)
            // Để đơn giản, cho phép nhưng nên cảnh báo hoặc yêu cầu set điểm khác.
            point.setIsDefault(false);
        }

        return mapToResponse(deliveryPointRepository.save(point));
    }

    @Override
    @Transactional
    public void deleteDeliveryPoint(Long id) {
        DealerDeliveryPoint point = deliveryPointRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy điểm giao hàng với id: " + id));
        deliveryPointRepository.delete(point);
    }

    @Override
    @Transactional
    public DealerDeliveryPointResponse setDefaultDeliveryPoint(Long dealerId, Long id) {
        DealerDeliveryPoint point = deliveryPointRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy điểm giao hàng với id: " + id));
        
        if (!point.getDealer().getId().equals(dealerId)) {
            throw new RuntimeException("Điểm giao hàng không thuộc đại lý này");
        }

        List<DealerDeliveryPoint> existingPoints = deliveryPointRepository.findByDealerId(dealerId);
        for (DealerDeliveryPoint dp : existingPoints) {
            if (dp.getIsDefault()) {
                dp.setIsDefault(false);
                deliveryPointRepository.save(dp);
            }
        }

        point.setIsDefault(true);
        return mapToResponse(deliveryPointRepository.save(point));
    }

    private DealerDeliveryPointResponse mapToResponse(DealerDeliveryPoint point) {
        return DealerDeliveryPointResponse.builder()
                .id(point.getId())
                .dealerId(point.getDealer().getId())
                .receiverName(point.getReceiverName())
                .phone(point.getPhone())
                .address(point.getAddress())
                .directionNote(point.getDirectionNote())
                .isDefault(point.getIsDefault())
                .createdAt(point.getCreatedAt())
                .updatedAt(point.getUpdatedAt())
                .build();
    }
}
