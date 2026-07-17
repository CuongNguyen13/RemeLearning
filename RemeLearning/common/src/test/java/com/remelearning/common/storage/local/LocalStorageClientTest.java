package com.remelearning.common.storage.local;

import com.remelearning.common.storage.StorageProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalStorageClientTest {

	@TempDir
	Path tempDir;

	private LocalStorageClient storageClient;

	// Points a fresh LocalStorageClient at the per-test temp directory.
	@BeforeEach
	void setUp() {
		StorageProperties properties = new StorageProperties();
		properties.getLocal().setRoot(tempDir.toString());
		storageClient = new LocalStorageClient(properties);
	}

	// A written stream can be read back byte-for-byte, and exists/size/url report the object.
	@Test
	void writesThenReadsBackContent() throws Exception {
		byte[] content = "hello dictation".getBytes(StandardCharsets.UTF_8);
		storageClient.write("audio/clip-1.wav", new ByteArrayInputStream(content), content.length);

		assertThat(storageClient.exists("audio/clip-1.wav")).isTrue();
		assertThat(storageClient.size("audio/clip-1.wav")).isEqualTo(content.length);
		assertThat(storageClient.url("audio/clip-1.wav")).isEqualTo("audio/clip-1.wav");
		try (InputStream in = storageClient.read("audio/clip-1.wav")) {
			assertThat(in.readAllBytes()).isEqualTo(content);
		}
	}

	// write(Path) copies an on-disk file, creating parent directories as needed.
	@Test
	void writesFromExistingFile() throws Exception {
		Path source = Files.writeString(tempDir.resolve("source.txt"), "script text");

		storageClient.write("scripts/nested/one.txt", source);

		try (InputStream in = storageClient.read("scripts/nested/one.txt")) {
			assertThat(new String(in.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("script text");
		}
	}

	// list(prefix) returns forward-slash keys relative to the root for every regular file under it.
	@Test
	void listsKeysUnderPrefixRecursively() {
		storageClient.write("lib/a.mp3", stream("a"), 1);
		storageClient.write("lib/sub/b.mp3", stream("b"), 1);
		storageClient.write("other/c.mp3", stream("c"), 1);

		assertThat(storageClient.list("lib")).containsExactly("lib/a.mp3", "lib/sub/b.mp3");
	}

	// A missing prefix directory yields an empty list rather than throwing.
	@Test
	void listReturnsEmptyForMissingPrefix() {
		assertThat(storageClient.list("does-not-exist")).isEmpty();
	}

	// exists is false for an absent key.
	@Test
	void existsIsFalseForMissingKey() {
		assertThat(storageClient.exists("nope.wav")).isFalse();
	}

	// A key escaping the root via "../" is rejected instead of touching the outside filesystem.
	@Test
	void rejectsPathTraversalKey() {
		assertThatThrownBy(() -> storageClient.exists("../escape.txt"))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("escapes root");
	}

	private static InputStream stream(String value) {
		return new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8));
	}
}
