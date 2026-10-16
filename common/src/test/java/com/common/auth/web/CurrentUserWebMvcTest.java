package com.common.auth.web;

import com.common.auth.AuthContract;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 실제 MVC 요청으로 입구 검사·리졸버·공통 오류 형식을 함께 확인한다 (C01 13-7, 17).
 * 등록은 자동 설정(AutoConfigureWebMvc.imports의 CommonAuthWebAutoConfiguration)에 맡긴다. 서비스의 @WebMvcTest와 같은 조건.
 */
@WebMvcTest(CurrentUserTestController.class)
class CurrentUserWebMvcTest {

    static final String ID = UUID.randomUUID().toString();

    @Autowired
    MockMvc mvc;

    ResultActions call(String path, String id, String role) throws Exception {
        var request = get(path);
        if (id != null) {
            request.header(AuthContract.HEADER_USER_ID, id);
        }
        if (role != null) {
            request.header(AuthContract.HEADER_USER_ROLE, role);
        }
        return mvc.perform(request);
    }

    static void unauthenticated(ResultActions result) throws Exception {
        result.andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.message").value("인증이 필요합니다."));
    }

    @Test
    @DisplayName("17. 올바른 헤더 → 200, Controller가 사용자를 받는다")
    void valid() throws Exception {
        call("/test/me", ID, "MASTER")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ID))
                .andExpect(jsonPath("$.role").value("MASTER"));
    }

    @Test
    @DisplayName("17. 필수 @CurrentUser인데 헤더 없음 → 401 공통 오류 형식")
    void requiredAbsent() throws Exception {
        unauthenticated(call("/test/me", null, null));
    }

    @Test
    @DisplayName("17. 선택 @CurrentUser + 헤더 없음 → 200, null")
    void optionalAbsent() throws Exception {
        call("/test/optional", null, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user").value("none"));
    }

    @Test
    @DisplayName("13-7. @CurrentUser가 없는 API도 헤더가 없으면 통과")
    void publicAbsent() throws Exception {
        call("/test/public", null, null).andExpect(status().isOk());
    }

    @Test
    @DisplayName("13-7. @CurrentUser가 없는 API에도 잘못된 헤더는 401 공통 오류 형식 (입구 검사)")
    void publicInvalid() throws Exception {
        unauthenticated(call("/test/public", "not-a-uuid", "USER"));
        unauthenticated(call("/test/public", ID, null));
        unauthenticated(call("/test/public", ID, "ADMIN"));
    }

    @Test
    @DisplayName("13-7. 선택 @CurrentUser여도 잘못된 헤더는 401")
    void optionalInvalid() throws Exception {
        unauthenticated(call("/test/optional", null, "USER"));
    }

    @Test
    @DisplayName("Gateway가 넣는 X-Token-* 헤더가 없으면 401 UNAUTHENTICATED (Gateway를 거치지 않은 요청, X-User-*와 같은 취급)")
    void missingTokenHeader() throws Exception {
        unauthenticated(call("/test/token-header", ID, "USER"));
    }

    @Test
    @DisplayName("클라이언트가 넣는 다른 필수 헤더가 없으면 400 INVALID_INPUT")
    void missingClientHeader() throws Exception {
        call("/test/client-header", null, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
    }
}
