package com.gateway.domain.request;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * 요청 추적 ID (GW-08). 한 요청이 Gateway → 서비스 → 다른 서비스로 퍼질 때 로그를 같은 ID로 묶는다.
 * 클라이언트가 보낸 값은 1~64자 [A-Za-z0-9-]일 때만 쓰고, 아니면 새 UUID를 만든다 (로그 주입·과도한 길이 방지).
 */
public record RequestId(String value) {

    private static final Pattern ALLOWED = Pattern.compile("[A-Za-z0-9-]{1,64}");

    public RequestId {
        if (value == null || !ALLOWED.matcher(value).matches()) {
            throw new IllegalArgumentException("요청 ID 형식이 아닙니다");
        }
    }

    public static RequestId of(String candidate, Supplier<UUID> generator) {
        if (candidate != null && ALLOWED.matcher(candidate).matches()) {
            return new RequestId(candidate);
        }
        return new RequestId(Objects.requireNonNull(generator.get()).toString());
    }
}
