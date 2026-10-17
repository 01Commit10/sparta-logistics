package com.delivery.presentation.controller;

import com.common.auth.AuthContract;
import com.delivery.application.dto.result.DeliveryResult;
import com.delivery.application.service.DeliveryQueryService;
import com.delivery.presentation.dto.response.DeliveryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/deliveries")
@RequiredArgsConstructor
public class DeliveryController {

    private final DeliveryQueryService deliveryQueryService;


    @GetMapping("/{deliveryId}")
    public ResponseEntity<DeliveryResponse> getDelivery(@PathVariable UUID deliveryId,
                                                        @RequestHeader(AuthContract.HEADER_USER_ID) UUID userId) {
        DeliveryResult result = deliveryQueryService.getDelivery(deliveryId, userId);
        DeliveryResponse deliveryResponse = DeliveryResponse.from(result);

        return ResponseEntity.ok(deliveryResponse);
    }

    @GetMapping
    public ResponseEntity<Page<DeliveryResponse>> getDeliveries(@RequestHeader(AuthContract.HEADER_USER_ID) UUID userId,
                                                                Pageable pageable) {
        Page<DeliveryResult> deliveries = deliveryQueryService.getDeliveries(userId, pageable);

        return ResponseEntity.ok(deliveries.map(DeliveryResponse::from));
    }

}
