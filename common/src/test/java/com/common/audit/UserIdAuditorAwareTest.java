package com.common.audit;

import static org.assertj.core.api.Assertions.assertThat;

import com.common.web.UserHeaders;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

class UserIdAuditorAwareTest {

    private final UserIdAuditorAware auditorAware = new UserIdAuditorAware();

    @AfterEach
    void clearRequest() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void returnsUserIdFromHeader() {
        UUID userId = UUID.randomUUID();
        bindRequest(userId.toString());

        assertThat(auditorAware.getCurrentAuditor()).contains(userId);
    }

    @Test
    void emptyWhenHeaderMissing() {
        bindRequest(null);

        assertThat(auditorAware.getCurrentAuditor()).isEmpty();
    }

    @Test
    void emptyWhenHeaderIsNotUuid() {
        bindRequest("not-a-uuid");

        assertThat(auditorAware.getCurrentAuditor()).isEmpty();
    }

    @Test
    void emptyOutsideHttpRequest() {
        assertThat(auditorAware.getCurrentAuditor()).isEmpty();
    }

    private void bindRequest(String userId) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (userId != null) {
            request.addHeader(UserHeaders.USER_ID, userId);
        }
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }
}
