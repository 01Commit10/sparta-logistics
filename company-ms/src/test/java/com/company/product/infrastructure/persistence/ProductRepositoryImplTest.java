package com.company.product.infrastructure.persistence;

import com.company.company.domain.entity.Company;
import com.company.product.domain.entity.Product;
import com.company.product.domain.repository.ProductRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** 기본 테스트 DB(H2, PostgreSQL 모드)로 확인한다. */
@DataJpaTest
@Import(ProductRepositoryImpl.class)
class ProductRepositoryImplTest {

    @Autowired
    ProductRepository productRepository;

    @Autowired
    EntityManager em;

    @Test
    @DisplayName("저장하면 ID가 생성되고, ID로 다시 조회된다 (업체 연관·생성 일시 포함)")
    void saveAndFind() {
        Company company = Company.create("스파르타상사", "서울시 강남구 테헤란로 1", UUID.randomUUID(), UUID.randomUUID());
        em.persist(company);

        Product saved = productRepository.save(Product.create(company, "생수 2L", "6개입", new BigDecimal("12000.50")));
        em.flush();
        em.clear();

        assertThat(saved.getId()).isNotNull();
        Product found = productRepository.findById(saved.getId()).orElseThrow();
        assertThat(found.getName()).isEqualTo("생수 2L");
        assertThat(found.getDescription()).isEqualTo("6개입");
        assertThat(found.getUnitPrice()).isEqualByComparingTo("12000.50");
        assertThat(found.getCompany().getId()).isEqualTo(company.getId());
        assertThat(found.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("없는 ID는 empty")
    void notFound() {
        assertThat(productRepository.findById(UUID.randomUUID())).isEmpty();
    }
}
