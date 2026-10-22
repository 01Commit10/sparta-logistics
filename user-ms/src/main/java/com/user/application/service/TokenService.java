package com.user.application.service;

import com.common.exception.AuthErrorCode;
import com.common.exception.BusinessException;
import com.user.application.dto.AuthenticatedUser;
import com.user.application.dto.LogoutCommand;
import com.user.application.dto.TokenPairResult;
import com.user.application.dto.VerifiedRefresh;
import com.user.domain.token.AccessTokenSpec;
import com.user.domain.token.IssuedToken;
import com.user.domain.token.RefreshTokenClaims;
import com.user.domain.token.RefreshTokenParser;
import com.user.domain.token.TokenIssuer;
import com.user.domain.token.TokenStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

/**
 * 토큰 발급·상태 기록 유스케이스 (token 도메인). DB를 쓰지 않으므로 트랜잭션이 없다.
 * 무효화(revokeAll)만 호출한 쪽의 트랜잭션이 있으면 커밋 뒤에 한 번 더 무효화하도록 등록한다.
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

    /** 발급 도중 무효화를 알아내기 위한 기준값. 인증에 쓰는 DB·Redis 값을 읽기 전에 읽는다 (없으면 EPOCH) */
    public Instant revocationSnapshot(UUID userId) {
        return tokenStore.findRevokedAt(userId).orElse(Instant.EPOCH);
    }

    /**
     * Access·Refresh를 발급하고 Refresh jti를 저장한다. 새로 로그인하면 이전 Refresh는 무효가 된다 (세션 1개).
     * revoked-at을 넘겨 무효화 직후 같은 초에 발급한 Access도 Gateway를 통과하게 한다 (U10 D1).
     * <p>
     * 인증(DB·비밀번호·Refresh 확인)과 발급 사이에 무효화가 지나가면 그 세션은 무효화 전 자격으로 받은 것이라 내주지 않는다 (empty).
     * revoked-at은 무효화할 때마다 커지므로(revokeAll) 기준값보다 크면 무효화가 지나간 것이다.
     * 발급 전에 보이면 만들지 않고, 저장 뒤에 보이면 내 Refresh만 지운다. 저장 뒤에 온 무효화는 그 무효화가 Refresh를 지운다.
     * TTL로 revoked-at이 사라진 경우(기준값보다 작음)는 무효화가 아니다.
     */
    public Optional<TokenPairResult> issue(AuthenticatedUser user, Instant revocationSnapshot) {
        Instant revokedAt = revocationSnapshot(user.id());
        if (revokedAt.isAfter(revocationSnapshot)) {
            return Optional.empty();
        }
        IssuedToken access = tokenIssuer.issueAccess(
                new AccessTokenSpec(user.id(), user.role(), user.passwordChangeRequired()), revokedAt);
        IssuedToken refresh = tokenIssuer.issueRefresh(user.id());
        tokenStore.saveRefresh(user.id(), refresh.jti(), refresh.lifetime());
        if (revocationSnapshot(user.id()).isAfter(revocationSnapshot)) {
            tokenStore.deleteRefreshIfMatches(user.id(), refresh.jti());
            log.info("발급 도중 토큰이 무효화되어 발급한 Refresh를 지웠습니다: userId={}", user.id());
            return Optional.empty();
        }
        return Optional.of(new TokenPairResult(access.value(), refresh.value()));
    }

    /**
     * 이 사용자의 모든 토큰을 무효로 한다 (비밀번호 변경·탈퇴·역할 변경·비활성화·비밀번호 초기화).
     * 지금(초) 이전에 발급된 Access를 Gateway가 거부하도록 revoked-at을 Access 유효 시간 동안 걸고, Refresh를 지운다.
     * revoked-at을 먼저 쓴다. 그 사이에 발급이 Refresh를 저장해도 발급 쪽이 저장 뒤 revoked-at을 다시 읽어 알아채거나, 이어지는 삭제가 지운다.
     * 이전 revoked-at과 같은 초에 다시 로그인한 토큰은 iat가 그 다음 초로 미뤄져 있으므로, 새 revoked-at은 이전 값 + 1초 이상이어야
     * 그 토큰까지 막힌다. 미뤄진 만큼 TTL도 늘려, 그 토큰이 만료되기 전에 키가 먼저 사라지지 않게 한다.
     * <p>
     * 호출하는 Facade의 트랜잭션 안에서 부르면 Redis 실패가 DB 변경을 롤백시킨다. 이 기록은 커밋 전이라, 커밋되기 전 틈에는
     * DB에 아직 이전 비밀번호·역할이 보여 그 값으로 로그인할 수 있다. 그래서 커밋 뒤에 한 번 더 무효화한다.
     * 커밋 뒤 무효화가 실패하면 DB는 이미 바뀌었으므로 예외 없이 로그만 남긴다 (커밋 전 무효화는 이미 끝났다).
     */
    public void revokeAll(UUID userId) {
        revokeNow(userId);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try {
                        revokeNow(userId);
                    } catch (RuntimeException e) {
                        log.warn("커밋 뒤 토큰 무효화에 실패했습니다: userId={}, {}", userId, e.getClass().getSimpleName());
                    }
                }
            });
        }
    }

    private void revokeNow(UUID userId) {
        Instant now = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        Instant revokedAt = tokenStore.findRevokedAt(userId)
                .map(previous -> previous.plusSeconds(1))
                .filter(next -> next.isAfter(now))
                .orElse(now);
        tokenStore.revokeAllIssuedBefore(userId, revokedAt,
                Duration.between(now, revokedAt).plus(tokenIssuer.accessTokenLifetime()));
        tokenStore.deleteRefresh(userId);
    }

    /**
     * USR-03 재발급: Refresh Token이 지금 저장된 그 토큰인지 확인한다. 실패 사유는 모두 REFRESH_TOKEN_INVALID.
     * 서명이 맞는데 저장된 jti와 다르면 이미 교체된 토큰이 다시 쓰인 것(유출 의심)이라 저장값을 지운다.
     * 그러면 공격자와 사용자 모두 재발급할 수 없고 다시 로그인해야 한다. 이미 발급된 Access는 만료(30분)까지 남는다 (U07 D2).
     * 읽고 비교한 뒤 저장하는 사이에 같은 토큰으로 온 요청은 둘 다 통과할 수 있다. 다음 재발급에서 재사용으로 걸린다 (U07 D1).
     * 저장값을 보기 전에 revoked-at 기준값을 읽어 함께 돌려준다. 확인한 뒤 무효화가 지나가면 issue가 알아챈다.
     */
    public VerifiedRefresh verifyRefresh(String refreshToken) {
        RefreshTokenClaims claims = refreshTokenParser.parse(refreshToken);
        Instant snapshot = revocationSnapshot(claims.userId());
        String storedJti = tokenStore.findRefreshJti(claims.userId())
                .orElseThrow(() -> new BusinessException(AuthErrorCode.REFRESH_TOKEN_INVALID));
        if (!storedJti.equals(claims.jti())) {
            tokenStore.deleteRefresh(claims.userId());
            log.warn("이미 교체된 Refresh Token이 다시 쓰여 저장된 Refresh를 지웠습니다: userId={}", claims.userId());
            throw new BusinessException(AuthErrorCode.REFRESH_TOKEN_INVALID);
        }
        return new VerifiedRefresh(claims, snapshot);
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
