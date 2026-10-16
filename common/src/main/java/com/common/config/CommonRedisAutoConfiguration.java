package com.common.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.cache.CacheAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * 모든 도메인 서비스가 공유하는 Redis 설정.
 * - 접속 정보: application-common.yaml(spring.data.redis.*)
 * - RedisTemplate: key는 String, value는 JSON으로 직렬화 (redis-cli에서 읽을 수 있음)
 * - 캐시: 기본 TTL 10분, 키 prefix "{spring.application.name}::" 로 서비스 간 키 충돌 방지
 *   예) hub-ms::hubs::{id}
 * - RedisCacheManager는 Boot의 CacheAutoConfiguration이 아래 RedisCacheConfiguration을 받아 생성한다.
 *   (spring.cache.type=none 인 테스트 환경에선 NoOpCacheManager)
 */
@AutoConfiguration(before = {RedisAutoConfiguration.class, CacheAutoConfiguration.class})
@ConditionalOnClass(RedisConnectionFactory.class)
@EnableCaching
public class CommonRedisAutoConfiguration {

    private static final Duration DEFAULT_CACHE_TTL = Duration.ofMinutes(10);

    // Boot 기본 redisTemplate(RedisTemplate<Object, Object>, JDK 직렬화)을 대체.
    // 문자열만 다룰 땐 Boot가 등록하는 StringRedisTemplate을 그대로 사용.
    @Bean
    @ConditionalOnMissingBean(name = "redisTemplate")
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        GenericJackson2JsonRedisSerializer jsonSerializer = jsonSerializer();

        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(StringRedisSerializer.UTF_8);
        template.setHashKeySerializer(StringRedisSerializer.UTF_8);
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);
        return template;
    }

    // 서비스에서 캐시별 TTL 등이 필요하면 RedisCacheManagerBuilderCustomizer 빈을 등록해 추가 설정.
    @Bean
    @ConditionalOnMissingBean
    public RedisCacheConfiguration redisCacheConfiguration(
            @Value("${spring.application.name}") String applicationName) {
        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(DEFAULT_CACHE_TTL)
                .prefixCacheNameWith(applicationName + "::")
                .disableCachingNullValues()
                .serializeKeysWith(SerializationPair.fromSerializer(StringRedisSerializer.UTF_8))
                .serializeValuesWith(SerializationPair.fromSerializer(jsonSerializer()));
    }

    /**
     * 역직렬화 시 원래 타입으로 복원할 수 있도록 JSON에 클래스 정보(@class)를 함께 저장.
     * - defaultTyping(true): Spring Data Redis의 타입 처리 사용 (record 등 final 클래스에도 타입 정보 기록)
     *   Jackson의 DefaultTyping.EVERYTHING은 2.16부터 deprecated라 직접 사용하지 않음.
     * - 임의 클래스 역직렬화를 막기 위해 프로젝트(com.*)와 JDK(java.*) 타입만 허용.
     *   (Spring의 타입 처리가 ObjectMapper의 PolymorphicTypeValidator를 그대로 사용)
     */
    private static GenericJackson2JsonRedisSerializer jsonSerializer() {
        BasicPolymorphicTypeValidator typeValidator = BasicPolymorphicTypeValidator.builder()
                .allowIfSubType("com.")
                .allowIfSubType("java.")
                .build();

        ObjectMapper objectMapper = JsonMapper.builder()
                .addModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .polymorphicTypeValidator(typeValidator)
                .build();

        return GenericJackson2JsonRedisSerializer.builder()
                .objectMapper(objectMapper)
                .defaultTyping(true)
                .build();
    }
}
