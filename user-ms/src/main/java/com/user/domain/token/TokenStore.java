package com.user.domain.token;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * 토큰 상태 저장소 Port (docs/plan/02 인증 계약 2.2). 구현은 infrastructure.redis.
 */
public interface TokenStore {

    /** 사용자당 Refresh Token은 1개다. 새로 저장하면 이전 jti를 덮어쓴다 */
    void saveRefresh(UUID userId, String jti, Duration ttl);

    /** 지금 유효한 Refresh jti. 로그아웃했거나 7일이 지나 없으면 empty */
    Optional<String> findRefreshJti(UUID userId);

    void deleteRefresh(UUID userId);

    /** 이 시각(초) 이전에 발급된 Access를 모두 무효로 한다 (auth:revoked-at:{userId} = epoch 초). ttl은 Access 유효 시간 */
    void revokeAllIssuedBefore(UUID userId, Instant revokedAt, Duration ttl);

    /** 지금 걸려 있는 revoked-at. 없거나 값이 깨졌으면 empty */
    Optional<Instant> findRevokedAt(UUID userId);

    /** 로그아웃한 Access Token. ttl은 그 토큰이 만료될 때까지 남은 시간 (만료 뒤에는 Gateway가 어차피 거부) */
    void blacklist(String jti, Duration ttl);
}
