package com.user.presentation.controller;

import com.user.domain.token.TokenStore;
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

    @Autowired
    TokenStore tokenStore;

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
    @DisplayName("U17: 비활성화 → 204, deleted_by=MASTER, 대상 토큰 무효, 로그인 401, 내부 API 404, 상세 active=false, 다시 하면 409")
    void deactivate() throws Exception {
        UUID target = signup("deact01", "HUB");
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"username\": \"deact01\", \"password\": \"Abcd123!\" }"))
                .andExpect(status().isOk());
        FakeTokenStore store = (FakeTokenStore) tokenStore;
        assertThat(store.refreshOf(target)).isPresent();

        mockMvc.perform(asMaster(delete("/api/v1/users/{userId}", target)))
                .andExpect(status().isNoContent());

        assertThat(jdbc.queryForMap("select deleted_at, deleted_by from auth.p_users where id = ?", target))
                .containsEntry("deleted_by", MASTER_ID)
                .extractingByKey("deleted_at").isNotNull();
        assertThat(store.revokedOf(target)).isPresent();
        assertThat(store.refreshOf(target)).isEmpty();
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"username\": \"deact01\", \"password\": \"Abcd123!\" }"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        mockMvc.perform(get("/internal/v1/users/{userId}", target)).andExpect(status().isNotFound());
        mockMvc.perform(asMaster(get("/api/v1/users/{userId}", target)))
                .andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.deletedBy").value(MASTER_ID.toString()));
        mockMvc.perform(asMaster(delete("/api/v1/users/{userId}", target)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("USER_ALREADY_DELETED"));
    }

    @Test
    @DisplayName("U17: MASTER 본인 비활성화 → 409 CANNOT_DEACTIVATE_SELF")
    void deactivateSelf() throws Exception {
        UUID self = signup("deact02", "HUB");

        mockMvc.perform(delete("/api/v1/users/{userId}", self)
                        .header("X-User-Id", self.toString()).header("X-User-Role", "MASTER"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CANNOT_DEACTIVATE_SELF"));
    }

    @Test
    @DisplayName("U14: 정보 수정 → 보낸 필드만 DB 반영, 역할·배송담당은 본문에 있어도 그대로, updated_by=MASTER")
    void updateByMaster() throws Exception {
        UUID user = signup("upd01", "COMPANY");

        mockMvc.perform(asMaster(patch("/api/v1/users/{userId}", user)).contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"name\": \"김철수\", \"organizationType\": \"HUB\", \"organizationName\": \"서울센터\", \"role\": \"MASTER\" }"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("김철수"))
                .andExpect(jsonPath("$.role").value("USER"));

        assertThat(jdbc.queryForMap("select name, slack_id, role, organization_type, organization_name, updated_by from auth.p_users where id = ?", user))
                .containsEntry("name", "김철수")
                .containsEntry("slack_id", "U-upd01")
                .containsEntry("role", "USER")
                .containsEntry("organization_type", "HUB")
                .containsEntry("organization_name", "서울센터")
                .containsEntry("updated_by", MASTER_ID);
    }

    @Test
    @DisplayName("U14: 다른 사용자의 Slack ID → 409 DUPLICATE_SLACK_ID")
    void updateDuplicateSlackId() throws Exception {
        UUID user = signup("upd02", "COMPANY");
        signup("upd03", "COMPANY");

        mockMvc.perform(asMaster(patch("/api/v1/users/{userId}", user)).contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"slackId\": \"U-upd03\" }"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_SLACK_ID"));
    }

    @Test
    @DisplayName("U15: USER→MASTER → 200, DB 역할 변경, 대상 토큰 무효. 같은 역할 다시 → 토큰 그대로")
    void changeRole() throws Exception {
        UUID user = signup("role01", "HUB");
        FakeTokenStore store = (FakeTokenStore) tokenStore;

        mockMvc.perform(asMaster(patch("/api/v1/users/{userId}/role", user)).contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"role\": \"MASTER\" }"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("MASTER"))
                .andExpect(jsonPath("$.updatedBy").value(MASTER_ID.toString()));

        assertThat(jdbc.queryForObject("select role from auth.p_users where id = ?", String.class, user)).isEqualTo("MASTER");
        var revokedAt = store.revokedOf(user).orElseThrow();

        mockMvc.perform(asMaster(patch("/api/v1/users/{userId}/role", user)).contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"role\": \"MASTER\" }"))
                .andExpect(status().isOk());
        assertThat(store.revokedOf(user)).contains(revokedAt);
    }

    @Test
    @DisplayName("U16: 없는 사용자 → 404 USER_NOT_FOUND")
    void unknownUser() throws Exception {
        mockMvc.perform(deliveryManager(UUID.randomUUID(), true))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
    }

    private static MockHttpServletRequestBuilder create(String username, String slackId) {
        return asMaster(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON).content("""
                { "username": "%s", "password": "Abcd123!", "name": "김배송", "slackId": "%s",
                  "organizationType": "HUB", "organizationName": "서울센터", "role": "USER", "isDeliveryManager": true }
                """.formatted(username, slackId)));
    }

    @Test
    @DisplayName("U12: 생성 → 201, 작성자는 MASTER, 해시 저장, 내부 API가 배송담당자, 로그인하면 비밀번호 변경 필요")
    void createByMaster() throws Exception {
        mockMvc.perform(create("new01", "U-new01"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.createdBy").value(MASTER_ID.toString()))
                .andExpect(jsonPath("$.passwordChangeRequired").value(true));

        var row = jdbc.queryForMap("select id, created_by, password_hash, password_change_required, role,"
                + " is_delivery_manager from auth.p_users where username = 'new01'");
        assertThat(row).containsEntry("created_by", MASTER_ID).containsEntry("password_change_required", true)
                .containsEntry("role", "USER").containsEntry("is_delivery_manager", true);
        assertThat((String) row.get("password_hash")).startsWith("$2").doesNotContain("Abcd123!");

        mockMvc.perform(get("/internal/v1/users/{userId}", row.get("id")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isDeliveryManager").value(true));
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"username\": \"new01\", \"password\": \"Abcd123!\" }"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passwordChangeRequired").value(true));
    }

    @Test
    @DisplayName("U12: 가입자와 같은 username → 409 DUPLICATE_USERNAME, 하나만 남는다")
    void createDuplicateUsername() throws Exception {
        signup("dupnew01", "HUB");

        mockMvc.perform(create("dupnew01", "U-other"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_USERNAME"));
        assertThat(jdbc.queryForObject("select count(*) from auth.p_users where username = 'dupnew01'", Integer.class))
                .isEqualTo(1);
    }
}
