package com.company.product.application.service;

import com.common.exception.BusinessException;
import com.common.exception.CommonErrorCode;
import com.common.exception.CompanyErrorCode;
import com.common.exception.ErrorCode;
import com.common.exception.ProductErrorCode;
import com.company.company.domain.entity.Company;
import com.company.fake.FakeCompanyRepository;
import com.company.fake.FakeInventoryClient;
import com.company.fake.FakeProductRepository;
import com.company.product.application.dto.ProductResult;
import com.company.product.application.dto.UpdateProductCommand;
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
 * 상품 수정 (04 API 명세서 Product, 07 서비스 정책 4장): MASTER 또는 해당 업체 관리자 본인만, 보낸 필드만 바꾼다.
 */
class UpdateProductTest {

    static final UUID MANAGER_ID = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
    static final UUID MASTER_ID = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
    static final UUID OTHER_USER_ID = UUID.fromString("00000000-0000-0000-0000-0000000000c2");

    private final FakeCompanyRepository companyRepository = new FakeCompanyRepository();
    private final FakeProductRepository productRepository = new FakeProductRepository();
    private final ProductService service = new ProductService(productRepository, companyRepository,
            new FakeInventoryClient(), TransactionOperations.withoutTransaction(),
            new ProductQueryService(productRepository, companyRepository), new DbProductViewCounter(productRepository));

    private final Company company = companyRepository.save(
            Company.create("스파르타상사", "서울시 강남구 테헤란로 1", UUID.randomUUID(), MANAGER_ID));
    private final Product product = productRepository.save(
            Product.create(company, "생수 2L", "6개입", new BigDecimal("12000.00")));

    private static UpdateProductCommand command(UUID productId, String name, BigDecimal unitPrice,
                                                UUID actorId, boolean master) {
        return new UpdateProductCommand(productId, name, null, unitPrice, actorId, master);
    }

    private static void assertError(ThrowingCallable call, ErrorCode expected) {
        assertThatThrownBy(call)
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(expected));
    }

    private void assertUnchanged() {
        assertThat(product.getName()).isEqualTo("생수 2L");
        assertThat(product.getUnitPrice()).isEqualByComparingTo("12000");
    }

    @Test
    @DisplayName("업체 관리자 본인이 수정하면 보낸 필드만 바꾸고 수정된 상품을 돌려준다")
    void updateByManager() {
        ProductResult result = service.updateProduct(
                command(product.getId(), "생수 500ml", null, MANAGER_ID, false));

        assertThat(result.id()).isEqualTo(product.getId());
        assertThat(result.companyId()).isEqualTo(company.getId());
        assertThat(result.name()).isEqualTo("생수 500ml");
        assertThat(result.description()).isEqualTo("6개입");
        assertThat(result.unitPrice()).isEqualByComparingTo("12000");
        assertThat(product.getName()).isEqualTo("생수 500ml");
    }

    @Test
    @DisplayName("MASTER는 다른 업체의 상품도 수정할 수 있다")
    void updateByMaster() {
        ProductResult result = service.updateProduct(
                command(product.getId(), null, new BigDecimal("9000"), MASTER_ID, true));

        assertThat(result.unitPrice()).isEqualByComparingTo("9000");
    }

    @Test
    @DisplayName("업체 관리자가 아닌 USER는 ACCESS_DENIED, 바꾸지 않는다")
    void notManager() {
        assertError(() -> service.updateProduct(command(product.getId(), "바뀐 이름", null, OTHER_USER_ID, false)),
                CommonErrorCode.ACCESS_DENIED);

        assertUnchanged();
    }

    @Test
    @DisplayName("없는 상품이면 PRODUCT_NOT_FOUND")
    void productNotFound() {
        assertError(() -> service.updateProduct(command(UUID.randomUUID(), "바뀐 이름", null, MASTER_ID, true)),
                ProductErrorCode.PRODUCT_NOT_FOUND);
    }

    @Test
    @DisplayName("삭제된 상품이면 PRODUCT_NOT_FOUND, 바꾸지 않는다")
    void deletedProduct() {
        ReflectionTestUtils.setField(product, "deletedAt", LocalDateTime.now());

        assertError(() -> service.updateProduct(command(product.getId(), "바뀐 이름", null, MASTER_ID, true)),
                ProductErrorCode.PRODUCT_NOT_FOUND);

        assertUnchanged();
    }

    @Test
    @DisplayName("소속 업체가 삭제됐으면 COMPANY_NOT_FOUND, 바꾸지 않는다")
    void deletedCompany() {
        ReflectionTestUtils.setField(company, "deletedAt", LocalDateTime.now());

        assertError(() -> service.updateProduct(command(product.getId(), "바뀐 이름", null, MASTER_ID, true)),
                CompanyErrorCode.COMPANY_NOT_FOUND);

        assertUnchanged();
    }

    @Test
    @DisplayName("상품이 없으면 권한보다 먼저 PRODUCT_NOT_FOUND (권한 없는 USER도 404)")
    void productCheckedBeforePermission() {
        assertError(() -> service.updateProduct(command(UUID.randomUUID(), "바뀐 이름", null, OTHER_USER_ID, false)),
                ProductErrorCode.PRODUCT_NOT_FOUND);
    }

    @Test
    @DisplayName("단가가 음수면 PRODUCT_INVALID_UNIT_PRICE, 바꾸지 않는다")
    void negativePrice() {
        assertError(() -> service.updateProduct(
                        command(product.getId(), "바뀐 이름", new BigDecimal("-1"), MANAGER_ID, false)),
                ProductErrorCode.PRODUCT_INVALID_UNIT_PRICE);

        assertUnchanged();
    }
}
