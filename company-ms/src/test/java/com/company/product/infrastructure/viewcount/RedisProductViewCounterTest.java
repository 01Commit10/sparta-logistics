package com.company.product.infrastructure.viewcount;

import com.company.company.domain.entity.Company;
import com.company.fake.FakeProductRepository;
import com.company.product.domain.entity.Product;
import com.company.product.infrastructure.ranking.RedisProductRanking;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/** 실제 Redis로 조회수 모으기·반영 확인 (Docker 필요: -PincludeContainer) */
@Tag("container")
@Testcontainers
class RedisProductViewCounterTest {

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    static LettuceConnectionFactory connectionFactory;
    static StringRedisTemplate redis;

    private final FakeProductRepository productRepository = new FakeProductRepository();
    private RedisProductViewCounter counter;
    private Product product;

    @BeforeAll
    static void connect() {
        connectionFactory = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
        connectionFactory.afterPropertiesSet();
        connectionFactory.start();
        redis = new StringRedisTemplate(connectionFactory);
    }

    @AfterAll
    static void close() {
        connectionFactory.destroy();
    }

    @BeforeEach
    void setUp() {
        redis.getConnectionFactory().getConnection().serverCommands().flushAll();
        counter = new RedisProductViewCounter(redis, productRepository);
        Company company = Company.create("스파르타상사", "서울시 강남구 테헤란로 1", UUID.randomUUID(), UUID.randomUUID());
        product = productRepository.save(Product.create(company, "생수 2L", "6개입", new BigDecimal("12000.00")));
        ReflectionTestUtils.setField(product, "viewCount", 10L);
    }

    @Test
    @DisplayName("처음 세는 상품은 DB 값에서 시작해 1씩 오른 누적값을 돌려주고, DB는 아직 그대로다")
    void startsFromDb() {
        assertThat(counter.increase(product.getId())).isEqualTo(11);
        assertThat(counter.increase(product.getId())).isEqualTo(12);

        assertThat(product.getViewCount()).isEqualTo(10);
    }

    @Test
    @DisplayName("셀 때 인기 순위 점수도 같은 누적값으로 덮는다 (응답 조회수 = 순위 점수 = 카운터 값)")
    void updatesRankScore() {
        counter.increase(product.getId());
        long count = counter.increase(product.getId());

        assertThat(rankScore()).isEqualTo(count).isEqualTo(12);
        assertThat(redis.opsForValue().get(RedisProductViewCounter.KEY_PREFIX + product.getId())).isEqualTo("12");
    }

    private Double rankScore() {
        return redis.opsForZSet().score(RedisProductRanking.KEY, product.getId().toString());
    }

    @Test
    @DisplayName("flush: 바뀐 상품의 누적값을 DB에 반영하고, 반영할 게 없으면 0")
    void flush() {
        counter.increase(product.getId());
        counter.increase(product.getId());

        assertThat(counter.flush(100)).isEqualTo(1);
        assertThat(product.getViewCount()).isEqualTo(12);

        assertThat(counter.flush(100)).isZero();
    }

    @Test
    @DisplayName("flush 뒤에도 Redis 누적값이 남아 DB를 다시 읽지 않고 이어서 센다")
    void continuesAfterFlush() {
        counter.increase(product.getId());
        counter.flush(100);
        ReflectionTestUtils.setField(product, "viewCount", 0L);   // DB를 다시 읽으면 0에서 시작하게 된다

        assertThat(counter.increase(product.getId())).isEqualTo(12);
    }

    @Test
    @DisplayName("처음 세는 상품을 50명이 동시에 20번씩 봐도 누락 없이 DB 값 + 1000")
    void concurrentFirstViews() throws Exception {
        int threads = 50;
        int perThread = 20;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                for (int j = 0; j < perThread; j++) {
                    counter.increase(product.getId());
                }
                return null;
            }));
        }
        start.countDown();
        for (Future<?> future : futures) {
            future.get(30, TimeUnit.SECONDS);
        }
        pool.shutdown();

        assertThat(counter.increase(product.getId())).isEqualTo(10 + threads * perThread + 1);
        assertThat(rankScore()).isEqualTo(10 + threads * perThread + 1);
    }
}
