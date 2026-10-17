package com.company.company.presentation.controller;

import com.common.auth.AuthContract;
import com.common.page.PageData;
import com.common.page.PageQuery;
import com.common.page.PageQuery.PageOrder;
import com.company.company.application.dto.CompanyResult;
import com.company.company.application.dto.CompanySearchQuery;
import com.company.company.application.service.CompanyService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 업체 목록 GET /api/v1/companies (04 API 명세서 Company): 로그인, 200. hubId·name 검색은 선택.
 * 페이지 규칙은 SYS-08 페이지네이션 정렬 설계 (정렬 허용: createdAt·name).
 */
@WebMvcTest(CompanyController.class)
class CompanyControllerSearchTest {

    static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    static final UUID COMPANY_ID = UUID.fromString("00000000-0000-0000-0000-0000000000d1");
    static final UUID HUB_ID = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
    static final UUID MANAGER_ID = UUID.fromString("00000000-0000-0000-0000-0000000000c1");

    static final PageOrder CREATED_AT_DESC = new PageOrder("createdAt", false);
    static final PageOrder ID_ASC = new PageOrder("id", true);
    static final PageQuery DEFAULT_PAGE = new PageQuery(0, 10, List.of(CREATED_AT_DESC, ID_ASC));

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    CompanyService companyService;

    private ResultActions search(String role, String queryString) throws Exception {
        MockHttpServletRequestBuilder request = get("/api/v1/companies" + queryString);
        if (role != null) {
            request.header(AuthContract.HEADER_USER_ID, USER_ID.toString())
                    .header(AuthContract.HEADER_USER_ROLE, role);
        }
        return mockMvc.perform(request);
    }

    private ResultActions search(String queryString) throws Exception {
        return search("USER", queryString);
    }

    private void givenEmptyPage() {
        given(companyService.searchCompanies(any(), any())).willReturn(new PageData<>(List.of(), 0));
    }

    @Test
    @DisplayName("일반 USER도 조회할 수 있고, 업체 정보와 페이지 정보를 PageResponse 형식으로 돌려준다")
    void ok() throws Exception {
        LocalDateTime createdAt = LocalDateTime.of(2026, 10, 17, 9, 0);
        given(companyService.searchCompanies(any(), any())).willReturn(new PageData<>(List.of(
                new CompanyResult(COMPANY_ID, "스파르타상사", "서울시 강남구 테헤란로 1", HUB_ID, MANAGER_ID, createdAt, createdAt)),
                11));

        search("?page=0&size=10")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(COMPANY_ID.toString()))
                .andExpect(jsonPath("$.content[0].name").value("스파르타상사"))
                .andExpect(jsonPath("$.content[0].hubId").value(HUB_ID.toString()))
                .andExpect(jsonPath("$.content[0].managerUserId").value(MANAGER_ID.toString()))
                .andExpect(jsonPath("$.totalElements").value(11))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.hasNext").value(true));
    }

    @Test
    @DisplayName("파라미터가 없으면 조건 없이, page=0, size=10, 정렬 createdAt DESC → id ASC로 Service를 호출한다")
    void defaults() throws Exception {
        givenEmptyPage();

        search("")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sort[0].property").value("createdAt"))
                .andExpect(jsonPath("$.sort[0].direction").value("DESC"));

        then(companyService).should().searchCompanies(new CompanySearchQuery(null, null), DEFAULT_PAGE);
    }

    @Test
    @DisplayName("hubId·name을 검색 조건으로, 정렬은 요청 순서대로 넘기고 마지막에 id ASC를 붙인다")
    void conditionsAndSort() throws Exception {
        givenEmptyPage();

        search("?hubId=" + HUB_ID + "&name=스파르타&page=1&size=20&sort=name,asc&sort=createdAt,asc")
                .andExpect(status().isOk());

        then(companyService).should().searchCompanies(new CompanySearchQuery(HUB_ID, "스파르타"),
                new PageQuery(1, 20, List.of(new PageOrder("name", true), new PageOrder("createdAt", true), ID_ASC)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "101"})
    @DisplayName("size가 1~100 밖이면 400이 아니라 10으로 바꾼다")
    void sizeOutOfRange(String size) throws Exception {
        givenEmptyPage();

        search("?size=" + size)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(10));

        then(companyService).should().searchCompanies(new CompanySearchQuery(null, null), DEFAULT_PAGE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"hub_id", "address", "managerUserId"})
    @DisplayName("허용되지 않은 필드(컬럼명 포함)로 정렬하면 400 INVALID_SORT_PROPERTY, Service를 호출하지 않는다")
    void invalidSort(String property) throws Exception {
        search("?sort=" + property + ",asc")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SORT_PROPERTY"));

        verifyNoInteractions(companyService);
    }

    @Test
    @DisplayName("hubId가 UUID 형식이 아니면 400 INVALID_INPUT, Service를 호출하지 않는다")
    void invalidHubId() throws Exception {
        search("?hubId=not-a-uuid")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        verifyNoInteractions(companyService);
    }

    @Test
    @DisplayName("name이 150자를 넘으면 400 INVALID_INPUT, Service를 호출하지 않는다")
    void nameTooLong() throws Exception {
        search("?name=" + "가".repeat(151))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        verifyNoInteractions(companyService);
    }

    @Test
    @DisplayName("X-User-* 헤더가 없으면 401 UNAUTHENTICATED")
    void noUserHeaders() throws Exception {
        search(null, "")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        verifyNoInteractions(companyService);
    }
}
