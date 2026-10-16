package com.gateway.application.auth;

import com.gateway.domain.token.TokenState;
import com.gateway.domain.token.TokenStateReader;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** 미리 정한 상태(기본: 유효)나 오류를 돌려주고, 조회 횟수를 기록한다 */
class FakeTokenStateReader implements TokenStateReader {

    private final Mono<TokenState> result;
    private final List<String> reads = new ArrayList<>();

    FakeTokenStateReader() {
        this(Mono.just(TokenState.active()));
    }

    FakeTokenStateReader(Mono<TokenState> result) {
        this.result = result;
    }

    static FakeTokenStateReader failing() {
        return new FakeTokenStateReader(Mono.error(new IllegalStateException("redis down")));
    }

    @Override
    public Mono<TokenState> read(UUID userId, String jti) {
        return Mono.defer(() -> {
            reads.add(userId + "/" + jti);
            return result;
        });
    }

    List<String> reads() {
        return reads;
    }
}
