package com.company.product.presentation.controller;

import com.common.exception.BusinessException;
import com.common.exception.CompanyErrorCode;
import com.common.exception.ProductErrorCode;
import com.company.product.application.dto.ProductResult;
import com.company.product.application.service.ProductService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 서비스 간 계약 GET /internal/v1/products/{productId} (order-ms 등이 Feign으로 호출).
 * Gateway를 거치지 않으므로 X-User-* 헤더가 없다.
 */
@WebMvcTest(InternalProductController.class)
class InternalProductControllerTest {

    static final UUID COMPANY_ID = UUID.fromString("00000000-0000-0000-0000-0000000000d1");
    static final UUID PRODUCT_ID = UUID.fromString("00000000-0000-0000-0000-0000000000e1");

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @MockitoBean
    ProductService productService;

    private static ProductResult result() {
        LocalDateTime at = LocalDateTime.of(2026, 10, 17, 9, 0);
        return new ProductResult(PRODUCT_ID, COMPANY_ID, "생수 2L", "6개입", new BigDecimal("12000.50"), at, at);
    }

    @Test
    @DisplayName("X-User-* 헤더 없이 호출하면 200과 계약 필드 4개만 돌려준다 (설명·생성/수정 일시는 없다)")
    void ok() throws Exception {
        given(productService.getProduct(PRODUCT_ID)).willReturn(result());

        String body = mockMvc.perform(get("/internal/v1/products/{productId}", PRODUCT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(PRODUCT_ID.toString()))
                .andExpect(jsonPath("$.companyId").value(COMPANY_ID.toString()))
                .andExpect(jsonPath("$.name").value("생수 2L"))
                .andExpect(jsonPath("$.unitPrice").value(12000.50))
                .andReturn().getResponse().getContentAsString();

        JsonNode json = objectMapper.readTree(body);
        List<String> fields = new ArrayList<>();
        json.fieldNames().forEachRemaining(fields::add);
        assertThat(fields).containsExactlyInAnyOrder("id", "companyId", "name", "unitPrice");
    }

    @Test
    @DisplayName("없거나 삭제된 상품은 404 PRODUCT_NOT_FOUND (공통 오류 형식)")
    void productNotFound() throws Exception {
        given(productService.getProduct(PRODUCT_ID)).willThrow(new BusinessException(ProductErrorCode.PRODUCT_NOT_FOUND));

        mockMvc.perform(get("/internal/v1/products/{productId}", PRODUCT_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("소속 업체가 삭제됐으면 404 COMPANY_NOT_FOUND")
    void companyNotFound() throws Exception {
        given(productService.getProduct(PRODUCT_ID)).willThrow(new BusinessException(CompanyErrorCode.COMPANY_NOT_FOUND));

        mockMvc.perform(get("/internal/v1/products/{productId}", PRODUCT_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COMPANY_NOT_FOUND"));
    }

    @Test
    @DisplayName("UUID 형식이 아니면 400 INVALID_INPUT, 유스케이스는 호출하지 않는다")
    void invalidUuid() throws Exception {
        mockMvc.perform(get("/internal/v1/products/{productId}", "not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.errors[0].field").value("productId"));

        verifyNoInteractions(productService);
    }
}
