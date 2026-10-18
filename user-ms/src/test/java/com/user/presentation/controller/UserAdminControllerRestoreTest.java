package com.user.presentation.controller;

import com.common.auth.AuthContract;
import com.common.exception.BusinessException;
import com.common.exception.UserErrorCode;
import com.user.application.dto.UserResult;
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

import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * POST /api/v1/users/{userId}/restore (U19 테스트 3).
 */
@WebMvcTest(UserAdminController.class)
class UserAdminControllerRestoreTest {

    private static final UUID MASTER_ID = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
    private static final UUID TARGET_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String URL = "/api/v1/users/{userId}/restore";

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    UserQueryService userQueryService;

    @MockitoBean
    UserCommandService userCommandService;

    @MockitoBean
    UserAdminFacade userAdminFacade;

    private static UserResult result() {
        return new UserResult(TARGET_ID, "user01", "홍길동", "U001", "USER", false, "HUB", "서울센터", null,
                false, true, LocalDateTime.of(2026, 10, 15, 9, 0), TARGET_ID, LocalDateTime.of(2026, 10, 16, 9, 0),
                MASTER_ID, null, null);
    }

    private static MockHttpServletRequestBuilder as(String role, Object userId) {
        return post(URL, userId)
                .header(AuthContract.HEADER_USER_ID, MASTER_ID.toString()).header(AuthContract.HEADER_USER_ROLE, role);
    }

    @Test
    @DisplayName("200, 관리자용 상세 (active=true, 삭제 정보 없음)")
    void ok() throws Exception {
        given(userCommandService.restore(any())).willReturn(result());

        mockMvc.perform(as("MASTER", TARGET_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(TARGET_ID.toString()))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.deletedAt").isEmpty())
                .andExpect(jsonPath("$.updatedBy").value(MASTER_ID.toString()));

        verify(userCommandService).restore(TARGET_ID);
    }

    @Test
    @DisplayName("USER면 403 ACCESS_DENIED, 재활성화하지 않는다")
    void user_forbidden() throws Exception {
        mockMvc.perform(as("USER", TARGET_ID))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        verifyNoInteractions(userCommandService);
    }

    @Test
    @DisplayName("X-User-* 헤더가 없으면 401 UNAUTHENTICATED")
    void missingHeader() throws Exception {
        mockMvc.perform(post(URL, TARGET_ID))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        verifyNoInteractions(userCommandService);
    }

    @Test
    @DisplayName("userId가 UUID가 아니면 400 INVALID_INPUT")
    void invalidUserId() throws Exception {
        mockMvc.perform(as("MASTER", "not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        verifyNoInteractions(userCommandService);
    }

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource({"USER_NOT_FOUND, 404", "USER_NOT_DELETED, 409", "DUPLICATE_SLACK_ID, 409"})
    @DisplayName("없음 404, 활성 409, Slack ID 충돌 409")
    void businessError(UserErrorCode code, int status) throws Exception {
        given(userCommandService.restore(any())).willThrow(new BusinessException(code));

        mockMvc.perform(as("MASTER", TARGET_ID))
                .andExpect(status().is(status))
                .andExpect(jsonPath("$.code").value(code.getCode()));
    }
}
