package com.hub.hub.presentation.controller;

import com.hub.hub.application.service.HubService;
import com.hub.hub.presentation.dto.HubResponseDto;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/internal/v1/hubs")
public class HubInternalController {

    private final HubService hubService;

    public HubInternalController(HubService hubService) {
        this.hubService = hubService;
    }

    @GetMapping("/{hubId}")
    public HubResponseDto getHub(@PathVariable("hubId") UUID hubId) {
        return HubResponseDto.from(hubService.getHub(hubId));
    }
}
