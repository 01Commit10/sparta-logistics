package com.company.product.application.service;

import com.common.page.PageData;
import com.common.page.PageQuery;
import com.company.company.domain.entity.Company;
import com.company.fake.FakeCompanyRepository;
import com.company.fake.FakeInventoryClient;
import com.company.fake.FakeProductRepository;
import com.company.product.application.dto.ProductResult;
import com.company.product.application.dto.ProductSearchQuery;
import com.company.product.domain.entity.Product;
import com.company.product.infrastructure.viewcount.DbProductViewCounter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionOperations;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 상품 목록 조회 (04 API 명세서 Product, 07 서비스 정책 1.3·1.5·4장): 로그인 사용자 모두, companyId·name 검색은 선택.
 */
class SearchProductsTest {

    private final FakeCompanyRepository companyRepository = new FakeCompanyRepository();
    private final FakeProductRepository productRepository = new FakeProductRepository();
    private final ProductService service = new ProductService(productRepository, companyRepository,
            new FakeInventoryClient(), TransactionOperations.withoutTransaction(),
            new ProductQueryService(productRepository, companyRepository), new DbProductViewCounter(productRepository));

    private final Company company = saveCompany("스파르타상사");
    private final Company otherCompany = saveCompany("다른상사");

    private Company saveCompany(String name) {
        return companyRepository.save(Company.create(name, "서울시 강남구 테헤란로 1", UUID.randomUUID(), UUID.randomUUID()));
    }

    private Product saveProduct(Company owner, String name) {
        return productRepository.save(Product.create(owner, name, null, new BigDecimal("1000")));
    }

    private PageData<ProductResult> search(UUID companyId, String name) {
        return service.searchProducts(new ProductSearchQuery(companyId, name),
                new PageQuery(0, 10, List.of(new PageQuery.PageOrder("createdAt", false))));
    }

    @Test
    @DisplayName("조건이 없으면 모든 업체의 상품을 돌려주고, 삭제된 상품과 삭제된 업체의 상품은 빠진다")
    void noCondition() {
        saveProduct(company, "생수 2L");
        saveProduct(otherCompany, "라면");
        Product deleted = saveProduct(company, "삭제된 상품");
        ReflectionTestUtils.setField(deleted, "deletedAt", LocalDateTime.now());
        Company deletedCompany = saveCompany("삭제된상사");
        saveProduct(deletedCompany, "삭제된 업체 상품");
        ReflectionTestUtils.setField(deletedCompany, "deletedAt", LocalDateTime.now());

        PageData<ProductResult> result = search(null, null);

        assertThat(result.content()).extracting(ProductResult::name).containsExactly("생수 2L", "라면");
        assertThat(result.totalElements()).isEqualTo(2);
    }

    @Test
    @DisplayName("companyId·name을 함께 보내면 두 조건을 모두 만족하는 상품만 돌려준다 (name은 부분 일치, 앞뒤 공백 무시)")
    void companyAndName() {
        saveProduct(company, "생수 2L");
        saveProduct(company, "탄산수");
        saveProduct(company, "라면");
        saveProduct(otherCompany, "생수 500ml");

        PageData<ProductResult> result = search(company.getId(), " 수 ");

        assertThat(result.content()).extracting(ProductResult::name).containsExactly("생수 2L", "탄산수");
        assertThat(result.content()).extracting(ProductResult::companyId).containsOnly(company.getId());
    }

    @Test
    @DisplayName("빈 name은 조건이 없는 것으로 본다")
    void blankName() {
        saveProduct(company, "생수 2L");
        saveProduct(otherCompany, "라면");

        assertThat(search(null, "  ").totalElements()).isEqualTo(2);
    }

    @Test
    @DisplayName("없거나 삭제된 업체로 검색하면 404가 아니라 빈 목록")
    void unknownCompany() {
        saveProduct(company, "생수 2L");
        ReflectionTestUtils.setField(company, "deletedAt", LocalDateTime.now());

        assertThat(search(UUID.randomUUID(), null).content()).isEmpty();
        assertThat(search(company.getId(), null).content()).isEmpty();
    }
}
