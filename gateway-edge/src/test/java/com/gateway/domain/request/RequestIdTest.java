package com.gateway.domain.request;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

class RequestIdTest {

    private static final UUID GENERATED = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final Supplier<UUID> GENERATOR = () -> GENERATED;

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("1. 없거나 비어 있으면 새로 만든다")
    void absent(String candidate) {
        assertThat(RequestId.of(candidate, GENERATOR).value()).isEqualTo(GENERATED.toString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0b6a9c1e-3f4d-4e2a-9b7c-1d2e3f4a5b6c", "demo-123", "A", "abcdefghij-abcdefghij-abcdefghij-abcdefghij-abcdefghij-abcdefghi"})
    @DisplayName("1. 형식이 맞으면(1~64자, 영문·숫자·하이픈) 그대로 쓴다")
    void valid(String candidate) {
        assertThat(RequestId.of(candidate, GENERATOR).value()).isEqualTo(candidate);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "abcdefghij-abcdefghij-abcdefghij-abcdefghij-abcdefghij-abcdefghij",   // 65자
            "demo 123", " demo-123", "demo_123", "<script>alert(1)</script>",
            "demo-123\r\nGET /admin 200", "데모-123", "demo.123"})
    @DisplayName("2. 형식이 틀리면(65자 이상, 공백, 스크립트, 줄바꿈, 다른 문자) 새로 만든다")
    void invalid(String candidate) {
        assertThat(RequestId.of(candidate, GENERATOR).value()).isEqualTo(GENERATED.toString());
    }

    @Test
    @DisplayName("만들 때마다 생성기를 부른다 (요청마다 다른 ID)")
    void generatesPerCall() {
        assertThat(RequestId.of(null, UUID::randomUUID)).isNotEqualTo(RequestId.of(null, UUID::randomUUID));
    }
}
