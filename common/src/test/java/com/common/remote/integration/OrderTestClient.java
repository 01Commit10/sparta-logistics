package com.common.remote.integration;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

/** 등록된 인스턴스가 없다 (Eureka 미등록) */
@FeignClient(name = "order-ms")
interface OrderTestClient {

    @GetMapping("/order/ok")
    String ok();
}
