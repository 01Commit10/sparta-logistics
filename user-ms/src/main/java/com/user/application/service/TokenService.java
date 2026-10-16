package com.user.application.service;

import com.user.application.dto.AuthenticatedUser;
import com.user.application.dto.LogoutCommand;
import com.user.application.dto.TokenPairResult;
import com.user.domain.token.AccessTokenSpec;
import com.user.domain.token.IssuedToken;
import com.user.domain.token.TokenIssuer;
import com.user.domain.token.TokenStore;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;

/**
 * 토큰 발급·상태 기록 유스케이스 (token 도메인). DB를 쓰지 않으므로 트랜잭션이 없다.
 */
@Service
public class TokenService {

    private final TokenIssuer tokenIssuer;
    private final TokenStore tokenStore;
    private final Clock clock;

    public TokenService(TokenIssuer tokenIssuer, TokenStore tokenStore, Clock clock) {
        this.tokenIssuer = tokenIssuer;
        this.tokenStore = tokenStore;
        this.clock = clock;
    }

    /** Access·Refresh를 발급하고 Refresh jti를 저장한다. 새로 로그인하면 이전 Refresh는 무효가 된다 (세션 1개) */
    public TokenPairResult issue(AuthenticatedUser user) {
        IssuedToken access = tokenIssuer.issueAccess(
                new AccessTokenSpec(user.id(), user.role(), user.passwordChangeRequired()));
        IssuedToken refresh = tokenIssuer.issueRefresh(user.id());
        tokenStore.saveRefresh(user.id(), refresh.jti(), refresh.lifetime());
        return new TokenPairResult(access.value(), refresh.value());
    }

    /**
     * USR-04 로그아웃: 이 Access Token을 즉시 무효로 하고 Refresh도 지운다. 다시 호출해도 결과가 같다.
     * Refresh(7일)를 먼저 지운다. 중간에 Redis가 실패해 503이 나도 오래 쓸 수 있는 토큰이 남지 않게 하고, 클라이언트는 다시 로그아웃하면 된다.
     * 이미 만료된 Access Token은 Gateway가 어차피 거부하므로 blacklist에 올리지 않는다.
     */
    public void logout(LogoutCommand command) {
        tokenStore.deleteRefresh(command.userId());
        Duration remaining = Duration.between(clock.instant(), command.expiresAt());
        if (remaining.isPositive()) {
            tokenStore.blacklist(command.jti(), remaining);
        }
    }
}
