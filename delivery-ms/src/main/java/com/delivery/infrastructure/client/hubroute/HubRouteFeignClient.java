package com.delivery.infrastructure.client.hubroute;

import com.delivery.infrastructure.client.hubroute.dto.HubRouteResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.UUID;

@FeignClient(name = "hub-ms")
public interface HubRouteFeignClient {

    @GetMapping("/internal/v1/hub-routes")
    HubRouteResponse getHubRoute(@RequestParam("departureHubId") UUID departureHubId,
                                 @RequestParam("arrivalHubId") UUID arrivalHubId);

}
