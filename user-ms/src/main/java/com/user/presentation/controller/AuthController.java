package com.user.presentation.controller;

import com.user.application.service.UserCommandService;
import com.user.application.service.UserQueryService;
import com.user.presentation.dto.request.SignupRequest;
import com.user.presentation.dto.response.SignupResponse;
import com.user.presentation.dto.response.UsernameAvailabilityResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 비회원 인증 API (docs/design/04 API 명세서 Auth). Gateway 공개 경로라 X-User-* 헤더가 없다.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final UserCommandService userCommandService;
    private final UserQueryService userQueryService;

    public AuthController(UserCommandService userCommandService, UserQueryService userQueryService) {
        this.userCommandService = userCommandService;
        this.userQueryService = userQueryService;
    }

    /** USR-01 회원가입 */
    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public SignupResponse signup(@Valid @RequestBody SignupRequest request) {
        return SignupResponse.from(userCommandService.signup(request.toCommand()));
    }

    /** USR-20 ID 중복 확인 */
    @GetMapping("/check-username")
    public UsernameAvailabilityResponse checkUsername(@RequestParam String username) {
        return new UsernameAvailabilityResponse(userQueryService.isUsernameAvailable(username));
    }
}
