package com.remelearning.common.storage;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Binds {@code reme.storage.*}: which {@link StorageClient} implementation is active
 * ({@code provider}) plus each implementation's own settings. Injected into the storage clients so
 * provider selection stays in configuration, never hardcoded in a class that uses the abstraction.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "reme.storage")
public class StorageProperties {

	/** {@code local} (default) or {@code s3}; selects which StorageClient bean is registered. */
	private String provider = "local";

	private final Local local = new Local();
	private final S3 s3 = new S3();

	/** Local-filesystem settings. */
	@Getter
	@Setter
	public static class Local {
		/** Base directory every key resolves under; defaults to a relative {@code ./data} dir. */
		private String root = "./data";
	}

	/** S3/MinIO settings for the storage abstraction (distinct from {@code reme.s3.*} used elsewhere). */
	@Getter
	@Setter
	public static class S3 {
		/** Bucket every key is stored under. */
		private String bucket;
	}
}
