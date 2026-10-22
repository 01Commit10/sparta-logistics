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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 본인 계정 API를 실제 빈으로 확인한다 (H2, 실제 BCrypt·jjwt·Auditing). Redis만 FakeTokenStore.
 * U09 수정자 기록, U10 무효화 직후 같은 초 재로그인, U11 테스트 7.
 */
@SpringBootTest
@AutoConfigureMockMvc
class MyAccountIntegrationTest {

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

    private UUID signup(String username) throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON).content("""
                        { "username": "%s", "password": "Abcd123!", "name": "홍길동", "slackId": "U-%s",
                          "organizationType": "HUB", "organizationName": "경기남부허브" }
                        """.formatted(username, username)))
                .andExpect(status().isCreated());
        return jdbc.queryForObject("select id from auth.p_users where username = ?", UUID.class, username);
    }

    private JsonNode login(String username, String password) throws Exception {
        return objectMapper.readTree(mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"username\": \"%s\", \"password\": \"%s\" }".formatted(username, password)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private static MockHttpServletRequestBuilder as(UUID userId, MockHttpServletRequestBuilder request) {
        return request.header("X-User-Id", userId.toString()).header("X-User-Role", "USER");
    }

    private static Claims parse(String token) {
        return Jwts.parser().verifyWith(Keys.hmacShaKeyFor(Base64.getDecoder().decode(TEST_SECRET))).build()
                .parseSignedClaims(token).getPayload();
    }

    @Test
    @DisplayName("U09: 내 정보 조회, Slack ID 수정 → DB 반영, 수정자(updated_by)는 본인")
    void myInfo() throws Exception {
        UUID me = signup("myinfo01");

        mockMvc.perform(as(me, get("/api/v1/users/me")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("myinfo01"))
                .andExpect(jsonPath("$.passwordChangeRequired").value(false));
        mockMvc.perform(as(me, patch("/api/v1/users/me")).contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"slackId\": \"U-new01\", \"name\": \"바뀌면안됨\" }"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slackId").value("U-new01"));

        assertThat(jdbc.queryForMap("select slack_id, name, updated_by from auth.p_users where id = ?", me))
                .containsEntry("slack_id", "U-new01")
                .containsEntry("name", "홍길동")
                .containsEntry("updated_by", me);
    }

    @Test
    @DisplayName("U10: 비밀번호 변경 → revoked-at 기록·Refresh 삭제 → 같은 초에 새 비밀번호로 로그인해도 새 Access의 iat > revoked-at")
    void changePasswordThenImmediateLogin() throws Exception {
        UUID me = signup("chpw01");
        login("chpw01", "Abcd123!");

        mockMvc.perform(as(me, patch("/api/v1/users/me/password")).contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"currentPassword\": \"Abcd123!\", \"newPassword\": \"Newpw123!\" }"))
                .andExpect(status().isNoContent());
        JsonNode relogin = login("chpw01", "Newpw123!");

        FakeTokenStore store = (FakeTokenStore) tokenStore;
        Instant revokedAt = store.findRevokedAt(me).orElseThrow();
        Instant newIssuedAt = parse(relogin.get("accessToken").asText()).getIssuedAt().toInstant();
        assertThat(newIssuedAt).isAfter(revokedAt);
        assertThat(store.findRefreshJti(me)).contains(parse(relogin.get("refreshToken").asText()).getId());
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"username\": \"chpw01\", \"password\": \"Abcd123!\" }"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("U11 테스트 7: 탈퇴 → deleted_at·deleted_by(본인) 기록, 토큰 무효화, 같은 ID 재가입 409, 로그인 401")
    void withdraw() throws Exception {
        UUID me = signup("bye01");
        login("bye01", "Abcd123!");

        mockMvc.perform(as(me, delete("/api/v1/users/me")).contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"password\": \"Abcd123!\" }"))
                .andExpect(status().isNoContent());

        assertThat(jdbc.queryForMap("select deleted_at, deleted_by from auth.p_users where id = ?", me))
                .containsEntry("deleted_by", me)
                .extractingByKey("deleted_at").isNotNull();
        FakeTokenStore store = (FakeTokenStore) tokenStore;
        assertThat(store.findRevokedAt(me)).isPresent();
        assertThat(store.findRefreshJti(me)).isEmpty();
        mockMvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON).content("""
                        { "username": "bye01", "password": "Abcd123!", "name": "홍길동", "slackId": "U-other",
                          "organizationType": "HUB", "organizationName": "경기남부허브" }
                        """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_USERNAME"));
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"username\": \"bye01\", \"password\": \"Abcd123!\" }"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(as(me, get("/api/v1/users/me")))
                .andExpect(status().isNotFound());
    }
}
