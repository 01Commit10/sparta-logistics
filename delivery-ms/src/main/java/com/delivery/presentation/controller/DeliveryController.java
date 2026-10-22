package com.delivery.presentation.controller;

import com.common.auth.AuthUser;
import com.common.auth.web.CurrentUser;
import com.common.page.PagePolicy;
import com.common.page.PageResponse;
import com.delivery.application.dto.result.DeliveryResult;
import com.delivery.application.service.DeliveryCommandService;
import com.delivery.application.service.DeliveryQueryService;
import com.delivery.presentation.dto.request.DeliveryManagerAssignRequest;
import com.delivery.presentation.dto.request.DeliverySearchRequest;
import com.delivery.presentation.dto.request.DeliveryStatusChangeRequest;
import com.delivery.presentation.dto.response.DeliveryResponse;
import com.delivery.presentation.dto.response.DeliveryRouteResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

import java.util.List;
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
    public DeliveryResponse getDelivery(@PathVariable UUID deliveryId,
                                        @CurrentUser AuthUser user) {
        DeliveryResult result = deliveryQueryService.getDelivery(deliveryId, user.id(), user.isMaster());
        return DeliveryResponse.from(result);
    }

    @GetMapping
    public PageResponse<DeliveryResponse> searchDeliveries(@CurrentUser AuthUser user,
                                                           @ModelAttribute DeliverySearchRequest request,
                                                           Pageable pageable) {
        Pageable normalized = PAGE_POLICY.apply(pageable);
        Page<DeliveryResult> deliveries = deliveryQueryService.searchDeliveries(
                request.toQuery(), user.id(), user.isMaster(), normalized);
        return PageResponse.from(deliveries, DeliveryResponse::from);
    }

    @GetMapping("/by-order/{orderId}")
    public DeliveryResponse getDeliveryByOrderId(@PathVariable UUID orderId,
                                                 @CurrentUser AuthUser user) {
        DeliveryResult result = deliveryQueryService.getDeliveryByOrderId(orderId, user.id(), user.isMaster());
        return DeliveryResponse.from(result);
    }

    @PatchMapping("/{deliveryId}/manager")
    public DeliveryResponse assignDeliveryManager(@PathVariable UUID deliveryId,
                                                  @RequestBody @Valid DeliveryManagerAssignRequest request) {
        DeliveryResult result = deliveryCommandService.assignDeliveryManager(deliveryId, request.toCommand());
        return DeliveryResponse.from(result);
    }

    @PatchMapping("/{deliveryId}/status")
    public DeliveryResponse changeDeliveryStatus(@PathVariable UUID deliveryId,
                                                 @CurrentUser AuthUser user,
                                                 @RequestBody @Valid DeliveryStatusChangeRequest request) {
        DeliveryResult result = deliveryCommandService.changeDeliveryStatus(deliveryId, request.status(), user.id(), user.isMaster());
        return DeliveryResponse.from(result);
    }

    @GetMapping("/{deliveryId}/routes")
    public List<DeliveryRouteResponse> getDeliveryRoutes(@PathVariable UUID deliveryId,
                                                         @CurrentUser AuthUser user) {
        return deliveryQueryService.getDeliveryRoutes(deliveryId, user.id(), user.isMaster()).stream()
                .map(DeliveryRouteResponse::from)
                .toList();
    }
}
