package com.user.application.facade;

import com.user.domain.service.PasswordHasher;
import com.user.fake.FakeTokenStore;
import com.user.infrastructure.security.BCryptPasswordHasher;
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
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 리뷰 지적의 트랜잭션 경계를 실제 빈으로 확인한다 (H2, 실제 BCrypt, Redis만 FakeTokenStore).
 * <ul>
 *     <li>로그인의 BCrypt 비교는 트랜잭션 밖에서 한다 (DB 연결을 잡은 채 수십 ms 걸리는 계산을 하지 않음)</li>
 *     <li>비밀번호 변경은 커밋 전에 한 번, 커밋 뒤에 한 번 더 무효화한다</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
class AuthTransactionBoundaryTest {

    /** matches를 부를 때 트랜잭션이 열려 있었는지 기록한다 */
    static class RecordingPasswordHasher extends BCryptPasswordHasher {
        final List<Boolean> matchesInTransaction = new CopyOnWriteArrayList<>();

        @Override
        public boolean matches(String rawPassword, String passwordHash) {
            matchesInTransaction.add(TransactionSynchronizationManager.isActualTransactionActive());
            return super.matches(rawPassword, passwordHash);
        }
    }

    @TestConfiguration
    static class Beans {
        @Bean
        @Primary
        FakeTokenStore fakeTokenStore() {
            return new FakeTokenStore();
        }

        @Bean
        @Primary
        RecordingPasswordHasher recordingPasswordHasher() {
            return new RecordingPasswordHasher();
        }
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PasswordHasher passwordHasher;

    @Autowired
    FakeTokenStore tokenStore;

    private UUID signup(String username) throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON).content("""
                        { "username": "%s", "password": "Abcd123!", "name": "홍길동", "slackId": "U-%s",
                          "organizationType": "HUB", "organizationName": "경기남부허브" }
                        """.formatted(username, username)))
                .andExpect(status().isCreated());
        return jdbc.queryForObject("select id from auth.p_users where username = ?", UUID.class, username);
    }

    @Test
    @DisplayName("로그인의 비밀번호 비교는 트랜잭션 밖에서 한다 (성공·실패 모두)")
    void loginMatchesOutsideTransaction() throws Exception {
        signup("tx01");
        RecordingPasswordHasher recorder = (RecordingPasswordHasher) passwordHasher;
        recorder.matchesInTransaction.clear();

        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"username\": \"tx01\", \"password\": \"Abcd123!\" }"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"username\": \"tx01\", \"password\": \"Wrong123!\" }"))
                .andExpect(status().isUnauthorized());

        assertThat(recorder.matchesInTransaction).containsExactly(false, false);
    }

    @Test
    @DisplayName("비밀번호 변경: 커밋 전 무효화 + 커밋 뒤 무효화 (revoked-at 기록 2번, Refresh 삭제 2번)")
    void changePasswordRevokesTwice() throws Exception {
        UUID me = signup("tx02");
        int before = tokenStore.operations().size();

        mockMvc.perform(patch("/api/v1/users/me/password").header("X-User-Id", me.toString()).header("X-User-Role", "USER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"currentPassword\": \"Abcd123!\", \"newPassword\": \"Newpw123!\" }"))
                .andExpect(status().isNoContent());

        assertThat(tokenStore.operations().subList(before, tokenStore.operations().size())).containsExactly(
                "revokeAllIssuedBefore", "deleteRefresh", "revokeAllIssuedBefore", "deleteRefresh");
    }
}
