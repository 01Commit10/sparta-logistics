package com.company.company.presentation.controller;

import com.common.auth.AuthContract;
import com.common.exception.BusinessException;
import com.common.exception.CommonErrorCode;
import com.common.exception.CompanyErrorCode;
import com.company.company.application.dto.CompanyResult;
import com.company.company.application.dto.UpdateCompanyCommand;
import com.company.company.application.service.CompanyService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 업체 수정 PATCH /api/v1/companies/{companyId} (04 API 명세서 Company): MASTER·업체관리자, 200.
 */
@WebMvcTest(CompanyController.class)
class CompanyControllerUpdateTest {

    static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
    static final UUID COMPANY_ID = UUID.fromString("00000000-0000-0000-0000-0000000000d1");
    static final UUID HUB_ID = UUID.fromString("00000000-0000-0000-0000-0000000000b1");

    static final CompanyResult UPDATED =
            new CompanyResult(COMPANY_ID, "스파르타물산", "서울시 강남구 테헤란로 1", HUB_ID, USER_ID, null, null);

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    CompanyService companyService;

    private ResultActions update(String role, String companyId, String body) throws Exception {
        MockHttpServletRequestBuilder request = patch("/api/v1/companies/{companyId}", companyId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
        if (role != null) {
            request.header(AuthContract.HEADER_USER_ID, USER_ID.toString())
                    .header(AuthContract.HEADER_USER_ROLE, role);
        }
        return mockMvc.perform(request);
    }

    private ResultActions update(String role, String body) throws Exception {
        return update(role, COMPANY_ID.toString(), body);
    }

    @Test
    @DisplayName("수정하면 200과 수정된 업체를 돌려주고, 빠진 필드는 null로 Command에 넘긴다")
    void updated() throws Exception {
        given(companyService.updateCompany(any())).willReturn(UPDATED);

        update("USER", "{\"name\":\"스파르타물산\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(COMPANY_ID.toString()))
                .andExpect(jsonPath("$.name").value("스파르타물산"))
                .andExpect(jsonPath("$.hubId").value(HUB_ID.toString()));

        then(companyService).should().updateCompany(new UpdateCompanyCommand(
                COMPANY_ID, "스파르타물산", null, null, null, USER_ID, false));
    }

    @Test
    @DisplayName("MASTER면 actorIsMaster=true로 넘기고, 허브·관리자도 Command에 담는다")
    void master() throws Exception {
        given(companyService.updateCompany(any())).willReturn(UPDATED);
        UUID newHub = UUID.randomUUID();
        UUID newManager = UUID.randomUUID();

        update("MASTER", "{\"hubId\":\"%s\",\"managerUserId\":\"%s\"}".formatted(newHub, newManager))
                .andExpect(status().isOk());

        then(companyService).should().updateCompany(new UpdateCompanyCommand(
                COMPANY_ID, null, null, newHub, newManager, USER_ID, true));
    }

    @Test
    @DisplayName("권한이 없으면 403 ACCESS_DENIED")
    void accessDenied() throws Exception {
        given(companyService.updateCompany(any())).willThrow(new BusinessException(CommonErrorCode.ACCESS_DENIED));

        update("USER", "{\"hubId\":\"%s\"}".formatted(HUB_ID))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    @DisplayName("업체가 없으면 404 COMPANY_NOT_FOUND")
    void companyNotFound() throws Exception {
        given(companyService.updateCompany(any())).willThrow(new BusinessException(CompanyErrorCode.COMPANY_NOT_FOUND));

        update("MASTER", "{\"name\":\"스파르타물산\"}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COMPANY_NOT_FOUND"));
    }

    @Test
    @DisplayName("바꿀 허브가 없으면 400 COMPANY_HUB_NOT_FOUND")
    void hubNotFound() throws Exception {
        given(companyService.updateCompany(any())).willThrow(new BusinessException(CompanyErrorCode.COMPANY_HUB_NOT_FOUND));

        update("MASTER", "{\"hubId\":\"%s\"}".formatted(HUB_ID))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMPANY_HUB_NOT_FOUND"));
    }

    @Test
    @DisplayName("X-User-* 헤더가 없으면 401 UNAUTHENTICATED")
    void noUserHeaders() throws Exception {
        update(null, "{\"name\":\"스파르타물산\"}")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        verifyNoInteractions(companyService);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{}",
            "{\"name\":null,\"address\":null,\"hubId\":null,\"managerUserId\":null}",
            "{\"name\":\" \"}",
            "{\"name\":\"\"}",
            "{\"address\":\"  \"}"
    })
    @DisplayName("빈 요청(모든 필드 null), name·address 공백이면 400 INVALID_INPUT")
    void invalidInput(String body) throws Exception {
        update("MASTER", body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.errors[0].field").exists());

        verifyNoInteractions(companyService);
    }

    @Test
    @DisplayName("업체명 150자·주소 255자를 넘으면 400 INVALID_INPUT")
    void tooLong() throws Exception {
        update("MASTER", "{\"name\":\"%s\"}".formatted("가".repeat(151)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        update("MASTER", "{\"address\":\"%s\"}".formatted("가".repeat(256)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        verifyNoInteractions(companyService);
    }

    @Test
    @DisplayName("hubId가 UUID 형식이 아니면 400 INVALID_INPUT")
    void invalidHubId() throws Exception {
        update("MASTER", "{\"hubId\":\"not-a-uuid\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        verifyNoInteractions(companyService);
    }

    @Test
    @DisplayName("경로의 업체 ID가 UUID 형식이 아니면 400 INVALID_INPUT")
    void invalidCompanyId() throws Exception {
        update("MASTER", "not-a-uuid", "{\"name\":\"스파르타물산\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        verifyNoInteractions(companyService);
    }
}
