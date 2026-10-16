package com.user.application.facade;

import com.user.application.dto.AuthenticatedUser;
import com.user.application.dto.LoginCommand;
import com.user.application.dto.LoginResult;
import com.user.application.dto.TokenPairResult;
import com.user.application.service.TokenService;
import com.user.application.service.UserQueryService;
import org.springframework.stereotype.Service;

/**
 * user 도메인(인증)과 token 도메인(발급·저장)을 묶는 인증 흐름 (U05 2장).
 * 로그인은 DB를 쓰지 않으므로 여기서 트랜잭션을 열지 않는다. 인증 조회는 UserQueryService의 읽기 트랜잭션에서 끝나고,
 * Redis 저장은 DB 연결을 잡지 않은 채 한다.
 */
@Service
public class AuthFacade {

    private final UserQueryService userQueryService;
    private final TokenService tokenService;

    public AuthFacade(UserQueryService userQueryService, TokenService tokenService) {
        this.userQueryService = userQueryService;
        this.tokenService = tokenService;
    }

    /** USR-02. 인증에 실패하면 토큰을 발급하지 않는다 */
    public LoginResult login(LoginCommand command) {
        AuthenticatedUser user = userQueryService.authenticate(command.username(), command.password());
        TokenPairResult tokens = tokenService.issue(user);
        return new LoginResult(tokens.accessToken(), tokens.refreshToken(), user.passwordChangeRequired());
    }
}
