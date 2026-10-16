package com.user.presentation.controller;

import com.common.auth.AuthContract;
import com.common.exception.BusinessException;
import com.common.exception.UserErrorCode;
import com.common.exception.AuthErrorCode;
import com.user.application.dto.ChangePasswordCommand;
import com.user.application.dto.UpdateSlackIdCommand;
import com.user.application.dto.WithdrawCommand;
import com.user.application.facade.AccountFacade;
import com.user.application.dto.UserResult;
import com.user.application.service.UserCommandService;
import com.user.application.service.UserQueryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 본인 계정 API (U09 테스트 5~7, U10·U11 컨트롤러 테스트).
 */
@WebMvcTest(MyAccountController.class)
class MyAccountControllerTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    UserQueryService userQueryService;

    @MockitoBean
    UserCommandService userCommandService;

    @MockitoBean
    AccountFacade accountFacade;

    private static UserResult result(String slackId) {
        return new UserResult(USER_ID, "user01", "홍길동", slackId, "USER", false, "COMPANY", "부산수산",
                null, true, true, LocalDateTime.of(2026, 10, 15, 9, 0), null, null, null, null, null);
    }

    private static MockHttpServletRequestBuilder asUser(MockHttpServletRequestBuilder request) {
        return request.header(AuthContract.HEADER_USER_ID, USER_ID.toString()).header(AuthContract.HEADER_USER_ROLE, "USER");
    }

    @Nested
    @DisplayName("GET /api/v1/users/me")
    class GetMe {

        @Test
        @DisplayName("5. 200, X-User-Id의 사용자 정보. 비밀번호·삭제 정보는 없고 passwordChangeRequired는 있다")
        void ok() throws Exception {
            given(userQueryService.getActiveUser(USER_ID)).willReturn(result("U001"));

            String body = mockMvc.perform(asUser(get("/api/v1/users/me")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(USER_ID.toString()))
                    .andExpect(jsonPath("$.username").value("user01"))
                    .andExpect(jsonPath("$.slackId").value("U001"))
                    .andExpect(jsonPath("$.role").value("USER"))
                    .andExpect(jsonPath("$.isDeliveryManager").value(false))
                    .andExpect(jsonPath("$.organizationType").value("COMPANY"))
                    .andExpect(jsonPath("$.passwordChangeRequired").value(true))
                    .andReturn().getResponse().getContentAsString();

            assertThat(body.toLowerCase()).doesNotContain("hash").doesNotContain("deleted").doesNotContain("\"active\"");
        }

        @Test
        @DisplayName("탈퇴한 사용자(토큰은 아직 유효)는 404 USER_NOT_FOUND")
        void deleted() throws Exception {
            given(userQueryService.getActiveUser(USER_ID)).willThrow(new BusinessException(UserErrorCode.USER_NOT_FOUND));

            mockMvc.perform(asUser(get("/api/v1/users/me")))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
        }

        @Test
        @DisplayName("7. X-User-* 헤더가 없으면 401 UNAUTHENTICATED (Gateway를 거치지 않은 요청)")
        void missingHeader() throws Exception {
            mockMvc.perform(get("/api/v1/users/me"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
            verifyNoInteractions(userQueryService);
        }
    }

    @Nested
    @DisplayName("PATCH /api/v1/users/me")
    class UpdateMe {

        @Test
        @DisplayName("6. 200, 본인 ID와 slackId만 서비스로 넘긴다 (본문의 name·role은 무시)")
        void ok() throws Exception {
            given(userCommandService.updateSlackId(any())).willReturn(result("U999"));

            mockMvc.perform(asUser(patch("/api/v1/users/me")).contentType(MediaType.APPLICATION_JSON)
                            .content("{ \"slackId\": \"U999\", \"name\": \"김철수\", \"role\": \"MASTER\" }"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.slackId").value("U999"))
                    .andExpect(jsonPath("$.name").value("홍길동"));

            verify(userCommandService).updateSlackId(new UpdateSlackIdCommand(USER_ID, "U999"));
        }

        @Test
        @DisplayName("slackId가 비었거나 80자를 넘으면 400 INVALID_INPUT")
        void invalid() throws Exception {
            mockMvc.perform(asUser(patch("/api/v1/users/me")).contentType(MediaType.APPLICATION_JSON).content("{ \"slackId\": \" \" }"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value("slackId"));
            mockMvc.perform(asUser(patch("/api/v1/users/me")).contentType(MediaType.APPLICATION_JSON)
                            .content("{ \"slackId\": \"" + "U".repeat(81) + "\" }"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
            verifyNoInteractions(userCommandService);
        }

        @Test
        @DisplayName("다른 사용자의 Slack ID면 409 DUPLICATE_SLACK_ID")
        void duplicate() throws Exception {
            given(userCommandService.updateSlackId(any())).willThrow(new BusinessException(UserErrorCode.DUPLICATE_SLACK_ID));

            mockMvc.perform(asUser(patch("/api/v1/users/me")).contentType(MediaType.APPLICATION_JSON).content("{ \"slackId\": \"U002\" }"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("DUPLICATE_SLACK_ID"));
        }

        @Test
        @DisplayName("7. X-User-* 헤더가 없으면 401, 수정하지 않는다")
        void missingHeader() throws Exception {
            mockMvc.perform(patch("/api/v1/users/me").contentType(MediaType.APPLICATION_JSON).content("{ \"slackId\": \"U999\" }"))
                    .andExpect(status().isUnauthorized());
            verifyNoInteractions(userCommandService);
        }
    }

    @Nested
    @DisplayName("PATCH /api/v1/users/me/password")
    class ChangePassword {

        private static final String BODY = """
                { "currentPassword": "Abcd123!", "newPassword": "Newpw123!" }
                """;

        @Test
        @DisplayName("U10 테스트 8: 204, 본인 ID와 두 비밀번호를 넘긴다")
        void noContent() throws Exception {
            mockMvc.perform(asUser(patch("/api/v1/users/me/password")).contentType(MediaType.APPLICATION_JSON).content(BODY))
                    .andExpect(status().isNoContent());

            verify(accountFacade).changePassword(new ChangePasswordCommand(USER_ID, "Abcd123!", "Newpw123!"));
        }

        @ParameterizedTest(name = "{0} 빈 값 → 400")
        @CsvSource({"currentPassword", "newPassword"})
        @DisplayName("빈 값은 400 INVALID_INPUT (규칙 위반은 서비스가 INVALID_PASSWORD로 판단)")
        void blank(String field) throws Exception {
            String body = BODY.replaceFirst("\"" + field + "\": \"[^\"]*\"", "\"" + field + "\": \"\"");

            mockMvc.perform(asUser(patch("/api/v1/users/me/password")).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value(field));
            verifyNoInteractions(accountFacade);
        }

        @ParameterizedTest(name = "{0} → {1}")
        @CsvSource({"INVALID_CREDENTIALS, 401", "INVALID_PASSWORD, 400", "SAME_PASSWORD, 400"})
        @DisplayName("서비스 오류를 공통 형식으로 응답한다")
        void businessError(UserErrorCode code, int status) throws Exception {
            willThrow(new BusinessException(code)).given(accountFacade).changePassword(any());

            mockMvc.perform(asUser(patch("/api/v1/users/me/password")).contentType(MediaType.APPLICATION_JSON).content(BODY))
                    .andExpect(status().is(status))
                    .andExpect(jsonPath("$.code").value(code.getCode()));
        }

        @Test
        @DisplayName("Redis 장애는 503 AUTH_UNAVAILABLE")
        void authUnavailable() throws Exception {
            willThrow(new BusinessException(AuthErrorCode.AUTH_UNAVAILABLE)).given(accountFacade).changePassword(any());

            mockMvc.perform(asUser(patch("/api/v1/users/me/password")).contentType(MediaType.APPLICATION_JSON).content(BODY))
                    .andExpect(status().isServiceUnavailable());
        }

        @Test
        @DisplayName("X-User-* 헤더가 없으면 401")
        void missingHeader() throws Exception {
            mockMvc.perform(patch("/api/v1/users/me/password").contentType(MediaType.APPLICATION_JSON).content(BODY))
                    .andExpect(status().isUnauthorized());
            verifyNoInteractions(accountFacade);
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/users/me")
    class Withdraw {

        @Test
        @DisplayName("U11 테스트 8: DELETE 본문의 password를 받아 204")
        void noContent() throws Exception {
            mockMvc.perform(asUser(delete("/api/v1/users/me")).contentType(MediaType.APPLICATION_JSON)
                            .content("{ \"password\": \"Abcd123!\" }"))
                    .andExpect(status().isNoContent());

            verify(accountFacade).withdraw(new WithdrawCommand(USER_ID, "Abcd123!"));
        }

        @Test
        @DisplayName("U11 테스트 8: 본문이 없거나 password가 비면 400 INVALID_INPUT")
        void missingBody() throws Exception {
            mockMvc.perform(asUser(delete("/api/v1/users/me")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
            mockMvc.perform(asUser(delete("/api/v1/users/me")).contentType(MediaType.APPLICATION_JSON).content("{ \"password\": \"\" }"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value("password"));
            verifyNoInteractions(accountFacade);
        }

        @ParameterizedTest(name = "{0} → {1}")
        @CsvSource({"INVALID_CREDENTIALS, 401", "LAST_MASTER, 409"})
        @DisplayName("비밀번호 불일치 401, 마지막 MASTER 409")
        void businessError(UserErrorCode code, int status) throws Exception {
            willThrow(new BusinessException(code)).given(accountFacade).withdraw(any());

            mockMvc.perform(asUser(delete("/api/v1/users/me")).contentType(MediaType.APPLICATION_JSON)
                            .content("{ \"password\": \"Abcd123!\" }"))
                    .andExpect(status().is(status))
                    .andExpect(jsonPath("$.code").value(code.getCode()));
        }
    }
}
