package com.user.presentation.controller;

import com.common.auth.AuthContract;
import com.common.exception.BusinessException;
import com.common.exception.UserErrorCode;
import com.user.application.dto.LinkOrganizationCommand;
import com.user.application.dto.UserResult;
import com.user.application.facade.UserAdminFacade;
import com.user.application.service.UserCommandService;
import com.user.application.service.UserQueryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PATCH /api/v1/users/{userId}/organization (U20 테스트 5).
 */
@WebMvcTest(UserAdminController.class)
class UserAdminControllerOrganizationTest {

    private static final UUID MASTER_ID = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
    private static final UUID TARGET_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID HUB_ID = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
    private static final String URL = "/api/v1/users/{userId}/organization";

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    UserQueryService userQueryService;

    @MockitoBean
    UserCommandService userCommandService;

    @MockitoBean
    UserAdminFacade userAdminFacade;

    private static UserResult result() {
        return new UserResult(TARGET_ID, "user01", "홍길동", "U001", "USER", false, "HUB", "서울센터", HUB_ID,
                false, true, LocalDateTime.of(2026, 10, 15, 9, 0), TARGET_ID, LocalDateTime.of(2026, 10, 16, 9, 0),
                MASTER_ID, null, null);
    }

    private static MockHttpServletRequestBuilder as(String role, String body) {
        return patch(URL, TARGET_ID).contentType(MediaType.APPLICATION_JSON).content(body)
                .header(AuthContract.HEADER_USER_ID, MASTER_ID.toString()).header(AuthContract.HEADER_USER_ROLE, role);
    }

    @Test
    @DisplayName("200, 관리자용 상세에 organizationId. 유형·ID·소속명을 넘긴다")
    void ok() throws Exception {
        given(userCommandService.linkOrganization(any())).willReturn(result());

        mockMvc.perform(as("MASTER", """
                        { "organizationType": "HUB", "organizationId": "%s", "organizationName": "서울센터" }
                        """.formatted(HUB_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.organizationId").value(HUB_ID.toString()))
                .andExpect(jsonPath("$.organizationType").value("HUB"));

        verify(userCommandService).linkOrganization(new LinkOrganizationCommand(TARGET_ID, "HUB", HUB_ID, "서울센터"));
    }

    @Test
    @DisplayName("소속명은 생략할 수 있다 (null로 넘긴다)")
    void withoutName() throws Exception {
        given(userCommandService.linkOrganization(any())).willReturn(result());

        mockMvc.perform(as("MASTER", "{ \"organizationType\": \"COMPANY\", \"organizationId\": \"%s\" }".formatted(HUB_ID)))
                .andExpect(status().isOk());

        verify(userCommandService).linkOrganization(new LinkOrganizationCommand(TARGET_ID, "COMPANY", HUB_ID, null));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
            "{ \"organizationType\": \"HQ\", \"organizationId\": \"00000000-0000-0000-0000-0000000000b1\" }",
            "{ \"organizationType\": \"hub\", \"organizationId\": \"00000000-0000-0000-0000-0000000000b1\" }",
            "{ \"organizationId\": \"00000000-0000-0000-0000-0000000000b1\" }",
            "{ \"organizationType\": \"HUB\" }",
            "{ \"organizationType\": \"HUB\", \"organizationId\": \"not-a-uuid\" }",
            "{ \"organizationType\": \"HUB\", \"organizationId\": \"00000000-0000-0000-0000-0000000000b1\", \"organizationName\": \" \" }"})
    @DisplayName("HQ·소문자·유형 없음·ID 없음·UUID 아님·빈 소속명 → 400 INVALID_INPUT, 바꾸지 않는다")
    void invalid(String body) throws Exception {
        mockMvc.perform(as("MASTER", body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        verifyNoInteractions(userCommandService);
    }

    @Test
    @DisplayName("USER면 403 ACCESS_DENIED, 바꾸지 않는다")
    void user_forbidden() throws Exception {
        mockMvc.perform(as("USER", "{ \"organizationType\": \"HUB\", \"organizationId\": \"%s\" }".formatted(HUB_ID)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        verifyNoInteractions(userCommandService);
    }

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource({"USER_NOT_FOUND, 404", "DELIVERY_MANAGER_REQUIRES_HUB, 409", "INVALID_ORGANIZATION, 400"})
    @DisplayName("대상 없음 404, 배송담당자를 업체로 409")
    void businessError(UserErrorCode code, int status) throws Exception {
        given(userCommandService.linkOrganization(any())).willThrow(new BusinessException(code));

        mockMvc.perform(as("MASTER", "{ \"organizationType\": \"COMPANY\", \"organizationId\": \"%s\" }".formatted(HUB_ID)))
                .andExpect(status().is(status))
                .andExpect(jsonPath("$.code").value(code.getCode()));
    }
}
