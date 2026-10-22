package com.company.product.presentation.controller;

import com.company.company.domain.entity.Company;
import com.company.company.domain.repository.CompanyRepository;
import com.company.product.domain.entity.Product;
import com.company.product.domain.repository.ProductRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 저장 → DB 값 변경(가격 수정·상품 삭제·업체 삭제) → 내부 API가 DB의 현재 상태를 그대로 돌려주는지 실제 빈으로 확인한다 (H2).
 * 사용자 헤더 확인 인터셉터 등 실제 MVC 설정을 거쳐도 헤더 없이 호출되는지도 함께 본다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class InternalProductIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    CompanyRepository companyRepository;

    @Autowired
    ProductRepository productRepository;

    private Product saveProduct() {
        Company company = companyRepository.save(
                Company.create("스파르타상사", "서울시 강남구 테헤란로 1", UUID.randomUUID(), UUID.randomUUID()));
        return productRepository.save(Product.create(company, "생수 2L", "6개입", new BigDecimal("12000.50")));
    }

    @Test
    @DisplayName("헤더 없이 조회하면 DB 값이 그대로 나오고, 가격 변경이 바로 반영된다")
    void reflectsDatabase() throws Exception {
        Product product = saveProduct();

        mockMvc.perform(get("/internal/v1/products/{productId}", product.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(product.getId().toString()))
                .andExpect(jsonPath("$.companyId").value(product.getCompany().getId().toString()))
                .andExpect(jsonPath("$.name").value("생수 2L"))
                .andExpect(jsonPath("$.unitPrice").value(12000.50));

        jdbc.update("update company.p_products set unit_price = 9000 where id = ?", product.getId());

        mockMvc.perform(get("/internal/v1/products/{productId}", product.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unitPrice").value(9000));
    }

    @Test
    @DisplayName("내부 API 조회는 조회수를 올리지 않는다 (주문 검증 호출이 인기 지표에 섞이지 않도록)")
    void doesNotIncreaseViewCount() throws Exception {
        Product product = saveProduct();

        mockMvc.perform(get("/internal/v1/products/{productId}", product.getId()))
                .andExpect(status().isOk());

        Long viewCount = jdbc.queryForObject(
                "select view_count from company.p_products where id = ?", Long.class, product.getId());
        assertThat(viewCount).isZero();
    }

    @Test
    @DisplayName("상품이 삭제(deleted_at 기록)되면 404 PRODUCT_NOT_FOUND")
    void deletedProduct() throws Exception {
        Product product = saveProduct();

        jdbc.update("update company.p_products set deleted_at = now() where id = ?", product.getId());

        mockMvc.perform(get("/internal/v1/products/{productId}", product.getId()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
    }

    @Test
    @DisplayName("소속 업체가 삭제되면 404 COMPANY_NOT_FOUND")
    void deletedCompany() throws Exception {
        Product product = saveProduct();

        jdbc.update("update company.p_companies set deleted_at = now() where id = ?", product.getCompany().getId());

        mockMvc.perform(get("/internal/v1/products/{productId}", product.getId()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COMPANY_NOT_FOUND"));
    }

    @Test
    @DisplayName("형식이 틀린 사용자 헤더는 내부 API에서도 401 (Gateway를 거치지 않은 위조 요청으로 본다)")
    void invalidUserHeader() throws Exception {
        Product product = saveProduct();

        mockMvc.perform(get("/internal/v1/products/{productId}", product.getId())
                        .header("X-User-Id", "not-a-uuid"))
                .andExpect(status().isUnauthorized());
    }
}
