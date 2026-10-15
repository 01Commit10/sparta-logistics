package com.gateway.application.auth;

import com.common.exception.AuthErrorCode;
import com.common.exception.CommonErrorCode;
import com.gateway.application.auth.dto.AuthRequest;
import com.gateway.application.auth.dto.AuthResult;
import com.gateway.domain.policy.RoutePolicy;
import com.gateway.domain.token.AccessTokenClaims;
import com.gateway.domain.token.BearerToken;
import com.gateway.domain.token.TokenVerifier;
import com.gateway.domain.token.UserHeaders;
import com.gateway.domain.token.VerificationResult;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Gateway 인증·인가 순서 (docs/plan/gateway GW-05·06, SYS-01 4.2).
 * 공개 경로 → 토큰 추출 → 서명·만료 검증 → MASTER 경로 인가 → 사용자 헤더.
 * 내부 경로 차단(GW-03)은 BlockedPathFilter가 먼저 처리한다.
 * GW-07에서 검증 뒤에 토큰 상태(Redis)·비밀번호 변경 강제 단계를 추가하므로 Mono로 둔다.
 */
@Service
public class AuthenticationService {

    private final RoutePolicy routePolicy;
    private final TokenVerifier tokenVerifier;

    public AuthenticationService(RoutePolicy routePolicy, TokenVerifier tokenVerifier) {
        this.routePolicy = routePolicy;
        this.tokenVerifier = tokenVerifier;
    }

    public Mono<AuthResult> authenticate(AuthRequest request) {
        if (routePolicy.isPublic(request.method(), request.path())) {
            return Mono.just(AuthResult.anonymous());
        }
        return Mono.just(BearerToken.extract(request.authorization())
                .map(token -> authorize(request, tokenVerifier.verify(token)))
                .orElseGet(() -> AuthResult.denied(AuthErrorCode.TOKEN_MISSING)));
    }

    private AuthResult authorize(AuthRequest request, VerificationResult verification) {
        if (!verification.isSuccess()) {
            return AuthResult.denied(verification.errorCode().orElseThrow());
        }
        AccessTokenClaims claims = verification.claims().orElseThrow();
        if (routePolicy.requiresMaster(request.method(), request.path()) && !claims.isMaster()) {
            return AuthResult.denied(CommonErrorCode.ACCESS_DENIED);
        }
        return AuthResult.authenticated(UserHeaders.from(claims));
    }
}
