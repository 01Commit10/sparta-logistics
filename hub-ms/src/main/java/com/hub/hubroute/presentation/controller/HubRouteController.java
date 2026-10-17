package com.hub.hubroute.presentation.controller;

import com.common.auth.AuthContract;
import com.hub.hubroute.application.service.HubRouteService;
import com.hub.hubroute.presentation.dto.HubRouteResponseDto;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/hub-routes")
public class HubRouteController {
    private final HubRouteService hubRouteService;

    public HubRouteController(HubRouteService hubRouteService) {
        this.hubRouteService = hubRouteService;
    }

    @GetMapping
    public List<HubRouteResponseDto> getHubRoutes(
            @RequestHeader(AuthContract.HEADER_USER_ID) UUID userId
    ) {
            return hubRouteService.getHubRoutes()
                    .stream()
                    .map(HubRouteResponseDto::from)
                    .toList();
    }

    @GetMapping("/{routeId}")
    public HubRouteResponseDto getHubRoute(
            @RequestHeader(AuthContract.HEADER_USER_ID) UUID userId,
            @PathVariable("routeId") UUID routeId
    ) {
        return HubRouteResponseDto.from(
                hubRouteService.getHubRoute(routeId)
        );
    }

    @GetMapping("/fromtosearch")
    public HubRouteResponseDto getHubRouteByHubs(
            @RequestHeader(AuthContract.HEADER_USER_ID) UUID userId,
            @RequestParam("fromHubId") UUID fromHubId,
            @RequestParam("toHubId") UUID toHubId
    ){
        return HubRouteResponseDto.from(
                hubRouteService.getHubRoute(fromHubId, toHubId)
        );

    }

}
