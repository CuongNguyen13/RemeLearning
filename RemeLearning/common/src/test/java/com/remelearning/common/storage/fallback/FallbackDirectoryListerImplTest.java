package com.remelearning.common.storage.fallback;

import com.remelearning.common.exception.BusinessException;
import com.remelearning.common.storage.drive.DriveItem;
import com.remelearning.common.storage.drive.GoogleDriveClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CommonPrefix;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FallbackDirectoryListerImplTest {

	private final S3Client s3Client = mock(S3Client.class);
	private final GoogleDriveClient driveClient = mock(GoogleDriveClient.class);
	private final FallbackDirectoryListerImpl lister = new FallbackDirectoryListerImpl(s3Client, driveClient);

	@Test
	void listsFoldersFromS3CommonPrefixes() {
		ListObjectsV2Response response = ListObjectsV2Response.builder()
				.commonPrefixes(
						CommonPrefix.builder().prefix("dictation/english-conversations/").build(),
						CommonPrefix.builder().prefix("dictation/business-english/").build())
				.build();
		when(s3Client.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(response);

		List<String> folders = lister.listFolders("bucket", "dictation", "drive-folder", "/local");

		assertThat(folders).containsExactlyInAnyOrder("english-conversations", "business-english");
	}

	@Test
	void listsFilesFromS3Contents() {
		ListObjectsV2Response response = ListObjectsV2Response.builder()
				.contents(
						S3Object.builder().key("dictation/topic/lesson-1.mp3").build(),
						S3Object.builder().key("dictation/topic/lesson-2.mp3").build())
				.build();
		when(s3Client.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(response);

		List<String> files = lister.listFiles("bucket", "dictation/topic", null, null);

		assertThat(files).containsExactlyInAnyOrder("lesson-1.mp3", "lesson-2.mp3");
	}

	@Test
	void fallsBackToGoogleDriveWhenS3Fails() {
		when(s3Client.listObjectsV2(any(ListObjectsV2Request.class))).thenThrow(new RuntimeException("s3 down"));
		when(driveClient.listChildren("drive-folder")).thenReturn(List.of(
				new DriveItem("id-1", "topic-a", true),
				new DriveItem("id-2", "lesson.mp3", false)));

		List<String> folders = lister.listFolders("bucket", "dictation", "drive-folder", null);

		assertThat(folders).containsExactly("topic-a");
	}

	@Test
	void fallsBackToLocalFilesystemWhenS3AndDriveFail(@TempDir Path tempDir) throws Exception {
		when(s3Client.listObjectsV2(any(ListObjectsV2Request.class))).thenThrow(new RuntimeException("s3 down"));
		when(driveClient.listChildren(any())).thenThrow(new RuntimeException("drive down"));
		Files.createDirectory(tempDir.resolve("topic-a"));
		Files.writeString(tempDir.resolve("readme.txt"), "not a folder");

		List<String> folders = lister.listFolders("bucket", "dictation", "drive-folder", tempDir.toString());

		assertThat(folders).containsExactly("topic-a");
	}

	@Test
	void throwsWhenNoSourceIsConfigured() {
		assertThatThrownBy(() -> lister.listFolders(null, null, null, null))
				.isInstanceOf(BusinessException.class);
	}
}
