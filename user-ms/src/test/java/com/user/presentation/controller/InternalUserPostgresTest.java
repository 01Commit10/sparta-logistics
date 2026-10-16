package com.user.presentation.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 실제 PostgreSQL에서 가입 → 탈퇴 → 같은 Slack ID로 재가입(부분 UNIQUE) → 내부 API 조회까지 확인한다.
 * 실행: sh ./gradlew :user-ms:test -PincludeContainer
 */
@Tag("container")
@Testcontainers
// 컨테이너가 컨텍스트보다 먼저 멈추므로 종료 시 drop(create-drop)을 하지 않는다 (30초 연결 대기 방지)
@SpringBootTest(properties = "spring.jpa.hibernate.ddl-auto=create")
@AutoConfigureMockMvc
class InternalUserPostgresTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    ObjectMapper objectMapper;

    @AfterEach
    void clean() {
        jdbc.update("delete from auth.p_users");
    }

    private UUID signup(String username, String slackId) throws Exception {
        String response = mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                { "username": "%s", "password": "Abcd123!", "name": "김배송", "slackId": "%s",
                                  "organizationType": "HUB", "organizationName": "경기남부허브" }
                                """.formatted(username, slackId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(response).get("id").asText());
    }

    @Test
    @DisplayName("PostgreSQL에 저장된 사용자를 조회한다 (uuid 컬럼 → 응답 id, 배송담당자 반영)")
    void active() throws Exception {
        UUID id = signup("pgint01", "UPG001");
        jdbc.update("update auth.p_users set is_delivery_manager = true where id = ?", id);

        mockMvc.perform(get("/internal/v1/users/{userId}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.isDeliveryManager").value(true))
                .andExpect(jsonPath("$.slackId").value("UPG001"))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    @DisplayName("탈퇴한 사용자의 Slack ID로 새 사용자가 가입하면, 옛 ID는 404이고 새 ID가 그 Slack ID로 조회된다")
    void slackIdReusedAfterWithdraw() throws Exception {
        UUID oldId = signup("pgint02", "UPG002");
        jdbc.update("update auth.p_users set deleted_at = now(), deleted_by = id where id = ?", oldId);

        UUID newId = signup("pgint03", "UPG002");

        mockMvc.perform(get("/internal/v1/users/{userId}", oldId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
        mockMvc.perform(get("/internal/v1/users/{userId}", newId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slackId").value("UPG002"));
        assertThat(jdbc.queryForObject("select count(*) from auth.p_users where slack_id = 'UPG002'", Long.class))
                .isEqualTo(2);
    }

    @Test
    @DisplayName("없는 ID는 404 USER_NOT_FOUND")
    void missing() throws Exception {
        mockMvc.perform(get("/internal/v1/users/{userId}", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
    }
}
