package com.remelearning.common.cache.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Wires up the {@link RedisTemplate} used by {@link RedisCacheClient}, with string keys and JSON values.
 * Only activated when {@code reme.cache.provider=redis} is set explicitly - a service that hasn't
 * configured a Redis server falls back to {@code cache.inmemory.InMemoryCacheClient} instead (see
 * that package for the {@code matchIfMissing} default).
 */
@Configuration
@ConditionalOnProperty(prefix = "reme.cache", name = "provider", havingValue = "redis")
public class RedisConfig {

	@Bean
	public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory,
			ObjectMapper objectMapper) {
		RedisTemplate<String, Object> template = new RedisTemplate<>();
		template.setConnectionFactory(connectionFactory);
		template.setKeySerializer(new StringRedisSerializer());
		template.setValueSerializer(new GenericJackson2JsonRedisSerializer(objectMapper));
		template.setHashKeySerializer(new StringRedisSerializer());
		template.setHashValueSerializer(new GenericJackson2JsonRedisSerializer(objectMapper));
		template.afterPropertiesSet();
		return template;
	}
}
