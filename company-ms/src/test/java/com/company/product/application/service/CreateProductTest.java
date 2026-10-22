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
import com.company.fake.HotProductCaches;
import com.company.product.application.dto.CreateProductCommand;
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
 * 상품 등록 (04 API 명세서 Product, 07 서비스 정책 4장): MASTER 또는 해당 업체 관리자 본인만, 삭제되지 않은 업체에 등록한다.
 */
class CreateProductTest {

    static final UUID MANAGER_ID = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
    static final UUID MASTER_ID = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
    static final UUID OTHER_USER_ID = UUID.fromString("00000000-0000-0000-0000-0000000000c2");

    private final FakeCompanyRepository companyRepository = new FakeCompanyRepository();
    private final FakeProductRepository productRepository = new FakeProductRepository();
    private final ProductService service = new ProductService(productRepository, companyRepository,
            new FakeInventoryClient(), TransactionOperations.withoutTransaction(),
            new ProductQueryService(productRepository, companyRepository), new DbProductViewCounter(productRepository), HotProductCaches.empty());

    private final Company company = companyRepository.save(
            Company.create("스파르타상사", "서울시 강남구 테헤란로 1", UUID.randomUUID(), MANAGER_ID));

    private static CreateProductCommand command(UUID companyId, BigDecimal unitPrice, UUID actorId, boolean master) {
        return new CreateProductCommand(companyId, "생수 2L", "6개입", unitPrice, actorId, master);
    }

    private static void assertError(ThrowingCallable call, ErrorCode expected) {
        assertThatThrownBy(call)
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(expected));
    }

    @Test
    @DisplayName("업체 관리자 본인이 등록하면 상품을 저장하고 생성된 ID와 입력값을 돌려준다")
    void createByManager() {
        ProductResult result = service.createProduct(
                command(company.getId(), new BigDecimal("12000.00"), MANAGER_ID, false));

        assertThat(result.id()).isNotNull();
        assertThat(result.companyId()).isEqualTo(company.getId());
        assertThat(result.name()).isEqualTo("생수 2L");
        assertThat(result.description()).isEqualTo("6개입");
        assertThat(result.unitPrice()).isEqualByComparingTo("12000");

        Product saved = productRepository.findById(result.id()).orElseThrow();
        assertThat(saved.getCompany()).isSameAs(company);
    }

    @Test
    @DisplayName("MASTER는 다른 업체의 상품도 등록할 수 있다")
    void createByMaster() {
        ProductResult result = service.createProduct(
                command(company.getId(), BigDecimal.ONE, MASTER_ID, true));

        assertThat(result.id()).isNotNull();
        assertThat(productRepository.all()).hasSize(1);
    }

    @Test
    @DisplayName("업체 관리자가 아닌 USER는 ACCESS_DENIED, 저장하지 않는다")
    void notManager() {
        assertError(() -> service.createProduct(command(company.getId(), BigDecimal.ONE, OTHER_USER_ID, false)),
                CommonErrorCode.ACCESS_DENIED);

        assertThat(productRepository.all()).isEmpty();
    }

    @Test
    @DisplayName("없는 업체면 COMPANY_NOT_FOUND")
    void companyNotFound() {
        assertError(() -> service.createProduct(command(UUID.randomUUID(), BigDecimal.ONE, MASTER_ID, true)),
                CompanyErrorCode.COMPANY_NOT_FOUND);

        assertThat(productRepository.all()).isEmpty();
    }

    @Test
    @DisplayName("삭제된 업체면 COMPANY_NOT_FOUND")
    void deletedCompany() {
        ReflectionTestUtils.setField(company, "deletedAt", LocalDateTime.now());

        assertError(() -> service.createProduct(command(company.getId(), BigDecimal.ONE, MASTER_ID, true)),
                CompanyErrorCode.COMPANY_NOT_FOUND);

        assertThat(productRepository.all()).isEmpty();
    }

    @Test
    @DisplayName("업체가 없으면 권한보다 먼저 COMPANY_NOT_FOUND (권한 없는 USER도 404)")
    void companyCheckedBeforePermission() {
        assertError(() -> service.createProduct(command(UUID.randomUUID(), BigDecimal.ONE, OTHER_USER_ID, false)),
                CompanyErrorCode.COMPANY_NOT_FOUND);
    }

    @Test
    @DisplayName("단가가 음수면 PRODUCT_INVALID_UNIT_PRICE, 저장하지 않는다")
    void negativePrice() {
        assertError(() -> service.createProduct(command(company.getId(), new BigDecimal("-1"), MANAGER_ID, false)),
                ProductErrorCode.PRODUCT_INVALID_UNIT_PRICE);

        assertThat(productRepository.all()).isEmpty();
    }
}
