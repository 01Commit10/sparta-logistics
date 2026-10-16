package com.gateway.application.auth;

import com.common.exception.AuthErrorCode;
import com.common.exception.CommonErrorCode;
import com.gateway.application.auth.dto.AuthRequest;
import com.gateway.application.auth.dto.AuthResult;
import com.gateway.domain.policy.RequestPath;
import com.gateway.domain.policy.RoutePolicy;
import com.gateway.domain.token.AccessTokenClaims;
import com.gateway.domain.token.BearerToken;
import com.gateway.domain.token.TokenStateReader;
import com.gateway.domain.token.TokenVerifier;
import com.gateway.domain.token.UserHeaders;
import com.gateway.domain.token.VerificationResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Optional;

/**
 * Gateway 인증·인가 순서 (docs/plan/gateway GW-05~07, SYS-01 4.2).
 * 경로 확인 → 공개 경로 → 토큰 추출 → 서명·만료 검증 → 토큰 상태(로그아웃·무효화) → 비밀번호 변경 강제 → MASTER 경로 인가 → 사용자 헤더.
 * <ul>
 *     <li>정책은 디코딩한 경로로 비교한다. 해석이 달라질 수 있는 경로는 INVALID_INPUT (RequestPath, BlockedPathFilter가 먼저 거르고 여기서 한 번 더)</li>
 *     <li>토큰 상태는 서명이 유효한 토큰만, 요청당 1번 조회한다. 저장소 오류·응답 없음은 AUTH_UNAVAILABLE (fail-closed)</li>
 *     <li>비밀번호 변경 강제가 MASTER 검사보다 먼저다. 비밀번호가 초기화된 MASTER도 먼저 비밀번호를 바꿔야 한다</li>
 * </ul>
 * 내부 경로 차단(GW-03)은 BlockedPathFilter가 먼저 처리한다.
 */
@Slf4j
@Service
public class AuthenticationService {

    private final RoutePolicy routePolicy;
    private final TokenVerifier tokenVerifier;
    private final TokenStateReader tokenStateReader;

    public AuthenticationService(RoutePolicy routePolicy, TokenVerifier tokenVerifier, TokenStateReader tokenStateReader) {
        this.routePolicy = routePolicy;
        this.tokenVerifier = tokenVerifier;
        this.tokenStateReader = tokenStateReader;
    }

    public Mono<AuthResult> authenticate(AuthRequest rawRequest) {
        Optional<RequestPath> path = RequestPath.parse(rawRequest.path());
        if (path.isEmpty()) {
            return Mono.just(AuthResult.denied(CommonErrorCode.INVALID_INPUT));
        }
        AuthRequest request = new AuthRequest(rawRequest.method(), path.get().value(), rawRequest.authorization());
        if (routePolicy.isPublic(request.method(), request.path())) {
            return Mono.just(AuthResult.anonymous());
        }
        return BearerToken.extract(request.authorization())
                .map(token -> verifyAndAuthorize(request, tokenVerifier.verify(token)))
                .orElseGet(() -> Mono.just(AuthResult.denied(AuthErrorCode.TOKEN_MISSING)));
    }

    private Mono<AuthResult> verifyAndAuthorize(AuthRequest request, VerificationResult verification) {
        if (!verification.isSuccess()) {
            return Mono.just(AuthResult.denied(verification.errorCode().orElseThrow()));
        }
        AccessTokenClaims claims = verification.claims().orElseThrow();
        return tokenStateReader.read(claims.userId(), claims.jti())
                .map(state -> state.isRevoked(claims.issuedAt())
                        ? AuthResult.denied(AuthErrorCode.TOKEN_REVOKED)
                        : authorize(request, claims))
                .switchIfEmpty(Mono.fromSupplier(() -> AuthResult.denied(AuthErrorCode.AUTH_UNAVAILABLE)))
                .onErrorResume(e -> {
                    log.warn("토큰 상태를 읽지 못해 요청을 거부합니다: {}", e.getClass().getSimpleName());
                    return Mono.just(AuthResult.denied(AuthErrorCode.AUTH_UNAVAILABLE));
                });
    }

    private AuthResult authorize(AuthRequest request, AccessTokenClaims claims) {
        if (claims.passwordChangeRequired()
                && !routePolicy.isAllowedDuringPasswordChange(request.method(), request.path())) {
            return AuthResult.denied(AuthErrorCode.PASSWORD_CHANGE_REQUIRED);
        }
        if (routePolicy.requiresMaster(request.method(), request.path()) && !claims.isMaster()) {
            return AuthResult.denied(CommonErrorCode.ACCESS_DENIED);
        }
        return AuthResult.authenticated(UserHeaders.from(claims));
    }
}
