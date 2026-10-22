package com.hub.hubroute.presentation.controller;

import com.hub.hubroute.application.service.HubRouteService;
import com.hub.hubroute.presentation.dto.HubRouteInternalResponseDto;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/internal/v1/hub-routes")
public class HubRouteInternalController {

    private final HubRouteService hubRouteService;

    public HubRouteInternalController(HubRouteService hubRouteService) {
        this.hubRouteService = hubRouteService;
    }

    @GetMapping
    public HubRouteInternalResponseDto getHubRoute(
            @RequestParam("departureHubId") UUID departureHubId,
            @RequestParam("arrivalHubId") UUID arrivalHubId
    ) {
        return HubRouteInternalResponseDto.from(
                hubRouteService.getHubRoute(departureHubId, arrivalHubId)
        );
    }
}
