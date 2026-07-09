package com.remelearning.common.cache;

import java.time.Duration;

public interface CacheClient {

	void put(String key, Object value, Duration ttl);

	<T> T get(String key, Class<T> type);

	void evict(String key);

	boolean exists(String key);
}
