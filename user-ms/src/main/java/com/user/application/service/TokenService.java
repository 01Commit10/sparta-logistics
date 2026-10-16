package com.user.application.service;

import com.common.exception.AuthErrorCode;
import com.common.exception.BusinessException;
import com.user.application.dto.AuthenticatedUser;
import com.user.application.dto.LogoutCommand;
import com.user.application.dto.TokenPairResult;
import com.user.domain.token.AccessTokenSpec;
import com.user.domain.token.IssuedToken;
import com.user.domain.token.RefreshTokenClaims;
import com.user.domain.token.RefreshTokenParser;
import com.user.domain.token.TokenIssuer;
import com.user.domain.token.TokenStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;

/**
 * 토큰 발급·상태 기록 유스케이스 (token 도메인). DB를 쓰지 않으므로 트랜잭션이 없다.
 */
@Slf4j
@Service
public class TokenService {

    private final TokenIssuer tokenIssuer;
    private final RefreshTokenParser refreshTokenParser;
    private final TokenStore tokenStore;
    private final Clock clock;

    public TokenService(TokenIssuer tokenIssuer, RefreshTokenParser refreshTokenParser, TokenStore tokenStore, Clock clock) {
        this.tokenIssuer = tokenIssuer;
        this.refreshTokenParser = refreshTokenParser;
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
     * USR-03 재발급: Refresh Token이 지금 저장된 그 토큰인지 확인한다. 실패 사유는 모두 REFRESH_TOKEN_INVALID.
     * 서명이 맞는데 저장된 jti와 다르면 이미 교체된 토큰이 다시 쓰인 것(유출 의심)이라 저장값을 지운다.
     * 그러면 공격자와 사용자 모두 재발급할 수 없고 다시 로그인해야 한다. 이미 발급된 Access는 만료(30분)까지 남는다 (U07 D2).
     * 읽고 비교한 뒤 저장하는 사이에 같은 토큰으로 온 요청은 둘 다 통과할 수 있다. 다음 재발급에서 재사용으로 걸린다 (U07 D1).
     */
    public RefreshTokenClaims verifyRefresh(String refreshToken) {
        RefreshTokenClaims claims = refreshTokenParser.parse(refreshToken);
        String storedJti = tokenStore.findRefreshJti(claims.userId())
                .orElseThrow(() -> new BusinessException(AuthErrorCode.REFRESH_TOKEN_INVALID));
        if (!storedJti.equals(claims.jti())) {
            tokenStore.deleteRefresh(claims.userId());
            log.warn("이미 교체된 Refresh Token이 다시 쓰여 저장된 Refresh를 지웠습니다: userId={}", claims.userId());
            throw new BusinessException(AuthErrorCode.REFRESH_TOKEN_INVALID);
        }
        return claims;
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
