package com.company.company.presentation.controller;

import com.common.auth.AuthContract;
import com.common.exception.BusinessException;
import com.common.exception.CompanyErrorCode;
import com.company.company.application.dto.CompanyResult;
import com.company.company.application.service.CompanyService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 업체 상세 조회 GET /api/v1/companies/{companyId} (04 API 명세서 Company): 로그인, 200.
 */
@WebMvcTest(CompanyController.class)
class CompanyControllerGetTest {

    static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    static final UUID COMPANY_ID = UUID.fromString("00000000-0000-0000-0000-0000000000d1");
    static final UUID HUB_ID = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
    static final UUID MANAGER_ID = UUID.fromString("00000000-0000-0000-0000-0000000000c1");

    static final CompanyResult FOUND =
            new CompanyResult(COMPANY_ID, "스파르타상사", "서울시 강남구 테헤란로 1", HUB_ID, MANAGER_ID, null, null);

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    CompanyService companyService;

    private ResultActions find(String role, String companyId) throws Exception {
        MockHttpServletRequestBuilder request = get("/api/v1/companies/{companyId}", companyId);
        if (role != null) {
            request.header(AuthContract.HEADER_USER_ID, USER_ID.toString())
                    .header(AuthContract.HEADER_USER_ROLE, role);
        }
        return mockMvc.perform(request);
    }

    @Test
    @DisplayName("일반 USER도 조회할 수 있고, 200과 업체 정보를 돌려준다")
    void found() throws Exception {
        given(companyService.getCompany(COMPANY_ID)).willReturn(FOUND);

        find("USER", COMPANY_ID.toString())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(COMPANY_ID.toString()))
                .andExpect(jsonPath("$.name").value("스파르타상사"))
                .andExpect(jsonPath("$.address").value("서울시 강남구 테헤란로 1"))
                .andExpect(jsonPath("$.hubId").value(HUB_ID.toString()))
                .andExpect(jsonPath("$.managerUserId").value(MANAGER_ID.toString()));
    }

    @Test
    @DisplayName("업체가 없으면 404 COMPANY_NOT_FOUND")
    void notFound() throws Exception {
        given(companyService.getCompany(any())).willThrow(new BusinessException(CompanyErrorCode.COMPANY_NOT_FOUND));

        find("USER", COMPANY_ID.toString())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COMPANY_NOT_FOUND"));
    }

    @Test
    @DisplayName("X-User-* 헤더가 없으면 401 UNAUTHENTICATED")
    void noUserHeaders() throws Exception {
        find(null, COMPANY_ID.toString())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        verifyNoInteractions(companyService);
    }

    @Test
    @DisplayName("경로의 업체 ID가 UUID 형식이 아니면 400 INVALID_INPUT")
    void invalidCompanyId() throws Exception {
        find("USER", "not-a-uuid")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        verifyNoInteractions(companyService);
    }
}
