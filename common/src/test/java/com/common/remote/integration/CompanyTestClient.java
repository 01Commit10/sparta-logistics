package com.common.remote.integration;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

/** 등록된 주소에 아무도 듣지 않는다 (연결 거부) */
@FeignClient(name = "company-ms")
interface CompanyTestClient {

    @GetMapping("/company/ok")
    String ok();
}
