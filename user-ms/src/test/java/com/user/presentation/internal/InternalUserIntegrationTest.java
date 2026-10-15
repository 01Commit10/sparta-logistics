package com.user.presentation.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 가입 → DB 값 변경(배송담당자 지정·탈퇴·복구) → 내부 API가 DB의 현재 상태를 그대로 돌려주는지 실제 빈으로 확인한다 (H2).
 * 탈퇴 필터(deleted_at is null)는 저장소 쿼리가 하므로 Fake 단위 테스트로는 확인할 수 없다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class InternalUserIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    ObjectMapper objectMapper;

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
    @DisplayName("가입한 사용자를 조회하면 DB 값이 그대로 나오고, 배송담당자 지정이 바로 반영된다")
    void reflectsDatabase() throws Exception {
        UUID id = signup("intg01", "UINTG01");

        mockMvc.perform(get("/internal/v1/users/{userId}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.name").value("김배송"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.isDeliveryManager").value(false))
                .andExpect(jsonPath("$.slackId").value("UINTG01"))
                .andExpect(jsonPath("$.active").value(true));

        jdbc.update("update auth.p_users set is_delivery_manager = true where id = ?", id);

        mockMvc.perform(get("/internal/v1/users/{userId}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isDeliveryManager").value(true));
    }

    @Test
    @DisplayName("탈퇴(deleted_at 기록)하면 404, 재활성화(deleted_at 해제)하면 다시 200")
    void deletedThenRestored() throws Exception {
        UUID id = signup("intg02", "UINTG02");

        jdbc.update("update auth.p_users set deleted_at = now(), deleted_by = id where id = ?", id);
        mockMvc.perform(get("/internal/v1/users/{userId}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));

        jdbc.update("update auth.p_users set deleted_at = null, deleted_by = null where id = ?", id);
        mockMvc.perform(get("/internal/v1/users/{userId}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()));
    }

    @Test
    @DisplayName("DB에 없는 ID는 404, UUID가 아니면 400 (DB 행은 변하지 않는다)")
    void missingAndInvalid() throws Exception {
        long before = jdbc.queryForObject("select count(*) from auth.p_users", Long.class);

        mockMvc.perform(get("/internal/v1/users/{userId}", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
        mockMvc.perform(get("/internal/v1/users/{userId}", "not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        assertThat(jdbc.queryForObject("select count(*) from auth.p_users", Long.class)).isEqualTo(before);
    }
}
