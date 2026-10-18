package com.user.presentation.controller;

import com.common.auth.AuthContract;
import com.common.exception.BusinessException;
import com.common.exception.UserErrorCode;
import com.user.application.dto.ResetPasswordCommand;
import com.user.application.dto.TemporaryPasswordResult;
import com.user.application.facade.UserAdminFacade;
import com.user.application.service.UserCommandService;
import com.user.application.service.UserQueryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * POST /api/v1/users/{userId}/password-reset (U18 테스트 5).
 */
@WebMvcTest(UserAdminController.class)
class UserAdminControllerResetPasswordTest {

    private static final UUID MASTER_ID = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
    private static final UUID TARGET_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String URL = "/api/v1/users/{userId}/password-reset";

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    UserQueryService userQueryService;

    @MockitoBean
    UserCommandService userCommandService;

    @MockitoBean
    UserAdminFacade userAdminFacade;

    private static MockHttpServletRequestBuilder as(String role, Object userId) {
        return post(URL, userId)
                .header(AuthContract.HEADER_USER_ID, MASTER_ID.toString()).header(AuthContract.HEADER_USER_ROLE, role);
    }

    @Test
    @DisplayName("200, 대상 id·username·임시 비밀번호와 Cache-Control: no-store. 대상 id와 MASTER id를 넘긴다")
    void ok() throws Exception {
        given(userAdminFacade.resetPassword(any())).willReturn(new TemporaryPasswordResult(TARGET_ID, "user01", "Temp1234!x"));

        mockMvc.perform(as("MASTER", TARGET_ID))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.userId").value(TARGET_ID.toString()))
                .andExpect(jsonPath("$.username").value("user01"))
                .andExpect(jsonPath("$.temporaryPassword").value("Temp1234!x"));

        verify(userAdminFacade).resetPassword(new ResetPasswordCommand(TARGET_ID, MASTER_ID));
    }

    @Test
    @DisplayName("USER면 403 ACCESS_DENIED, 초기화하지 않는다")
    void user_forbidden() throws Exception {
        mockMvc.perform(as("USER", TARGET_ID))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        verifyNoInteractions(userAdminFacade);
    }

    @Test
    @DisplayName("X-User-* 헤더가 없으면 401 UNAUTHENTICATED")
    void missingHeader() throws Exception {
        mockMvc.perform(post(URL, TARGET_ID))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        verifyNoInteractions(userAdminFacade);
    }

    @Test
    @DisplayName("userId가 UUID가 아니면 400 INVALID_INPUT")
    void invalidUserId() throws Exception {
        mockMvc.perform(as("MASTER", "not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        verifyNoInteractions(userAdminFacade);
    }

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource({"USER_NOT_FOUND, 404", "CANNOT_RESET_OWN_PASSWORD, 409"})
    @DisplayName("대상 없음 404, 본인 409. 오류 응답에는 임시 비밀번호가 없다")
    void businessError(UserErrorCode code, int status) throws Exception {
        given(userAdminFacade.resetPassword(any())).willThrow(new BusinessException(code));

        mockMvc.perform(as("MASTER", TARGET_ID))
                .andExpect(status().is(status))
                .andExpect(jsonPath("$.code").value(code.getCode()))
                .andExpect(jsonPath("$.temporaryPassword").doesNotExist());
    }
}
