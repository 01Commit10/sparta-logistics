package com.user.presentation.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.user.domain.token.TokenStore;
import com.user.fake.FakeTokenStore;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 가입 → 로그인 → 발급 토큰 확인까지 실제 빈으로 확인한다 (H2, 실제 BCrypt·jjwt).
 * Redis만 FakeTokenStore로 바꾼다 (실제 Redis는 RedisTokenStoreTest, Postman 실행 확인).
 * 토큰을 테스트 키로 검증해, 테스트가 .env의 운영 키가 아니라 config/application-test.yaml의 키를 쓰는지도 확인한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class LoginIntegrationTest {

    /** src/test/resources/config/application-test.yaml의 jwt.secret-key (테스트 전용) */
    private static final String TEST_SECRET = "dGVzdC1zZWNyZXQta2V5LWZvci11c2VyLW1zLXRva2VuLWlzc3VlciE=";

    @TestConfiguration
    static class FakeRedis {
        @Bean
        @Primary
        FakeTokenStore fakeTokenStore() {
            return new FakeTokenStore();
        }
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    TokenStore tokenStore;

    private void signup(String username) throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON).content("""
                        { "username": "%s", "password": "Abcd123!", "name": "홍길동", "slackId": "U-%s",
                          "organizationType": "HUB", "organizationName": "경기남부허브" }
                        """.formatted(username, username)))
                .andExpect(status().isCreated());
    }

    private String login(String username, String password) {
        return """
                { "username": "%s", "password": "%s" }
                """.formatted(username, password);
    }

    private static Claims parse(String token) {
        return Jwts.parser().verifyWith(Keys.hmacShaKeyFor(Base64.getDecoder().decode(TEST_SECRET))).build()
                .parseSignedClaims(token).getPayload();
    }

    @Test
    @DisplayName("가입한 ID·비밀번호로 로그인하면 계약대로 서명된 두 토큰을 받고, Refresh jti가 7일 TTL로 저장된다")
    void loginFlow() throws Exception {
        signup("login01");
        UUID userId = jdbc.queryForObject("select id from auth.p_users where username = 'login01'", UUID.class);

        String response = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(login("login01", "Abcd123!")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passwordChangeRequired").value(false))
                .andReturn().getResponse().getContentAsString();

        JsonNode body = objectMapper.readTree(response);
        Claims access = parse(body.get("accessToken").asText());
        Claims refresh = parse(body.get("refreshToken").asText());
        assertThat(access.getSubject()).isEqualTo(userId.toString());
        assertThat(access.get("role", String.class)).isEqualTo("USER");
        assertThat(access.get("typ", String.class)).isEqualTo("access");
        assertThat(refresh.get("typ", String.class)).isEqualTo("refresh");

        FakeTokenStore.Saved saved = ((FakeTokenStore) tokenStore).refreshOf(userId).orElseThrow();
        assertThat(saved.jti()).isEqualTo(refresh.getId());
        assertThat(saved.ttl()).isEqualTo(Duration.ofDays(7));
    }

    @Test
    @DisplayName("틀린 비밀번호·없는 ID는 같은 401 INVALID_CREDENTIALS 응답")
    void failuresLookTheSame() throws Exception {
        signup("login02");

        String wrongPassword = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(login("login02", "Wrong123!")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andReturn().getResponse().getContentAsString();
        String unknownUser = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(login("nobody99", "Abcd123!")))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        assertThat(unknownUser).isEqualTo(wrongPassword);
    }

    @Test
    @DisplayName("탈퇴한 계정은 비밀번호가 맞아도 401")
    void withdrawnUser() throws Exception {
        signup("login03");
        jdbc.update("update auth.p_users set deleted_at = now() where username = 'login03'");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(login("login03", "Abcd123!")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }
}
