package com.company.product;

import com.common.auth.AuthContract;
import com.company.company.domain.entity.Company;
import com.company.company.domain.repository.CompanyRepository;
import com.company.product.application.service.HotProductRefresher;
import com.company.product.domain.entity.Product;
import com.company.product.domain.repository.ProductRepository;
import com.company.product.infrastructure.ranking.RedisProductRanking;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * hot 모드 전체 흐름 (P03 T12): 실제 Redis(컨테이너) + 실제 빈. DB는 기본 테스트 DB(H2).
 * 상세 조회로 순위가 바뀜 → 갱신 1번 → TOP 10 반영, 캐시 적중 시 DB 쿼리 0번, 수정 → 다음 갱신 후 새 이름.
 * 스케줄러는 테스트 중에 돌지 않게 주기를 길게 두고 refresh()를 직접 부른다. (Docker 필요: -PincludeContainer)
 */
@Tag("container")
@Testcontainers
@SpringBootTest(properties = {
        "product.cache.mode=hot",
        "product.hot.refresh-interval=1h",
        "product.hot.local-ttl=2h",
        "spring.jpa.properties.hibernate.generate_statistics=true",
        "eureka.client.enabled=false"
})
@AutoConfigureMockMvc
class HotProductIntegrationTest {

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @DynamicPropertySource
    static void redis(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("spring.data.redis.password", () -> "");
    }

    @Autowired
    MockMvc mockMvc;
    @Autowired
    StringRedisTemplate redis;
    @Autowired
    CompanyRepository companyRepository;
    @Autowired
    ProductRepository productRepository;
    @Autowired
    HotProductRefresher refresher;
    @Autowired
    EntityManagerFactory entityManagerFactory;

    private Company company;
    private Product water;
    private Product noodle;

    @BeforeEach
    void setUp() {
        redis.getConnectionFactory().getConnection().serverCommands().flushAll();
        company = companyRepository.save(
                Company.create("스파르타상사", "서울시 강남구 테헤란로 1", UUID.randomUUID(), UUID.randomUUID()));
        water = productRepository.save(Product.create(company, "생수 2L", "6개입", new BigDecimal("12000")));
        noodle = productRepository.save(Product.create(company, "라면", "5개입", new BigDecimal("4500")));
    }

    private static MockHttpServletRequestBuilder asUser(MockHttpServletRequestBuilder request) {
        return request.header(AuthContract.HEADER_USER_ID, UUID.randomUUID().toString())
                .header(AuthContract.HEADER_USER_ROLE, "MASTER");
    }

    private void view(Product product, int times) throws Exception {
        for (int i = 0; i < times; i++) {
            mockMvc.perform(asUser(get("/api/v1/products/{id}", product.getId()))).andExpect(status().isOk());
        }
    }

    private Statistics statistics() {
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        return statistics;
    }

    @Test
    @DisplayName("상세 조회로 순위가 바뀌고, 갱신 1번 뒤 TOP 10에 순위대로 반영된다 (조회수 = 순위 점수)")
    void rankingReflectsViews() throws Exception {
        view(noodle, 3);
        view(water, 1);

        assertThat(redis.opsForZSet().score(RedisProductRanking.KEY, noodle.getId().toString())).isEqualTo(3.0);
        refresher.refresh();

        mockMvc.perform(asUser(get("/api/v1/products?sort=viewCount,desc&size=10")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].id").value(noodle.getId().toString()))
                .andExpect(jsonPath("$.content[0].viewCount").value(3))
                .andExpect(jsonPath("$.content[1].id").value(water.getId().toString()))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(10));
    }

    @Test
    @DisplayName("스냅샷에 든 인기 상품: TOP 10 조회와 상세 조회 모두 DB 쿼리 0번")
    void noDatabaseOnHit() throws Exception {
        view(noodle, 2);
        refresher.refresh();
        Statistics statistics = statistics();

        mockMvc.perform(asUser(get("/api/v1/products?sort=viewCount,desc&size=10"))).andExpect(status().isOk());
        mockMvc.perform(asUser(get("/api/v1/products/{id}", noodle.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.viewCount").value(3));

        assertThat(statistics.getPrepareStatementCount()).isZero();
    }

    @Test
    @DisplayName("상품을 수정하면 스냅샷에서 빠지고, 다음 갱신 뒤 TOP 10에 새 이름으로 다시 들어온다")
    void updateReflectedAfterRefresh() throws Exception {
        view(noodle, 2);
        refresher.refresh();

        mockMvc.perform(asUser(patch("/api/v1/products/{id}", noodle.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"짜장라면\"}"))
                .andExpect(status().isOk());
        refresher.refresh();

        mockMvc.perform(asUser(get("/api/v1/products?sort=viewCount,desc&size=10")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(noodle.getId().toString()))
                .andExpect(jsonPath("$.content[0].name").value("짜장라면"));
    }

    @Test
    @DisplayName("스냅샷이 비어 있으면(순위 없음) TOP 10 요청은 기존 검색 경로로 응답한다")
    void fallsBackWhenSnapshotEmpty() throws Exception {
        refresher.refresh();   // 순위가 비어 빈 스냅샷

        mockMvc.perform(asUser(get("/api/v1/products?sort=viewCount,desc&size=10")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(2)));
    }
}
