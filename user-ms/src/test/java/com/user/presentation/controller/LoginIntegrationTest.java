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
 * 가입 → 로그인 → 발급 토큰 확인(U05), 로그아웃(U08), 재발급(U07)까지 실제 빈으로 확인한다 (H2, 실제 BCrypt·jjwt).
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

    @Test
    @DisplayName("U08: 로그인 → 로그아웃(Gateway가 넣는 헤더) → Access jti가 남은 시간 동안 blacklist, Refresh 삭제")
    void loginThenLogout() throws Exception {
        signup("logout01");
        UUID userId = jdbc.queryForObject("select id from auth.p_users where username = 'logout01'", UUID.class);
        String response = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(login("logout01", "Abcd123!")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Claims access = parse(objectMapper.readTree(response).get("accessToken").asText());

        mockMvc.perform(post("/api/v1/auth/logout")
                        .header("X-User-Id", userId.toString())
                        .header("X-User-Role", "USER")
                        .header("X-Token-Jti", access.getId())
                        .header("X-Token-Exp", String.valueOf(access.getExpiration().toInstant().getEpochSecond())))
                .andExpect(status().isNoContent());

        FakeTokenStore store = (FakeTokenStore) tokenStore;
        assertThat(store.refreshOf(userId)).isEmpty();
        assertThat(store.blacklistTtlOf(access.getId()))
                .hasValueSatisfying(ttl -> assertThat(ttl).isBetween(Duration.ofMinutes(29), Duration.ofMinutes(30)));
    }

    private String reissue(String refreshToken) {
        return """
                { "refreshToken": "%s" }
                """.formatted(refreshToken);
    }

    @Test
    @DisplayName("U07: 로그인 → 재발급(새 jti 저장, DB 역할 반영) → 이전 Refresh 재사용 401 → 새 Refresh도 401")
    void reissueRotationAndReuseDetection() throws Exception {
        signup("reissue01");
        UUID userId = jdbc.queryForObject("select id from auth.p_users where username = 'reissue01'", UUID.class);
        JsonNode login = objectMapper.readTree(mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(login("reissue01", "Abcd123!")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        String oldRefresh = login.get("refreshToken").asText();
        jdbc.update("update auth.p_users set role = 'MASTER' where id = ?", userId);

        JsonNode reissued = objectMapper.readTree(mockMvc.perform(post("/api/v1/auth/reissue")
                        .contentType(MediaType.APPLICATION_JSON).content(reissue(oldRefresh)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        Claims newAccess = parse(reissued.get("accessToken").asText());
        Claims newRefresh = parse(reissued.get("refreshToken").asText());
        FakeTokenStore store = (FakeTokenStore) tokenStore;
        assertThat(newAccess.get("role", String.class)).isEqualTo("MASTER");
        assertThat(newRefresh.get("typ", String.class)).isEqualTo("refresh");
        assertThat(store.findRefreshJti(userId)).contains(newRefresh.getId());
        assertThat(newRefresh.getId()).isNotEqualTo(parse(oldRefresh).getId());

        mockMvc.perform(post("/api/v1/auth/reissue").contentType(MediaType.APPLICATION_JSON).content(reissue(oldRefresh)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("REFRESH_TOKEN_INVALID"));
        mockMvc.perform(post("/api/v1/auth/reissue").contentType(MediaType.APPLICATION_JSON)
                        .content(reissue(reissued.get("refreshToken").asText())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("REFRESH_TOKEN_INVALID"));
        assertThat(store.findRefreshJti(userId)).isEmpty();
    }

    @Test
    @DisplayName("U07: Access Token으로 재발급하면 401이고, 저장된 Refresh는 그대로 남는다")
    void reissueWithAccessToken() throws Exception {
        signup("reissue02");
        UUID userId = jdbc.queryForObject("select id from auth.p_users where username = 'reissue02'", UUID.class);
        JsonNode login = objectMapper.readTree(mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(login("reissue02", "Abcd123!")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());

        mockMvc.perform(post("/api/v1/auth/reissue").contentType(MediaType.APPLICATION_JSON)
                        .content(reissue(login.get("accessToken").asText())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("REFRESH_TOKEN_INVALID"));

        assertThat(((FakeTokenStore) tokenStore).findRefreshJti(userId))
                .contains(parse(login.get("refreshToken").asText()).getId());
    }

    @Test
    @DisplayName("U07: 로그아웃한 뒤의 Refresh로는 재발급할 수 없다")
    void reissueAfterLogout() throws Exception {
        signup("reissue03");
        UUID userId = jdbc.queryForObject("select id from auth.p_users where username = 'reissue03'", UUID.class);
        JsonNode login = objectMapper.readTree(mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(login("reissue03", "Abcd123!")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        Claims access = parse(login.get("accessToken").asText());
        mockMvc.perform(post("/api/v1/auth/logout")
                        .header("X-User-Id", userId.toString())
                        .header("X-User-Role", "USER")
                        .header("X-Token-Jti", access.getId())
                        .header("X-Token-Exp", String.valueOf(access.getExpiration().toInstant().getEpochSecond())))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/v1/auth/reissue").contentType(MediaType.APPLICATION_JSON)
                        .content(reissue(login.get("refreshToken").asText())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("REFRESH_TOKEN_INVALID"));
    }
}
