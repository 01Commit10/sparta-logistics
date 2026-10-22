package com.company.product.application.service;

import com.common.exception.BusinessException;
import com.common.exception.CompanyErrorCode;
import com.common.page.PageData;
import com.common.page.PageQuery;
import com.company.company.domain.entity.Company;
import com.company.fake.FakeCompanyRepository;
import com.company.fake.FakeInventoryClient;
import com.company.fake.FakeProductRepository;
import com.company.fake.HotProductCaches;
import com.company.product.application.dto.ProductResult;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 업체별 상품 목록 (04 API 명세서 Product, 07 서비스 정책 1.5·4장): 로그인 사용자 모두, 삭제되지 않은 업체의 삭제되지 않은 상품만.
 */
class GetProductsByCompanyTest {

    private final FakeCompanyRepository companyRepository = new FakeCompanyRepository();
    private final FakeProductRepository productRepository = new FakeProductRepository();
    private final ProductService service = new ProductService(productRepository, companyRepository,
            new FakeInventoryClient(), TransactionOperations.withoutTransaction(),
            new ProductQueryService(productRepository, companyRepository), new DbProductViewCounter(productRepository), HotProductCaches.empty());

    private final Company company = saveCompany("스파르타상사");
    private final Company otherCompany = saveCompany("다른상사");

    private Company saveCompany(String name) {
        return companyRepository.save(Company.create(name, "서울시 강남구 테헤란로 1", UUID.randomUUID(), UUID.randomUUID()));
    }

    private Product saveProduct(Company owner, String name) {
        return productRepository.save(Product.create(owner, name, null, new BigDecimal("1000")));
    }

    private static PageQuery query(int page, int size) {
        return new PageQuery(page, size, List.of(new PageQuery.PageOrder("createdAt", false)));
    }

    @Test
    @DisplayName("해당 업체의 상품만 돌려주고, 다른 업체의 상품과 삭제된 상품은 목록·전체 건수에서 빠진다")
    void onlyActiveProductsOfCompany() {
        saveProduct(company, "생수 2L");
        saveProduct(company, "라면");
        saveProduct(otherCompany, "다른 업체 상품");
        Product deleted = saveProduct(company, "삭제된 상품");
        ReflectionTestUtils.setField(deleted, "deletedAt", LocalDateTime.now());

        PageData<ProductResult> result = service.getProductsByCompany(company.getId(), query(0, 10));

        assertThat(result.totalElements()).isEqualTo(2);
        assertThat(result.content()).extracting(ProductResult::name).containsExactly("생수 2L", "라면");
        assertThat(result.content()).extracting(ProductResult::companyId).containsOnly(company.getId());
    }

    @Test
    @DisplayName("page·size대로 잘라 주고, 전체 건수는 자르기 전 건수")
    void paging() {
        for (int i = 1; i <= 5; i++) {
            saveProduct(company, "상품" + i);
        }

        PageData<ProductResult> result = service.getProductsByCompany(company.getId(), query(1, 2));

        assertThat(result.content()).extracting(ProductResult::name).containsExactly("상품3", "상품4");
        assertThat(result.totalElements()).isEqualTo(5);
    }

    @Test
    @DisplayName("마지막 페이지를 넘으면 에러가 아니라 빈 content")
    void pageOutOfRange() {
        saveProduct(company, "생수 2L");

        PageData<ProductResult> result = service.getProductsByCompany(company.getId(), query(5, 10));

        assertThat(result.content()).isEmpty();
        assertThat(result.totalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("상품이 없는 업체면 빈 목록")
    void noProducts() {
        PageData<ProductResult> result = service.getProductsByCompany(company.getId(), query(0, 10));

        assertThat(result.content()).isEmpty();
        assertThat(result.totalElements()).isZero();
    }

    @Test
    @DisplayName("없는 업체면 빈 목록이 아니라 COMPANY_NOT_FOUND")
    void companyNotFound() {
        assertThatThrownBy(() -> service.getProductsByCompany(UUID.randomUUID(), query(0, 10)))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(CompanyErrorCode.COMPANY_NOT_FOUND));
    }

    @Test
    @DisplayName("삭제된 업체면 상품이 남아 있어도 COMPANY_NOT_FOUND")
    void deletedCompany() {
        saveProduct(company, "생수 2L");
        ReflectionTestUtils.setField(company, "deletedAt", LocalDateTime.now());

        assertThatThrownBy(() -> service.getProductsByCompany(company.getId(), query(0, 10)))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(CompanyErrorCode.COMPANY_NOT_FOUND));
    }
}
