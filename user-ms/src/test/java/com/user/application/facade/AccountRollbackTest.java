package com.user.application.facade;

import com.common.exception.AuthErrorCode;
import com.common.exception.BusinessException;
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

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 토큰 무효화(Redis)가 실패하면 비밀번호 변경·탈퇴·비활성화·역할 변경이 DB에 남지 않는다
 * (U10 테스트 6, U18 비밀번호 초기화 포함, @Transactional은 AccountFacade·UserAdminFacade).
 */
@SpringBootTest
@AutoConfigureMockMvc
class AccountRollbackTest {

    /** revoked-at 기록만 실패하는 저장소 (Redis 장애) */
    static class FailingRevokeTokenStore extends FakeTokenStore {
        @Override
        public void revokeAllIssuedBefore(UUID userId, Instant revokedAt, Duration ttl) {
            throw new BusinessException(AuthErrorCode.AUTH_UNAVAILABLE);
        }
    }

    @TestConfiguration
    static class FailingRedis {
        @Bean
        @Primary
        FakeTokenStore failingTokenStore() {
            return new FailingRevokeTokenStore();
        }
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbc;

    private UUID signup(String username) throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON).content("""
                        { "username": "%s", "password": "Abcd123!", "name": "홍길동", "slackId": "U-%s",
                          "organizationType": "HUB", "organizationName": "경기남부허브" }
                        """.formatted(username, username)))
                .andExpect(status().isCreated());
        return jdbc.queryForObject("select id from auth.p_users where username = ?", UUID.class, username);
    }

    @Test
    @DisplayName("비밀번호 변경 중 Redis 실패 → 503, 이전 비밀번호로 계속 로그인된다 (DB 롤백)")
    void changePasswordRollsBack() throws Exception {
        UUID me = signup("rb01");
        String hashBefore = jdbc.queryForObject("select password_hash from auth.p_users where id = ?", String.class, me);

        mockMvc.perform(patch("/api/v1/users/me/password").header("X-User-Id", me.toString()).header("X-User-Role", "USER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"currentPassword\": \"Abcd123!\", \"newPassword\": \"Newpw123!\" }"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("AUTH_UNAVAILABLE"));

        assertThat(jdbc.queryForObject("select password_hash from auth.p_users where id = ?", String.class, me))
                .isEqualTo(hashBefore);
    }

    @Test
    @DisplayName("탈퇴 중 Redis 실패 → 503, 계정은 활성 상태로 남는다 (DB 롤백)")
    void withdrawRollsBack() throws Exception {
        UUID me = signup("rb02");

        mockMvc.perform(delete("/api/v1/users/me").header("X-User-Id", me.toString()).header("X-User-Role", "USER")
                        .contentType(MediaType.APPLICATION_JSON).content("{ \"password\": \"Abcd123!\" }"))
                .andExpect(status().isServiceUnavailable());

        assertThat(jdbc.queryForObject("select deleted_at is null from auth.p_users where id = ?", Boolean.class, me)).isTrue();
    }

    @Test
    @DisplayName("U17: MASTER 비활성화 중 Redis 실패 → 503, 대상은 활성 상태로 남는다 (DB 롤백)")
    void deactivateRollsBack() throws Exception {
        UUID target = signup("rb03");

        mockMvc.perform(delete("/api/v1/users/{userId}", target)
                        .header("X-User-Id", "00000000-0000-0000-0000-0000000000aa").header("X-User-Role", "MASTER"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("AUTH_UNAVAILABLE"));

        assertThat(jdbc.queryForObject("select deleted_at is null from auth.p_users where id = ?", Boolean.class, target))
                .isTrue();
    }

    @Test
    @DisplayName("U15: 역할 변경 중 Redis 실패 → 503, 역할은 그대로다 (DB 롤백)")
    void changeRoleRollsBack() throws Exception {
        UUID target = signup("rb04");

        mockMvc.perform(patch("/api/v1/users/{userId}/role", target)
                        .header("X-User-Id", "00000000-0000-0000-0000-0000000000aa").header("X-User-Role", "MASTER")
                        .contentType(MediaType.APPLICATION_JSON).content("{ \"role\": \"MASTER\" }"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("AUTH_UNAVAILABLE"));

        assertThat(jdbc.queryForObject("select role from auth.p_users where id = ?", String.class, target))
                .isEqualTo("USER");
    }

    @Test
    @DisplayName("U18: 비밀번호 초기화 중 Redis 실패 → 503, 임시 비밀번호가 응답에 없고 이전 비밀번호·변경 불필요 상태 그대로 (DB 롤백)")
    void resetPasswordRollsBack() throws Exception {
        UUID target = signup("rb05");
        String hashBefore = jdbc.queryForObject("select password_hash from auth.p_users where id = ?", String.class, target);

        mockMvc.perform(post("/api/v1/users/{userId}/password-reset", target)
                        .header("X-User-Id", "00000000-0000-0000-0000-0000000000aa").header("X-User-Role", "MASTER"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("AUTH_UNAVAILABLE"))
                .andExpect(jsonPath("$.temporaryPassword").doesNotExist());

        assertThat(jdbc.queryForMap("select password_hash, password_change_required from auth.p_users where id = ?", target))
                .containsEntry("password_hash", hashBefore)
                .containsEntry("password_change_required", false);
    }
}
