package com.company.company.infrastructure.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

// TODO hub-ms에 내부 API(/internal/v1/hubs/{hubId}, 04 API 명세서 Internal API)가 생기면 그쪽으로 바꾼다.
//  지금은 공개 API를 쓰며, 필요한 X-User-Id 헤더는 공통 UserHeaderRelayInterceptor가 이어 보낸다.
@FeignClient(name = "hub-ms", path = "/api/v1/hubs")
interface HubFeignClient {

    @GetMapping("/{hubId}")
    HubFeignDto getHub(@PathVariable("hubId") UUID hubId);
}
