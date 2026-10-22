package com.company.company.presentation.controller;

import com.common.auth.AuthContract;
import com.common.exception.BusinessException;
import com.common.exception.CompanyErrorCode;
import com.company.company.application.dto.DeleteCompanyCommand;
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
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 업체 삭제 DELETE /api/v1/companies/{companyId} (04 API 명세서 Company): MASTER만, 204.
 */
@WebMvcTest(CompanyController.class)
class CompanyControllerDeleteTest {

    static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
    static final UUID COMPANY_ID = UUID.fromString("00000000-0000-0000-0000-0000000000d1");

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    CompanyService companyService;

    private ResultActions remove(String role, String companyId) throws Exception {
        MockHttpServletRequestBuilder request = delete("/api/v1/companies/{companyId}", companyId);
        if (role != null) {
            request.header(AuthContract.HEADER_USER_ID, USER_ID.toString())
                    .header(AuthContract.HEADER_USER_ROLE, role);
        }
        return mockMvc.perform(request);
    }

    private ResultActions remove(String role) throws Exception {
        return remove(role, COMPANY_ID.toString());
    }

    @Test
    @DisplayName("MASTER가 삭제하면 204와 빈 본문을 돌려주고, 요청자를 Command에 넘긴다")
    void deleted() throws Exception {
        remove("MASTER")
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        then(companyService).should().deleteCompany(new DeleteCompanyCommand(COMPANY_ID, USER_ID));
    }

    @Test
    @DisplayName("MASTER가 아니면(업체 관리자 포함) 403 ACCESS_DENIED, 유스케이스는 호출하지 않는다")
    void notMaster() throws Exception {
        remove("USER")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        verifyNoInteractions(companyService);
    }

    @Test
    @DisplayName("업체가 없거나 이미 삭제됐으면 404 COMPANY_NOT_FOUND")
    void companyNotFound() throws Exception {
        willThrow(new BusinessException(CompanyErrorCode.COMPANY_NOT_FOUND)).given(companyService).deleteCompany(any());

        remove("MASTER")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COMPANY_NOT_FOUND"));
    }

    @Test
    @DisplayName("X-User-* 헤더가 없으면 401 UNAUTHENTICATED")
    void noUserHeaders() throws Exception {
        remove(null)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        verifyNoInteractions(companyService);
    }

    @Test
    @DisplayName("경로의 업체 ID가 UUID 형식이 아니면 400 INVALID_INPUT")
    void invalidCompanyId() throws Exception {
        remove("MASTER", "not-a-uuid")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        verifyNoInteractions(companyService);
    }
}
