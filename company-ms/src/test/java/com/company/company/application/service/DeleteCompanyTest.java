package com.company.company.application.service;

import com.common.exception.BusinessException;
import com.common.exception.CompanyErrorCode;
import com.common.exception.ErrorCode;
import com.common.page.PageQuery;
import com.company.company.application.dto.CompanySearchQuery;
import com.company.company.application.dto.DeleteCompanyCommand;
import com.company.company.domain.entity.Company;
import com.company.fake.FakeCompanyRepository;
import com.company.fake.FakeHubClient;
import com.company.fake.FakeInventoryClient;
import com.company.fake.FakeProductRepository;
import com.company.fake.HotProductCaches;
import com.company.fake.FakeUserClient;
import com.company.product.application.dto.ProductSearchQuery;
import com.company.product.application.service.ProductQueryService;
import com.company.product.application.service.ProductService;
import com.company.product.domain.entity.Product;
import com.company.product.infrastructure.viewcount.DbProductViewCounter;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionOperations;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 업체 삭제 (04 API 명세서 Company, 07 서비스 정책 1.3·4장): MASTER만(Controller에서 확인), 논리 삭제.
 */
class DeleteCompanyTest {

    static final UUID MANAGER_ID = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
    static final UUID MASTER_ID = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
    static final UUID OTHER_MASTER_ID = UUID.fromString("00000000-0000-0000-0000-0000000000ab");
    static final PageQuery PAGE = new PageQuery(0, 10, List.of(new PageQuery.PageOrder("createdAt", false)));

    private final FakeCompanyRepository companyRepository = new FakeCompanyRepository();
    private final FakeProductRepository productRepository = new FakeProductRepository();
    private final CompanyService service = new CompanyService(
            companyRepository, new FakeUserClient(), new FakeHubClient(), TransactionOperations.withoutTransaction());

    private final Company company = companyRepository.save(
            Company.create("스파르타상사", "서울시 강남구 테헤란로 1", UUID.randomUUID(), MANAGER_ID));

    private static void assertError(ThrowingCallable call, ErrorCode expected) {
        assertThatThrownBy(call)
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(expected));
    }

    @Test
    @DisplayName("삭제하면 삭제 시각·삭제자를 기록하고, 이후 조회는 COMPANY_NOT_FOUND")
    void deleted() {
        service.deleteCompany(new DeleteCompanyCommand(company.getId(), MASTER_ID));

        assertThat(company.isDeleted()).isTrue();
        assertThat(company.getDeletedAt()).isNotNull();
        assertThat(company.getDeletedBy()).isEqualTo(MASTER_ID);
        assertError(() -> service.getCompany(company.getId()), CompanyErrorCode.COMPANY_NOT_FOUND);
    }

    @Test
    @DisplayName("삭제된 업체는 목록·검색에서 빠진다")
    void excludedFromSearch() {
        service.deleteCompany(new DeleteCompanyCommand(company.getId(), MASTER_ID));

        assertThat(service.searchCompanies(new CompanySearchQuery(null, null), PAGE).content())
                .isEmpty();
    }

    @Test
    @DisplayName("없는 업체면 COMPANY_NOT_FOUND")
    void notFound() {
        assertError(() -> service.deleteCompany(new DeleteCompanyCommand(UUID.randomUUID(), MASTER_ID)),
                CompanyErrorCode.COMPANY_NOT_FOUND);
    }

    @Test
    @DisplayName("이미 삭제된 업체면 COMPANY_NOT_FOUND, 최초 삭제자를 그대로 둔다")
    void alreadyDeleted() {
        service.deleteCompany(new DeleteCompanyCommand(company.getId(), MASTER_ID));

        assertError(() -> service.deleteCompany(new DeleteCompanyCommand(company.getId(), OTHER_MASTER_ID)),
                CompanyErrorCode.COMPANY_NOT_FOUND);

        assertThat(company.getDeletedBy()).isEqualTo(MASTER_ID);
    }

    @Test
    @DisplayName("소속 상품은 지우지 않지만, 상품 조회·검색에서는 함께 제외된다")
    void productsHidden() {
        ProductService productService = new ProductService(productRepository, companyRepository,
            new FakeInventoryClient(), TransactionOperations.withoutTransaction(),
            new ProductQueryService(productRepository, companyRepository), new DbProductViewCounter(productRepository), HotProductCaches.empty());
        Product product = productRepository.save(
                Product.create(company, "생수 2L", "6개입", new BigDecimal("12000.00")));

        service.deleteCompany(new DeleteCompanyCommand(company.getId(), MASTER_ID));

        assertThat(product.isDeleted()).isFalse();
        assertError(() -> productService.getProduct(product.getId()), CompanyErrorCode.COMPANY_NOT_FOUND);
        assertThat(productService.searchProducts(new ProductSearchQuery(null, null), PAGE).content())
                .isEmpty();
    }
}
