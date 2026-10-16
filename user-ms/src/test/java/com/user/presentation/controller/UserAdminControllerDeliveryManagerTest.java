package com.user.presentation.controller;

import com.common.auth.AuthContract;
import com.common.exception.BusinessException;
import com.common.exception.UserErrorCode;
import com.user.application.dto.AssignDeliveryManagerCommand;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PATCH /api/v1/users/{userId}/delivery-manager (U16 테스트 3).
 */
@WebMvcTest(UserAdminController.class)
class UserAdminControllerDeliveryManagerTest {

    private static final UUID MASTER_ID = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
    private static final UUID TARGET_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String URL = "/api/v1/users/{userId}/delivery-manager";

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    UserQueryService userQueryService;

    @MockitoBean
    UserCommandService userCommandService;

    @MockitoBean
    UserAdminFacade userAdminFacade;

    private static UserResult result(boolean deliveryManager) {
        return new UserResult(TARGET_ID, "driver01", "김배송", "U001", "USER", deliveryManager, "HUB", "서울센터",
                null, false, true, LocalDateTime.of(2026, 10, 15, 9, 0), TARGET_ID,
                LocalDateTime.of(2026, 10, 16, 9, 0), MASTER_ID, null, null);
    }

    private static MockHttpServletRequestBuilder as(String role, UUID id, String body) {
        return patch(URL, TARGET_ID).contentType(MediaType.APPLICATION_JSON).content(body)
                .header(AuthContract.HEADER_USER_ID, id.toString()).header(AuthContract.HEADER_USER_ROLE, role);
    }

    private static MockHttpServletRequestBuilder asMaster(String body) {
        return as("MASTER", MASTER_ID, body);
    }

    @ParameterizedTest(name = "isDeliveryManager={0}")
    @ValueSource(booleans = {true, false})
    @DisplayName("200, 대상 ID와 값을 넘기고 관리자용 상세를 돌려준다")
    void ok(boolean value) throws Exception {
        given(userCommandService.assignDeliveryManager(any())).willReturn(result(value));

        String body = mockMvc.perform(asMaster("{ \"isDeliveryManager\": " + value + " }"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(TARGET_ID.toString()))
                .andExpect(jsonPath("$.isDeliveryManager").value(value))
                .andExpect(jsonPath("$.organizationType").value("HUB"))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.updatedAt").exists())
                .andExpect(jsonPath("$.updatedBy").value(MASTER_ID.toString()))
                .andReturn().getResponse().getContentAsString();

        verify(userCommandService).assignDeliveryManager(new AssignDeliveryManagerCommand(TARGET_ID, value));
        assertThat(body.toLowerCase()).doesNotContain("hash");
    }

    @ParameterizedTest(name = "본문 {0}")
    @ValueSource(strings = {"{}", "{ \"isDeliveryManager\": null }"})
    @DisplayName("값이 없으면 400 INVALID_INPUT, 바꾸지 않는다")
    void missingValue(String body) throws Exception {
        mockMvc.perform(asMaster(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.errors[0].field").value("isDeliveryManager"));
        verifyNoInteractions(userCommandService);
    }

    @Test
    @DisplayName("불리언이 아니면 400 INVALID_INPUT")
    void notBoolean() throws Exception {
        mockMvc.perform(asMaster("{ \"isDeliveryManager\": \"yes\" }"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        verifyNoInteractions(userCommandService);
    }

    @Test
    @DisplayName("userId가 UUID가 아니면 400 INVALID_INPUT")
    void invalidUserId() throws Exception {
        mockMvc.perform(patch(URL, "not-a-uuid").contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"isDeliveryManager\": true }")
                        .header(AuthContract.HEADER_USER_ID, MASTER_ID.toString())
                        .header(AuthContract.HEADER_USER_ROLE, "MASTER"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        verifyNoInteractions(userCommandService);
    }

    @Test
    @DisplayName("USER면 403 ACCESS_DENIED, 바꾸지 않는다 (Gateway를 거치지 않은 요청 방어). 본인 대상이어도 같다")
    void user_forbidden() throws Exception {
        mockMvc.perform(as("USER", TARGET_ID, "{ \"isDeliveryManager\": true }"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        verifyNoInteractions(userCommandService);
    }

    @Test
    @DisplayName("X-User-* 헤더가 없으면 401 UNAUTHENTICATED")
    void missingHeader() throws Exception {
        mockMvc.perform(patch(URL, TARGET_ID).contentType(MediaType.APPLICATION_JSON).content("{ \"isDeliveryManager\": true }"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        verifyNoInteractions(userCommandService);
    }

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource({"USER_NOT_FOUND, 404", "DELIVERY_MANAGER_REQUIRES_HUB, 409"})
    @DisplayName("대상 없음 404, 허브 소속 아님 409")
    void businessError(UserErrorCode code, int status) throws Exception {
        given(userCommandService.assignDeliveryManager(any())).willThrow(new BusinessException(code));

        mockMvc.perform(asMaster("{ \"isDeliveryManager\": true }"))
                .andExpect(status().is(status))
                .andExpect(jsonPath("$.code").value(code.getCode()));
    }
}
