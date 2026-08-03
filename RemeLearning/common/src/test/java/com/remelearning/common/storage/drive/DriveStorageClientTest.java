package com.remelearning.common.storage.drive;

import com.remelearning.common.storage.StorageProperties;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DriveStorageClientTest {

	private final GoogleDriveClient driveClient = mock(GoogleDriveClient.class);
	private final StorageProperties properties = new StorageProperties();
	private final DriveStorageClient storageClient = new DriveStorageClient(driveClient, properties);

	private void givenRootFolder(String rootFolderId) {
		properties.getDrive().setRootFolderId(rootFolderId);
	}

	@Test
	void listsFilesRecursivelyUnderPrefixAsForwardSlashKeys() {
		givenRootFolder("root");
		when(driveClient.listChildren("root")).thenReturn(List.of(
				new DriveItem("topic-id", "topic-a", true),
				new DriveItem("readme-id", "readme.txt", false)));
		when(driveClient.listChildren("topic-id")).thenReturn(List.of(
				new DriveItem("lesson-id", "lesson-1.mp3", false)));

		List<String> keys = storageClient.list("");

		assertThat(keys).containsExactlyInAnyOrder("readme.txt", "topic-a/lesson-1.mp3");
	}

	@Test
	void existsIsTrueOnlyForIndexedFiles() {
		givenRootFolder("root");
		when(driveClient.listChildren("root")).thenReturn(List.of(
				new DriveItem("topic-id", "topic-a", true),
				new DriveItem("lesson-id", "lesson-1.mp3", false)));

		assertThat(storageClient.exists("lesson-1.mp3")).isTrue();
		assertThat(storageClient.exists("topic-a")).isFalse();
		assertThat(storageClient.exists("missing.mp3")).isFalse();
	}

	@Test
	void readDownloadsTheResolvedFileId() throws Exception {
		givenRootFolder("root");
		when(driveClient.listChildren("root")).thenReturn(List.of(new DriveItem("lesson-id", "lesson-1.mp3", false)));
		when(driveClient.downloadFile("lesson-id")).thenReturn("audio-bytes".getBytes());

		try (InputStream in = storageClient.read("lesson-1.mp3")) {
			assertThat(new String(in.readAllBytes())).isEqualTo("audio-bytes");
		}
	}

	@Test
	void sizeDelegatesToDriveClientForTheResolvedFileId() {
		givenRootFolder("root");
		when(driveClient.listChildren("root")).thenReturn(List.of(new DriveItem("lesson-id", "lesson-1.mp3", false)));
		when(driveClient.fileSize("lesson-id")).thenReturn(42L);

		assertThat(storageClient.size("lesson-1.mp3")).isEqualTo(42L);
	}

	@Test
	void readFailsFastForAnUnknownKey() {
		givenRootFolder("root");
		when(driveClient.listChildren("root")).thenReturn(List.of());

		assertThatThrownBy(() -> storageClient.read("missing.mp3")).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void failsFastWhenRootFolderNotConfigured() {
		assertThatThrownBy(() -> storageClient.list("")).isInstanceOf(IllegalStateException.class);
	}
}
