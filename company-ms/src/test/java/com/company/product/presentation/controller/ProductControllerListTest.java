package com.company.product.presentation.controller;

import com.common.auth.AuthContract;
import com.common.exception.BusinessException;
import com.common.exception.CompanyErrorCode;
import com.common.page.PageData;
import com.common.page.PageQuery;
import com.common.page.PageQuery.PageOrder;
import com.company.product.application.dto.ProductResult;
import com.company.product.application.service.ProductService;
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

import java.math.BigDecimal;
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
 * 업체별 상품 목록 GET /api/v1/companies/{companyId}/products (04 API 명세서 Product): 로그인, 200.
 * 페이지 규칙은 SYS-08 페이지네이션 정렬 설계 (정렬 허용: createdAt·name·unitPrice).
 */
@WebMvcTest(ProductController.class)
class ProductControllerListTest {

    static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
    static final UUID COMPANY_ID = UUID.fromString("00000000-0000-0000-0000-0000000000d1");
    static final UUID PRODUCT_ID = UUID.fromString("00000000-0000-0000-0000-0000000000e1");

    static final PageOrder CREATED_AT_DESC = new PageOrder("createdAt", false);
    static final PageOrder ID_ASC = new PageOrder("id", true);

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    ProductService productService;

    private ResultActions list(String role, String companyId, String queryString) throws Exception {
        MockHttpServletRequestBuilder request = get("/api/v1/companies/" + companyId + "/products" + queryString);
        if (role != null) {
            request.header(AuthContract.HEADER_USER_ID, USER_ID.toString())
                    .header(AuthContract.HEADER_USER_ROLE, role);
        }
        return mockMvc.perform(request);
    }

    private ResultActions list(String queryString) throws Exception {
        return list("USER", COMPANY_ID.toString(), queryString);
    }

    private void givenEmptyPage() {
        given(productService.getProductsByCompany(any(), any())).willReturn(new PageData<>(List.of(), 0));
    }

    @Test
    @DisplayName("일반 USER도 조회할 수 있고, 상품 정보와 페이지 정보를 PageResponse 형식으로 돌려준다")
    void ok() throws Exception {
        LocalDateTime createdAt = LocalDateTime.of(2026, 10, 17, 9, 0);
        given(productService.getProductsByCompany(any(), any())).willReturn(new PageData<>(List.of(
                new ProductResult(PRODUCT_ID, COMPANY_ID, "생수 2L", "6개입", new BigDecimal("12000.50"), createdAt, createdAt)),
                11));

        list("?page=0&size=10")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(PRODUCT_ID.toString()))
                .andExpect(jsonPath("$.content[0].companyId").value(COMPANY_ID.toString()))
                .andExpect(jsonPath("$.content[0].name").value("생수 2L"))
                .andExpect(jsonPath("$.content[0].unitPrice").value(12000.50))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.totalElements").value(11))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.hasNext").value(true));
    }

    @Test
    @DisplayName("파라미터가 없으면 page=0, size=10, 정렬 createdAt DESC → id ASC로 Service를 호출하고 응답에도 그대로 담는다")
    void defaults() throws Exception {
        givenEmptyPage();

        list("")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.totalPages").value(0))
                .andExpect(jsonPath("$.hasNext").value(false))
                .andExpect(jsonPath("$.sort[0].property").value("createdAt"))
                .andExpect(jsonPath("$.sort[0].direction").value("DESC"))
                .andExpect(jsonPath("$.sort[1].property").value("id"))
                .andExpect(jsonPath("$.sort[1].direction").value("ASC"));

        then(productService).should().getProductsByCompany(COMPANY_ID,
                new PageQuery(0, 10, List.of(CREATED_AT_DESC, ID_ASC)));
    }

    @Test
    @DisplayName("name·unitPrice 정렬을 요청 순서대로 넘기고, 마지막에 id ASC를 붙인다")
    void allowedSort() throws Exception {
        givenEmptyPage();

        list("?page=2&size=30&sort=unitPrice,desc&sort=name,asc").andExpect(status().isOk());

        then(productService).should().getProductsByCompany(COMPANY_ID, new PageQuery(2, 30, List.of(
                new PageOrder("unitPrice", false), new PageOrder("name", true), ID_ASC)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "101", "500"})
    @DisplayName("size가 1~100 밖이면 400이 아니라 10으로 바꾼다")
    void sizeOutOfRange(String size) throws Exception {
        givenEmptyPage();

        list("?size=" + size)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(10));

        then(productService).should().getProductsByCompany(COMPANY_ID,
                new PageQuery(0, 10, List.of(CREATED_AT_DESC, ID_ASC)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"unit_price", "description", "company"})
    @DisplayName("허용되지 않은 필드(컬럼명 포함)로 정렬하면 400 INVALID_SORT_PROPERTY, Service를 호출하지 않는다")
    void invalidSort(String property) throws Exception {
        list("?sort=" + property + ",asc")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SORT_PROPERTY"));

        verifyNoInteractions(productService);
    }

    @Test
    @DisplayName("업체가 없으면 404 COMPANY_NOT_FOUND")
    void companyNotFound() throws Exception {
        given(productService.getProductsByCompany(any(), any()))
                .willThrow(new BusinessException(CompanyErrorCode.COMPANY_NOT_FOUND));

        list("")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COMPANY_NOT_FOUND"));
    }

    @Test
    @DisplayName("X-User-* 헤더가 없으면 401 UNAUTHENTICATED")
    void noUserHeaders() throws Exception {
        list(null, COMPANY_ID.toString(), "")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        verifyNoInteractions(productService);
    }

    @Test
    @DisplayName("경로의 업체 ID가 UUID 형식이 아니면 400 INVALID_INPUT")
    void invalidCompanyId() throws Exception {
        list("USER", "not-a-uuid", "")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        verifyNoInteractions(productService);
    }
}
