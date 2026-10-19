package com.company.product.application.service;

import com.company.company.domain.entity.Company;
import com.company.fake.FakeCompanyRepository;
import com.company.fake.FakeInventoryClient;
import com.company.fake.FakeProductRepository;
import com.company.product.application.dto.ProductResult;
import com.company.product.domain.entity.Product;
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
 * 인기 상품 조회: 로그인 사용자 모두, 조회수 상위 10개. companyId는 선택.
 */
class GetPopularProductsTest {

    private final FakeCompanyRepository companyRepository = new FakeCompanyRepository();
    private final FakeProductRepository productRepository = new FakeProductRepository();
    private final ProductService service = new ProductService(productRepository, companyRepository,
            new FakeInventoryClient(), TransactionOperations.withoutTransaction());

    private final Company company = saveCompany("스파르타상사");
    private final Company otherCompany = saveCompany("다른상사");

    private Company saveCompany(String name) {
        return companyRepository.save(Company.create(name, "서울시 강남구 테헤란로 1", UUID.randomUUID(), UUID.randomUUID()));
    }

    private Product saveProduct(Company owner, String name, long viewCount) {
        Product product = productRepository.save(Product.create(owner, name, null, new BigDecimal("1000")));
        ReflectionTestUtils.setField(product, "viewCount", viewCount);
        return product;
    }

    @Test
    @DisplayName("조건이 없으면 전체 업체 상품 중 조회수가 높은 순으로 10개만 돌려준다")
    void top10() {
        for (int i = 1; i <= 12; i++) {
            saveProduct(i % 2 == 0 ? company : otherCompany, "상품" + i, i);
        }

        List<ProductResult> result = service.getPopularProducts(null);

        assertThat(result).hasSize(ProductService.POPULAR_LIMIT);
        assertThat(result).extracting(ProductResult::viewCount)
                .containsExactly(12L, 11L, 10L, 9L, 8L, 7L, 6L, 5L, 4L, 3L);
    }

    @Test
    @DisplayName("companyId를 보내면 그 업체 상품 중에서만 고른다")
    void byCompany() {
        saveProduct(company, "생수", 5);
        saveProduct(company, "라면", 50);
        saveProduct(otherCompany, "다른 업체 인기 상품", 500);

        List<ProductResult> result = service.getPopularProducts(company.getId());

        assertThat(result).extracting(ProductResult::name).containsExactly("라면", "생수");
    }

    @Test
    @DisplayName("삭제된 상품과 삭제된 업체의 상품은 빠진다")
    void excludesDeleted() {
        saveProduct(company, "생수", 1);
        Product deleted = saveProduct(company, "삭제된 상품", 100);
        ReflectionTestUtils.setField(deleted, "deletedAt", LocalDateTime.now());
        saveProduct(otherCompany, "삭제된 업체 상품", 200);
        ReflectionTestUtils.setField(otherCompany, "deletedAt", LocalDateTime.now());

        assertThat(service.getPopularProducts(null)).extracting(ProductResult::name).containsExactly("생수");
    }

    @Test
    @DisplayName("없거나 삭제된 업체면 404가 아니라 빈 목록")
    void unknownCompany() {
        saveProduct(company, "생수", 1);
        ReflectionTestUtils.setField(company, "deletedAt", LocalDateTime.now());

        assertThat(service.getPopularProducts(UUID.randomUUID())).isEmpty();
        assertThat(service.getPopularProducts(company.getId())).isEmpty();
    }
}
