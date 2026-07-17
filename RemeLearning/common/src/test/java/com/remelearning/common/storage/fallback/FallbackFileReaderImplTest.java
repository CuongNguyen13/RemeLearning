package com.remelearning.common.storage.fallback;

import com.remelearning.common.exception.BusinessException;
import com.remelearning.common.storage.drive.GoogleDriveClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FallbackFileReaderImplTest {

	private final S3Client s3Client = mock(S3Client.class);
	private final GoogleDriveClient driveClient = mock(GoogleDriveClient.class);
	private final FallbackFileReaderImpl reader = new FallbackFileReaderImpl(s3Client, driveClient);

	@Test
	void readsFromS3WhenAvailable() {
		byte[] content = "s3-content".getBytes(StandardCharsets.UTF_8);
		when(s3Client.getObject(any(GetObjectRequest.class)))
				.thenReturn(new ResponseInputStream<>(GetObjectResponse.builder().build(), new ByteArrayInputStream(content)));

		byte[] result = reader.readFile("bucket", "key", "drive-id", "/local/path");

		assertThat(result).isEqualTo(content);
		verify(driveClient, never()).downloadFile(any());
	}

	@Test
	void fallsBackToGoogleDriveWhenS3Fails(@TempDir Path tempDir) {
		when(s3Client.getObject(any(GetObjectRequest.class))).thenThrow(NoSuchKeyException.builder().build());
		byte[] driveContent = "drive-content".getBytes(StandardCharsets.UTF_8);
		when(driveClient.downloadFile("drive-id")).thenReturn(driveContent);

		byte[] result = reader.readFile("bucket", "key", "drive-id", tempDir.resolve("missing.txt").toString());

		assertThat(result).isEqualTo(driveContent);
	}

	@Test
	void fallsBackToLocalFilesystemWhenS3AndDriveFail(@TempDir Path tempDir) throws Exception {
		when(s3Client.getObject(any(GetObjectRequest.class))).thenThrow(NoSuchKeyException.builder().build());
		when(driveClient.downloadFile(any())).thenThrow(new RuntimeException("drive unreachable"));
		Path localFile = tempDir.resolve("script.txt");
		byte[] localContent = "local-content".getBytes(StandardCharsets.UTF_8);
		Files.write(localFile, localContent);

		byte[] result = reader.readFile("bucket", "key", "drive-id", localFile.toString());

		assertThat(result).isEqualTo(localContent);
	}

	@Test
	void skipsS3WhenBucketOrKeyIsBlank() {
		byte[] driveContent = "drive-content".getBytes(StandardCharsets.UTF_8);
		when(driveClient.downloadFile("drive-id")).thenReturn(driveContent);

		byte[] result = reader.readFile("", "key", "drive-id", null);

		assertThat(result).isEqualTo(driveContent);
		verify(s3Client, never()).getObject(any(GetObjectRequest.class));
	}

	@Test
	void throwsWhenNoSourceIsConfigured() {
		assertThatThrownBy(() -> reader.readFile(null, null, null, null))
				.isInstanceOf(BusinessException.class);
	}

	@Test
	void throwsWhenAllConfiguredSourcesFail() {
		when(s3Client.getObject(any(GetObjectRequest.class))).thenThrow(NoSuchKeyException.builder().build());

		assertThatThrownBy(() -> reader.readFile("bucket", "key", null, "/does/not/exist"))
				.isInstanceOf(BusinessException.class);
	}
}
