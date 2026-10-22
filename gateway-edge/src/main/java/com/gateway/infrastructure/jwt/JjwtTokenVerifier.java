package com.gateway.infrastructure.jwt;

import com.common.auth.AuthContract;
import com.common.exception.AuthErrorCode;
import com.gateway.domain.token.AccessTokenClaims;
import com.gateway.domain.token.Role;
import com.gateway.domain.token.TokenVerifier;
import com.gateway.domain.token.VerificationResult;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

/**
 * jjwt로 Access Token을 검증한다 (docs/plan/02 인증 계약 2.1).
 * 서명·만료를 확인한 뒤 typ=access와 claim 값을 확인한다. 계약과 다른 값이 하나라도 있으면 TOKEN_INVALID.
 * 실패 원인(예외 메시지)에 토큰 원문이 들어갈 수 있어 로그를 남기지 않는다.
 */
@Component
public class JjwtTokenVerifier implements TokenVerifier {

    private final JwtParser parser;

    public JjwtTokenVerifier(JwtProperties properties, Clock clock) {
        this.parser = Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(properties.secretKeyBytes()))
                .clock(() -> Date.from(clock.instant()))
                .build();
    }

    @Override
    public VerificationResult verify(String token) {
        Claims claims;
        try {
            claims = parser.parseSignedClaims(token).getPayload();
        } catch (ExpiredJwtException e) {
            return VerificationResult.failure(AuthErrorCode.TOKEN_EXPIRED);
        } catch (JwtException | IllegalArgumentException e) {
            return VerificationResult.failure(AuthErrorCode.TOKEN_INVALID);
        }
        return toAccessTokenClaims(claims)
                .map(VerificationResult::success)
                .orElseGet(() -> VerificationResult.failure(AuthErrorCode.TOKEN_INVALID));
    }

    private Optional<AccessTokenClaims> toAccessTokenClaims(Claims claims) {
        try {
            if (!AuthContract.TOKEN_TYPE_ACCESS.equals(claims.get(AuthContract.CLAIM_TOKEN_TYPE, String.class))) {
                return Optional.empty();
            }
            Optional<Role> role = Role.from(claims.get(AuthContract.CLAIM_ROLE, String.class));
            Boolean passwordChangeRequired = claims.get(AuthContract.CLAIM_PASSWORD_CHANGE_REQUIRED, Boolean.class);
            if (role.isEmpty() || passwordChangeRequired == null || claims.getSubject() == null
                    || claims.getId() == null || claims.getIssuedAt() == null || claims.getExpiration() == null) {
                return Optional.empty();
            }
            return Optional.of(new AccessTokenClaims(
                    UUID.fromString(claims.getSubject()),
                    role.get(),
                    passwordChangeRequired,
                    claims.getId(),
                    toInstant(claims.getIssuedAt()),
                    toInstant(claims.getExpiration())));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private static Instant toInstant(Date date) {
        return date.toInstant();
    }
}
