package com.delivery.infrastructure.client.user;

import com.delivery.infrastructure.client.user.dto.UserResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

@FeignClient(name = "user-ms")
public interface UserFeignClient {

    @GetMapping("/internal/v1/users/{userId}")
    UserResponse getUser(@PathVariable UUID userId);
}
