package com.user.presentation.controller;

import com.common.exception.BusinessException;
import com.common.exception.UserErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.user.application.dto.UserResult;
import com.user.application.service.UserQueryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 서비스 간 계약(delivery-ms 등이 Feign으로 호출). Gateway를 거치지 않으므로 X-User-* 헤더가 없다.
 */
@WebMvcTest(InternalUserController.class)
class InternalUserControllerTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @MockitoBean
    UserQueryService userQueryService;

    private static UserResult result() {
        return new UserResult(USER_ID, "driver01", "홍길동", "U001", "USER", true, "HUB", "서울센터",
                null, false, true, LocalDateTime.of(2026, 10, 15, 9, 0), null, null);
    }

    @Test
    @DisplayName("200과 계약 필드 6개만 돌려준다 (username·소속·비밀번호 관련 값은 없다)")
    void ok() throws Exception {
        given(userQueryService.getActiveUser(USER_ID)).willReturn(result());

        String body = mockMvc.perform(get("/internal/v1/users/{userId}", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(USER_ID.toString()))
                .andExpect(jsonPath("$.name").value("홍길동"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.isDeliveryManager").value(true))
                .andExpect(jsonPath("$.slackId").value("U001"))
                .andExpect(jsonPath("$.active").value(true))
                .andReturn().getResponse().getContentAsString();

        JsonNode json = objectMapper.readTree(body);
        List<String> fields = new ArrayList<>();
        json.fieldNames().forEachRemaining(fields::add);
        assertThat(fields).containsExactlyInAnyOrder("id", "name", "role", "isDeliveryManager", "slackId", "active");
    }

    @Test
    @DisplayName("X-User-* 헤더 없이 호출해도 된다 (서비스 간 호출)")
    void noUserHeaders() throws Exception {
        given(userQueryService.getActiveUser(USER_ID)).willReturn(result());

        mockMvc.perform(get("/internal/v1/users/{userId}", USER_ID))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("없거나 삭제된 사용자는 404 USER_NOT_FOUND (공통 오류 형식)")
    void notFound() throws Exception {
        given(userQueryService.getActiveUser(USER_ID)).willThrow(new BusinessException(UserErrorCode.USER_NOT_FOUND));

        mockMvc.perform(get("/internal/v1/users/{userId}", USER_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("UUID 형식이 아니면 400 INVALID_INPUT, 유스케이스는 호출하지 않는다")
    void invalidUuid() throws Exception {
        mockMvc.perform(get("/internal/v1/users/{userId}", "not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.errors[0].field").value("userId"));

        verifyNoInteractions(userQueryService);
    }
}
