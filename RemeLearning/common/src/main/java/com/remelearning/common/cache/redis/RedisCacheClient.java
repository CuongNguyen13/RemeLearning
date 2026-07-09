package com.remelearning.common.cache.redis;

import com.remelearning.common.cache.CacheClient;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
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
