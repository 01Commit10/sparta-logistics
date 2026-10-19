package com.company.product.infrastructure.persistence;

import com.common.page.PageData;
import com.common.page.PageQuery;
import com.common.page.PageQuery.PageOrder;
import com.company.company.domain.entity.Company;
import com.company.product.domain.entity.Product;
import com.company.product.domain.repository.ProductRepository;
import com.company.product.domain.repository.ProductSearchCondition;
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

/** 상품 목록 검색. 기본 테스트 DB(H2, PostgreSQL 모드)로 확인한다. */
@DataJpaTest
@Import(ProductRepositoryImpl.class)
class ProductRepositorySearchTest {

    static final PageOrder ID_ASC = new PageOrder("id", true);
    static final PageOrder NAME_ASC = new PageOrder("name", true);

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

    private Product persist(Company owner, String name, String unitPrice) {
        Product product = Product.create(owner, name, null, new BigDecimal(unitPrice));
        em.persist(product);
        return product;
    }

    // PagePolicy를 거친 것처럼 마지막에 id ASC를 붙여 조회한다
    private PageData<Product> search(UUID companyId, String name, int page, int size, PageOrder... orders) {
        em.flush();
        em.clear();
        List<PageOrder> orderList = new ArrayList<>(List.of(orders));
        orderList.add(ID_ASC);
        return productRepository.search(new ProductSearchCondition(companyId, name), new PageQuery(page, size, orderList));
    }

    private PageData<Product> search(UUID companyId, String name) {
        return search(companyId, name, 0, 10, NAME_ASC);
    }

    @Test
    @DisplayName("조건이 없으면 전체 업체의 상품을 조회하고, 삭제된 상품·삭제된 업체의 상품은 목록과 전체 건수에서 빠진다")
    void excludesDeleted() {
        persist(company, "생수 2L", "1000");
        persist(otherCompany, "라면", "2000");
        Product deleted = persist(company, "삭제된 상품", "3000");
        ReflectionTestUtils.setField(deleted, "deletedAt", LocalDateTime.now());
        Company deletedCompany = persistCompany("삭제된상사");
        persist(deletedCompany, "삭제된 업체 상품", "4000");
        ReflectionTestUtils.setField(deletedCompany, "deletedAt", LocalDateTime.now());

        PageData<Product> result = search(null, null);

        assertThat(result.content()).extracting(Product::getName).containsExactly("라면", "생수 2L");
        assertThat(result.totalElements()).isEqualTo(2);
    }

    @Test
    @DisplayName("companyId는 일치, name은 대소문자를 무시한 부분 일치이고 두 조건은 AND")
    void companyAndName() {
        persist(company, "Water 2L", "1000");
        persist(company, "sparkling WATER", "1000");
        persist(company, "라면", "1000");
        persist(otherCompany, "water 500ml", "1000");

        assertThat(search(company.getId(), "water").content()).extracting(Product::getName)
                .containsExactly("Water 2L", "sparkling WATER");
        assertThat(search(null, "WATER").totalElements()).isEqualTo(3);
        assertThat(search(otherCompany.getId(), null).content()).extracting(Product::getName)
                .containsExactly("water 500ml");
    }

    @Test
    @DisplayName("검색어의 %·_는 와일드카드가 아니라 글자로 찾는다")
    void escapesWildcards() {
        persist(company, "할인 50%", "1000");
        persist(company, "할인 500", "1000");
        persist(company, "A_B", "1000");
        persist(company, "AXB", "1000");

        assertThat(search(null, "50%").content()).extracting(Product::getName).containsExactly("할인 50%");
        assertThat(search(null, "a_b").content()).extracting(Product::getName).containsExactly("A_B");
    }

    @Test
    @DisplayName("요청한 정렬 순서를 그대로 적용하고, page·size로 자른다")
    void sortAndPaging() {
        persist(company, "B상품", "1000");
        persist(company, "A상품", "1000");
        persist(otherCompany, "C상품", "5000");

        PageData<Product> first = search(null, null, 0, 2, new PageOrder("unitPrice", false), NAME_ASC);
        PageData<Product> second = search(null, null, 1, 2, new PageOrder("unitPrice", false), NAME_ASC);

        assertThat(first.content()).extracting(Product::getName).containsExactly("C상품", "A상품");
        assertThat(second.content()).extracting(Product::getName).containsExactly("B상품");
        assertThat(second.totalElements()).isEqualTo(3);
    }

    @Test
    @DisplayName("인기 상품: viewCount 내림차순으로 정렬하고, 조회수가 같으면 다음 정렬(name)을 따른다")
    void sortByViewCount() {
        Product rarelyViewed = persist(company, "B상품", "1000");
        Product popular = persist(company, "A상품", "1000");
        persist(otherCompany, "C상품", "1000");
        persist(otherCompany, "D상품", "1000");
        ReflectionTestUtils.setField(rarelyViewed, "viewCount", 1L);
        ReflectionTestUtils.setField(popular, "viewCount", 10L);

        PageData<Product> result = search(null, null, 0, 10, new PageOrder("viewCount", false), NAME_ASC);

        assertThat(result.content()).extracting(Product::getName).containsExactly("A상품", "B상품", "C상품", "D상품");
    }
}
