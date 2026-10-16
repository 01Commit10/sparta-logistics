package com.common.auth.feign;

import com.common.auth.AuthContract;
import com.common.auth.web.RequestHeaderCurrentUserProvider;
import feign.RequestTemplate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UserHeaderRelayInterceptorTest {

    static final String USER_ID = "3f2b8c1e-7a4d-4e5f-9b6a-1c2d3e4f5a6b";

    final UserHeaderRelayInterceptor interceptor = new UserHeaderRelayInterceptor(new RequestHeaderCurrentUserProvider());

    @AfterEach
    void clear() {
        RequestContextHolder.resetRequestAttributes();
    }

    static MockHttpServletRequest bind() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        return request;
    }

    static MockHttpServletRequest bindUser() {
        MockHttpServletRequest request = bind();
        request.addHeader(AuthContract.HEADER_USER_ID, USER_ID);
        request.addHeader(AuthContract.HEADER_USER_ROLE, "MASTER");
        request.addHeader(AuthContract.HEADER_REQUEST_ID, "req-1");
        return request;
    }

    RequestTemplate apply(RequestTemplate template) {
        interceptor.apply(template);
        return template;
    }

    @Test
    @DisplayName("11. 현재 요청의 X-User-Id·X-User-Role·X-Request-Id를 복사한다")
    void relaysThreeHeaders() {
        bindUser();

        RequestTemplate template = apply(new RequestTemplate());

        assertThat(template.headers().get(AuthContract.HEADER_USER_ID)).containsExactly(USER_ID);
        assertThat(template.headers().get(AuthContract.HEADER_USER_ROLE)).containsExactly("MASTER");
        assertThat(template.headers().get(AuthContract.HEADER_REQUEST_ID)).containsExactly("req-1");
    }

    @Test
    @DisplayName("11. 사용자 값은 검증된 표준 형식으로 보낸다 (공백 제거, UUID 소문자)")
    void relaysNormalizedUser() {
        MockHttpServletRequest request = bind();
        request.addHeader(AuthContract.HEADER_USER_ID, " " + USER_ID.toUpperCase() + " ");
        request.addHeader(AuthContract.HEADER_USER_ROLE, " USER ");
        request.addHeader(AuthContract.HEADER_REQUEST_ID, " req-1 ");

        RequestTemplate template = apply(new RequestTemplate());

        assertThat(template.headers().get(AuthContract.HEADER_USER_ID)).containsExactly(USER_ID);
        assertThat(template.headers().get(AuthContract.HEADER_USER_ROLE)).containsExactly("USER");
        assertThat(template.headers().get(AuthContract.HEADER_REQUEST_ID)).containsExactly("req-1");
    }

    @Test
    @DisplayName("12. 요청 밖(스케줄러 등)에서는 아무 헤더도 붙이지 않는다 (예외 없음)")
    void outsideRequest() {
        RequestTemplate template = apply(new RequestTemplate());

        assertThat(template.headers()).isEmpty();
    }

    @Test
    @DisplayName("13. Authorization·X-Token-*은 복사하지 않는다")
    void doesNotRelayTokens() {
        MockHttpServletRequest request = bindUser();
        request.addHeader("Authorization", "Bearer secret");
        request.addHeader(AuthContract.HEADER_TOKEN_JTI, "jti");
        request.addHeader(AuthContract.HEADER_TOKEN_EXP, "1760000000");
        request.addHeader("Cookie", "a=b");

        RequestTemplate template = apply(new RequestTemplate());

        assertThat(template.headers().keySet()).containsExactlyInAnyOrder(
                AuthContract.HEADER_USER_ID, AuthContract.HEADER_USER_ROLE, AuthContract.HEADER_REQUEST_ID);
    }

    @Test
    @DisplayName("13-1. Feign 메서드에서 직접 지정한 X-Request-Id는 덮어쓰지 않는다 (대소문자 무관)")
    void keepsExplicitRequestId() {
        bindUser();

        RequestTemplate template = apply(new RequestTemplate().header("x-request-id", "explicit"));

        assertThat(template.headers().get(AuthContract.HEADER_REQUEST_ID)).containsExactly("explicit");
        assertThat(template.headers().get(AuthContract.HEADER_USER_ID)).containsExactly(USER_ID);
    }

    @Test
    @DisplayName("13-1. 사용자 헤더는 한 쌍: 하나라도 직접 지정했으면 둘 다 건드리지 않는다 (다른 신원과 섞이지 않게)")
    void keepsExplicitUserPair() {
        bindUser();
        String explicitId = UUID.randomUUID().toString();

        RequestTemplate onlyId = apply(new RequestTemplate().header(AuthContract.HEADER_USER_ID, explicitId));
        RequestTemplate onlyRole = apply(new RequestTemplate().header(AuthContract.HEADER_USER_ROLE, "USER"));

        assertThat(onlyId.headers().get(AuthContract.HEADER_USER_ID)).containsExactly(explicitId);
        assertThat(onlyId.headers()).doesNotContainKey(AuthContract.HEADER_USER_ROLE);
        assertThat(onlyRole.headers().get(AuthContract.HEADER_USER_ROLE)).containsExactly("USER");
        assertThat(onlyRole.headers()).doesNotContainKey(AuthContract.HEADER_USER_ID);
        assertThat(onlyRole.headers().get(AuthContract.HEADER_REQUEST_ID)).containsExactly("req-1");
    }

    @Test
    @DisplayName("13-2. 요청에 없는 헤더는 건너뛰고 나머지는 복사한다")
    void skipsMissingHeaders() {
        MockHttpServletRequest request = bind();
        request.addHeader(AuthContract.HEADER_USER_ID, USER_ID);
        request.addHeader(AuthContract.HEADER_USER_ROLE, "USER");

        RequestTemplate template = apply(new RequestTemplate());

        assertThat(template.headers()).containsOnlyKeys(AuthContract.HEADER_USER_ID, AuthContract.HEADER_USER_ROLE);
    }

    @Test
    @DisplayName("13-2. 사용자 헤더가 없는 요청(공개 경로)은 X-Request-Id만 복사한다, 빈 X-Request-Id는 복사하지 않는다")
    void requestIdOnlyAndBlank() {
        bind().addHeader(AuthContract.HEADER_REQUEST_ID, "req-2");
        RequestTemplate publicCall = apply(new RequestTemplate());
        assertThat(publicCall.headers()).containsOnlyKeys(AuthContract.HEADER_REQUEST_ID);

        MockHttpServletRequest blank = bind();
        blank.addHeader(AuthContract.HEADER_REQUEST_ID, "  ");
        assertThat(apply(new RequestTemplate()).headers()).isEmpty();
    }
}
