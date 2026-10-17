package com.company.product.presentation.controller;

import com.common.auth.AuthContract;
import com.common.exception.BusinessException;
import com.common.exception.CommonErrorCode;
import com.common.exception.CompanyErrorCode;
import com.company.product.application.dto.CreateProductCommand;
import com.company.product.application.dto.ProductResult;
import com.company.product.application.service.ProductService;
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

import java.math.BigDecimal;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 상품 등록 POST /api/v1/companies/{companyId}/products (04 API 명세서 Product): MASTER·업체관리자, 201.
 */
@WebMvcTest(ProductController.class)
class ProductControllerTest {

    static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
    static final UUID COMPANY_ID = UUID.fromString("00000000-0000-0000-0000-0000000000d1");
    static final UUID PRODUCT_ID = UUID.fromString("00000000-0000-0000-0000-0000000000e1");

    static final String VALID_BODY = """
            {"name":"생수 2L","description":"6개입","unitPrice":12000.50}
            """;

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    ProductService productService;

    private ResultActions create(String role, String companyId, String body) throws Exception {
        MockHttpServletRequestBuilder request = post("/api/v1/companies/{companyId}/products", companyId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
        if (role != null) {
            request.header(AuthContract.HEADER_USER_ID, USER_ID.toString())
                    .header(AuthContract.HEADER_USER_ROLE, role);
        }
        return mockMvc.perform(request);
    }

    private ResultActions create(String role, String body) throws Exception {
        return create(role, COMPANY_ID.toString(), body);
    }

    @Test
    @DisplayName("등록하면 201과 상품 정보를 돌려주고, 경로의 업체 ID·요청자 정보를 Command로 넘긴다")
    void created() throws Exception {
        given(productService.createProduct(any())).willReturn(
                new ProductResult(PRODUCT_ID, COMPANY_ID, "생수 2L", "6개입", new BigDecimal("12000.50")));

        create("USER", VALID_BODY)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(PRODUCT_ID.toString()))
                .andExpect(jsonPath("$.companyId").value(COMPANY_ID.toString()))
                .andExpect(jsonPath("$.name").value("생수 2L"))
                .andExpect(jsonPath("$.description").value("6개입"))
                .andExpect(jsonPath("$.unitPrice").value(12000.50));

        then(productService).should().createProduct(new CreateProductCommand(
                COMPANY_ID, "생수 2L", "6개입", new BigDecimal("12000.50"), USER_ID, false));
    }

    @Test
    @DisplayName("MASTER면 actorIsMaster=true로 넘긴다")
    void master() throws Exception {
        given(productService.createProduct(any())).willReturn(
                new ProductResult(PRODUCT_ID, COMPANY_ID, "생수 2L", "6개입", new BigDecimal("12000.50")));

        create("MASTER", VALID_BODY).andExpect(status().isCreated());

        then(productService).should().createProduct(new CreateProductCommand(
                COMPANY_ID, "생수 2L", "6개입", new BigDecimal("12000.50"), USER_ID, true));
    }

    @Test
    @DisplayName("업체 관리자가 아니면 403 ACCESS_DENIED")
    void accessDenied() throws Exception {
        given(productService.createProduct(any())).willThrow(new BusinessException(CommonErrorCode.ACCESS_DENIED));

        create("USER", VALID_BODY)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    @DisplayName("업체가 없으면 404 COMPANY_NOT_FOUND")
    void companyNotFound() throws Exception {
        given(productService.createProduct(any())).willThrow(new BusinessException(CompanyErrorCode.COMPANY_NOT_FOUND));

        create("MASTER", VALID_BODY)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COMPANY_NOT_FOUND"));
    }

    @Test
    @DisplayName("X-User-* 헤더가 없으면 401 UNAUTHENTICATED")
    void noUserHeaders() throws Exception {
        create(null, VALID_BODY)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        verifyNoInteractions(productService);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"name\":\" \",\"unitPrice\":1000}",
            "{\"unitPrice\":1000}",
            "{\"name\":\"생수 2L\"}",
            "{\"name\":\"생수 2L\",\"unitPrice\":-1}",
            "{\"name\":\"생수 2L\",\"unitPrice\":1000.123}",
            "{\"name\":\"생수 2L\",\"unitPrice\":12345678901234}"
    })
    @DisplayName("name 공백·누락, unitPrice 누락·음수·소수 3자리·정수 14자리면 400 INVALID_INPUT")
    void invalidInput(String body) throws Exception {
        create("MASTER", body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.errors[0].field").exists());

        verifyNoInteractions(productService);
    }

    @Test
    @DisplayName("상품명이 150자를 넘으면 400 INVALID_INPUT")
    void nameTooLong() throws Exception {
        create("MASTER", "{\"name\":\"%s\",\"unitPrice\":1000}".formatted("가".repeat(151)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        verifyNoInteractions(productService);
    }

    @Test
    @DisplayName("경로의 업체 ID가 UUID 형식이 아니면 400 INVALID_INPUT")
    void invalidCompanyId() throws Exception {
        create("MASTER", "not-a-uuid", VALID_BODY)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        verifyNoInteractions(productService);
    }
}
