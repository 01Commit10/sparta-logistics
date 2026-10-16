package com.common.auth.web;

import com.common.auth.AuthContract;
import com.common.exception.BusinessException;
import com.common.exception.CommonErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CurrentUserHeaderInterceptorTest {

    static final String ID = UUID.randomUUID().toString();

    final CurrentUserHeaderInterceptor interceptor = new CurrentUserHeaderInterceptor();

    static MockHttpServletRequest request(String id, String role) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/orders");
        if (id != null) {
            request.addHeader(AuthContract.HEADER_USER_ID, id);
        }
        if (role != null) {
            request.addHeader(AuthContract.HEADER_USER_ROLE, role);
        }
        return request;
    }

    boolean preHandle(MockHttpServletRequest request) throws Exception {
        return interceptor.preHandle(request, new MockHttpServletResponse(), new Object());
    }

    @Test
    @DisplayName("13-6. 사용자 헤더가 없으면 통과 (공개 API, 시스템 호출)")
    void absentPasses() throws Exception {
        assertThat(preHandle(request(null, null))).isTrue();
    }

    @Test
    @DisplayName("13-6. 두 헤더가 올바르면 통과 (앞뒤 공백 허용)")
    void validPasses() throws Exception {
        assertThat(preHandle(request(ID, "USER"))).isTrue();
        assertThat(preHandle(request(" " + ID + " ", " MASTER "))).isTrue();
    }

    @ParameterizedTest(name = "[{index}] id={0}, role={1}")
    @CsvSource(nullValues = "NULL", value = {
            "3f2b8c1e-7a4d-4e5f-9b6a-1c2d3e4f5a6b, NULL",    // Id만
            "NULL, USER",                                     // Role만
            "not-a-uuid, USER",                               // UUID 아님
            "3f2b8c1e-7a4d-4e5f-9b6a-1c2d3e4f5a6b, ADMIN",    // 정의되지 않은 역할
    })
    @DisplayName("13-6. 하나만 있거나 형식이 틀리면 UNAUTHENTICATED")
    void invalidRejected(String id, String role) {
        assertThatThrownBy(() -> preHandle(request(id, role)))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.UNAUTHENTICATED));
    }
}
