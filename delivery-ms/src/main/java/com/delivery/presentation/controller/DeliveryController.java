package com.delivery.presentation.controller;

import com.common.auth.AuthContract;
import com.common.page.PagePolicy;
import com.common.page.PageResponse;
import com.delivery.application.dto.result.DeliveryResult;
import com.delivery.application.service.DeliveryCommandService;
import com.delivery.application.service.DeliveryQueryService;
import com.delivery.presentation.dto.request.DeliveryManagerAssignRequest;
import com.delivery.presentation.dto.response.DeliveryResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/deliveries")
@RequiredArgsConstructor
public class DeliveryController {

    // 정렬을 허용할 필드(현재는 status만)
    private static final PagePolicy PAGE_POLICY = PagePolicy.of("status");

    private final DeliveryCommandService deliveryCommandService;
    private final DeliveryQueryService deliveryQueryService;


    @GetMapping("/{deliveryId}")
    public ResponseEntity<DeliveryResponse> getDelivery(@PathVariable UUID deliveryId,
                                                        @RequestHeader(AuthContract.HEADER_USER_ID) UUID userId) {
        DeliveryResult result = deliveryQueryService.getDelivery(deliveryId, userId);
        DeliveryResponse deliveryResponse = DeliveryResponse.from(result);

        return ResponseEntity.ok(deliveryResponse);
    }

    @GetMapping
    public ResponseEntity<PageResponse<DeliveryResponse>> getDeliveries(@RequestHeader(AuthContract.HEADER_USER_ID) UUID userId,
                                                                Pageable pageable) {
        Pageable normalized = PAGE_POLICY.apply(pageable);
        Page<DeliveryResult> deliveries = deliveryQueryService.getDeliveries(userId, normalized);

        return ResponseEntity.ok(PageResponse.from(deliveries, DeliveryResponse::from));
    }

    @GetMapping("/by-order/{orderId}")
    public ResponseEntity<DeliveryResponse> getDeliveryByOrderId(@PathVariable UUID orderId,
                                                                 @RequestHeader(AuthContract.HEADER_USER_ID) UUID userId) {
        DeliveryResult result = deliveryQueryService.getDeliveryByOrderId(orderId, userId);
        DeliveryResponse deliveryResponse = DeliveryResponse.from(result);

        return ResponseEntity.ok(deliveryResponse);
    }

    @PatchMapping("/{deliveryId}/manager")
    public ResponseEntity<DeliveryResponse> assignDeliveryManager(@PathVariable UUID deliveryId,
                                                                  @RequestBody @Valid DeliveryManagerAssignRequest request) {

        DeliveryResult result = deliveryCommandService.assignDeliveryManager(deliveryId, request.deliveryManagerId());
        DeliveryResponse deliveryResponse = DeliveryResponse.from(result);

        return ResponseEntity.ok(deliveryResponse);
    }

}
