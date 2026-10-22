package com.gateway.domain.request;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AccessLogLineTest {

    @Test
    @DisplayName("3. 형식: method path status durationMs requestId userId")
    void format() {
        AccessLogLine line = new AccessLogLine("GET", "/api/v1/hubs", 200, 12, "demo-123",
                "00000000-0000-0000-0000-000000000001");

        assertThat(line.format()).isEqualTo("GET /api/v1/hubs 200 12 demo-123 00000000-0000-0000-0000-000000000001");
    }

    @Test
    @DisplayName("3. 사용자·상태가 없으면 -")
    void absentValues() {
        assertThat(new AccessLogLine("POST", "/api/v1/auth/login", null, 3, "id-1", null).format())
                .isEqualTo("POST /api/v1/auth/login - 3 id-1 -");
    }

    @Test
    @DisplayName("4. 쿼리스트링이 들어간 경로를 넣어도 경로만 출력한다")
    void stripsQuery() {
        assertThat(new AccessLogLine("GET", "/api/v1/auth/check-username?username=secret01&token=abc", 200, 1, "id-1", null).format())
                .isEqualTo("GET /api/v1/auth/check-username 200 1 id-1 -")
                .doesNotContain("secret01").doesNotContain("abc");
    }
}
