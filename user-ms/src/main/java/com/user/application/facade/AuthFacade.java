package com.user.application.facade;

import com.common.exception.AuthErrorCode;
import com.common.exception.BusinessException;
import com.common.exception.UserErrorCode;
import com.user.application.dto.AuthenticatedUser;
import com.user.application.dto.LoginCommand;
import com.user.application.dto.LoginResult;
import com.user.application.dto.TokenPairResult;
import com.user.application.dto.UserCredential;
import com.user.application.dto.VerifiedRefresh;
import com.user.application.service.TokenService;
import com.user.application.service.UserQueryService;
import com.user.domain.service.PasswordHasher;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

/**
 * user 도메인(인증)과 token 도메인(발급·저장)을 묶는 인증 흐름 (U05 2장).
 * 로그인·재발급은 DB를 쓰지 않으므로 여기서 트랜잭션을 열지 않는다. 조회는 UserQueryService의 짧은 읽기 트랜잭션에서 끝나고,
 * BCrypt 비교와 Redis 저장은 DB 연결을 잡지 않은 채 한다.
 */
@Service
public class AuthFacade {

    private final UserQueryService userQueryService;
    private final TokenService tokenService;
    private final PasswordHasher passwordHasher;

    public AuthFacade(UserQueryService userQueryService, TokenService tokenService, PasswordHasher passwordHasher) {
        this.userQueryService = userQueryService;
        this.tokenService = tokenService;
        this.passwordHasher = passwordHasher;
    }

    /**
     * USR-02. 인증에 실패하면 토큰을 발급하지 않는다.
     * 없는 ID·틀린 비밀번호·탈퇴(비활성화) 계정은 모두 같은 INVALID_CREDENTIALS (어느 쪽인지 드러내지 않음).
     * 무효화 기준값은 해시를 읽기 전에 읽는다. 해시를 읽은 뒤 비밀번호 변경이 커밋되고 무효화까지 끝난 다음 기준값을 읽으면
     * 그 무효화를 알아챌 수 없다. 발급 도중 무효화가 지나가면 INVALID_CREDENTIALS (역할 변경이었다면 다시 로그인하면 된다).
     */
    public LoginResult login(LoginCommand command) {
        UUID userId = userQueryService.findActiveIdByUsername(command.username()).orElseThrow(AuthFacade::invalidCredentials);
        Instant snapshot = tokenService.revocationSnapshot(userId);
        UserCredential credential = userQueryService.findActiveCredential(userId).orElseThrow(AuthFacade::invalidCredentials);
        if (!passwordHasher.matches(command.password(), credential.passwordHash())) {
            throw invalidCredentials();
        }
        AuthenticatedUser user = credential.user();
        TokenPairResult tokens = tokenService.issue(user, snapshot).orElseThrow(AuthFacade::invalidCredentials);
        return new LoginResult(tokens.accessToken(), tokens.refreshToken(), user.passwordChangeRequired());
    }

    /**
     * USR-03 재발급. 새 Access의 역할·비밀번호 변경 필요 여부는 DB 최신값이다 (토큰 발급 뒤 바뀐 역할 반영).
     * 탈퇴·비활성화된 사용자도 REFRESH_TOKEN_INVALID로 응답해 계정 상태를 드러내지 않는다.
     * 기준값은 verifyRefresh가 저장된 jti를 보기 전에 읽는다. 발급 도중 무효화가 지나가도 REFRESH_TOKEN_INVALID.
     */
    public TokenPairResult reissue(String refreshToken) {
        VerifiedRefresh verified = tokenService.verifyRefresh(refreshToken);
        AuthenticatedUser user = userQueryService.findActiveAuthUser(verified.claims().userId())
                .orElseThrow(AuthFacade::refreshInvalid);
        return tokenService.issue(user, verified.revocationSnapshot()).orElseThrow(AuthFacade::refreshInvalid);
    }

    private static BusinessException invalidCredentials() {
        return new BusinessException(UserErrorCode.INVALID_CREDENTIALS);
    }

    private static BusinessException refreshInvalid() {
        return new BusinessException(AuthErrorCode.REFRESH_TOKEN_INVALID);
    }
}
