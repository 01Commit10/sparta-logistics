package com.delivery.presentation.controller;

import com.common.auth.AuthUser;
import com.common.auth.web.CurrentUser;
import com.delivery.application.dto.result.DeliveryRouteResult;
import com.delivery.application.service.DeliveryCommandService;
import com.delivery.presentation.dto.request.DeliveryRouteActualMeasurementUpdateRequest;
import com.delivery.presentation.dto.response.DeliveryRouteResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/delivery-routes")
@RequiredArgsConstructor
public class DeliveryRouteController {

    private final DeliveryCommandService deliveryCommandService;


    @PatchMapping("/{routeId}/metrics")
    public DeliveryRouteResponse updateActualMeasurement(@PathVariable UUID routeId,
                                                         @RequestBody @Valid DeliveryRouteActualMeasurementUpdateRequest request,
                                                         @CurrentUser AuthUser user) {
        DeliveryRouteResult result = deliveryCommandService.updateActualMeasurement(routeId, request.toCommand(), user.id(), user.isMaster());
        return DeliveryRouteResponse.from(result);
    }
}
