package com.remelearning.common.cache.redis;

import com.remelearning.common.cache.CacheClient;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Redis-backed {@link CacheClient} implementation; values are JSON-serialized (see {@link RedisConfig}).
 * Only registered when {@code reme.cache.provider=redis} is set explicitly, so exactly one
 * {@link CacheClient} bean exists at a time - see {@code cache.inmemory.InMemoryCacheClient} for the
 * fallback used when no cache server is configured.
 */
@Component
@ConditionalOnProperty(prefix = "reme.cache", name = "provider", havingValue = "redis")
@RequiredArgsConstructor
public class RedisCacheClient implements CacheClient {

	private final RedisTemplate<String, Object> redisTemplate;

	@Override
	public void put(String key, Object value, Duration ttl) {
		if (ttl == null) {
			redisTemplate.opsForValue().set(key, value);
		} else {
			redisTemplate.opsForValue().set(key, value, ttl);
		}
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T> T get(String key, Class<T> type) {
		Object value = redisTemplate.opsForValue().get(key);
		return type.isInstance(value) ? (T) value : null;
	}

	@Override
	public void evict(String key) {
		redisTemplate.delete(key);
	}

	@Override
	public boolean exists(String key) {
		return Boolean.TRUE.equals(redisTemplate.hasKey(key));
	}
}
