package com.company.product.infrastructure.ranking;

import com.company.product.domain.service.RankedProduct;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** 실제 Redis Sorted Set으로 인기 순위 확인 (Docker 필요: -PincludeContainer) */
@Tag("container")
@Testcontainers
class RedisProductRankingTest {

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    static LettuceConnectionFactory connectionFactory;
    static StringRedisTemplate redis;

    private RedisProductRanking ranking;
    private final UUID a = UUID.randomUUID();
    private final UUID b = UUID.randomUUID();
    private final UUID c = UUID.randomUUID();
    private final UUID d = UUID.randomUUID();

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
        ranking = new RedisProductRanking(redis);
    }

    private void score(UUID id, long viewCount) {
        redis.opsForZSet().add(RedisProductRanking.KEY, id.toString(), viewCount);
    }

    @Test
    @DisplayName("top: 점수 내림차순으로 최대 count개, 점수는 조회수 그대로")
    void top() {
        score(a, 5);
        score(b, 50);
        score(c, 20);
        score(d, 1);

        assertThat(ranking.top(3)).containsExactly(
                new RankedProduct(b, 50), new RankedProduct(c, 20), new RankedProduct(a, 5));
        assertThat(ranking.top(10)).hasSize(4);
    }

    @Test
    @DisplayName("비어 있으면 빈 목록")
    void emptyTop() {
        assertThat(ranking.top(10)).isEmpty();
    }

    @Test
    @DisplayName("remove: 순위에서 빠진다")
    void remove() {
        score(a, 5);
        score(b, 50);

        ranking.remove(b);

        assertThat(ranking.top(10)).containsExactly(new RankedProduct(a, 5));
    }

    @Test
    @DisplayName("warmUpIfEmpty: 비었을 때만 넣는다")
    void warmUpWhenEmpty() {
        ranking.warmUpIfEmpty(List.of(new RankedProduct(a, 30), new RankedProduct(b, 10)));

        assertThat(ranking.top(10)).containsExactly(new RankedProduct(a, 30), new RankedProduct(b, 10));
    }

    @Test
    @DisplayName("warmUpIfEmpty: 이미 순위가 있으면 아무것도 넣지 않고, 기존 점수도 덮지 않는다")
    void warmUpSkipsWhenPresent() {
        score(a, 100);

        ranking.warmUpIfEmpty(List.of(new RankedProduct(a, 30), new RankedProduct(b, 10)));

        assertThat(ranking.top(10)).containsExactly(new RankedProduct(a, 100));
    }

    @Test
    @DisplayName("warmUpIfEmpty: 빈 목록이면 아무 일도 하지 않는다")
    void warmUpWithNothing() {
        ranking.warmUpIfEmpty(List.of());

        assertThat(ranking.top(10)).isEmpty();
    }
}
