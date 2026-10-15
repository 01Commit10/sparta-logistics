package com.common.feign;

import static org.assertj.core.api.Assertions.assertThat;

import com.common.web.UserHeaders;
import feign.RequestTemplate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

class UserIdRelayInterceptorTest {

    private static final String USER_ID = "00000000-0000-0000-0000-000000000001";

    private final UserIdRelayInterceptor interceptor = new UserIdRelayInterceptor();

    @AfterEach
    void clearRequest() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void relaysUserIdOfCurrentRequest() {
        bindRequest(USER_ID);
        RequestTemplate template = new RequestTemplate();

        interceptor.apply(template);

        assertThat(template.headers().get(UserHeaders.USER_ID)).containsExactly(USER_ID);
    }

    @Test
    void addsNothingWhenCurrentRequestHasNoUserId() {
        bindRequest(null);
        RequestTemplate template = new RequestTemplate();

        interceptor.apply(template);

        assertThat(template.headers()).doesNotContainKey(UserHeaders.USER_ID);
    }

    @Test
    void addsNothingOutsideHttpRequest() {
        RequestTemplate template = new RequestTemplate();

        interceptor.apply(template);

        assertThat(template.headers()).doesNotContainKey(UserHeaders.USER_ID);
    }

    @Test
    void keepsHeaderSetByFeignMethod() {
        bindRequest(USER_ID);
        String explicitUserId = "00000000-0000-0000-0000-000000000002";
        RequestTemplate template = new RequestTemplate().header(UserHeaders.USER_ID, explicitUserId);

        interceptor.apply(template);

        assertThat(template.headers().get(UserHeaders.USER_ID)).containsExactly(explicitUserId);
    }

    private void bindRequest(String userId) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (userId != null) {
            request.addHeader(UserHeaders.USER_ID, userId);
        }
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }
}
