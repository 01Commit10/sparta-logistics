package com.order.infrastructure.client.delivery;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.UUID;

@FeignClient(name = "delivery-ms")
public interface DeliveryFeignClient {

    @PostMapping("/internal/v1/deliveries")
    DeliveryFeignResponse createDelivery(@RequestHeader("Idempotency-Key") String idempotencyKey,
                                         @RequestBody DeliveryFeignRequest request);

    @PostMapping("/internal/v1/deliveries/{deliveryId}/cancel")
    void cancel(@PathVariable("deliveryId") UUID deliveryId);
}
