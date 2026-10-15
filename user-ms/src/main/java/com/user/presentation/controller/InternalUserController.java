package com.user.presentation.controller;

import com.user.application.service.UserQueryService;
import com.user.presentation.dto.response.InternalUserResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * 서비스 간 내부 API. Gateway에 라우트가 없고 차단되므로 외부에서는 호출할 수 없다.
 * Eureka로 직접 호출되므로 X-User-* 헤더를 요구하지 않는다.
 */
@RestController
public class InternalUserController {

    private final UserQueryService userQueryService;

    public InternalUserController(UserQueryService userQueryService) {
        this.userQueryService = userQueryService;
    }

    @GetMapping("/internal/v1/users/{userId}")
    public InternalUserResponse getUser(@PathVariable UUID userId) {
        return InternalUserResponse.from(userQueryService.getActiveUser(userId));
    }
}
