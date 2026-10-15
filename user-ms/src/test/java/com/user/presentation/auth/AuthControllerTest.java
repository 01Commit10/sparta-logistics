package com.user.presentation.auth;

import com.common.exception.BusinessException;
import com.common.exception.UserErrorCode;
import com.user.application.user.UserCommandService;
import com.user.application.user.UserQueryService;
import com.user.application.user.dto.SignupCommand;
import com.user.application.user.dto.UserResult;
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

    private static final String VALID_BODY = """
            { "username": "user01", "password": "Abcd123!", "name": "홍길동", "slackId": "U001",
              "organizationType": "COMPANY", "organizationName": "부산수산" }
            """;

    private static UserResult result() {
        return new UserResult(USER_ID, "user01", "홍길동", "U001", "USER", false, "COMPANY", "부산수산",
                null, false, true, LocalDateTime.of(2026, 10, 15, 9, 0));
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
}
