package com.company.product.infrastructure.persistence;

import com.common.page.PageData;
import com.common.page.PageQuery;
import com.common.page.PageQuery.PageOrder;
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** 업체별 상품 목록 조회. 기본 테스트 DB(H2, PostgreSQL 모드)로 확인한다. */
@DataJpaTest
@Import(ProductRepositoryImpl.class)
class ProductRepositoryListTest {

    static final PageOrder ID_ASC = new PageOrder("id", true);

    @Autowired
    ProductRepository productRepository;

    @Autowired
    EntityManager em;

    Company company;
    Company otherCompany;

    @BeforeEach
    void setUp() {
        company = Company.create("스파르타상사", "서울시 강남구 테헤란로 1", UUID.randomUUID(), UUID.randomUUID());
        otherCompany = Company.create("다른상사", "서울시 강남구 테헤란로 2", UUID.randomUUID(), UUID.randomUUID());
        em.persist(company);
        em.persist(otherCompany);
    }

    private Product persist(Company owner, String name, String unitPrice) {
        Product product = Product.create(owner, name, null, new BigDecimal(unitPrice));
        em.persist(product);
        return product;
    }

    // PagePolicy를 거친 것처럼 마지막에 id ASC를 붙여 조회한다
    private PageData<Product> find(int page, int size, PageOrder... orders) {
        em.flush();
        em.clear();
        List<PageOrder> orderList = new ArrayList<>(List.of(orders));
        orderList.add(ID_ASC);
        return productRepository.findActiveByCompanyId(company.getId(), new PageQuery(page, size, orderList));
    }

    @Test
    @DisplayName("해당 업체의 삭제되지 않은 상품만 조회하고, 전체 건수도 같은 조건으로 센다")
    void excludesOtherCompanyAndDeleted() {
        persist(company, "생수 2L", "1000");
        persist(company, "라면", "2000");
        persist(otherCompany, "다른 업체 상품", "3000");
        Product deleted = persist(company, "삭제된 상품", "4000");
        ReflectionTestUtils.setField(deleted, "deletedAt", LocalDateTime.now());

        PageData<Product> result = find(0, 10, new PageOrder("name", true));

        assertThat(result.content()).extracting(Product::getName).containsExactly("라면", "생수 2L");
        assertThat(result.totalElements()).isEqualTo(2);
    }

    @Test
    @DisplayName("요청한 정렬 순서를 그대로 적용한다 (unitPrice DESC → name ASC)")
    void sortOrder() {
        persist(company, "B상품", "1000");
        persist(company, "A상품", "1000");
        persist(company, "C상품", "5000");

        PageData<Product> result = find(0, 10, new PageOrder("unitPrice", false), new PageOrder("name", true));

        assertThat(result.content()).extracting(Product::getName).containsExactly("C상품", "A상품", "B상품");
    }

    @Test
    @DisplayName("page·size로 자르고, totalElements는 자르기 전 전체 건수")
    void paging() {
        for (int i = 1; i <= 5; i++) {
            persist(company, "상품" + i, "1000");
        }

        PageData<Product> second = find(1, 2, new PageOrder("name", true));
        PageData<Product> outOfRange = find(3, 2, new PageOrder("name", true));

        assertThat(second.content()).extracting(Product::getName).containsExactly("상품3", "상품4");
        assertThat(second.totalElements()).isEqualTo(5);
        assertThat(outOfRange.content()).isEmpty();
        assertThat(outOfRange.totalElements()).isEqualTo(5);
    }

    @Test
    @DisplayName("기본 정렬 createdAt DESC로 조회할 수 있고, 업체 연관은 지연 로딩 프록시에서도 ID를 꺼낼 수 있다")
    void defaultSort() {
        persist(company, "생수 2L", "1000");

        PageData<Product> result = find(0, 10, new PageOrder("createdAt", false));

        assertThat(result.content()).hasSize(1);
        assertThat(result.content().getFirst().getCompany().getId()).isEqualTo(company.getId());
    }
}
