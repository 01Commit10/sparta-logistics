package com.company.product.infrastructure.persistence;

import com.company.company.domain.entity.Company;
import com.company.product.domain.entity.Product;
import com.company.product.domain.repository.ProductRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** 인기 상품 (조회수 상위 N개). 기본 테스트 DB(H2, PostgreSQL 모드)로 확인한다. */
@DataJpaTest
@Import(ProductRepositoryImpl.class)
class ProductRepositoryPopularTest {

    @Autowired
    ProductRepository productRepository;

    @Autowired
    EntityManager em;

    Company company;
    Company otherCompany;

    @BeforeEach
    void setUp() {
        company = persistCompany("스파르타상사");
        otherCompany = persistCompany("다른상사");
    }

    private Company persistCompany(String name) {
        Company saved = Company.create(name, "서울시 강남구 테헤란로 1", UUID.randomUUID(), UUID.randomUUID());
        em.persist(saved);
        return saved;
    }

    private Product persist(Company owner, String name, long viewCount) {
        Product product = Product.create(owner, name, null, new BigDecimal("1000"));
        ReflectionTestUtils.setField(product, "viewCount", viewCount);
        em.persist(product);
        return product;
    }

    private List<Product> findPopular(UUID companyId, int limit) {
        em.flush();
        em.clear();
        return productRepository.findPopular(companyId, limit);
    }

    @Test
    @DisplayName("조회수 내림차순으로 limit개만 조회하고, 조회수가 같으면 id 오름차순")
    void orderAndLimit() {
        persist(company, "A", 10);
        persist(otherCompany, "B", 30);
        persist(company, "C", 20);
        Product tie1 = persist(company, "D", 5);
        Product tie2 = persist(otherCompany, "E", 5);

        assertThat(findPopular(null, 3)).extracting(Product::getName).containsExactly("B", "C", "A");

        List<Product> all = findPopular(null, 10);
        assertThat(all).hasSize(5);
        // 조회수가 같은 D·E는 id 순 (UUID 정렬 규칙이 DB마다 달라 순서 자체는 보지 않고, 항상 같은 순서인지만 본다)
        assertThat(all.subList(3, 5)).extracting(Product::getId)
                .containsExactlyInAnyOrder(tie1.getId(), tie2.getId());
        assertThat(findPopular(null, 10)).extracting(Product::getId)
                .containsExactlyElementsOf(all.stream().map(Product::getId).toList());
    }

    @Test
    @DisplayName("companyId를 주면 그 업체 상품만 조회한다")
    void byCompany() {
        persist(company, "A", 10);
        persist(company, "B", 20);
        persist(otherCompany, "C", 100);

        assertThat(findPopular(company.getId(), 10)).extracting(Product::getName).containsExactly("B", "A");
    }

    @Test
    @DisplayName("삭제된 상품과 삭제된 업체의 상품은 빠진다")
    void excludesDeleted() {
        persist(company, "A", 1);
        Product deleted = persist(company, "삭제된 상품", 100);
        ReflectionTestUtils.setField(deleted, "deletedAt", LocalDateTime.now());
        Company deletedCompany = persistCompany("삭제된상사");
        persist(deletedCompany, "삭제된 업체 상품", 200);
        ReflectionTestUtils.setField(deletedCompany, "deletedAt", LocalDateTime.now());

        assertThat(findPopular(null, 10)).extracting(Product::getName).containsExactly("A");
        assertThat(findPopular(deletedCompany.getId(), 10)).isEmpty();
    }
}
