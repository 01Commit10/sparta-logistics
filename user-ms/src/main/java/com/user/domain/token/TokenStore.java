package com.user.domain.token;

import java.time.Duration;
import java.util.UUID;

/**
 * 토큰 상태 저장소 Port (docs/plan/02 인증 계약 2.2). 구현은 infrastructure.redis.
 * 조회·삭제(U07 재발급, U08 로그아웃)와 blacklist·revoked-at(U08, U10)은 각 기능에서 추가한다.
 */
public interface TokenStore {

    /** 사용자당 Refresh Token은 1개다. 새로 저장하면 이전 jti를 덮어쓴다 */
    void saveRefresh(UUID userId, String jti, Duration ttl);
}
