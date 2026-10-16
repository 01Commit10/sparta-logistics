package com.user.presentation.controller;

import com.user.fake.FakeTokenStore;
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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 관리자 사용자 관리 API를 실제 빈으로 확인한다 (H2, 실제 Auditing). Redis만 FakeTokenStore.
 * U16 완료 기준: 지정 후 내부 API가 isDeliveryManager=true를 돌려준다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class UserAdminIntegrationTest {

    private static final UUID MASTER_ID = UUID.fromString("00000000-0000-0000-0000-0000000000aa");

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
    JdbcTemplate jdbc;

    private UUID signup(String username, String organizationType) throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON).content("""
                        { "username": "%s", "password": "Abcd123!", "name": "김배송", "slackId": "U-%s",
                          "organizationType": "%s", "organizationName": "서울센터" }
                        """.formatted(username, username, organizationType)))
                .andExpect(status().isCreated());
        return jdbc.queryForObject("select id from auth.p_users where username = ?", UUID.class, username);
    }

    private static MockHttpServletRequestBuilder deliveryManager(UUID target, boolean value) {
        return patch("/api/v1/users/{userId}/delivery-manager", target)
                .header("X-User-Id", MASTER_ID.toString()).header("X-User-Role", "MASTER")
                .contentType(MediaType.APPLICATION_JSON).content("{ \"isDeliveryManager\": " + value + " }");
    }

    @Test
    @DisplayName("U16: 허브 소속 지정 → DB 반영, 수정자는 MASTER, 내부 API가 true. 해제하면 false")
    void assignThenUnassign() throws Exception {
        UUID driver = signup("dmhub01", "HUB");

        mockMvc.perform(deliveryManager(driver, true))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isDeliveryManager").value(true))
                .andExpect(jsonPath("$.createdBy").value(driver.toString()))
                .andExpect(jsonPath("$.updatedBy").value(MASTER_ID.toString()));

        assertThat(jdbc.queryForMap("select is_delivery_manager, updated_by from auth.p_users where id = ?", driver))
                .containsEntry("is_delivery_manager", true)
                .containsEntry("updated_by", MASTER_ID);
        mockMvc.perform(get("/internal/v1/users/{userId}", driver))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isDeliveryManager").value(true));

        mockMvc.perform(deliveryManager(driver, false))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isDeliveryManager").value(false));
        mockMvc.perform(get("/internal/v1/users/{userId}", driver))
                .andExpect(jsonPath("$.isDeliveryManager").value(false));
    }

    @Test
    @DisplayName("U16: 업체 소속 지정 → 409 DELIVERY_MANAGER_REQUIRES_HUB, DB는 그대로")
    void companyUserRejected() throws Exception {
        UUID staff = signup("dmcom01", "COMPANY");

        mockMvc.perform(deliveryManager(staff, true))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DELIVERY_MANAGER_REQUIRES_HUB"));

        assertThat(jdbc.queryForObject("select is_delivery_manager from auth.p_users where id = ?", Boolean.class, staff))
                .isFalse();
    }

    private static MockHttpServletRequestBuilder asMaster(MockHttpServletRequestBuilder request) {
        return request.header("X-User-Id", MASTER_ID.toString()).header("X-User-Role", "MASTER");
    }

    @Test
    @DisplayName("U13 상세: 활성 사용자 작성자=본인, 탈퇴 후에도 조회되고 active=false·삭제자=본인")
    void detailIncludesDeleted() throws Exception {
        UUID user = signup("detail01", "HUB");

        mockMvc.perform(asMaster(get("/api/v1/users/{userId}", user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("detail01"))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.createdBy").value(user.toString()))
                .andExpect(jsonPath("$.deletedAt").doesNotExist());

        mockMvc.perform(delete("/api/v1/users/me").header("X-User-Id", user.toString()).header("X-User-Role", "USER")
                        .contentType(MediaType.APPLICATION_JSON).content("{ \"password\": \"Abcd123!\" }"))
                .andExpect(status().isNoContent());

        mockMvc.perform(asMaster(get("/api/v1/users/{userId}", user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.deletedAt").isNotEmpty())
                .andExpect(jsonPath("$.deletedBy").value(user.toString()));
    }

    @Test
    @DisplayName("U13 상세: MASTER의 GET /users/me는 상세가 아니라 내 정보 API로 간다 (경로 우선순위)")
    void meIsNotUserId() throws Exception {
        mockMvc.perform(asMaster(get("/api/v1/users/me")))
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
    }

    @Test
    @DisplayName("U16: 없는 사용자 → 404 USER_NOT_FOUND")
    void unknownUser() throws Exception {
        mockMvc.perform(deliveryManager(UUID.randomUUID(), true))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
    }
}
