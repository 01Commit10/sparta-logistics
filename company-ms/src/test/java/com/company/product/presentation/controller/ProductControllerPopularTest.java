package com.company.product.presentation.controller;

import com.common.auth.AuthContract;
import com.company.product.application.dto.ProductResult;
import com.company.product.application.service.ProductService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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
 * 인기 상품 GET /api/v1/products/popular: 로그인, 200, 조회수 상위 10개 배열. companyId는 선택.
 */
@WebMvcTest(ProductController.class)
class ProductControllerPopularTest {

    static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
    static final UUID COMPANY_ID = UUID.fromString("00000000-0000-0000-0000-0000000000d1");
    static final UUID PRODUCT_ID = UUID.fromString("00000000-0000-0000-0000-0000000000e1");

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    ProductService productService;

    private ResultActions popular(String role, String queryString) throws Exception {
        MockHttpServletRequestBuilder request = get("/api/v1/products/popular" + queryString);
        if (role != null) {
            request.header(AuthContract.HEADER_USER_ID, USER_ID.toString())
                    .header(AuthContract.HEADER_USER_ROLE, role);
        }
        return mockMvc.perform(request);
    }

    @Test
    @DisplayName("일반 USER도 조회할 수 있고, 페이지 정보 없이 상품 배열을 돌려준다")
    void ok() throws Exception {
        LocalDateTime createdAt = LocalDateTime.of(2026, 10, 17, 9, 0);
        given(productService.getPopularProducts(any())).willReturn(List.of(
                new ProductResult(PRODUCT_ID, COMPANY_ID, "생수 2L", null, new BigDecimal("1000"), 99L, createdAt, createdAt)));

        popular("USER", "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(PRODUCT_ID.toString()))
                .andExpect(jsonPath("$[0].viewCount").value(99))
                .andExpect(jsonPath("$.length()").value(1));

        then(productService).should().getPopularProducts(null);
    }

    @Test
    @DisplayName("companyId를 Service에 그대로 넘긴다 (/products/{productId}가 아니라 이 API로 매핑된다)")
    void byCompany() throws Exception {
        given(productService.getPopularProducts(any())).willReturn(List.of());

        popular("USER", "?companyId=" + COMPANY_ID).andExpect(status().isOk());

        then(productService).should().getPopularProducts(COMPANY_ID);
    }

    @Test
    @DisplayName("companyId가 UUID 형식이 아니면 400 INVALID_INPUT, Service를 호출하지 않는다")
    void invalidCompanyId() throws Exception {
        popular("USER", "?companyId=not-a-uuid")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        verifyNoInteractions(productService);
    }

    @Test
    @DisplayName("X-User-* 헤더가 없으면 401 UNAUTHENTICATED")
    void noUserHeaders() throws Exception {
        popular(null, "")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        verifyNoInteractions(productService);
    }
}
