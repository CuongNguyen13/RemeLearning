package com.remelearning.common.storage;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;

/**
 * Vendor-neutral object-storage contract shared by all services: reads and writes binary assets
 * (audio clips, generated speech, ...) addressed by an opaque string {@code key}.
 *
 * <p>The backing store is selected at the composition root via {@code reme.storage.provider}:
 * {@code storage.local.LocalStorageClient} (a plain local filesystem, the default) or
 * {@code storage.s3.S3StorageAdapter} (S3/MinIO). Callers depend only on this interface, so the
 * store can move from local disk to the cloud later without touching business code. This is
 * intentionally separate from the older {@link S3StorageClient} (which recording-service still uses
 * with explicit bucket names) - new code should depend on this abstraction instead.
 */
public interface StorageClient {

	/** Writes {@code content} under {@code key}, creating any missing parent "directories". */
	void write(String key, InputStream content, long contentLength);

	/** Writes a file already on disk under {@code key}. */
	void write(String key, Path filePath);

	/** Opens the object stored under {@code key} for reading; the caller must close the stream. */
	InputStream read(String key);

	/** Whether an object exists under {@code key}. */
	boolean exists(String key);

	/** Size in bytes of the object under {@code key}. */
	long size(String key);

	/** All keys under {@code prefix} (recursively), using forward slashes; empty if none. */
	List<String> list(String prefix);

	/** A locator for the object: a directly-fetchable URL for remote stores, or the key itself for local. */
	String url(String key);
}
