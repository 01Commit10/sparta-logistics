package com.user.domain.token;

import java.time.Duration;
import java.util.UUID;

/**
 * 토큰 상태 저장소 Port (docs/plan/02 인증 계약 2.2). 구현은 infrastructure.redis.
 * 조회(U07 재발급)와 revoked-at(U10 등)은 각 기능에서 추가한다.
 */
public interface TokenStore {

    /** 사용자당 Refresh Token은 1개다. 새로 저장하면 이전 jti를 덮어쓴다 */
    void saveRefresh(UUID userId, String jti, Duration ttl);

    void deleteRefresh(UUID userId);

    /** 로그아웃한 Access Token. ttl은 그 토큰이 만료될 때까지 남은 시간 (만료 뒤에는 Gateway가 어차피 거부) */
    void blacklist(String jti, Duration ttl);
}
