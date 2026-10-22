package com.gateway.domain.token;

import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * 토큰 상태 조회 Port (GW-07). 구현은 infrastructure.redis (MGET 1회). Gateway는 읽기만 한다.
 * 저장소 장애는 Mono.error로 알린다 (호출 쪽이 503 AUTH_UNAVAILABLE로 거부, fail-closed).
 */
public interface TokenStateReader {

    Mono<TokenState> read(UUID userId, String jti);
}
