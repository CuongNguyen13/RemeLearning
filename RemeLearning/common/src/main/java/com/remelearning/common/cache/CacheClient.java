package com.remelearning.common.cache;

import java.time.Duration;

/**
 * Vendor-neutral cache contract shared by all services.
 * The backing store (Redis, in-memory, ...) is not decided yet;
 * concrete implementations live in a technology-specific subpackage, e.g. {@code cache.redis}.
 */
public interface CacheClient {

	/** Stores {@code value} under {@code key}; a {@code null} ttl means no expiration. */
	void put(String key, Object value, Duration ttl);

	/** Reads the value stored under {@code key}, or {@code null} if absent or of a different type. */
	<T> T get(String key, Class<T> type);

	/** Removes the entry stored under {@code key}, if any. */
	void evict(String key);

	boolean exists(String key);
}
