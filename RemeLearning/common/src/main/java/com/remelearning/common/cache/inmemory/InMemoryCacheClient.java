package com.remelearning.common.cache.inmemory;

import com.remelearning.common.cache.CacheClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.Cache;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Default {@link CacheClient} implementation, backed by Spring Boot's own
 * {@link ConcurrentMapCacheManager} (a single-JVM, in-memory {@code ConcurrentHashMap} cache).
 * Registered whenever {@code reme.cache.provider} is unset or not {@code redis}, so a service that
 * hasn't provisioned a cache server (Redis, ...) still gets a working {@link CacheClient} out of the
 * box. Entries carry their own expiry timestamp since {@link ConcurrentMapCacheManager} has no
 * built-in TTL support; expiry is checked lazily on {@link #get} / {@link #exists} - an expired entry
 * that is never read again lingers in memory rather than being swept eagerly, which is an acceptable
 * tradeoff for a single-JVM fallback cache (no scheduling infra required from calling services).
 */
@Component
@ConditionalOnProperty(prefix = "reme.cache", name = "provider", havingValue = "redis", matchIfMissing = true)
public class InMemoryCacheClient implements CacheClient {

	private static final String CACHE_NAME = "reme-in-memory-cache";

	private final ConcurrentMapCacheManager cacheManager = new ConcurrentMapCacheManager(CACHE_NAME);
	private final ConcurrentHashMap<String, Instant> expiryByKey = new ConcurrentHashMap<>();

	// Wraps put with an expiry timestamp (if a ttl was given) recorded alongside the entry.
	@Override
	public void put(String key, Object value, Duration ttl) {
		nativeCache().put(key, value);
		if (ttl == null) {
			expiryByKey.remove(key);
		} else {
			expiryByKey.put(key, Instant.now().plus(ttl));
		}
	}

	// Returns the value for key if present, not expired, and assignable to the requested type.
	@Override
	@SuppressWarnings("unchecked")
	public <T> T get(String key, Class<T> type) {
		if (isExpired(key)) {
			evict(key);
			return null;
		}
		Cache.ValueWrapper wrapper = nativeCache().get(key);
		Object value = wrapper == null ? null : wrapper.get();
		return type.isInstance(value) ? (T) value : null;
	}

	// Removes the entry and its tracked expiry, if any.
	@Override
	public void evict(String key) {
		nativeCache().evict(key);
		expiryByKey.remove(key);
	}

	// Reports whether a non-expired entry exists for key.
	@Override
	public boolean exists(String key) {
		if (isExpired(key)) {
			evict(key);
			return false;
		}
		return nativeCache().get(key) != null;
	}

	private boolean isExpired(String key) {
		Instant expiresAt = expiryByKey.get(key);
		return expiresAt != null && Instant.now().isAfter(expiresAt);
	}

	private Cache nativeCache() {
		return cacheManager.getCache(CACHE_NAME);
	}
}
