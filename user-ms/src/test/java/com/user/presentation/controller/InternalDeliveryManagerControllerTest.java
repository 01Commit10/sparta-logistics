package com.user.presentation.controller;

import com.common.exception.BusinessException;
import com.common.exception.UserErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.user.application.dto.DeliveryManagerAssignment;
import com.user.application.dto.DeliveryManagerResult;
import com.user.application.service.DeliveryManagerAssignService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

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
 * 서비스 간 계약 (delivery-ms가 배송 생성 시 Feign으로 호출). Gateway를 거치지 않으므로 X-User-* 헤더가 없다.
 */
@WebMvcTest(InternalDeliveryManagerController.class)
class InternalDeliveryManagerControllerTest {

    private static final String URL = "/internal/v1/users/delivery-managers/random";
    private static final UUID SEOUL = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID BUSAN = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
    private static final UUID MANAGER_1 = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID MANAGER_2 = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @MockitoBean
    DeliveryManagerAssignService deliveryManagerAssignService;

    @Test
    @DisplayName("200과 출발지·도착지 담당자 id 두 개만, 평평한 문자열로 (이름·Slack ID는 내보내지 않는다)")
    void ok() throws Exception {
        given(deliveryManagerAssignService.assignRandomly(SEOUL, BUSAN)).willReturn(new DeliveryManagerAssignment(
                new DeliveryManagerResult(MANAGER_1, "김서울", "U001", SEOUL),
                new DeliveryManagerResult(MANAGER_2, "김부산", "U002", BUSAN)));

        String body = mockMvc.perform(get(URL).param("departureHubId", SEOUL.toString())
                        .param("arrivalHubId", BUSAN.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.departureManagerId").value(MANAGER_1.toString()))
                .andExpect(jsonPath("$.arrivalManagerId").value(MANAGER_2.toString()))
                .andReturn().getResponse().getContentAsString();

        JsonNode json = objectMapper.readTree(body);
        assertThat(fields(json)).containsExactlyInAnyOrder("departureManagerId", "arrivalManagerId");
        assertThat(json.get("departureManagerId").isTextual()).isTrue();
        assertThat(json.get("arrivalManagerId").isTextual()).isTrue();
    }

    @Test
    @DisplayName("후보가 없으면 404 DELIVERY_MANAGER_NOT_FOUND (공통 오류 형식)")
    void notFound() throws Exception {
        given(deliveryManagerAssignService.assignRandomly(SEOUL, BUSAN)).willThrow(new BusinessException(
                UserErrorCode.DELIVERY_MANAGER_NOT_FOUND, "출발지 허브에 배정할 수 있는 배송담당자가 없습니다."));

        mockMvc.perform(get(URL).param("departureHubId", SEOUL.toString())
                        .param("arrivalHubId", BUSAN.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DELIVERY_MANAGER_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("출발지 허브에 배정할 수 있는 배송담당자가 없습니다."));
    }

    @Test
    @DisplayName("허브 ID가 빠지면 400 INVALID_INPUT, 유스케이스는 호출하지 않는다")
    void missingParam() throws Exception {
        mockMvc.perform(get(URL).param("departureHubId", SEOUL.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        verifyNoInteractions(deliveryManagerAssignService);
    }

    @Test
    @DisplayName("UUID 형식이 아니면 400 INVALID_INPUT, 유스케이스는 호출하지 않는다")
    void invalidUuid() throws Exception {
        mockMvc.perform(get(URL).param("departureHubId", "not-a-uuid")
                        .param("arrivalHubId", BUSAN.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.errors[0].field").value("departureHubId"));

        verifyNoInteractions(deliveryManagerAssignService);
    }

    private static List<String> fields(JsonNode node) {
        List<String> fields = new ArrayList<>();
        node.fieldNames().forEachRemaining(fields::add);
        return fields;
    }
}
