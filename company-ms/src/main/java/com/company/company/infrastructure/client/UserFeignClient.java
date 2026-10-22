package com.company.company.infrastructure.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

@FeignClient(name = "user-ms", path = "/internal/v1/users")
interface UserFeignClient {

    @GetMapping("/{userId}")
    UserFeignDto getUser(@PathVariable("userId") UUID userId);
}
