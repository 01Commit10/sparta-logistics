package com.user.domain.token;

import java.util.UUID;

/** 서명·만료·종류(typ=refresh) 검증을 통과한 Refresh Token의 값. 저장된 jti와 같은지는 아직 확인하지 않은 상태다 */
public record RefreshTokenClaims(UUID userId, String jti) {
}
