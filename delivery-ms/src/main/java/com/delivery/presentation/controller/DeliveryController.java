package com.delivery.presentation.controller;

import com.delivery.application.dto.result.DeliveryResult;
import com.delivery.application.service.DeliveryQueryService;
import com.delivery.presentation.dto.response.DeliveryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/deliveries")
@RequiredArgsConstructor
public class DeliveryController {

    private final DeliveryQueryService deliveryQueryService;


    @GetMapping("/{deliveryId}")
    public ResponseEntity<DeliveryResponse> getDelivery(@PathVariable UUID deliveryId, @RequestHeader("X-User-Id") UUID userId) {
        DeliveryResult result = deliveryQueryService.getDelivery(deliveryId, userId);
        DeliveryResponse deliveryResponse = DeliveryResponse.from(result);

        return ResponseEntity.ok(deliveryResponse);
    }
}
