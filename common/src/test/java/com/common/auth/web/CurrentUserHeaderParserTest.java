package com.common.auth.web;

import com.common.auth.AuthContract;
import com.common.auth.AuthRole;
import com.common.auth.AuthUser;
import com.common.auth.web.CurrentUserHeaderParser.Absent;
import com.common.auth.web.CurrentUserHeaderParser.Invalid;
import com.common.auth.web.CurrentUserHeaderParser.Result;
import com.common.auth.web.CurrentUserHeaderParser.Valid;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CurrentUserHeaderParserTest {

    static final UUID ID = UUID.fromString("3f2b8c1e-7a4d-4e5f-9b6a-1c2d3e4f5a6b");

    @Nested
    @DisplayName("헤더 값 두 개로 판정")
    class Values {

        @Test
        @DisplayName("둘 다 없으면 Absent (공개 경로, 요청 밖)")
        void absent() {
            assertThat(CurrentUserHeaderParser.parse(null, null)).isInstanceOf(Absent.class);
        }

        @Test
        @DisplayName("빈 값·공백만 있는 값은 없는 것으로 본다")
        void blankIsAbsent() {
            assertThat(CurrentUserHeaderParser.parse("", " ")).isInstanceOf(Absent.class);
        }

        @Test
        @DisplayName("둘 다 올바르면 Valid(AuthUser)")
        void valid() {
            assertThat(CurrentUserHeaderParser.parse(ID.toString(), "MASTER"))
                    .isEqualTo(new Valid(new AuthUser(ID, AuthRole.MASTER)));
            assertThat(CurrentUserHeaderParser.parse(ID.toString(), "USER"))
                    .isEqualTo(new Valid(new AuthUser(ID, AuthRole.USER)));
        }

        @Test
        @DisplayName("앞뒤 공백은 지우고 판정한다")
        void trims() {
            assertThat(CurrentUserHeaderParser.parse("  " + ID + " ", " USER\t"))
                    .isEqualTo(new Valid(new AuthUser(ID, AuthRole.USER)));
        }

        @Test
        @DisplayName("UUID는 대문자로 와도 같은 사용자다")
        void uppercaseUuid() {
            assertThat(CurrentUserHeaderParser.parse(ID.toString().toUpperCase(), "USER"))
                    .isEqualTo(new Valid(new AuthUser(ID, AuthRole.USER)));
        }

        @ParameterizedTest(name = "[{index}] id={0}, role={1}")
        @CsvSource(nullValues = "NULL", value = {
                "3f2b8c1e-7a4d-4e5f-9b6a-1c2d3e4f5a6b, NULL",     // Id만
                "3f2b8c1e-7a4d-4e5f-9b6a-1c2d3e4f5a6b, ''",       // Id만 (Role 빈 값)
                "NULL, MASTER",                                    // Role만
                "not-a-uuid, USER",                                // UUID 아님
                "1-1-1-1-1, USER",                                 // fromString은 받지만 표준 형식 아님
                "3f2b8c1e7a4d4e5f9b6a1c2d3e4f5a6b, USER",          // 하이픈 없음
                "3f2b8c1e-7a4d-4e5f-9b6a-1c2d3e4f5a6b, ADMIN",     // 정의되지 않은 역할
                "3f2b8c1e-7a4d-4e5f-9b6a-1c2d3e4f5a6b, master",    // 대소문자 다름
        })
        @DisplayName("하나만 있거나 형식이 틀리면 Invalid")
        void invalid(String id, String role) {
            assertThat(CurrentUserHeaderParser.parse(id, role)).isInstanceOf(Invalid.class);
        }
    }

    @Nested
    @DisplayName("서블릿 요청에서 판정")
    class Request {

        @Test
        @DisplayName("요청 헤더를 읽어 판정한다 (헤더 이름 대소문자 무관)")
        void fromRequest() {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("x-user-id", ID.toString());
            request.addHeader("x-user-role", "USER");

            assertThat(CurrentUserHeaderParser.parse(request))
                    .isEqualTo(new Valid(new AuthUser(ID, AuthRole.USER)));
        }

        @Test
        @DisplayName("헤더가 없으면 Absent")
        void absent() {
            assertThat(CurrentUserHeaderParser.parse(new MockHttpServletRequest())).isInstanceOf(Absent.class);
        }

        @Test
        @DisplayName("같은 헤더가 두 번 오면 어느 값을 믿을지 모호하므로 Invalid")
        void duplicatedHeader() {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader(AuthContract.HEADER_USER_ID, ID.toString());
            request.addHeader(AuthContract.HEADER_USER_ID, UUID.randomUUID().toString());
            request.addHeader(AuthContract.HEADER_USER_ROLE, "USER");

            Result result = CurrentUserHeaderParser.parse(request);

            assertThat(result).isInstanceOf(Invalid.class);
        }

        @Test
        @DisplayName("역할 헤더가 두 번 와도 Invalid")
        void duplicatedRole() {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader(AuthContract.HEADER_USER_ID, ID.toString());
            request.addHeader(AuthContract.HEADER_USER_ROLE, "USER");
            request.addHeader(AuthContract.HEADER_USER_ROLE, "MASTER");

            assertThat(CurrentUserHeaderParser.parse(request)).isInstanceOf(Invalid.class);
        }
    }
}
