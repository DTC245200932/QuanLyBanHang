package com.quanlybanhang.controller;

import com.quanlybanhang.dto.request.DealerDeliveryPointRequest;
import com.quanlybanhang.dto.response.DealerDeliveryPointResponse;
import com.quanlybanhang.service.DealerDeliveryPointService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/dealers")
@RequiredArgsConstructor
public class DealerDeliveryPointController {

    private final DealerDeliveryPointService deliveryPointService;

    @GetMapping("/{dealerId}/delivery-points")
    @PreAuthorize("hasRole('ADMIN') or (hasAnyAuthority('CUSTOMER_VIEW', 'ORDER_CREATE', 'ORDER_VIEW'))")
    public ResponseEntity<List<DealerDeliveryPointResponse>> getDeliveryPoints(@PathVariable Long dealerId) {
        return ResponseEntity.ok(deliveryPointService.getDeliveryPointsByDealer(dealerId));
    }

    @PostMapping("/{dealerId}/delivery-points")
    @PreAuthorize("hasRole('ADMIN') or (hasAuthority('CUSTOMER_EDIT'))")
    public ResponseEntity<DealerDeliveryPointResponse> createDeliveryPoint(
            @PathVariable Long dealerId,
            @Valid @RequestBody DealerDeliveryPointRequest request) {
        return ResponseEntity.ok(deliveryPointService.createDeliveryPoint(dealerId, request));
    }

    @GetMapping("/delivery-points/{id}")
    @PreAuthorize("hasRole('ADMIN') or (hasAnyAuthority('CUSTOMER_VIEW', 'ORDER_CREATE', 'ORDER_VIEW'))")
    public ResponseEntity<DealerDeliveryPointResponse> getDeliveryPoint(@PathVariable Long id) {
        return ResponseEntity.ok(deliveryPointService.getDeliveryPoint(id));
    }

    @PutMapping("/delivery-points/{id}")
    @PreAuthorize("hasRole('ADMIN') or (hasAuthority('CUSTOMER_EDIT'))")
    public ResponseEntity<DealerDeliveryPointResponse> updateDeliveryPoint(
            @PathVariable Long id,
            @Valid @RequestBody DealerDeliveryPointRequest request) {
        return ResponseEntity.ok(deliveryPointService.updateDeliveryPoint(id, request));
    }

    @DeleteMapping("/delivery-points/{id}")
    @PreAuthorize("hasRole('ADMIN') or (hasAuthority('CUSTOMER_EDIT'))")
    public ResponseEntity<Void> deleteDeliveryPoint(@PathVariable Long id) {
        deliveryPointService.deleteDeliveryPoint(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{dealerId}/delivery-points/{id}/default")
    @PreAuthorize("hasRole('ADMIN') or (hasAuthority('CUSTOMER_EDIT'))")
    public ResponseEntity<DealerDeliveryPointResponse> setDefaultDeliveryPoint(
            @PathVariable Long dealerId,
            @PathVariable Long id) {
        return ResponseEntity.ok(deliveryPointService.setDefaultDeliveryPoint(dealerId, id));
    }
}
