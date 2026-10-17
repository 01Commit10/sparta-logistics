package com.company.product.presentation.controller;

import com.common.auth.AuthContract;
import com.common.exception.BusinessException;
import com.common.exception.CommonErrorCode;
import com.common.exception.ProductErrorCode;
import com.company.product.application.dto.DeleteProductCommand;
import com.company.product.application.service.ProductService;
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
 * 상품 삭제 DELETE /api/v1/products/{productId} (04 API 명세서 Product): MASTER·업체관리자, 204.
 */
@WebMvcTest(ProductController.class)
class ProductControllerDeleteTest {

    static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
    static final UUID PRODUCT_ID = UUID.fromString("00000000-0000-0000-0000-0000000000e1");

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    ProductService productService;

    private ResultActions remove(String role, String productId) throws Exception {
        MockHttpServletRequestBuilder request = delete("/api/v1/products/{productId}", productId);
        if (role != null) {
            request.header(AuthContract.HEADER_USER_ID, USER_ID.toString())
                    .header(AuthContract.HEADER_USER_ROLE, role);
        }
        return mockMvc.perform(request);
    }

    private ResultActions remove(String role) throws Exception {
        return remove(role, PRODUCT_ID.toString());
    }

    @Test
    @DisplayName("삭제하면 204와 빈 본문을 돌려주고, 요청자를 Command에 넘긴다")
    void deleted() throws Exception {
        remove("USER")
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        then(productService).should().deleteProduct(new DeleteProductCommand(PRODUCT_ID, USER_ID, false));
    }

    @Test
    @DisplayName("MASTER면 actorIsMaster=true로 넘긴다")
    void master() throws Exception {
        remove("MASTER").andExpect(status().isNoContent());

        then(productService).should().deleteProduct(new DeleteProductCommand(PRODUCT_ID, USER_ID, true));
    }

    @Test
    @DisplayName("업체 관리자가 아니면 403 ACCESS_DENIED")
    void accessDenied() throws Exception {
        willThrow(new BusinessException(CommonErrorCode.ACCESS_DENIED)).given(productService).deleteProduct(any());

        remove("USER")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    @DisplayName("상품이 없거나 이미 삭제됐으면 404 PRODUCT_NOT_FOUND")
    void productNotFound() throws Exception {
        willThrow(new BusinessException(ProductErrorCode.PRODUCT_NOT_FOUND)).given(productService).deleteProduct(any());

        remove("MASTER")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
    }

    @Test
    @DisplayName("X-User-* 헤더가 없으면 401 UNAUTHENTICATED")
    void noUserHeaders() throws Exception {
        remove(null)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        verifyNoInteractions(productService);
    }

    @Test
    @DisplayName("경로의 상품 ID가 UUID 형식이 아니면 400 INVALID_INPUT")
    void invalidProductId() throws Exception {
        remove("MASTER", "not-a-uuid")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        verifyNoInteractions(productService);
    }
}
