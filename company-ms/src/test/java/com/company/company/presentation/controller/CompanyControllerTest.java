package com.company.company.presentation.controller;

import com.common.auth.AuthContract;
import com.common.exception.BusinessException;
import com.common.exception.CompanyErrorCode;
import com.common.remote.RemoteServiceException;
import com.company.company.application.dto.CompanyResult;
import com.company.company.application.dto.CreateCompanyCommand;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 업체 등록 POST /api/v1/companies (04 API 명세서 Company): MASTER만, 201.
 */
@WebMvcTest(CompanyController.class)
class CompanyControllerTest {

    static final UUID MASTER_ID = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
    static final UUID COMPANY_ID = UUID.fromString("00000000-0000-0000-0000-0000000000d1");
    static final UUID HUB_ID = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
    static final UUID MANAGER_ID = UUID.fromString("00000000-0000-0000-0000-0000000000c1");

    static final String VALID_BODY = """
            {"name":"스파르타상사","address":"서울시 강남구 테헤란로 1","hubId":"%s","managerUserId":"%s"}
            """.formatted(HUB_ID, MANAGER_ID);

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    CompanyService companyService;

    private ResultActions create(String role, String body) throws Exception {
        MockHttpServletRequestBuilder request = post("/api/v1/companies")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
        if (role != null) {
            request.header(AuthContract.HEADER_USER_ID, MASTER_ID.toString())
                    .header(AuthContract.HEADER_USER_ROLE, role);
        }
        return mockMvc.perform(request);
    }

    @Test
    @DisplayName("MASTER가 등록하면 201과 업체 정보를 돌려주고, 요청값을 그대로 Command로 넘긴다")
    void created() throws Exception {
        given(companyService.createCompany(any()))
                .willReturn(new CompanyResult(COMPANY_ID, "스파르타상사", "서울시 강남구 테헤란로 1", HUB_ID, MANAGER_ID, null, null));

        create("MASTER", VALID_BODY)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(COMPANY_ID.toString()))
                .andExpect(jsonPath("$.name").value("스파르타상사"))
                .andExpect(jsonPath("$.address").value("서울시 강남구 테헤란로 1"))
                .andExpect(jsonPath("$.hubId").value(HUB_ID.toString()))
                .andExpect(jsonPath("$.managerUserId").value(MANAGER_ID.toString()));

        then(companyService).should().createCompany(
                new CreateCompanyCommand("스파르타상사", "서울시 강남구 테헤란로 1", HUB_ID, MANAGER_ID));
    }

    @Test
    @DisplayName("MASTER가 아니면 403 ACCESS_DENIED, 유스케이스는 호출하지 않는다")
    void notMaster() throws Exception {
        create("USER", VALID_BODY)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        verifyNoInteractions(companyService);
    }

    @Test
    @DisplayName("X-User-* 헤더가 없으면 401 UNAUTHENTICATED")
    void noUserHeaders() throws Exception {
        create(null, VALID_BODY)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        verifyNoInteractions(companyService);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"name\":\" \",\"address\":\"서울\",\"hubId\":\"00000000-0000-0000-0000-0000000000b1\",\"managerUserId\":\"00000000-0000-0000-0000-0000000000c1\"}",
            "{\"name\":\"스파르타상사\",\"address\":\"\",\"hubId\":\"00000000-0000-0000-0000-0000000000b1\",\"managerUserId\":\"00000000-0000-0000-0000-0000000000c1\"}",
            "{\"name\":\"스파르타상사\",\"address\":\"서울\",\"managerUserId\":\"00000000-0000-0000-0000-0000000000c1\"}",
            "{\"name\":\"스파르타상사\",\"address\":\"서울\",\"hubId\":\"00000000-0000-0000-0000-0000000000b1\"}"
    })
    @DisplayName("필수값(name·address 공백, hubId·managerUserId 누락)이 없으면 400 INVALID_INPUT")
    void invalidInput(String body) throws Exception {
        create("MASTER", body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.errors[0].field").exists());

        verifyNoInteractions(companyService);
    }

    @Test
    @DisplayName("업체명 150자, 주소 255자까지는 등록할 수 있다")
    void maxLength() throws Exception {
        given(companyService.createCompany(any()))
                .willReturn(new CompanyResult(COMPANY_ID, "가".repeat(150), "가".repeat(255), HUB_ID, MANAGER_ID, null, null));

        create("MASTER", body("가".repeat(150), "가".repeat(255)))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("업체명이 150자를 넘으면 400 INVALID_INPUT (field: name)")
    void nameTooLong() throws Exception {
        create("MASTER", body("가".repeat(151), "서울"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.errors[0].field").value("name"));

        verifyNoInteractions(companyService);
    }

    @Test
    @DisplayName("주소가 255자를 넘으면 400 INVALID_INPUT (field: address)")
    void addressTooLong() throws Exception {
        create("MASTER", body("스파르타상사", "가".repeat(256)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.errors[0].field").value("address"));

        verifyNoInteractions(companyService);
    }

    private static String body(String name, String address) {
        return """
                {"name":"%s","address":"%s","hubId":"%s","managerUserId":"%s"}
                """.formatted(name, address, HUB_ID, MANAGER_ID);
    }

    @Test
    @DisplayName("UUID 형식이 아니면 400 INVALID_INPUT")
    void invalidUuid() throws Exception {
        create("MASTER", """
                {"name":"스파르타상사","address":"서울","hubId":"not-a-uuid","managerUserId":"%s"}
                """.formatted(MANAGER_ID))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        verifyNoInteractions(companyService);
    }

    @Test
    @DisplayName("허브가 없으면 400 COMPANY_HUB_NOT_FOUND (공통 오류 형식)")
    void hubNotFound() throws Exception {
        given(companyService.createCompany(any()))
                .willThrow(new BusinessException(CompanyErrorCode.COMPANY_HUB_NOT_FOUND));

        create("MASTER", VALID_BODY)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMPANY_HUB_NOT_FOUND"))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("관리자가 없으면 400 COMPANY_MANAGER_NOT_FOUND")
    void managerNotFound() throws Exception {
        given(companyService.createCompany(any()))
                .willThrow(new BusinessException(CompanyErrorCode.COMPANY_MANAGER_NOT_FOUND));

        create("MASTER", VALID_BODY)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMPANY_MANAGER_NOT_FOUND"));
    }

    @Test
    @DisplayName("원격 서비스 장애는 503 SERVICE_UNAVAILABLE")
    void remoteUnavailable() throws Exception {
        given(companyService.createCompany(any())).willThrow(new RemoteServiceException("hub-ms", 500));

        create("MASTER", VALID_BODY)
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("SERVICE_UNAVAILABLE"));
    }
}
