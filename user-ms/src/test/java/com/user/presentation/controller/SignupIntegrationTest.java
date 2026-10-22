package com.user.presentation.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;
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
    @ParameterizedTest(name = "{0}")
    @DisplayName("username·비밀번호 규칙을 어기면 400이고 DB에 행이 생기지 않는다")
    @CsvSource({
            "username 대문자,     Bad01,      Abcd123!,   INVALID_USERNAME",
            "username 3자,        abc,        Abcd123!,   INVALID_USERNAME",
            "username 11자,       abcdefghijk, Abcd123!,  INVALID_USERNAME",
            "비밀번호 7자,        pw0001,     Abc123!,    INVALID_PASSWORD",
            "비밀번호 16자,       pw0002,     Abcd1234!Abcd12x, INVALID_PASSWORD",
            "비밀번호 특수문자 없음, pw0003,  Abcd1234,   INVALID_PASSWORD",
            "비밀번호 대문자 없음, pw0004,    abcd123!,   INVALID_PASSWORD"
    })
    void invalidUsernameOrPassword(String caseName, String username, String password, String code) throws Exception {
        long before = jdbc.queryForObject("select count(*) from auth.p_users", Long.class);
        JsonNode node = objectMapper.readTree(body(username, "UINV" + Math.abs(caseName.hashCode())));
        ((ObjectNode) node).put("password", password);

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON).content(node.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(code));

        assertThat(jdbc.queryForObject("select count(*) from auth.p_users", Long.class)).isEqualTo(before);
    }

    @Test
    @DisplayName("가입 응답에는 정해진 7개 필드만 있고 비밀번호·해시·감사 필드는 없다")
    void responseFieldsOnly() throws Exception {
        String response = mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON).content(body("resp01", "URESP01")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        JsonNode json = objectMapper.readTree(response);
        List<String> fields = new ArrayList<>();
        json.fieldNames().forEachRemaining(fields::add);
        assertThat(fields).containsExactlyInAnyOrder(
                "id", "username", "name", "slackId", "role", "organizationType", "organizationName");
        assertThat(response).doesNotContain("Abcd123!").doesNotContain("$2");
        assertThat(json.get("role").asText()).isEqualTo("USER");
    }
}
