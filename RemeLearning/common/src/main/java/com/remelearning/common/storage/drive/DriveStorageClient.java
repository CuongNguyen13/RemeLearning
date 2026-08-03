package com.remelearning.common.storage.drive;

import com.remelearning.common.storage.StorageClient;
import com.remelearning.common.storage.StorageProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Read-only {@link StorageClient} backed by {@link GoogleDriveClient}, addressing Drive content by
 * the same forward-slash key convention as {@code storage.local.LocalStorageClient} (a path relative
 * to {@code reme.storage.drive.root-folder-id}) instead of Drive's own opaque file/folder ids. Only
 * registered when {@code reme.storage.provider=drive} is set explicitly - the default remains the
 * local-filesystem {@code storage.local.LocalStorageClient}.
 *
 * <p>Drive has no path-based lookup, so the whole folder tree under the root is walked once (lazily,
 * on first call) and cached in memory as a key-to-{@link DriveItem} index; every subsequent call
 * resolves against that cache rather than re-walking Drive. This trades staleness (a change made in
 * Drive after the first call won't be seen until the owning service restarts) for far fewer Drive API
 * calls - acceptable for the fixed content libraries this is built for.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "reme.storage", name = "provider", havingValue = "drive")
public class DriveStorageClient implements StorageClient {

	private final GoogleDriveClient driveClient;
	private final StorageProperties properties;
	private volatile Map<String, DriveItem> index;

	public DriveStorageClient(GoogleDriveClient driveClient, StorageProperties properties) {
		this.driveClient = driveClient;
		this.properties = properties;
	}

	@Override
	public void write(String key, InputStream content, long contentLength) {
		throw new UnsupportedOperationException("Drive storage is read-only");
	}

	@Override
	public void write(String key, Path filePath) {
		throw new UnsupportedOperationException("Drive storage is read-only");
	}

	@Override
	public InputStream read(String key) {
		return new ByteArrayInputStream(driveClient.downloadFile(requireFile(key).id()));
	}

	@Override
	public boolean exists(String key) {
		DriveItem item = index().get(key);
		return item != null && !item.folder();
	}

	@Override
	public long size(String key) {
		return driveClient.fileSize(requireFile(key).id());
	}

	// Every key under prefix, files only (folders are traversal nodes, not storage objects), matching
	// LocalStorageClient's contract.
	@Override
	public List<String> list(String prefix) {
		String normalizedPrefix = prefix == null ? "" : prefix;
		return index().entrySet().stream()
				.filter(entry -> !entry.getValue().folder())
				.map(Map.Entry::getKey)
				.filter(key -> normalizedPrefix.isEmpty() || key.startsWith(normalizedPrefix))
				.sorted()
				.toList();
	}

	// No public URL for Drive-backed content - same as the local store, the owning service streams it
	// through its own endpoint.
	@Override
	public String url(String key) {
		return key;
	}

	private DriveItem requireFile(String key) {
		DriveItem item = index().get(key);
		if (item == null || item.folder()) {
			throw new IllegalArgumentException("No Drive file found for storage key: " + key);
		}
		return item;
	}

	// Lazily builds and caches the key -> DriveItem index; double-checked locking keeps concurrent
	// first-callers from each walking (and discarding) their own copy of the tree.
	private Map<String, DriveItem> index() {
		Map<String, DriveItem> current = index;
		if (current == null) {
			synchronized (this) {
				current = index;
				if (current == null) {
					current = buildIndex();
					index = current;
				}
			}
		}
		return current;
	}

	private Map<String, DriveItem> buildIndex() {
		String rootFolderId = properties.getDrive().getRootFolderId();
		if (rootFolderId == null || rootFolderId.isBlank()) {
			throw new IllegalStateException(
					"Drive storage root folder is not configured (reme.storage.drive.root-folder-id)");
		}
		Map<String, DriveItem> map = new LinkedHashMap<>();
		walk(rootFolderId, "", map);
		log.info("Indexed {} Drive storage entries under root folder {}", map.size(), rootFolderId);
		return map;
	}

	// Depth-first walk of the Drive folder tree, recording every child under its path-relative key.
	private void walk(String folderId, String pathPrefix, Map<String, DriveItem> map) {
		for (DriveItem item : driveClient.listChildren(folderId)) {
			String key = pathPrefix.isEmpty() ? item.name() : pathPrefix + "/" + item.name();
			map.put(key, item);
			if (item.folder()) {
				walk(item.id(), key, map);
			}
		}
	}
}
