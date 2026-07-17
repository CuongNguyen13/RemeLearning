package com.remelearning.common.storage.local;

import com.remelearning.common.storage.StorageClient;
import com.remelearning.common.storage.StorageProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.stream.Stream;

/**
 * Default {@link StorageClient}: a plain local filesystem rooted at {@code reme.storage.local.root}.
 * Registered whenever {@code reme.storage.provider} is unset or {@code local}, so a service without
 * cloud storage provisioned still gets a working store out of the box (see
 * {@code storage.s3.S3StorageAdapter} for the S3-backed alternative).
 *
 * <p>Every key is resolved relative to the root and validated to stay inside it, so a crafted key
 * (e.g. containing {@code ../}) cannot read or write outside the configured directory.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "reme.storage", name = "provider", havingValue = "local", matchIfMissing = true)
public class LocalStorageClient implements StorageClient {

	private final Path root;

	// Resolves and stores the absolute, normalized storage root; no filesystem access happens here
	// so the bean constructs cleanly even in services that never use it (root created lazily on write).
	public LocalStorageClient(StorageProperties properties) {
		this.root = Path.of(properties.getLocal().getRoot()).toAbsolutePath().normalize();
	}

	// Streams content to the resolved path, creating parent directories first; replaces any existing file.
	@Override
	public void write(String key, InputStream content, long contentLength) {
		Path target = resolve(key);
		try {
			Files.createDirectories(target.getParent());
			Files.copy(content, target, StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException ex) {
			throw new UncheckedIOException("Failed to write local storage key: " + key, ex);
		}
	}

	// Copies an on-disk file to the resolved path, creating parent directories first.
	@Override
	public void write(String key, Path filePath) {
		Path target = resolve(key);
		try {
			Files.createDirectories(target.getParent());
			Files.copy(filePath, target, StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException ex) {
			throw new UncheckedIOException("Failed to write local storage key: " + key, ex);
		}
	}

	// Opens the file for reading; the caller closes the returned stream.
	@Override
	public InputStream read(String key) {
		try {
			return Files.newInputStream(resolve(key));
		} catch (IOException ex) {
			throw new UncheckedIOException("Failed to read local storage key: " + key, ex);
		}
	}

	@Override
	public boolean exists(String key) {
		return Files.exists(resolve(key));
	}

	@Override
	public long size(String key) {
		try {
			return Files.size(resolve(key));
		} catch (IOException ex) {
			throw new UncheckedIOException("Failed to stat local storage key: " + key, ex);
		}
	}

	// Walks the directory tree under root+prefix and returns each regular file as a forward-slash
	// key relative to the root; a missing prefix directory yields an empty list rather than throwing.
	@Override
	public List<String> list(String prefix) {
		Path base = resolve(prefix);
		if (!Files.isDirectory(base)) {
			return List.of();
		}
		try (Stream<Path> paths = Files.walk(base)) {
			return paths.filter(Files::isRegularFile)
					.map(path -> root.relativize(path).toString().replace('\\', '/'))
					.sorted()
					.toList();
		} catch (IOException ex) {
			throw new UncheckedIOException("Failed to list local storage prefix: " + prefix, ex);
		}
	}

	// For the local store there is no public URL - the key itself is returned and served by the
	// owning service's streaming endpoint; the S3 adapter returns a real object URL instead.
	@Override
	public String url(String key) {
		return key;
	}

	// Normalizes the key against the root and rejects any path that escapes it (e.g. via "..").
	private Path resolve(String key) {
		Path resolved = root.resolve(key).normalize();
		if (!resolved.startsWith(root)) {
			throw new IllegalArgumentException("Storage key escapes root: " + key);
		}
		return resolved;
	}
}
