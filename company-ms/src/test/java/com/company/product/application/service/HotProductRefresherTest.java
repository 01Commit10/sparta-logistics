package com.company.product.application.service;

import com.company.company.domain.entity.Company;
import com.company.fake.FakeCompanyRepository;
import com.company.fake.FakeProductRanking;
import com.company.fake.FakeProductRepository;
import com.company.product.application.cache.HotProductCache;
import com.company.product.application.cache.ProductHotProperties;
import com.company.product.application.dto.ProductResult;
import com.company.product.domain.entity.Product;
import com.company.product.domain.service.RankedProduct;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/** 인기 상품 스냅샷 덮어쓰기 갱신 (P03 2.3): 순위 후보 → 상세 → 앞에서부터 N개를 새 스냅샷으로 교체 */
class HotProductRefresherTest {

    private final FakeCompanyRepository companyRepository = new FakeCompanyRepository();
    private final FakeProductRepository productRepository = new FakeProductRepository();
    private final FakeProductRanking ranking = new FakeProductRanking();
    private final ProductHotProperties properties = new ProductHotProperties(10, 30, Duration.ofSeconds(5),
            Duration.ofMinutes(1), 1000);
    private final HotProductCache cache = new HotProductCache(properties);
    private final HotProductRefresher refresher = new HotProductRefresher(ranking,
            new ProductQueryService(productRepository, companyRepository), productRepository, cache, properties);

    private final Company company = companyRepository.save(
            Company.create("스파르타상사", "서울시 강남구 테헤란로 1", UUID.randomUUID(), UUID.randomUUID()));

    // 앞쪽일수록 조회수가 높은 상품 count개 (순위 점수 = 1000 - i)
    private List<Product> rankedProducts(int count) {
        List<Product> products = new ArrayList<>();
        IntStream.range(0, count).forEach(i -> {
            Product product = productRepository.save(Product.create(company, "상품" + i, null, new BigDecimal("1000")));
            ranking.score(product.getId(), 1000 - i);
            products.add(product);
        });
        return products;
    }

    @Test
    @DisplayName("후보 30개 중 앞에서부터 10개를 순위 순서대로 담고, 조회수는 순위 점수로 채운다")
    void fillsTopN() {
        List<Product> products = rankedProducts(30);

        refresher.refresh();

        List<ProductResult> top = cache.top(10);
        assertThat(top).extracting(ProductResult::id)
                .containsExactlyElementsOf(products.subList(0, 10).stream().map(Product::getId).toList());
        assertThat(top).extracting(ProductResult::viewCount)
                .containsExactly(1000L, 999L, 998L, 997L, 996L, 995L, 994L, 993L, 992L, 991L);
    }

    @Test
    @DisplayName("삭제된 상품·삭제된 업체의 상품은 건너뛰고 순위에서 빼며, 다음 후보로 10개를 채운다")
    void skipsDeleted() {
        List<Product> products = rankedProducts(30);
        ReflectionTestUtils.setField(products.get(1), "deletedAt", LocalDateTime.now());
        Company closed = companyRepository.save(
                Company.create("폐업상사", "서울시 강남구 테헤란로 2", UUID.randomUUID(), UUID.randomUUID()));
        Product ofClosed = productRepository.save(Product.create(closed, "폐업 상품", null, new BigDecimal("1000")));
        ranking.score(ofClosed.getId(), 5000);
        ReflectionTestUtils.setField(closed, "deletedAt", LocalDateTime.now());

        refresher.refresh();

        assertThat(cache.top(10)).hasSize(10).extracting(ProductResult::id)
                .doesNotContain(products.get(1).getId(), ofClosed.getId())
                .contains(products.get(10).getId());
        assertThat(ranking.removed()).containsExactlyInAnyOrder(products.get(1).getId(), ofClosed.getId());
    }

    @Test
    @DisplayName("상품이 N개보다 적으면 있는 만큼만 담는다")
    void fewerThanN() {
        rankedProducts(3);

        refresher.refresh();

        assertThat(cache.top(10)).hasSize(3);
    }

    @Test
    @DisplayName("순위를 읽다 실패하면(Redis 장애) 기존 스냅샷을 그대로 둔다")
    void keepsSnapshotOnFailure() {
        rankedProducts(10);
        refresher.refresh();
        List<ProductResult> before = cache.top(10);

        ranking.failWith(new IllegalStateException("Redis 연결 실패"));
        refresher.refresh();

        assertThat(cache.top(10)).isEqualTo(before);
    }

    @Test
    @DisplayName("갱신하는 도중에도 캐시가 비지 않는다 (옛 스냅샷이 보이다가 새 스냅샷으로 바뀜)")
    void notEmptyWhileRefreshing() {
        List<Product> products = rankedProducts(10);
        refresher.refresh();
        List<ProductResult> before = cache.top(10);
        ranking.score(products.get(9).getId(), 9999);   // 순위가 바뀜

        AtomicReference<List<ProductResult>> seenDuringRefresh = new AtomicReference<>();
        ranking.onTop(() -> seenDuringRefresh.set(cache.top(10)));
        refresher.refresh();

        assertThat(seenDuringRefresh.get()).isEqualTo(before);
        assertThat(cache.top(1)).extracting(ProductResult::id).containsExactly(products.get(9).getId());
    }

    @Test
    @DisplayName("기동 시 순위가 비었으면 DB 조회수 상위 상품으로 채우고 바로 스냅샷을 만든다")
    void warmUpOnStart() {
        Product popular = productRepository.save(Product.create(company, "인기", null, new BigDecimal("1000")));
        ReflectionTestUtils.setField(popular, "viewCount", 500L);
        Product quiet = productRepository.save(Product.create(company, "조용", null, new BigDecimal("1000")));
        ReflectionTestUtils.setField(quiet, "viewCount", 3L);

        refresher.start();

        assertThat(ranking.top(10)).containsExactly(new RankedProduct(popular.getId(), 500),
                new RankedProduct(quiet.getId(), 3));
        assertThat(cache.top(10)).extracting(ProductResult::name).containsExactly("인기", "조용");
    }
}
