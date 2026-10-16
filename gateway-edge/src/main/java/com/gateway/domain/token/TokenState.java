package com.gateway.domain.token;

import java.time.Instant;
import java.util.Optional;

/**
 * user-ms가 Redis에 기록한 토큰 상태 (docs/plan/02 인증 계약 2.2). Gateway는 읽기만 한다.
 * <ul>
 *     <li>blacklisted: 로그아웃한 Access Token (auth:blacklist:{jti})</li>
 *     <li>revokedAt: 이 시각 이전(같은 초 포함)에 발급된 그 사용자의 모든 토큰 무효 (auth:revoked-at:{userId}).
 *         비밀번호 변경·탈퇴·역할 변경·비활성화·비밀번호 초기화</li>
 *     <li>corrupted: revoked-at 값이 숫자가 아님. 판단할 수 없으므로 거부한다 (fail-closed)</li>
 * </ul>
 */
public record TokenState(boolean blacklisted, Optional<Instant> revokedAt, boolean corrupted) {

    public static TokenState active() {
        return new TokenState(false, Optional.empty(), false);
    }

    public boolean isRevoked(Instant issuedAt) {
        return blacklisted || corrupted || revokedAt.map(at -> !issuedAt.isAfter(at)).orElse(false);
    }
}
