package com.user.presentation.controller;

import com.user.application.service.DeliveryManagerAssignService;
import com.user.presentation.dto.response.DeliveryManagerAssignmentResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * 배송담당자 무작위 배정 내부 API (USR-23). Gateway에 라우트가 없고 차단되므로 외부에서는 호출할 수 없다.
 * 고르기만 하고 상태를 바꾸지 않으므로 GET이다 (결과는 호출마다 다를 수 있다).
 */
@RestController
public class InternalDeliveryManagerController {

    private final DeliveryManagerAssignService deliveryManagerAssignService;

    public InternalDeliveryManagerController(DeliveryManagerAssignService deliveryManagerAssignService) {
        this.deliveryManagerAssignService = deliveryManagerAssignService;
    }

    @GetMapping("/internal/v1/users/delivery-managers/random")
    public DeliveryManagerAssignmentResponse assignRandomly(@RequestParam UUID departureHubId,
                                                            @RequestParam UUID arrivalHubId) {
        return DeliveryManagerAssignmentResponse.from(
                deliveryManagerAssignService.assignRandomly(departureHubId, arrivalHubId));
    }
}
