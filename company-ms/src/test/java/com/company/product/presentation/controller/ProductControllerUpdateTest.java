package com.company.product.presentation.controller;

import com.common.auth.AuthContract;
import com.common.exception.BusinessException;
import com.common.exception.CommonErrorCode;
import com.common.exception.ProductErrorCode;
import com.company.product.application.dto.ProductResult;
import com.company.product.application.dto.UpdateProductCommand;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 상품 수정 PATCH /api/v1/products/{productId} (04 API 명세서 Product): MASTER·업체관리자, 200.
 */
@WebMvcTest(ProductController.class)
class ProductControllerUpdateTest {

    static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
    static final UUID COMPANY_ID = UUID.fromString("00000000-0000-0000-0000-0000000000d1");
    static final UUID PRODUCT_ID = UUID.fromString("00000000-0000-0000-0000-0000000000e1");

    static final ProductResult UPDATED =
            new ProductResult(PRODUCT_ID, COMPANY_ID, "생수 500ml", "6개입", new BigDecimal("9000.00"), 0L, null, null);

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    ProductService productService;

    private ResultActions update(String role, String productId, String body) throws Exception {
        MockHttpServletRequestBuilder request = patch("/api/v1/products/{productId}", productId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
        if (role != null) {
            request.header(AuthContract.HEADER_USER_ID, USER_ID.toString())
                    .header(AuthContract.HEADER_USER_ROLE, role);
        }
        return mockMvc.perform(request);
    }

    private ResultActions update(String role, String body) throws Exception {
        return update(role, PRODUCT_ID.toString(), body);
    }

    @Test
    @DisplayName("수정하면 200과 수정된 상품을 돌려주고, 빠진 필드는 null로 Command에 넘긴다")
    void updated() throws Exception {
        given(productService.updateProduct(any())).willReturn(UPDATED);

        update("USER", "{\"name\":\"생수 500ml\",\"unitPrice\":9000}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(PRODUCT_ID.toString()))
                .andExpect(jsonPath("$.name").value("생수 500ml"))
                .andExpect(jsonPath("$.unitPrice").value(9000.00));

        then(productService).should().updateProduct(new UpdateProductCommand(
                PRODUCT_ID, "생수 500ml", null, new BigDecimal("9000"), USER_ID, false));
    }

    @Test
    @DisplayName("MASTER면 actorIsMaster=true로 넘긴다")
    void master() throws Exception {
        given(productService.updateProduct(any())).willReturn(UPDATED);

        update("MASTER", "{\"description\":\"20개입\"}").andExpect(status().isOk());

        then(productService).should().updateProduct(new UpdateProductCommand(
                PRODUCT_ID, null, "20개입", null, USER_ID, true));
    }

    @Test
    @DisplayName("업체 관리자가 아니면 403 ACCESS_DENIED")
    void accessDenied() throws Exception {
        given(productService.updateProduct(any())).willThrow(new BusinessException(CommonErrorCode.ACCESS_DENIED));

        update("USER", "{\"name\":\"생수 500ml\"}")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    @DisplayName("상품이 없으면 404 PRODUCT_NOT_FOUND")
    void productNotFound() throws Exception {
        given(productService.updateProduct(any())).willThrow(new BusinessException(ProductErrorCode.PRODUCT_NOT_FOUND));

        update("MASTER", "{\"name\":\"생수 500ml\"}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
    }

    @Test
    @DisplayName("X-User-* 헤더가 없으면 401 UNAUTHENTICATED")
    void noUserHeaders() throws Exception {
        update(null, "{\"name\":\"생수 500ml\"}")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        verifyNoInteractions(productService);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{}",
            "{\"name\":null,\"description\":null,\"unitPrice\":null}",
            "{\"name\":\" \"}",
            "{\"name\":\"\"}",
            "{\"unitPrice\":-1}",
            "{\"unitPrice\":1000.123}",
            "{\"unitPrice\":12345678901234}"
    })
    @DisplayName("빈 요청(모든 필드 null), name 공백, unitPrice 음수·소수 3자리·정수 14자리면 400 INVALID_INPUT")
    void invalidInput(String body) throws Exception {
        update("MASTER", body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.errors[0].field").exists());

        verifyNoInteractions(productService);
    }

    @Test
    @DisplayName("상품명이 150자를 넘으면 400 INVALID_INPUT")
    void nameTooLong() throws Exception {
        update("MASTER", "{\"name\":\"%s\"}".formatted("가".repeat(151)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        verifyNoInteractions(productService);
    }

    @Test
    @DisplayName("경로의 상품 ID가 UUID 형식이 아니면 400 INVALID_INPUT")
    void invalidProductId() throws Exception {
        update("MASTER", "not-a-uuid", "{\"name\":\"생수 500ml\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        verifyNoInteractions(productService);
    }
}
