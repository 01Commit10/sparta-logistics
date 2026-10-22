package com.user.presentation.controller;

import com.common.auth.AuthContract;
import com.common.auth.AuthUser;
import com.common.auth.web.CurrentUser;
import com.user.application.dto.LogoutCommand;
import com.user.application.facade.AuthFacade;
import com.user.application.service.TokenService;
import com.user.application.service.UserCommandService;
import com.user.application.service.UserQueryService;
import com.user.presentation.dto.request.LoginRequest;
import com.user.presentation.dto.request.ReissueRequest;
import com.user.presentation.dto.request.SignupRequest;
import com.user.presentation.dto.response.LoginResponse;
import com.user.presentation.dto.response.SignupResponse;
import com.user.presentation.dto.response.TokenResponse;
import com.user.presentation.dto.response.UsernameAvailabilityResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * 인증 API (docs/design/04 API 명세서 Auth). 가입·ID 확인·로그인·재발급은 Gateway 공개 경로라 X-User-* 헤더가 없고,
 * 로그아웃은 Gateway가 검증한 토큰의 값(X-User-*, X-Token-*)을 헤더로 받는다.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final UserCommandService userCommandService;
    private final UserQueryService userQueryService;
    private final AuthFacade authFacade;
    private final TokenService tokenService;

    public AuthController(UserCommandService userCommandService, UserQueryService userQueryService,
                          AuthFacade authFacade, TokenService tokenService) {
        this.userCommandService = userCommandService;
        this.userQueryService = userQueryService;
        this.authFacade = authFacade;
        this.tokenService = tokenService;
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

    /** USR-02 로그인 */
    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return LoginResponse.from(authFacade.login(request.toCommand()));
    }

    /** USR-03 토큰 재발급 (Gateway 공개 경로). Refresh도 새로 바뀌므로 응답의 두 토큰으로 모두 교체해야 한다 */
    @PostMapping("/reissue")
    public TokenResponse reissue(@Valid @RequestBody ReissueRequest request) {
        return TokenResponse.from(authFacade.reissue(request.refreshToken()));
    }

    /** USR-04 로그아웃. 토큰을 다시 파싱하지 않는다 (검증은 Gateway 한 곳). 헤더가 없으면 401 (Gateway를 거치지 않은 요청) */
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@CurrentUser AuthUser user,
                       @RequestHeader(AuthContract.HEADER_TOKEN_JTI) String jti,
                       @RequestHeader(AuthContract.HEADER_TOKEN_EXP) long expiresAtEpochSecond) {
        tokenService.logout(new LogoutCommand(user.id(), jti, Instant.ofEpochSecond(expiresAtEpochSecond)));
    }
}
