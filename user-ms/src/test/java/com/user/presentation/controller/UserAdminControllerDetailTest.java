package com.user.presentation.controller;

import com.common.auth.AuthContract;
import com.common.exception.BusinessException;
import com.common.exception.UserErrorCode;
import com.user.application.dto.UserResult;
import com.user.application.service.UserCommandService;
import com.user.application.service.UserQueryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GET /api/v1/users/{userId} (U13 상세, USR-12).
 */
@WebMvcTest(UserAdminController.class)
class UserAdminControllerDetailTest {

    private static final UUID MASTER_ID = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
    private static final UUID TARGET_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    /** 마지막 수정자와 삭제자를 구분하려고 다른 MASTER로 둔다 */
    private static final UUID OTHER_MASTER_ID = UUID.fromString("00000000-0000-0000-0000-0000000000bb");

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    UserQueryService userQueryService;

    @MockitoBean
    UserCommandService userCommandService;

    private static MockHttpServletRequestBuilder as(String role, UUID id, Object userId) {
        return get("/api/v1/users/{userId}", userId)
                .header(AuthContract.HEADER_USER_ID, id.toString()).header(AuthContract.HEADER_USER_ROLE, role);
    }

    @Test
    @DisplayName("200, 삭제된 사용자도 active=false와 삭제 일시·삭제자, 작성·수정자를 보여 준다. 해시는 없다")
    void deletedUser() throws Exception {
        given(userQueryService.getUser(TARGET_ID)).willReturn(new UserResult(TARGET_ID, "user01", "홍길동", "U001",
                "USER", false, "HUB", "서울센터", null, false, false,
                LocalDateTime.of(2026, 10, 15, 9, 0), TARGET_ID,
                LocalDateTime.of(2026, 10, 16, 9, 0), MASTER_ID,
                LocalDateTime.of(2026, 10, 16, 10, 0), OTHER_MASTER_ID));

        String body = mockMvc.perform(as("MASTER", MASTER_ID, TARGET_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(TARGET_ID.toString()))
                .andExpect(jsonPath("$.username").value("user01"))
                .andExpect(jsonPath("$.slackId").value("U001"))
                .andExpect(jsonPath("$.isDeliveryManager").value(false))
                .andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.createdBy").value(TARGET_ID.toString()))
                .andExpect(jsonPath("$.updatedBy").value(MASTER_ID.toString()))
                .andExpect(jsonPath("$.updatedAt").value("2026-10-16T09:00:00"))
                .andExpect(jsonPath("$.deletedAt").value("2026-10-16T10:00:00"))
                .andExpect(jsonPath("$.deletedBy").value(OTHER_MASTER_ID.toString()))
                .andReturn().getResponse().getContentAsString();

        assertThat(body.toLowerCase()).doesNotContain("hash").doesNotContain("password\"");
    }

    @Test
    @DisplayName("없는 사용자는 404 USER_NOT_FOUND")
    void notFound() throws Exception {
        given(userQueryService.getUser(TARGET_ID)).willThrow(new BusinessException(UserErrorCode.USER_NOT_FOUND));

        mockMvc.perform(as("MASTER", MASTER_ID, TARGET_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
    }

    @Test
    @DisplayName("7. USER면 403 ACCESS_DENIED, 조회하지 않는다 (본인 ID여도 /users/me를 써야 한다)")
    void user_forbidden() throws Exception {
        mockMvc.perform(as("USER", TARGET_ID, TARGET_ID))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        verifyNoInteractions(userQueryService);
    }

    @Test
    @DisplayName("X-User-* 헤더가 없으면 401, userId가 UUID가 아니면 400")
    void unauthenticatedOrInvalid() throws Exception {
        mockMvc.perform(get("/api/v1/users/{userId}", TARGET_ID))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(as("MASTER", MASTER_ID, "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        verifyNoInteractions(userQueryService);
    }
}
