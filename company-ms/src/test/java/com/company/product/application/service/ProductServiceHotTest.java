package com.company.product.application.service;

import com.common.page.PageData;
import com.common.page.PageQuery;
import com.common.page.PageQuery.PageOrder;
import com.company.company.domain.entity.Company;
import com.company.fake.FakeCompanyRepository;
import com.company.fake.FakeInventoryClient;
import com.company.fake.FakeProductRepository;
import com.company.fake.HotProductCaches;
import com.company.product.application.cache.HotProductCache;
import com.company.product.application.dto.DeleteProductCommand;
import com.company.product.application.dto.ProductResult;
import com.company.product.application.dto.ProductSearchQuery;
import com.company.product.application.dto.UpdateProductCommand;
import com.company.product.domain.entity.Product;
import com.company.product.domain.repository.ProductSearchCondition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionOperations;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/** hot 모드 조회 경로 (P03 2.1·2.2): 인기 상품은 L1 스냅샷에서, 그 밖은 기존 경로로 */
class ProductServiceHotTest {

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

    private static final PageQuery POPULAR = new PageQuery(0, 10,
            List.of(new PageOrder("viewCount", false), new PageOrder("id", true)));
    private static final ProductSearchQuery NO_CONDITION = new ProductSearchQuery(null, null);

    private final FakeCompanyRepository companyRepository = new FakeCompanyRepository();
    private final CountingProductRepository productRepository = new CountingProductRepository();
    private final HotProductCache hotProductCache = HotProductCaches.empty();
    private final ProductService service = new ProductService(productRepository, companyRepository,
            new FakeInventoryClient(), TransactionOperations.withoutTransaction(),
            new ProductQueryService(productRepository, companyRepository), productId -> 777L, hotProductCache);

    private final Company company = companyRepository.save(
            Company.create("스파르타상사", "서울시 강남구 테헤란로 1", UUID.randomUUID(), UUID.randomUUID()));
    private final Product product = productRepository.save(
            Product.create(company, "생수 2L", "6개입", new BigDecimal("12000.00")));

    private void putInSnapshot(Product... products) {
        hotProductCache.replace(List.of(products).stream().map(ProductResult::from).toList());
        productRepository.finds.set(0);
        productRepository.searches.set(0);
    }

    @Test
    @DisplayName("상세: 스냅샷에 있으면 상품을 읽지 않고, 조회수는 카운터 값으로 응답한다")
    void viewFromSnapshot() {
        putInSnapshot(product);

        ProductResult result = service.viewProduct(product.getId());

        assertThat(result.name()).isEqualTo("생수 2L");
        assertThat(result.viewCount()).isEqualTo(777L);
        assertThat(productRepository.finds).hasValue(0);
    }

    @Test
    @DisplayName("상세: 스냅샷에 없으면 기존 경로(ProductQueryService)로 읽는다")
    void viewFallsBack() {
        ProductResult result = service.viewProduct(product.getId());

        assertThat(result.name()).isEqualTo("생수 2L");
        assertThat(productRepository.finds).hasPositiveValue();
    }

    @Test
    @DisplayName("인기 상품 요청: 스냅샷에서 바로 돌려주고 저장소 검색은 하지 않는다. totalElements는 스냅샷 크기")
    void popularFromSnapshot() {
        Product other = productRepository.save(Product.create(company, "라면", null, new BigDecimal("900")));
        putInSnapshot(other, product);

        PageData<ProductResult> result = service.searchProducts(NO_CONDITION, POPULAR);

        assertThat(result.content()).extracting(ProductResult::name).containsExactly("라면", "생수 2L");
        assertThat(result.totalElements()).isEqualTo(2);
        assertThat(productRepository.searches).hasValue(0);
    }

    @Test
    @DisplayName("인기 상품 요청이어도 스냅샷이 비었으면 기존 검색 경로로 (빈 목록을 주지 않음)")
    void popularFallsBackWhenEmpty() {
        PageData<ProductResult> result = service.searchProducts(NO_CONDITION, POPULAR);

        assertThat(result.content()).extracting(ProductResult::name).containsExactly("생수 2L");
        assertThat(productRepository.searches).hasValue(1);
    }

    @Test
    @DisplayName("인기 상품 요청이 아니면(검색어 있음) 스냅샷이 있어도 기존 검색 경로로")
    void searchIgnoresSnapshot() {
        putInSnapshot(product);

        service.searchProducts(new ProductSearchQuery(null, "생수"), POPULAR);

        assertThat(productRepository.searches).hasValue(1);
    }

    @Test
    @DisplayName("상품을 수정하면 스냅샷에서 빠져 다음 상세 조회는 바뀐 값을 읽는다")
    void updateInvalidates() {
        putInSnapshot(product);

        service.updateProduct(new UpdateProductCommand(product.getId(), "생수 500ml", null, null,
                company.getManagerUserId(), false));

        assertThat(hotProductCache.find(product.getId())).isEmpty();
        assertThat(service.viewProduct(product.getId()).name()).isEqualTo("생수 500ml");
    }

    @Test
    @DisplayName("상품을 삭제하면 스냅샷에서 빠진다")
    void deleteInvalidates() {
        putInSnapshot(product);

        service.deleteProduct(new DeleteProductCommand(product.getId(), company.getManagerUserId(), false));

        assertThat(hotProductCache.find(product.getId())).isEmpty();
        assertThat(hotProductCache.top(10)).isEmpty();
    }
}
