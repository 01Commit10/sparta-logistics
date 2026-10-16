package com.user.presentation.controller;

import com.common.auth.AuthContract;
import com.common.exception.BusinessException;
import com.common.exception.UserErrorCode;
import com.user.application.dto.CreateUserCommand;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * POST /api/v1/users (U12 테스트 4).
 */
@WebMvcTest(UserAdminController.class)
class UserAdminControllerCreateTest {

    private static final UUID MASTER_ID = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
    private static final UUID NEW_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String URL = "/api/v1/users";

    private static final String VALID = """
            { "username": "driver01", "password": "Abcd123!", "name": "김배송", "slackId": "U001",
              "organizationType": "HUB", "organizationName": "서울센터", "role": "USER", "isDeliveryManager": true }
            """;

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    UserQueryService userQueryService;

    @MockitoBean
    UserCommandService userCommandService;

    @MockitoBean
    UserAdminFacade userAdminFacade;

    private static UserResult result() {
        return new UserResult(NEW_ID, "driver01", "김배송", "U001", "USER", true, "HUB", "서울센터", null,
                true, true, LocalDateTime.of(2026, 10, 16, 9, 0), MASTER_ID, LocalDateTime.of(2026, 10, 16, 9, 0),
                MASTER_ID, null, null);
    }

    private static MockHttpServletRequestBuilder as(String role, String body) {
        return post(URL).contentType(MediaType.APPLICATION_JSON).content(body)
                .header(AuthContract.HEADER_USER_ID, MASTER_ID.toString()).header(AuthContract.HEADER_USER_ROLE, role);
    }

    /** VALID에서 필드 하나를 바꾸거나("null"이면 null) 뺀 본문 */
    private static String with(String field, String json) {
        String replaced = VALID.replaceAll("\"" + field + "\": (\"[^\"]*\"|true|false)", "\"" + field + "\": " + json);
        assertThat(replaced).isNotEqualTo(VALID);
        return replaced;
    }

    @Test
    @DisplayName("201, Location과 관리자용 상세를 돌려주고, 입력과 MASTER id를 넘긴다. 비밀번호·해시는 응답에 없다")
    void created() throws Exception {
        given(userCommandService.createByMaster(any())).willReturn(result());

        String body = mockMvc.perform(as("MASTER", VALID))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/users/" + NEW_ID))
                .andExpect(jsonPath("$.id").value(NEW_ID.toString()))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.isDeliveryManager").value(true))
                .andExpect(jsonPath("$.passwordChangeRequired").value(true))
                .andExpect(jsonPath("$.createdBy").value(MASTER_ID.toString()))
                .andReturn().getResponse().getContentAsString();

        verify(userCommandService).createByMaster(new CreateUserCommand(MASTER_ID, "driver01", "Abcd123!", "김배송",
                "U001", "HUB", "서울센터", "USER", true));
        assertThat(body.toLowerCase()).doesNotContain("hash").doesNotContain("abcd123!");
    }

    @ParameterizedTest(name = "{0} 없음")
    @ValueSource(strings = {"username", "password", "name", "slackId", "organizationType", "organizationName",
            "role", "isDeliveryManager"})
    @DisplayName("필수값이 null이면 400 INVALID_INPUT, 만들지 않는다 (role·isDeliveryManager도 필수)")
    void missing(String field) throws Exception {
        mockMvc.perform(as("MASTER", with(field, "null")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.errors[0].field").value(field));
        verifyNoInteractions(userCommandService);
    }

    @ParameterizedTest(name = "{0}={1}")
    @CsvSource(delimiter = '|', value = {
            "role|\"master\"", "role|\"ADMIN\"", "role|\"\"", "isDeliveryManager|\"yes\"", "name|\"  \""})
    @DisplayName("역할은 MASTER·USER만, 배송담당 여부는 불리언, 문자열 필드는 공백 불가 → 400 INVALID_INPUT")
    void invalidFormat(String field, String json) throws Exception {
        mockMvc.perform(as("MASTER", with(field, json)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        verifyNoInteractions(userCommandService);
    }

    @Test
    @DisplayName("USER면 403 ACCESS_DENIED, 만들지 않는다 (Gateway를 거치지 않은 요청 방어)")
    void user_forbidden() throws Exception {
        mockMvc.perform(as("USER", VALID))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        verifyNoInteractions(userCommandService);
    }

    @Test
    @DisplayName("X-User-* 헤더가 없으면 401 UNAUTHENTICATED")
    void missingHeader() throws Exception {
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        verifyNoInteractions(userCommandService);
    }

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource({"INVALID_USERNAME, 400", "INVALID_PASSWORD, 400", "INVALID_ORGANIZATION, 400",
            "DUPLICATE_USERNAME, 409", "DUPLICATE_SLACK_ID, 409", "DELIVERY_MANAGER_REQUIRES_HUB, 409"})
    @DisplayName("규칙 위반은 domain 코드 그대로 응답한다")
    void businessError(UserErrorCode code, int status) throws Exception {
        given(userCommandService.createByMaster(any())).willThrow(new BusinessException(code));

        mockMvc.perform(as("MASTER", VALID))
                .andExpect(status().is(status))
                .andExpect(jsonPath("$.code").value(code.getCode()));
    }
}
