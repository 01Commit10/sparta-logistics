package com.user.presentation.controller;

import com.common.auth.AuthContract;
import com.common.exception.BusinessException;
import com.common.exception.CommonErrorCode;
import com.common.exception.ErrorCode;
import com.common.exception.UserErrorCode;
import com.user.application.dto.UpdateUserCommand;
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
 * PATCH /api/v1/users/{userId} (U14 테스트 5).
 */
@WebMvcTest(UserAdminController.class)
class UserAdminControllerUpdateTest {

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
        return patch("/api/v1/users/{userId}", TARGET_ID).contentType(MediaType.APPLICATION_JSON).content(body)
                .header(AuthContract.HEADER_USER_ID, MASTER_ID.toString()).header(AuthContract.HEADER_USER_ROLE, role);
    }

    private static UserResult result() {
        return new UserResult(TARGET_ID, "user01", "김철수", "U001", "USER", false, "COMPANY", "스파르타상사", null,
                false, true, LocalDateTime.of(2026, 10, 15, 9, 0), TARGET_ID, LocalDateTime.of(2026, 10, 16, 9, 0),
                MASTER_ID, null, null);
    }

    @Test
    @DisplayName("200, 보낸 필드만 넘기고(나머지 null) 역할·배송담당 등 다른 필드는 무시한다")
    void ok() throws Exception {
        given(userCommandService.updateByMaster(any())).willReturn(result());

        mockMvc.perform(as("MASTER", """
                        { "name": "김철수", "role": "MASTER", "isDeliveryManager": true, "password": "Newpw123!" }
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("김철수"))
                .andExpect(jsonPath("$.updatedBy").value(MASTER_ID.toString()));

        verify(userCommandService).updateByMaster(new UpdateUserCommand(TARGET_ID, "김철수", null, null, null));
    }

    @Test
    @DisplayName("네 필드를 모두 넘긴다")
    void allFields() throws Exception {
        given(userCommandService.updateByMaster(any())).willReturn(result());

        mockMvc.perform(as("MASTER", """
                { "name": "김철수", "slackId": "U999", "organizationType": "HUB", "organizationName": "서울센터" }
                """)).andExpect(status().isOk());

        verify(userCommandService).updateByMaster(new UpdateUserCommand(TARGET_ID, "김철수", "U999", "HUB", "서울센터"));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"{}", "{ \"name\": null, \"slackId\": null }", "{ \"slackid\": \"U999\" }"})
    @DisplayName("바꿀 필드가 하나도 없으면 400 INVALID_INPUT (오타 필드도 무시되지 않고 400)")
    void nothingToUpdate(String body) throws Exception {
        mockMvc.perform(as("MASTER", body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.errors[0].field").value("anyFieldPresent"));
        verifyNoInteractions(userCommandService);
    }

    static Stream<Arguments> invalidFields() {
        return Stream.of(
                Arguments.of("{ \"name\": \"\" }", "name"),
                Arguments.of("{ \"name\": \"   \" }", "name"),
                Arguments.of("{ \"slackId\": \" \" }", "slackId"),
                Arguments.of("{ \"organizationType\": \"\", \"organizationName\": \"서울센터\" }", "organizationType"),
                Arguments.of("{ \"organizationName\": \"\" }", "organizationName"),
                Arguments.of("{ \"name\": \"" + "가".repeat(101) + "\" }", "name"),
                Arguments.of("{ \"slackId\": \"" + "U".repeat(81) + "\" }", "slackId"),
                Arguments.of("{ \"organizationName\": \"" + "가".repeat(151) + "\" }", "organizationName"));
    }

    @ParameterizedTest(name = "{1}: {0}")
    @MethodSource("invalidFields")
    @DisplayName("보낸 필드가 비었거나 너무 길면 400 INVALID_INPUT")
    void invalidField(String body, String field) throws Exception {
        mockMvc.perform(as("MASTER", body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value(field));
        verifyNoInteractions(userCommandService);
    }

    static Stream<Arguments> errors() {
        return Stream.of(
                Arguments.of(UserErrorCode.USER_NOT_FOUND, 404),
                Arguments.of(UserErrorCode.DUPLICATE_SLACK_ID, 409),
                Arguments.of(UserErrorCode.INVALID_ORGANIZATION, 400),
                Arguments.of(UserErrorCode.DELIVERY_MANAGER_REQUIRES_HUB, 409),
                Arguments.of(CommonErrorCode.INVALID_INPUT, 400));
    }

    @ParameterizedTest(name = "{0} → {1}")
    @MethodSource("errors")
    @DisplayName("서비스 오류를 공통 형식으로 응답한다")
    void businessError(ErrorCode code, int status) throws Exception {
        given(userCommandService.updateByMaster(any())).willThrow(new BusinessException(code));

        mockMvc.perform(as("MASTER", "{ \"name\": \"김철수\" }"))
                .andExpect(status().is(status))
                .andExpect(jsonPath("$.code").value(code.getCode()));
    }

    @Test
    @DisplayName("USER면 403, 헤더 없으면 401, 수정하지 않는다")
    void forbidden() throws Exception {
        mockMvc.perform(as("USER", "{ \"name\": \"김철수\" }"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        mockMvc.perform(patch("/api/v1/users/{userId}", TARGET_ID).contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"name\": \"김철수\" }"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(userCommandService);
    }
}
