package com.common.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.exc.InvalidTypeIdException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.SerializationException;

class CommonRedisAutoConfigurationTest {

    record SampleDto(UUID id, String name, LocalDateTime createdAt, List<String> tags) {
    }

    private final CommonRedisAutoConfiguration config = new CommonRedisAutoConfiguration();

    @Test
    @SuppressWarnings("unchecked")
    void typeRecoverRecordAndLocalDateTime() {
        RedisTemplate<String, Object> template = config.redisTemplate(mock(RedisConnectionFactory.class));
        RedisSerializer<Object> serializer = (RedisSerializer<Object>) template.getValueSerializer();
        SampleDto dto = new SampleDto(UUID.randomUUID(), "허브", LocalDateTime.of(2026, 10, 15, 12, 0), List.of("a", "b"));

        Object restored = serializer.deserialize(serializer.serialize(dto));

        assertThat(restored).isEqualTo(dto);
    }

    @Test
    void cache_TTL_10minute_and_service_name_is_prefix() {
        RedisCacheConfiguration cacheConfig = config.redisCacheConfiguration("hub-ms");

        Object redisKeyValue = new Object();

        assertThat(cacheConfig.getTtlFunction().getTimeToLive(redisKeyValue, redisKeyValue)).isEqualTo(Duration.ofMinutes(10));
        assertThat(cacheConfig.getKeyPrefixFor("hubs")).isEqualTo("hub-ms::hubs::");
        assertThat(cacheConfig.getAllowCacheNullValues()).isFalse();
    }

    @Test
    @SuppressWarnings("unchecked")
    void rejectNotAllowedType() {
        RedisTemplate<String, Object> template = config.redisTemplate(mock(RedisConnectionFactory.class));
        RedisSerializer<Object> serializer = (RedisSerializer<Object>) template.getValueSerializer();
        // com.* / java.* 외 패키지 타입은 역직렬화 거부
        byte[] json = "{\"@class\":\"org.springframework.util.StopWatch\"}".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> serializer.deserialize(json))
                .isInstanceOf(SerializationException.class)
                .hasRootCauseInstanceOf(InvalidTypeIdException.class)
                .hasStackTraceContaining("PolymorphicTypeValidator");
    }
}
