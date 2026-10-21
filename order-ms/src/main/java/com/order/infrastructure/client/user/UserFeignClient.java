package com.order.infrastructure.client.user;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

@FeignClient(name = "user-ms")
public interface UserFeignClient {

    @GetMapping("/internal/v1/users/{userId}")
    UserFeignResponse getUser(@PathVariable("userId")UUID userId);
}
