package com.user.presentation.controller;

import com.common.auth.AuthContract;
import com.common.exception.AuthErrorCode;
import com.common.exception.BusinessException;
import com.common.exception.ErrorCode;
import com.common.exception.UserErrorCode;
import com.user.application.dto.DeactivateUserCommand;
import com.user.application.facade.UserAdminFacade;
import com.user.application.service.UserCommandService;
import com.user.application.service.UserQueryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.UUID;
import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * DELETE /api/v1/users/{userId} (U17 테스트 4).
 */
@WebMvcTest(UserAdminController.class)
class UserAdminControllerDeactivateTest {

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

    private static MockHttpServletRequestBuilder as(String role, UUID id, Object userId) {
        return delete("/api/v1/users/{userId}", userId)
                .header(AuthContract.HEADER_USER_ID, id.toString()).header(AuthContract.HEADER_USER_ROLE, role);
    }

    @Test
    @DisplayName("204 본문 없음, 대상 ID와 MASTER ID(삭제자)를 넘긴다")
    void noContent() throws Exception {
        mockMvc.perform(as("MASTER", MASTER_ID, TARGET_ID))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(userAdminFacade).deactivate(new DeactivateUserCommand(TARGET_ID, MASTER_ID));
    }

    static Stream<Arguments> errors() {
        return Stream.of(
                Arguments.of(UserErrorCode.USER_NOT_FOUND, 404),
                Arguments.of(UserErrorCode.USER_ALREADY_DELETED, 409),
                Arguments.of(UserErrorCode.LAST_MASTER, 409),
                Arguments.of(UserErrorCode.CANNOT_DEACTIVATE_SELF, 409),
                Arguments.of(AuthErrorCode.AUTH_UNAVAILABLE, 503));
    }

    @ParameterizedTest(name = "{0} → {1}")
    @MethodSource("errors")
    @DisplayName("없음 404, 이미 삭제·마지막 MASTER·본인 409, Redis 장애 503")
    void businessError(ErrorCode code, int status) throws Exception {
        willThrow(new BusinessException(code)).given(userAdminFacade).deactivate(any());

        mockMvc.perform(as("MASTER", MASTER_ID, TARGET_ID))
                .andExpect(status().is(status))
                .andExpect(jsonPath("$.code").value(code.getCode()));
    }

    @Test
    @DisplayName("USER면 403 ACCESS_DENIED, 비활성화하지 않는다")
    void user_forbidden() throws Exception {
        mockMvc.perform(as("USER", TARGET_ID, MASTER_ID))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        verifyNoInteractions(userAdminFacade);
    }

    @Test
    @DisplayName("헤더 없음 401, userId가 UUID가 아니면 400")
    void unauthenticatedOrInvalid() throws Exception {
        mockMvc.perform(delete("/api/v1/users/{userId}", TARGET_ID)).andExpect(status().isUnauthorized());
        mockMvc.perform(as("MASTER", MASTER_ID, "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        verifyNoInteractions(userAdminFacade);
    }
}
