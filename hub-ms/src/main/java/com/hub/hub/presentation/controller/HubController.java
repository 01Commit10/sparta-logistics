package com.hub.hub.presentation.controller;

import com.common.auth.AuthContract;
import com.hub.hub.application.service.HubService;
import com.hub.hub.presentation.dto.HubResponse;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/hubs")
public class HubController {
    private final HubService hubService;

    public HubController(HubService hubService) {
        this.hubService = hubService;
    }
    //전체 허브 조회
    @GetMapping
    public List<HubResponse> getHubs(
            @RequestHeader(AuthContract.HEADER_USER_ID) UUID userId
    ) {
        return hubService.getHubs()
                .stream()
                .map(HubResponse::fromEntity)
                .toList();
    }


    //단일 허브 조회
    @GetMapping("/{hubId}")
    public HubResponse getHub(
            @RequestHeader(AuthContract.HEADER_USER_ID) UUID userId,
            @PathVariable UUID hubId
    ){
        return HubResponse.fromEntity(hubService.getHub(hubId));
    }
}



