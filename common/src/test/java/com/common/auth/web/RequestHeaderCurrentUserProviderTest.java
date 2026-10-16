package com.common.auth.web;

import com.common.auth.AuthContract;
import com.common.auth.AuthRole;
import com.common.auth.AuthUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RequestHeaderCurrentUserProviderTest {

    static final UUID ID = UUID.randomUUID();

    final RequestHeaderCurrentUserProvider provider = new RequestHeaderCurrentUserProvider();

    @AfterEach
    void clear() {
        RequestContextHolder.resetRequestAttributes();
    }

    static void inRequest(String id, String role) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (id != null) {
            request.addHeader(AuthContract.HEADER_USER_ID, id);
        }
        if (role != null) {
            request.addHeader(AuthContract.HEADER_USER_ROLE, role);
        }
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @Test
    @DisplayName("10. 요청 안 + 올바른 헤더 → 현재 사용자")
    void inRequestWithUser() {
        inRequest(ID.toString(), "MASTER");

        assertThat(provider.current()).contains(new AuthUser(ID, AuthRole.MASTER));
        assertThat(provider.inRequest()).isTrue();
    }

    @Test
    @DisplayName("10. 요청 안 + 헤더 없음(공개 경로) → empty, 요청 안으로 본다")
    void inRequestWithoutUser() {
        inRequest(null, null);

        assertThat(provider.current()).isEmpty();
        assertThat(provider.inRequest()).isTrue();
    }

    @Test
    @DisplayName("10. 요청 안 + 잘못된 헤더 → 예외 없이 empty (입구 인터셉터가 이미 401로 막음)")
    void inRequestWithInvalidUser() {
        inRequest("not-a-uuid", "USER");

        assertThat(provider.current()).isEmpty();
    }

    @Test
    @DisplayName("10. 요청 밖(스케줄러·기동) → empty, 요청 밖으로 본다")
    void outsideRequest() {
        assertThat(provider.current()).isEmpty();
        assertThat(provider.inRequest()).isFalse();
    }
}
