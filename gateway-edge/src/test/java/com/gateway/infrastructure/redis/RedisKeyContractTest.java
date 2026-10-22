package com.gateway.infrastructure.redis;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 계약 테스트 (GW-07 테스트 12, docs/plan/02 인증 계약 2.2): Gateway가 읽는 키 = user-ms가 쓰는 키.
 * 계약이 바뀌면 깨지도록 AuthContract 상수가 아니라 계약 문서의 문자열로 비교한다.
 */
class RedisKeyContractTest {

    @Test
    @DisplayName("[auth:blacklist:{jti}, auth:revoked-at:{userId}] 순서로 읽는다")
    void keys() {
        UUID userId = UUID.fromString("00000000-0000-0000-0000-000000000001");

        assertThat(RedisTokenStateReader.keys(userId, "jti-1")).containsExactly(
                "auth:blacklist:jti-1",
                "auth:revoked-at:00000000-0000-0000-0000-000000000001");
    }
}
