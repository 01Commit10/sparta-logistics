package com.common.remote.integration;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

/** 메서드마다 Circuit Breaker가 따로 생긴다 (C02 E1). 이름: HubTestClientok, HubTestClientunstable */
@FeignClient(name = "hub-ms")
interface HubTestClient {

    @GetMapping("/hub/ok")
    String ok();

    @GetMapping("/hub/unstable")
    String unstable();
}
