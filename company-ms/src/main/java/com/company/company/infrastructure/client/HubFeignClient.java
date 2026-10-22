package com.company.company.infrastructure.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

// hub-ms 내부 API (04 API 명세서 Internal API). 없는 허브는 404
@FeignClient(name = "hub-ms", contextId = "hubFeignClient", path = "/internal/v1/hubs")
interface HubFeignClient {

    @GetMapping("/{hubId}")
    HubFeignDto getHub(@PathVariable("hubId") UUID hubId);
}
