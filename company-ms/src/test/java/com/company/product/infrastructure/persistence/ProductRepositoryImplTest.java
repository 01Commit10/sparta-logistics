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
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
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
    @DisplayName("수정한 상품을 저장하면 바로 flush돼 반환한 엔티티의 updatedAt이 갱신된다")
    void saveFlushesUpdate() {
        Company company = Company.create("스파르타상사", "서울시 강남구 테헤란로 1", UUID.randomUUID(), UUID.randomUUID());
        em.persist(company);
        Product saved = productRepository.save(Product.create(company, "생수 2L", "6개입", new BigDecimal("12000.50")));
        LocalDateTime past = LocalDateTime.of(2000, 1, 1, 0, 0);
        ReflectionTestUtils.setField(saved, "updatedAt", past);

        saved.update(null, null, new BigDecimal("9000"));
        Product updated = productRepository.save(saved);

        assertThat(updated.getUpdatedAt()).isAfter(past);
    }

    @Test
    @DisplayName("조회수 증가: 새 상품은 0에서 시작하고, 호출할 때마다 DB 값이 1씩 오르며 updatedAt은 바뀌지 않는다")
    void increaseViewCount() {
        Company company = Company.create("스파르타상사", "서울시 강남구 테헤란로 1", UUID.randomUUID(), UUID.randomUUID());
        em.persist(company);
        Product saved = productRepository.save(Product.create(company, "생수 2L", "6개입", new BigDecimal("12000.50")));
        // 비교 기준은 DB에 저장된 값으로 잡는다 (메모리 값은 나노초라 DB 정밀도로 바뀌면서 달라질 수 있다)
        em.clear();
        Product before = productRepository.findById(saved.getId()).orElseThrow();
        assertThat(before.getViewCount()).isZero();

        productRepository.increaseViewCount(saved.getId());
        productRepository.increaseViewCount(saved.getId());

        Product found = productRepository.findById(saved.getId()).orElseThrow();
        assertThat(found.getViewCount()).isEqualTo(2);
        assertThat(found.getUpdatedAt()).isEqualTo(before.getUpdatedAt());
    }

    @Test
    @DisplayName("없는 ID는 empty")
    void notFound() {
        assertThat(productRepository.findById(UUID.randomUUID())).isEmpty();
    }

    @Test
    @DisplayName("조회수 반영(Redis → DB): 지금 값보다 클 때만 바꾼다 (늦게 온 작은 값으로 줄어들지 않는다)")
    void applyViewCount() {
        Company company = Company.create("스파르타상사", "서울시 강남구 테헤란로 1", UUID.randomUUID(), UUID.randomUUID());
        em.persist(company);
        Product saved = productRepository.save(Product.create(company, "생수 2L", "6개입", new BigDecimal("12000.50")));

        productRepository.applyViewCount(saved.getId(), 120);
        assertThat(productRepository.findById(saved.getId()).orElseThrow().getViewCount()).isEqualTo(120);

        productRepository.applyViewCount(saved.getId(), 100);
        assertThat(productRepository.findById(saved.getId()).orElseThrow().getViewCount()).isEqualTo(120);
    }
}
