package com.common.audit;

import com.common.auth.AuthContract;
import com.common.auth.AuthRole;
import com.common.auth.AuthUser;
import com.common.auth.CurrentUserProvider;
import com.common.auth.web.RequestHeaderCurrentUserProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** 작업자 결정 규칙 (C01 D7, 07 정책 1.4) */
class UserIdAuditorAwareTest {

    private final UserIdAuditorAware auditorAware = new UserIdAuditorAware(new RequestHeaderCurrentUserProvider());

    @AfterEach
    void clearRequest() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    @DisplayName("요청 안 + 사용자 헤더 → 그 사용자")
    void returnsUserIdFromHeader() {
        UUID userId = UUID.randomUUID();
        bindRequest(userId.toString(), "USER");

        assertThat(auditorAware.getCurrentAuditor()).contains(userId);
    }

    @Test
    @DisplayName("요청 안 + 헤더 없음(회원가입 등 공개 경로) → empty: 직접 지정한 값을 유지한다")
    void emptyWhenHeaderMissing() {
        bindRequest(null, null);

        assertThat(auditorAware.getCurrentAuditor()).isEmpty();
    }

    @Test
    @DisplayName("요청 안 + X-User-Id만 있음 → empty (입구 검사가 401로 막는 요청)")
    void emptyWhenRoleMissing() {
        bindRequest(UUID.randomUUID().toString(), null);

        assertThat(auditorAware.getCurrentAuditor()).isEmpty();
    }

    @Test
    @DisplayName("요청 안 + UUID 아님 → empty")
    void emptyWhenHeaderIsNotUuid() {
        bindRequest("not-a-uuid", "USER");

        assertThat(auditorAware.getCurrentAuditor()).isEmpty();
    }

    @Test
    @DisplayName("요청 밖(스케줄러·기동·시드) → 시스템 UUID")
    void systemOutsideHttpRequest() {
        assertThat(auditorAware.getCurrentAuditor()).contains(AuthContract.SYSTEM_USER_ID);
    }

    @Test
    @DisplayName("주입한 CurrentUserProvider로 판단한다 (헤더를 직접 읽지 않음)")
    void usesProvider() {
        UUID userId = UUID.randomUUID();
        CurrentUserProvider provider = new CurrentUserProvider() {
            @Override
            public Optional<AuthUser> current() {
                return Optional.of(new AuthUser(userId, AuthRole.MASTER));
            }

            @Override
            public boolean inRequest() {
                return true;
            }
        };

        assertThat(new UserIdAuditorAware(provider).getCurrentAuditor()).contains(userId);
    }

    private void bindRequest(String userId, String role) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (userId != null) {
            request.addHeader(AuthContract.HEADER_USER_ID, userId);
        }
        if (role != null) {
            request.addHeader(AuthContract.HEADER_USER_ROLE, role);
        }
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }
}
