package com.user.presentation.check;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 개발 확인용: Postman → Gateway → user-ms 라우팅과 Gateway가 넣은 헤더를 눈으로 확인한다.
 * (docs/Common/로컬 실행·라우팅 확인 가이드.md)
 */
class RoutingCheckControllerTest {

    @Nested
    @WebMvcTest(RoutingCheckController.class)
    class Enabled {

        @Autowired
        MockMvc mockMvc;

        @Test
        @DisplayName("받은 요청의 경로·메서드와 Gateway 관련 헤더를 돌려준다")
        void echoesGatewayHeaders() throws Exception {
            mockMvc.perform(get("/api/v1/auth/routing-check").queryParam("from", "postman")
                            .header("X-User-Id", "00000000-0000-0000-0000-000000000001")
                            .header("X-User-Role", "MASTER")
                            .header("X-Request-Id", "req-1")
                            .header("X-Forwarded-Host", "localhost:19090")
                            .header("Forwarded", "proto=http;host=\"localhost:19090\""))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.service").value("user-ms"))
                    .andExpect(jsonPath("$.method").value("GET"))
                    .andExpect(jsonPath("$.path").value("/api/v1/auth/routing-check"))
                    .andExpect(jsonPath("$.query").value("from=postman"))
                    .andExpect(jsonPath("$.headers.X-User-Id").value("00000000-0000-0000-0000-000000000001"))
                    .andExpect(jsonPath("$.headers.X-User-Role").value("MASTER"))
                    .andExpect(jsonPath("$.headers.X-Request-Id").value("req-1"))
                    .andExpect(jsonPath("$.headers.X-Forwarded-Host").value("localhost:19090"));
        }

        @Test
        @DisplayName("받지 않은 헤더는 응답에 없다")
        void directCall() throws Exception {
            mockMvc.perform(get("/api/v1/auth/routing-check"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.viaGateway").doesNotExist())
                    .andExpect(jsonPath("$.headers.X-User-Id").doesNotExist());
        }

        @Test
        @DisplayName("Authorization 같은 비밀값은 돌려주지 않는다")
        void doesNotEchoSecrets() throws Exception {
            mockMvc.perform(get("/api/v1/auth/routing-check")
                            .header("Authorization", "Bearer secret-token")
                            .header("Cookie", "SESSION=abc"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.headers.Authorization").doesNotExist())
                    .andExpect(jsonPath("$.headers.Cookie").doesNotExist());
        }
    }

    @Nested
    @WebMvcTest(RoutingCheckController.class)
    @TestPropertySource(properties = "app.routing-check.enabled=false")
    class Disabled {

        @Autowired
        MockMvc mockMvc;

        @Test
        @DisplayName("app.routing-check.enabled=false면 엔드포인트가 없다 (제출·운영용)")
        void notRegistered() throws Exception {
            mockMvc.perform(get("/api/v1/auth/routing-check"))
                    .andExpect(status().isNotFound());
        }
    }
}
