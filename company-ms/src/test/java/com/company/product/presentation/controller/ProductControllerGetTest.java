package com.company.product.presentation.controller;

import com.common.auth.AuthContract;
import com.common.exception.BusinessException;
import com.common.exception.ProductErrorCode;
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
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 상품 상세 조회 GET /api/v1/products/{productId} (04 API 명세서 Product): 로그인, 200.
 */
@WebMvcTest(ProductController.class)
class ProductControllerGetTest {

    static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
    static final UUID COMPANY_ID = UUID.fromString("00000000-0000-0000-0000-0000000000d1");
    static final UUID PRODUCT_ID = UUID.fromString("00000000-0000-0000-0000-0000000000e1");

    static final ProductResult FOUND =
            new ProductResult(PRODUCT_ID, COMPANY_ID, "생수 2L", "6개입", new BigDecimal("12000.00"), 5L, null, null);

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    ProductService productService;

    private ResultActions find(String role, String productId) throws Exception {
        MockHttpServletRequestBuilder request = get("/api/v1/products/{productId}", productId);
        if (role != null) {
            request.header(AuthContract.HEADER_USER_ID, USER_ID.toString())
                    .header(AuthContract.HEADER_USER_ROLE, role);
        }
        return mockMvc.perform(request);
    }

    @Test
    @DisplayName("일반 USER도 조회할 수 있고, 200과 상품 정보(조회수 포함)를 돌려준다. 조회수를 올리는 viewProduct를 쓴다")
    void found() throws Exception {
        given(productService.viewProduct(PRODUCT_ID)).willReturn(FOUND);

        find("USER", PRODUCT_ID.toString())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(PRODUCT_ID.toString()))
                .andExpect(jsonPath("$.companyId").value(COMPANY_ID.toString()))
                .andExpect(jsonPath("$.name").value("생수 2L"))
                .andExpect(jsonPath("$.unitPrice").value(12000.00))
                .andExpect(jsonPath("$.viewCount").value(5));
    }

    @Test
    @DisplayName("상품이 없으면 404 PRODUCT_NOT_FOUND")
    void productNotFound() throws Exception {
        given(productService.viewProduct(any())).willThrow(new BusinessException(ProductErrorCode.PRODUCT_NOT_FOUND));

        find("USER", PRODUCT_ID.toString())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
    }

    @Test
    @DisplayName("X-User-* 헤더가 없으면 401 UNAUTHENTICATED")
    void noUserHeaders() throws Exception {
        find(null, PRODUCT_ID.toString())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        verifyNoInteractions(productService);
    }

    @Test
    @DisplayName("경로의 상품 ID가 UUID 형식이 아니면 400 INVALID_INPUT")
    void invalidProductId() throws Exception {
        find("USER", "not-a-uuid")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        verifyNoInteractions(productService);
    }
}
