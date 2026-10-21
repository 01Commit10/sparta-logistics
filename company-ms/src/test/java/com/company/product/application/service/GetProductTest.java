package com.company.product.application.service;

import com.common.exception.BusinessException;
import com.common.exception.CompanyErrorCode;
import com.common.exception.ErrorCode;
import com.common.exception.ProductErrorCode;
import com.company.company.domain.entity.Company;
import com.company.fake.FakeCompanyRepository;
import com.company.fake.FakeInventoryClient;
import com.company.fake.FakeProductRepository;
import com.company.fake.HotProductCaches;
import com.company.product.application.dto.ProductResult;
import com.company.product.domain.entity.Product;
import com.company.product.infrastructure.viewcount.DbProductViewCounter;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionOperations;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 상품 상세 조회 (04 API 명세서 Product, 07 서비스 정책 1.3·4장): 로그인 사용자 모두, 삭제된 상품·업체는 404.
 */
class GetProductTest {

    private final FakeCompanyRepository companyRepository = new FakeCompanyRepository();
    private final FakeProductRepository productRepository = new FakeProductRepository();
    private final ProductService service = new ProductService(productRepository, companyRepository,
            new FakeInventoryClient(), TransactionOperations.withoutTransaction(),
            new ProductQueryService(productRepository, companyRepository), new DbProductViewCounter(productRepository), HotProductCaches.empty());

    private final Company company = companyRepository.save(
            Company.create("스파르타상사", "서울시 강남구 테헤란로 1", UUID.randomUUID(), UUID.randomUUID()));
    private final Product product = productRepository.save(
            Product.create(company, "생수 2L", "6개입", new BigDecimal("12000.00")));

    private static void assertError(ThrowingCallable call, ErrorCode expected) {
        assertThatThrownBy(call)
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(expected));
    }

    @Test
    @DisplayName("상품 정보와 소속 업체 ID를 돌려준다")
    void found() {
        ProductResult result = service.getProduct(product.getId());

        assertThat(result.id()).isEqualTo(product.getId());
        assertThat(result.companyId()).isEqualTo(company.getId());
        assertThat(result.name()).isEqualTo("생수 2L");
        assertThat(result.description()).isEqualTo("6개입");
        assertThat(result.unitPrice()).isEqualByComparingTo("12000");
    }

    @Test
    @DisplayName("없는 상품이면 PRODUCT_NOT_FOUND")
    void productNotFound() {
        assertError(() -> service.getProduct(UUID.randomUUID()), ProductErrorCode.PRODUCT_NOT_FOUND);
    }

    @Test
    @DisplayName("삭제된 상품이면 PRODUCT_NOT_FOUND")
    void deletedProduct() {
        ReflectionTestUtils.setField(product, "deletedAt", LocalDateTime.now());

        assertError(() -> service.getProduct(product.getId()), ProductErrorCode.PRODUCT_NOT_FOUND);
    }

    @Test
    @DisplayName("소속 업체가 삭제됐으면 COMPANY_NOT_FOUND")
    void deletedCompany() {
        ReflectionTestUtils.setField(company, "deletedAt", LocalDateTime.now());

        assertError(() -> service.getProduct(product.getId()), CompanyErrorCode.COMPANY_NOT_FOUND);
    }
}
