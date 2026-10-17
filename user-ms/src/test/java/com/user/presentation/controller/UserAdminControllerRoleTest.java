package com.user.presentation.controller;

import com.common.auth.AuthContract;
import com.common.exception.AuthErrorCode;
import com.common.exception.BusinessException;
import com.common.exception.ErrorCode;
import com.common.exception.UserErrorCode;
import com.user.application.dto.ChangeRoleCommand;
import com.user.application.dto.UserResult;
import com.user.application.facade.UserAdminFacade;
import com.user.application.service.UserCommandService;
import com.user.application.service.UserQueryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PATCH /api/v1/users/{userId}/role (U15 테스트 6).
 */
@WebMvcTest(UserAdminController.class)
class UserAdminControllerRoleTest {

    private static final UUID MASTER_ID = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
    private static final UUID TARGET_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    UserQueryService userQueryService;

    @MockitoBean
    UserCommandService userCommandService;

    @MockitoBean
    UserAdminFacade userAdminFacade;

    private static MockHttpServletRequestBuilder as(String role, String body) {
        return patch("/api/v1/users/{userId}/role", TARGET_ID).contentType(MediaType.APPLICATION_JSON).content(body)
                .header(AuthContract.HEADER_USER_ID, MASTER_ID.toString()).header(AuthContract.HEADER_USER_ROLE, role);
    }

    @Test
    @DisplayName("200, 대상 ID와 역할을 넘기고 바뀐 상세를 돌려준다")
    void ok() throws Exception {
        given(userAdminFacade.changeRole(any())).willReturn(new UserResult(TARGET_ID, "user01", "홍길동", "U001",
                "MASTER", false, "HUB", "서울센터", null, false, true,
                LocalDateTime.of(2026, 10, 15, 9, 0), TARGET_ID, LocalDateTime.of(2026, 10, 16, 9, 0), MASTER_ID,
                null, null));

        mockMvc.perform(as("MASTER", "{ \"role\": \"MASTER\" }"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(TARGET_ID.toString()))
                .andExpect(jsonPath("$.role").value("MASTER"));

        verify(userAdminFacade).changeRole(new ChangeRoleCommand(TARGET_ID, "MASTER"));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"{}", "{ \"role\": null }", "{ \"role\": \"\" }", "{ \"role\": \"master\" }",
            "{ \"role\": \"ADMIN\" }", "{ \"role\": \"DELIVERY_MANAGER\" }"})
    @DisplayName("잘못된 role은 400 INVALID_INPUT (MASTER·USER만, 대소문자 구분)")
    void invalidRole(String body) throws Exception {
        mockMvc.perform(as("MASTER", body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.errors[0].field").value("role"));
        verifyNoInteractions(userAdminFacade);
    }

    static Stream<Arguments> errors() {
        return Stream.of(
                Arguments.of(UserErrorCode.USER_NOT_FOUND, 404),
                Arguments.of(UserErrorCode.LAST_MASTER, 409),
                Arguments.of(UserErrorCode.INVALID_ORGANIZATION, 400),
                Arguments.of(AuthErrorCode.AUTH_UNAVAILABLE, 503));
    }

    @ParameterizedTest(name = "{0} → {1}")
    @MethodSource("errors")
    @DisplayName("없음 404, 마지막 MASTER 409, 본사 소속 강등 400, Redis 장애 503")
    void businessError(ErrorCode code, int status) throws Exception {
        given(userAdminFacade.changeRole(any())).willThrow(new BusinessException(code));

        mockMvc.perform(as("MASTER", "{ \"role\": \"USER\" }"))
                .andExpect(status().is(status))
                .andExpect(jsonPath("$.code").value(code.getCode()));
    }

    @Test
    @DisplayName("USER면 403 (본인을 MASTER로 올리는 시도 포함), 바꾸지 않는다")
    void forbidden() throws Exception {
        mockMvc.perform(as("USER", "{ \"role\": \"MASTER\" }"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        verifyNoInteractions(userAdminFacade);
    }
}
