package com.common.auth.web;

import com.common.auth.AuthContract;
import com.common.auth.AuthRole;
import com.common.auth.AuthUser;
import com.common.exception.BusinessException;
import com.common.exception.CommonErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.ServletWebRequest;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CurrentUserArgumentResolverTest {

    static final UUID ID = UUID.randomUUID();

    final CurrentUserArgumentResolver resolver = new CurrentUserArgumentResolver();

    @SuppressWarnings("unused")
    static class SampleController {
        void required(@CurrentUser AuthUser user) {
        }

        void optional(@CurrentUser(required = false) AuthUser user) {
        }

        void notAnnotated(AuthUser user) {
        }

        void wrongType(@CurrentUser UUID userId) {
        }
    }

    static MethodParameter parameter(String method) {
        for (var m : SampleController.class.getDeclaredMethods()) {
            if (m.getName().equals(method)) {
                return new MethodParameter(m, 0);
            }
        }
        throw new IllegalArgumentException(method);
    }

    static NativeWebRequest request(String id, String role) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (id != null) {
            request.addHeader(AuthContract.HEADER_USER_ID, id);
        }
        if (role != null) {
            request.addHeader(AuthContract.HEADER_USER_ROLE, role);
        }
        return new ServletWebRequest(request);
    }

    Object resolve(String method, NativeWebRequest request) throws Exception {
        return resolver.resolveArgument(parameter(method), null, request, null);
    }

    static void assertUnauthenticated(ThrowingCall call) {
        assertThatThrownBy(call::run)
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.UNAUTHENTICATED));
    }

    interface ThrowingCall {
        void run() throws Exception;
    }

    @Test
    @DisplayName("4. 두 헤더가 올바르면 AuthUser를 넣는다")
    void resolves() throws Exception {
        assertThat(resolve("required", request(ID.toString(), "MASTER")))
                .isEqualTo(new AuthUser(ID, AuthRole.MASTER));
        assertThat(resolve("optional", request(ID.toString(), "USER")))
                .isEqualTo(new AuthUser(ID, AuthRole.USER));
    }

    @Test
    @DisplayName("5. 필수인데 헤더가 없으면 UNAUTHENTICATED")
    void requiredButAbsent() {
        assertUnauthenticated(() -> resolve("required", request(null, null)));
    }

    @Test
    @DisplayName("6. required=false이고 헤더가 없으면 null")
    void optionalAndAbsent() throws Exception {
        assertThat(resolve("optional", request(null, null))).isNull();
    }

    @Test
    @DisplayName("7. UUID가 아니거나 역할이 ADMIN이거나 하나만 있으면 UNAUTHENTICATED (required=false여도)")
    void invalid() {
        assertUnauthenticated(() -> resolve("required", request("not-a-uuid", "USER")));
        assertUnauthenticated(() -> resolve("required", request(ID.toString(), "ADMIN")));
        assertUnauthenticated(() -> resolve("optional", request("not-a-uuid", "USER")));
        assertUnauthenticated(() -> resolve("optional", request(ID.toString(), null)));
    }

    @Test
    @DisplayName("8. 헤더 이름 대소문자는 구분하지 않는다")
    void headerNameCaseInsensitive() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("x-user-id", ID.toString());
        request.addHeader("X-USER-ROLE", "USER");

        assertThat(resolve("required", new ServletWebRequest(request)))
                .isEqualTo(new AuthUser(ID, AuthRole.USER));
    }

    @Test
    @DisplayName("9. @CurrentUser가 붙은 AuthUser 파라미터만 지원한다")
    void supportsParameter() {
        assertThat(resolver.supportsParameter(parameter("required"))).isTrue();
        assertThat(resolver.supportsParameter(parameter("optional"))).isTrue();
        assertThat(resolver.supportsParameter(parameter("notAnnotated"))).isFalse();
        assertThat(resolver.supportsParameter(parameter("wrongType"))).isFalse();
    }
}
