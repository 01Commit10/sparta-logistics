package com.company.product.application.service;

import com.common.exception.BusinessException;
import com.common.exception.CompanyErrorCode;
import com.common.exception.ErrorCode;
import com.common.exception.ProductErrorCode;
import com.company.company.domain.entity.Company;
import com.company.fake.FakeCompanyRepository;
import com.company.fake.FakeProductRepository;
import com.company.product.application.dto.ProductResult;
import com.company.product.domain.entity.Product;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 사용자 상품 상세 조회 + 조회수 증가 (인기 상품용): 조회할 때마다 +1, 실패한 조회는 세지 않는다.
 */
class ViewProductTest {

    private final FakeCompanyRepository companyRepository = new FakeCompanyRepository();
    private final FakeProductRepository productRepository = new FakeProductRepository();
    private final ProductService service = new ProductService(productRepository, companyRepository);

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
    @DisplayName("조회할 때마다 조회수가 1씩 오르고, 응답에 올라간 값이 담긴다")
    void increases() {
        ProductResult first = service.viewProduct(product.getId());
        ProductResult second = service.viewProduct(product.getId());

        assertThat(first.viewCount()).isEqualTo(1);
        assertThat(second.viewCount()).isEqualTo(2);
        assertThat(second.name()).isEqualTo("생수 2L");
        assertThat(second.companyId()).isEqualTo(company.getId());
    }

    @Test
    @DisplayName("조회수를 올리지 않는 getProduct(내부 API용)는 값을 그대로 둔다")
    void getProductDoesNotIncrease() {
        service.getProduct(product.getId());

        assertThat(product.getViewCount()).isZero();
    }

    @Test
    @DisplayName("없는 상품이면 PRODUCT_NOT_FOUND")
    void productNotFound() {
        assertError(() -> service.viewProduct(UUID.randomUUID()), ProductErrorCode.PRODUCT_NOT_FOUND);
    }

    @Test
    @DisplayName("삭제된 상품이면 PRODUCT_NOT_FOUND, 조회수는 오르지 않는다")
    void deletedProduct() {
        ReflectionTestUtils.setField(product, "deletedAt", LocalDateTime.now());

        assertError(() -> service.viewProduct(product.getId()), ProductErrorCode.PRODUCT_NOT_FOUND);
        assertThat(product.getViewCount()).isZero();
    }

    @Test
    @DisplayName("소속 업체가 삭제됐으면 COMPANY_NOT_FOUND, 조회수는 오르지 않는다")
    void deletedCompany() {
        ReflectionTestUtils.setField(company, "deletedAt", LocalDateTime.now());

        assertError(() -> service.viewProduct(product.getId()), CompanyErrorCode.COMPANY_NOT_FOUND);
        assertThat(product.getViewCount()).isZero();
    }
}
