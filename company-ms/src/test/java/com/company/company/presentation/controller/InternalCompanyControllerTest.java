package com.company.company.presentation.controller;

import com.common.exception.BusinessException;
import com.common.exception.CompanyErrorCode;
import com.company.company.application.dto.CompanyResult;
import com.company.company.application.service.CompanyService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * 서비스 간 계약 GET /internal/v1/companies/{companyId} (order-ms 등이 Feign으로 호출).
 * Gateway를 거치지 않으므로 X-User-* 헤더가 없다.
 */
@WebMvcTest(InternalCompanyController.class)
class InternalCompanyControllerTest {

    static final UUID COMPANY_ID = UUID.fromString("00000000-0000-0000-0000-0000000000d1");
    static final UUID HUB_ID = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
    static final UUID MANAGER_ID = UUID.fromString("00000000-0000-0000-0000-0000000000c1");

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @MockitoBean
    CompanyService companyService;

    private static CompanyResult result() {
        LocalDateTime at = LocalDateTime.of(2026, 10, 17, 9, 0);
        return new CompanyResult(COMPANY_ID, "스파르타상사", "서울시 강남구 테헤란로 1", HUB_ID, MANAGER_ID, at, at);
    }

    @Test
    @DisplayName("X-User-* 헤더 없이 호출하면 200과 계약 필드 3개만 돌려준다 (주소·관리자·생성/수정 일시는 없다)")
    void ok() throws Exception {
        given(companyService.getCompany(COMPANY_ID)).willReturn(result());

        String body = mockMvc.perform(get("/internal/v1/companies/{companyId}", COMPANY_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(COMPANY_ID.toString()))
                .andExpect(jsonPath("$.name").value("스파르타상사"))
                .andExpect(jsonPath("$.hubId").value(HUB_ID.toString()))
                .andReturn().getResponse().getContentAsString();

        JsonNode json = objectMapper.readTree(body);
        List<String> fields = new ArrayList<>();
        json.fieldNames().forEachRemaining(fields::add);
        assertThat(fields).containsExactlyInAnyOrder("id", "name", "hubId");
    }

    @Test
    @DisplayName("없거나 삭제된 업체는 404 COMPANY_NOT_FOUND (공통 오류 형식)")
    void companyNotFound() throws Exception {
        given(companyService.getCompany(COMPANY_ID)).willThrow(new BusinessException(CompanyErrorCode.COMPANY_NOT_FOUND));

        mockMvc.perform(get("/internal/v1/companies/{companyId}", COMPANY_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COMPANY_NOT_FOUND"))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("UUID 형식이 아니면 400 INVALID_INPUT, 유스케이스는 호출하지 않는다")
    void invalidUuid() throws Exception {
        mockMvc.perform(get("/internal/v1/companies/{companyId}", "not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.errors[0].field").value("companyId"));

        verifyNoInteractions(companyService);
    }
}
