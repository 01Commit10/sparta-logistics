package com.company.product.application.service;

import com.common.exception.BusinessException;
import com.common.exception.CommonErrorCode;
import com.common.exception.CompanyErrorCode;
import com.common.exception.ErrorCode;
import com.common.exception.ProductErrorCode;
import com.common.remote.RemoteServiceException;
import com.company.company.domain.entity.Company;
import com.company.fake.FakeCompanyRepository;
import com.company.fake.FakeInventoryClient;
import com.company.fake.FakeProductRepository;
import com.company.product.application.dto.DeleteProductCommand;
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
 * 상품 삭제 (04 API 명세서 Product, 07 서비스 정책 1.3·4장): MASTER 또는 해당 업체 관리자 본인만, 논리 삭제.
 */
class DeleteProductTest {

    static final UUID MANAGER_ID = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
    static final UUID MASTER_ID = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
    static final UUID OTHER_USER_ID = UUID.fromString("00000000-0000-0000-0000-0000000000c2");

    private final FakeCompanyRepository companyRepository = new FakeCompanyRepository();
    private final FakeProductRepository productRepository = new FakeProductRepository();
    private final FakeInventoryClient inventoryClient = new FakeInventoryClient();
    private final ProductService service = new ProductService(productRepository, companyRepository,
            inventoryClient, TransactionOperations.withoutTransaction(),
            new ProductQueryService(productRepository, companyRepository), new DbProductViewCounter(productRepository));

    private final Company company = companyRepository.save(
            Company.create("스파르타상사", "서울시 강남구 테헤란로 1", UUID.randomUUID(), MANAGER_ID));
    private final Product product = productRepository.save(
            Product.create(company, "생수 2L", "6개입", new BigDecimal("12000.00")));

    private static void assertError(ThrowingCallable call, ErrorCode expected) {
        assertThatThrownBy(call)
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(expected));
    }

    @Test
    @DisplayName("업체 관리자 본인이 삭제하면 삭제 시각·삭제자를 기록하고, 이후 조회는 PRODUCT_NOT_FOUND")
    void deleteByManager() {
        service.deleteProduct(new DeleteProductCommand(product.getId(), MANAGER_ID, false));

        assertThat(product.isDeleted()).isTrue();
        assertThat(product.getDeletedBy()).isEqualTo(MANAGER_ID);
        assertError(() -> service.getProduct(product.getId()), ProductErrorCode.PRODUCT_NOT_FOUND);
        assertThat(inventoryClient.deletedProductIds()).containsExactly(product.getId());
    }

    @Test
    @DisplayName("MASTER는 다른 업체의 상품도 삭제할 수 있다")
    void deleteByMaster() {
        service.deleteProduct(new DeleteProductCommand(product.getId(), MASTER_ID, true));

        assertThat(product.getDeletedBy()).isEqualTo(MASTER_ID);
    }

    @Test
    @DisplayName("업체 관리자가 아닌 USER는 ACCESS_DENIED, 삭제하지 않는다")
    void notManager() {
        assertError(() -> service.deleteProduct(new DeleteProductCommand(product.getId(), OTHER_USER_ID, false)),
                CommonErrorCode.ACCESS_DENIED);

        assertThat(product.isDeleted()).isFalse();
        assertThat(inventoryClient.deletedProductIds()).isEmpty();
    }

    @Test
    @DisplayName("hub-ms 재고 삭제가 실패하면 예외를 그대로 던지고 상품은 지우지 않는다 (다시 요청 가능)")
    void inventoryDeleteFails() {
        inventoryClient.failWith(new RemoteServiceException("hub-ms", new IllegalStateException("down")));

        assertThatThrownBy(() -> service.deleteProduct(new DeleteProductCommand(product.getId(), MANAGER_ID, false)))
                .isInstanceOf(RemoteServiceException.class);

        assertThat(product.isDeleted()).isFalse();
    }

    @Test
    @DisplayName("재고 삭제 중에 상품이 먼저 삭제됐으면 PRODUCT_NOT_FOUND, 최초 삭제자를 그대로 둔다")
    void deletedDuringRemoteCall() {
        TransactionOperations deleteBeforeWrite = new TransactionOperations() {
            @Override
            public <T> T execute(org.springframework.transaction.support.TransactionCallback<T> action) {
                product.delete(MASTER_ID);
                return action.doInTransaction(null);
            }
        };
        ProductService racing = new ProductService(productRepository, companyRepository, inventoryClient, deleteBeforeWrite,
                new ProductQueryService(productRepository, companyRepository), new DbProductViewCounter(productRepository));

        assertError(() -> racing.deleteProduct(new DeleteProductCommand(product.getId(), MANAGER_ID, false)),
                ProductErrorCode.PRODUCT_NOT_FOUND);

        assertThat(product.getDeletedBy()).isEqualTo(MASTER_ID);
    }

    @Test
    @DisplayName("없는 상품이면 PRODUCT_NOT_FOUND")
    void productNotFound() {
        assertError(() -> service.deleteProduct(new DeleteProductCommand(UUID.randomUUID(), MASTER_ID, true)),
                ProductErrorCode.PRODUCT_NOT_FOUND);
    }

    @Test
    @DisplayName("이미 삭제된 상품이면 PRODUCT_NOT_FOUND, 최초 삭제자를 그대로 둔다")
    void alreadyDeleted() {
        service.deleteProduct(new DeleteProductCommand(product.getId(), MANAGER_ID, false));

        assertError(() -> service.deleteProduct(new DeleteProductCommand(product.getId(), MASTER_ID, true)),
                ProductErrorCode.PRODUCT_NOT_FOUND);

        assertThat(product.getDeletedBy()).isEqualTo(MANAGER_ID);
    }

    @Test
    @DisplayName("소속 업체가 삭제됐으면 COMPANY_NOT_FOUND, 삭제하지 않는다")
    void deletedCompany() {
        ReflectionTestUtils.setField(company, "deletedAt", LocalDateTime.now());

        assertError(() -> service.deleteProduct(new DeleteProductCommand(product.getId(), MASTER_ID, true)),
                CompanyErrorCode.COMPANY_NOT_FOUND);

        assertThat(product.isDeleted()).isFalse();
    }

    @Test
    @DisplayName("상품이 없으면 권한보다 먼저 PRODUCT_NOT_FOUND (권한 없는 USER도 404)")
    void productCheckedBeforePermission() {
        assertError(() -> service.deleteProduct(new DeleteProductCommand(UUID.randomUUID(), OTHER_USER_ID, false)),
                ProductErrorCode.PRODUCT_NOT_FOUND);
    }
}
