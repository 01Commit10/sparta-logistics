package com.user.presentation.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 가입 → DB 저장(BCrypt 해시, created_by = 본인) → 중복 가입 409 → ID 중복 확인까지 실제 빈으로 확인한다 (H2).
 */
@SpringBootTest
@AutoConfigureMockMvc
class SignupIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    ObjectMapper objectMapper;

    private static String body(String username, String slackId) {
        return """
                { "username": "%s", "password": "Abcd123!", "name": "홍길동", "slackId": "%s",
                  "organizationType": "HUB", "organizationName": "경기남부허브" }
                """.formatted(username, slackId);
    }

    @Test
    @DisplayName("가입하면 DB에 USER 1행이 생기고, 같은 username·slackId 재가입은 409, ID 확인은 사용 불가")
    void signupFlow() throws Exception {
        mockMvc.perform(get("/api/v1/auth/check-username").queryParam("username", "flow01"))
                .andExpect(jsonPath("$.available").value(true));

        String response = mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON).content(body("flow01", "UFLOW01")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID id = UUID.fromString(objectMapper.readTree(response).get("id").asText());

        Map<String, Object> row = jdbc.queryForMap(
                "select role, password_hash, created_by, created_at, deleted_at, organization_type from auth.p_users where id = ?", id);
        assertThat(row.get("role")).isEqualTo("USER");
        assertThat(row.get("organization_type")).isEqualTo("HUB");
        assertThat((String) row.get("password_hash")).startsWith("$2").doesNotContain("Abcd123!");
        assertThat(row.get("created_by")).isEqualTo(id);
        assertThat(row.get("created_at")).isNotNull();
        assertThat(row.get("deleted_at")).isNull();

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON).content(body("flow01", "UFLOW02")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_USERNAME"));
        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON).content(body("flow02", "UFLOW01")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_SLACK_ID"));
        mockMvc.perform(get("/api/v1/auth/check-username").queryParam("username", "flow01"))
                .andExpect(jsonPath("$.available").value(false));

        assertThat(jdbc.queryForObject("select count(*) from auth.p_users where username like 'flow%'", Long.class))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("본사(HQ)로는 가입할 수 없다 (400 INVALID_ORGANIZATION)")
    void hqNotAllowed() throws Exception {
        JsonNode node = objectMapper.readTree(body("hq0001", "UHQ01"));
        ((ObjectNode) node).put("organizationType", "HQ");

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON).content(node.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ORGANIZATION"));
    }
}
