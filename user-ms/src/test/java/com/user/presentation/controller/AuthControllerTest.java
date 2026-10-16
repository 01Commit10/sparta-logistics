package com.user.presentation.controller;

import com.common.exception.AuthErrorCode;
import com.common.exception.BusinessException;
import com.common.exception.UserErrorCode;
import com.user.application.dto.LoginCommand;
import com.user.application.dto.LoginResult;
import com.user.application.dto.SignupCommand;
import com.user.application.dto.TokenPairResult;
import com.user.application.dto.UserResult;
import com.common.auth.AuthContract;
import com.user.application.dto.LogoutCommand;
import com.user.application.facade.AuthFacade;
import com.user.application.service.TokenService;
import com.user.application.service.UserCommandService;
import com.user.application.service.UserQueryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
class AuthControllerTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    UserCommandService userCommandService;

    @MockitoBean
    UserQueryService userQueryService;

    @MockitoBean
    AuthFacade authFacade;

    @MockitoBean
    TokenService tokenService;

    private static final String VALID_BODY = """
            { "username": "user01", "password": "Abcd123!", "name": "홍길동", "slackId": "U001",
              "organizationType": "COMPANY", "organizationName": "부산수산" }
            """;

    private static UserResult result() {
        return new UserResult(USER_ID, "user01", "홍길동", "U001", "USER", false, "COMPANY", "부산수산",
                null, false, true, LocalDateTime.of(2026, 10, 15, 9, 0), null, null);
    }

    @Nested
    @DisplayName("POST /api/v1/auth/signup")
    class Signup {

        @Test
        @DisplayName("201과 가입한 사용자 정보를 돌려주고, 비밀번호는 응답에 없다")
        void created() throws Exception {
            given(userCommandService.signup(any())).willReturn(result());

            String body = mockMvc.perform(post("/api/v1/auth/signup")
                            .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(USER_ID.toString()))
                    .andExpect(jsonPath("$.username").value("user01"))
                    .andExpect(jsonPath("$.name").value("홍길동"))
                    .andExpect(jsonPath("$.slackId").value("U001"))
                    .andExpect(jsonPath("$.role").value("USER"))
                    .andExpect(jsonPath("$.organizationType").value("COMPANY"))
                    .andExpect(jsonPath("$.organizationName").value("부산수산"))
                    .andReturn().getResponse().getContentAsString();

            assertThat(body.toLowerCase()).doesNotContain("password").doesNotContain("abcd123!");
        }

        @Test
        @DisplayName("요청 값을 그대로 Command로 넘긴다")
        void passesCommand() throws Exception {
            given(userCommandService.signup(any())).willReturn(result());

            mockMvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                    .andExpect(status().isCreated());

            ArgumentCaptor<SignupCommand> captor = ArgumentCaptor.forClass(SignupCommand.class);
            verify(userCommandService).signup(captor.capture());
            assertThat(captor.getValue()).isEqualTo(
                    new SignupCommand("user01", "Abcd123!", "홍길동", "U001", "COMPANY", "부산수산"));
        }

        @ParameterizedTest(name = "{0} 누락·빈 값 → 400")
        @CsvSource({"username", "password", "name", "slackId", "organizationType", "organizationName"})
        @DisplayName("필수값이 없으면 400 INVALID_INPUT, errors에 필드 이름")
        void missingField(String field) throws Exception {
            String body = VALID_BODY.replaceFirst("\"" + field + "\": \"[^\"]*\"", "\"" + field + "\": \"\"");

            mockMvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                    .andExpect(jsonPath("$.errors[0].field").value(field));
            verifyNoInteractions(userCommandService);
        }

        @Test
        @DisplayName("길이 제한을 넘으면 400 INVALID_INPUT (name 100, slackId 80, organizationName 150)")
        void tooLong() throws Exception {
            String body = VALID_BODY.replace("\"홍길동\"", "\"" + "가".repeat(101) + "\"");

            mockMvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value("name"));
        }

        @Test
        @DisplayName("본문이 JSON이 아니면 400 INVALID_INPUT")
        void notJson() throws Exception {
            mockMvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON).content("{"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        }

        @ParameterizedTest
        @CsvSource({
                "DUPLICATE_USERNAME, 409",
                "DUPLICATE_SLACK_ID, 409",
                "INVALID_PASSWORD, 400",
                "INVALID_USERNAME, 400",
                "INVALID_ORGANIZATION, 400"
        })
        @DisplayName("서비스의 업무 오류는 공통 형식으로 응답한다")
        void businessError(UserErrorCode code, int status) throws Exception {
            given(userCommandService.signup(any())).willThrow(new BusinessException(code));

            mockMvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                    .andExpect(status().is(status))
                    .andExpect(jsonPath("$.code").value(code.getCode()))
                    .andExpect(jsonPath("$.message").value(code.getMessage()));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/auth/check-username")
    class CheckUsername {

        @Test
        @DisplayName("사용 가능하면 200 { available: true }")
        void available() throws Exception {
            given(userQueryService.isUsernameAvailable("user01")).willReturn(true);

            mockMvc.perform(get("/api/v1/auth/check-username").queryParam("username", "user01"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.available").value(true));
        }

        @Test
        @DisplayName("이미 쓰면 200 { available: false }")
        void taken() throws Exception {
            given(userQueryService.isUsernameAvailable("user01")).willReturn(false);

            mockMvc.perform(get("/api/v1/auth/check-username").queryParam("username", "user01"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.available").value(false));
        }

        @Test
        @DisplayName("username이 없으면 400 INVALID_INPUT")
        void missing() throws Exception {
            mockMvc.perform(get("/api/v1/auth/check-username"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
            verifyNoInteractions(userQueryService);
        }

        @Test
        @DisplayName("형식이 틀리면 400 INVALID_USERNAME (서비스가 판단)")
        void invalidFormat() throws Exception {
            given(userQueryService.isUsernameAvailable("A!")).willThrow(new BusinessException(UserErrorCode.INVALID_USERNAME));

            mockMvc.perform(get("/api/v1/auth/check-username").queryParam("username", "A!"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_USERNAME"));
        }
    }

    @Nested
    @DisplayName("POST /api/v1/auth/login")
    class Login {

        private static final String LOGIN_BODY = """
                { "username": "user01", "password": "Abcd123!" }
                """;

        @Test
        @DisplayName("200과 accessToken·refreshToken·passwordChangeRequired 3개 필드를 감싸지 않고 돌려준다")
        void ok() throws Exception {
            given(authFacade.login(any())).willReturn(new LoginResult("access.jwt", "refresh.jwt", true));

            mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(LOGIN_BODY))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(3))
                    .andExpect(jsonPath("$.accessToken").value("access.jwt"))
                    .andExpect(jsonPath("$.refreshToken").value("refresh.jwt"))
                    .andExpect(jsonPath("$.passwordChangeRequired").value(true));

            verify(authFacade).login(new LoginCommand("user01", "Abcd123!"));
        }

        @Test
        @DisplayName("인증 실패는 401 INVALID_CREDENTIALS")
        void invalidCredentials() throws Exception {
            given(authFacade.login(any())).willThrow(new BusinessException(UserErrorCode.INVALID_CREDENTIALS));

            mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(LOGIN_BODY))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        }

        @Test
        @DisplayName("Redis 장애는 503 AUTH_UNAVAILABLE")
        void authUnavailable() throws Exception {
            given(authFacade.login(any())).willThrow(new BusinessException(AuthErrorCode.AUTH_UNAVAILABLE));

            mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(LOGIN_BODY))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.code").value("AUTH_UNAVAILABLE"));
        }

        @ParameterizedTest(name = "{0} 빈 값 → 400")
        @CsvSource({"username", "password"})
        @DisplayName("빈 값은 400 INVALID_INPUT이고 인증을 시도하지 않는다")
        void blank(String field) throws Exception {
            String body = LOGIN_BODY.replaceFirst("\"" + field + "\": \"[^\"]*\"", "\"" + field + "\": \" \"");

            mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                    .andExpect(jsonPath("$.errors[0].field").value(field));
            verifyNoInteractions(authFacade);
        }

        @Test
        @DisplayName("가입 규칙에 맞지 않는 ID도 400이 아니라 인증으로 넘긴다 (결과는 401)")
        void malformedUsernameGoesToAuthentication() throws Exception {
            given(authFacade.login(any())).willThrow(new BusinessException(UserErrorCode.INVALID_CREDENTIALS));

            mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                            .content("{ \"username\": \"NOT_VALID!\", \"password\": \"x\" }"))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("POST /api/v1/auth/reissue")
    class Reissue {

        private static final String REISSUE_BODY = """
                { "refreshToken": "refresh.jwt" }
                """;

        @Test
        @DisplayName("200과 새 accessToken·refreshToken 2개 필드를 감싸지 않고 돌려준다")
        void ok() throws Exception {
            given(authFacade.reissue("refresh.jwt")).willReturn(new TokenPairResult("new-access.jwt", "new-refresh.jwt"));

            mockMvc.perform(post("/api/v1/auth/reissue").contentType(MediaType.APPLICATION_JSON).content(REISSUE_BODY))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$.accessToken").value("new-access.jwt"))
                    .andExpect(jsonPath("$.refreshToken").value("new-refresh.jwt"));
        }

        @Test
        @DisplayName("검증 실패는 401 REFRESH_TOKEN_INVALID")
        void invalid() throws Exception {
            given(authFacade.reissue(any())).willThrow(new BusinessException(AuthErrorCode.REFRESH_TOKEN_INVALID));

            mockMvc.perform(post("/api/v1/auth/reissue").contentType(MediaType.APPLICATION_JSON).content(REISSUE_BODY))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("REFRESH_TOKEN_INVALID"));
        }

        @Test
        @DisplayName("Redis 장애는 503 AUTH_UNAVAILABLE")
        void authUnavailable() throws Exception {
            given(authFacade.reissue(any())).willThrow(new BusinessException(AuthErrorCode.AUTH_UNAVAILABLE));

            mockMvc.perform(post("/api/v1/auth/reissue").contentType(MediaType.APPLICATION_JSON).content(REISSUE_BODY))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.code").value("AUTH_UNAVAILABLE"));
        }

        @ParameterizedTest(name = "본문 {0} → 400")
        @org.junit.jupiter.params.provider.ValueSource(strings = {"{}", "{ \"refreshToken\": \"\" }", "{ \"refreshToken\": \" \" }"})
        @DisplayName("refreshToken이 없거나 빈 값이면 400 INVALID_INPUT이고 재발급을 시도하지 않는다")
        void blank(String body) throws Exception {
            mockMvc.perform(post("/api/v1/auth/reissue").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                    .andExpect(jsonPath("$.errors[0].field").value("refreshToken"));
            verifyNoInteractions(authFacade);
        }
    }

    @Nested
    @DisplayName("POST /api/v1/auth/logout")
    class Logout {

        private static final long EXP = 1792117822L;

        private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder logout() {
            return post("/api/v1/auth/logout")
                    .header(AuthContract.HEADER_USER_ID, USER_ID.toString())
                    .header(AuthContract.HEADER_USER_ROLE, "USER")
                    .header(AuthContract.HEADER_TOKEN_JTI, "jti-1")
                    .header(AuthContract.HEADER_TOKEN_EXP, String.valueOf(EXP));
        }

        @Test
        @DisplayName("204, Gateway가 넣은 사용자 ID·jti·exp로 로그아웃한다")
        void noContent() throws Exception {
            mockMvc.perform(logout()).andExpect(status().isNoContent());

            verify(tokenService).logout(new LogoutCommand(USER_ID, "jti-1", java.time.Instant.ofEpochSecond(EXP)));
        }

        @ParameterizedTest(name = "{0} 없음 → 401")
        @CsvSource({"X-User-Id", "X-Token-Jti", "X-Token-Exp"})
        @DisplayName("Gateway가 넣는 헤더가 없으면 401 UNAUTHENTICATED (Gateway를 거치지 않은 요청), 로그아웃하지 않는다")
        void missingGatewayHeader(String header) throws Exception {
            var request = post("/api/v1/auth/logout");
            if (!header.equals("X-User-Id")) {
                request.header(AuthContract.HEADER_USER_ID, USER_ID.toString()).header(AuthContract.HEADER_USER_ROLE, "USER");
            }
            if (!header.equals("X-Token-Jti")) {
                request.header(AuthContract.HEADER_TOKEN_JTI, "jti-1");
            }
            if (!header.equals("X-Token-Exp")) {
                request.header(AuthContract.HEADER_TOKEN_EXP, String.valueOf(EXP));
            }

            mockMvc.perform(request)
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
            verifyNoInteractions(tokenService);
        }

        @Test
        @DisplayName("Redis 장애는 503 AUTH_UNAVAILABLE")
        void authUnavailable() throws Exception {
            org.mockito.BDDMockito.willThrow(new BusinessException(AuthErrorCode.AUTH_UNAVAILABLE))
                    .given(tokenService).logout(any());

            mockMvc.perform(logout())
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.code").value("AUTH_UNAVAILABLE"));
        }
    }
}
