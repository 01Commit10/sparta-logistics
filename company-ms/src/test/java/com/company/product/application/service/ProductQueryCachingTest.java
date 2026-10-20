package com.company.product.application.service;

import com.common.exception.BusinessException;
import com.common.exception.ProductErrorCode;
import com.common.page.PageQuery;
import com.common.page.PageQuery.PageOrder;
import com.company.company.domain.entity.Company;
import com.company.fake.FakeCompanyRepository;
import com.company.fake.FakeInventoryClient;
import com.company.fake.FakeProductRepository;
import com.company.product.application.dto.DeleteProductCommand;
import com.company.product.application.dto.ProductResult;
import com.company.product.application.dto.ProductSearchQuery;
import com.company.product.application.dto.UpdateProductCommand;
import com.company.product.domain.entity.Product;
import com.company.product.domain.repository.ProductSearchCondition;
import com.company.product.infrastructure.viewcount.DbProductViewCounter;
import com.common.page.PageData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.support.TransactionOperations;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 캐시 어노테이션이 붙은 조회: 같은 조건은 저장소를 한 번만 읽고, 수정·삭제하면 상세 캐시를 지운다.
 * 캐시 저장소는 메모리(ConcurrentMap)로 바꿔 어노테이션 동작만 본다 (Redis·Caffeine은 infrastructure 테스트).
 */
@SpringJUnitConfig(ProductQueryCachingTest.Config.class)
class ProductQueryCachingTest {

    /** 몇 번 읽었는지 세는 저장소 */
    static class CountingProductRepository extends FakeProductRepository {
        final AtomicInteger finds = new AtomicInteger();
        final AtomicInteger searches = new AtomicInteger();

        @Override
        public Optional<Product> findById(UUID id) {
            finds.incrementAndGet();
            return super.findById(id);
        }

        @Override
        public PageData<Product> search(ProductSearchCondition condition, PageQuery query) {
            searches.incrementAndGet();
            return super.search(condition, query);
        }
    }

    @Configuration
    @EnableCaching
    static class Config {
        @Bean
        CacheManager cacheManager() {
            return new ConcurrentMapCacheManager();
        }

        @Bean
        CountingProductRepository productRepository() {
            return new CountingProductRepository();
        }

        @Bean
        FakeCompanyRepository companyRepository() {
            return new FakeCompanyRepository();
        }

        @Bean
        ProductQueryService productQueryService(CountingProductRepository productRepository,
                                                FakeCompanyRepository companyRepository) {
            return new ProductQueryService(productRepository, companyRepository);
        }

        @Bean
        ProductService productService(CountingProductRepository productRepository,
                                      FakeCompanyRepository companyRepository,
                                      ProductQueryService productQueryService) {
            return new ProductService(productRepository, companyRepository, new FakeInventoryClient(),
                    TransactionOperations.withoutTransaction(), productQueryService,
                    new DbProductViewCounter(productRepository));
        }
    }

    @Autowired
    CountingProductRepository productRepository;
    @Autowired
    FakeCompanyRepository companyRepository;
    @Autowired
    ProductQueryService queryService;
    @Autowired
    ProductService productService;
    @Autowired
    CacheManager cacheManager;

    private Company company;
    private Product product;

    private static final PageQuery POPULAR = new PageQuery(0, 10,
            List.of(new PageOrder("viewCount", false), new PageOrder("id", true)));

    @BeforeEach
    void setUp() {
        cacheManager.getCacheNames().forEach(name -> cacheManager.getCache(name).clear());
        company = companyRepository.save(
                Company.create("스파르타상사", "서울시 강남구 테헤란로 1", UUID.randomUUID(), UUID.randomUUID()));
        product = productRepository.save(Product.create(company, "생수 2L", "6개입", new BigDecimal("12000.00")));
        productRepository.finds.set(0);
        productRepository.searches.set(0);
    }

    @Test
    @DisplayName("같은 상품 상세를 두 번 보면 저장소는 한 번만 읽는다")
    void detailCached() {
        ProductResult first = queryService.getActiveProduct(product.getId());
        ProductResult second = queryService.getActiveProduct(product.getId());

        assertThat(second).isEqualTo(first);
        assertThat(productRepository.finds).hasValue(1);
    }

    @Test
    @DisplayName("같은 검색 조건·페이지는 한 번만 읽고, 페이지나 검색어가 다르면 따로 읽는다")
    void searchCached() {
        ProductSearchQuery all = new ProductSearchQuery(null, null);

        queryService.searchProducts(all, POPULAR);
        queryService.searchProducts(all, POPULAR);
        assertThat(productRepository.searches).hasValue(1);

        queryService.searchProducts(all, new PageQuery(1, 10, POPULAR.orderList()));
        queryService.searchProducts(new ProductSearchQuery(null, "생수"), POPULAR);
        assertThat(productRepository.searches).hasValue(3);
    }

    @Test
    @DisplayName("없는 상품(404)은 캐시하지 않는다")
    void notFoundNotCached() {
        UUID missing = UUID.randomUUID();

        assertThatThrownBy(() -> queryService.getActiveProduct(missing)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> queryService.getActiveProduct(missing)).isInstanceOf(BusinessException.class);
        assertThat(productRepository.finds).hasValue(2);
    }

    @Test
    @DisplayName("상품을 수정하면 상세 캐시를 지워 다음 조회는 바뀐 값을 읽는다")
    void updateEvicts() {
        queryService.getActiveProduct(product.getId());

        productService.updateProduct(new UpdateProductCommand(product.getId(), "생수 500ml", null, null,
                company.getManagerUserId(), false));

        assertThat(queryService.getActiveProduct(product.getId()).name()).isEqualTo("생수 500ml");
    }

    @Test
    @DisplayName("상품을 삭제하면 상세 캐시를 지워 다음 조회는 404")
    void deleteEvicts() {
        queryService.getActiveProduct(product.getId());

        productService.deleteProduct(new DeleteProductCommand(product.getId(), company.getManagerUserId(), false));

        assertThatThrownBy(() -> queryService.getActiveProduct(product.getId()))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ProductErrorCode.PRODUCT_NOT_FOUND));
    }

    @Test
    @DisplayName("상세 조회(조회수 +1): 상품 내용은 캐시에서 읽고, 조회수는 매번 센 값으로 응답한다")
    void viewUsesCacheAndCounts() {
        ProductResult first = productService.viewProduct(product.getId());
        int findsAfterFirst = productRepository.finds.get();
        ProductResult second = productService.viewProduct(product.getId());

        assertThat(first.viewCount()).isEqualTo(1);
        assertThat(second.viewCount()).isEqualTo(2);
        assertThat(second.name()).isEqualTo("생수 2L");
        // 두 번째 조회는 상품 내용을 다시 읽지 않는다 (조회수 반영용 읽기만)
        assertThat(productRepository.finds.get() - findsAfterFirst).isEqualTo(1);
    }
}
