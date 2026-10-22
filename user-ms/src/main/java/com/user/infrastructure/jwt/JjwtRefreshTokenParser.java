package com.user.infrastructure.jwt;

import com.common.auth.AuthContract;
import com.common.exception.AuthErrorCode;
import com.common.exception.BusinessException;
import com.user.domain.token.RefreshTokenClaims;
import com.user.domain.token.RefreshTokenParser;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.Date;
import java.util.UUID;

/**
 * jjwt로 Refresh Token을 검증한다 (docs/plan/02 인증 계약 2.1). gateway-edge의 Access 검증과 같은 순서다:
 * 서명·만료 → typ=refresh → sub(UUID)·jti. 하나라도 어긋나면 REFRESH_TOKEN_INVALID (사유를 구분하지 않음).
 * 예외 메시지에 토큰 원문이 들어갈 수 있어 로그를 남기지 않는다.
 */
@Component
public class JjwtRefreshTokenParser implements RefreshTokenParser {

    private final JwtParser parser;

    public JjwtRefreshTokenParser(JwtProperties properties, Clock clock) {
        this.parser = Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(properties.secretKeyBytes()))
                .clock(() -> Date.from(clock.instant()))
                .build();
    }

    @Override
    public RefreshTokenClaims parse(String token) {
        try {
            Claims claims = parser.parseSignedClaims(token).getPayload();
            if (!AuthContract.TOKEN_TYPE_REFRESH.equals(claims.get(AuthContract.CLAIM_TOKEN_TYPE, String.class))
                    || claims.getSubject() == null || claims.getId() == null) {
                throw invalid();
            }
            return new RefreshTokenClaims(UUID.fromString(claims.getSubject()), claims.getId());
        } catch (JwtException | IllegalArgumentException e) {
            throw invalid();
        }
    }

    private static BusinessException invalid() {
        return new BusinessException(AuthErrorCode.REFRESH_TOKEN_INVALID);
    }
}
