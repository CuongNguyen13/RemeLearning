package com.remelearning.common.storage.fallback;

import com.remelearning.common.exception.BusinessException;
import com.remelearning.common.exception.ErrorCode;
import com.remelearning.common.storage.drive.GoogleDriveClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Default {@link FallbackFileReader}: tries S3, then Google Drive, then the local filesystem,
 * logging which source served the file (or failed) and moving to the next one on any error rather
 * than propagating it, so a transient/missing source degrades gracefully instead of failing the
 * whole read.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FallbackFileReaderImpl implements FallbackFileReader {

	private final S3Client s3Client;
	private final GoogleDriveClient driveClient;

	@Override
	public byte[] readFile(String s3Bucket, String s3Key, String driveFileId, String localPath) {
		if (isSet(s3Bucket) && isSet(s3Key)) {
			try {
				log.info("Reading file from S3: bucket={}, key={}", s3Bucket, s3Key);
				return readFromS3(s3Bucket, s3Key);
			} catch (Exception ex) {
				log.warn("Failed to read file from S3 (bucket={}, key={}); falling back to Google Drive", s3Bucket, s3Key, ex);
			}
		}

		if (isSet(driveFileId)) {
			try {
				log.info("Reading file from Google Drive: fileId={}", driveFileId);
				return driveClient.downloadFile(driveFileId);
			} catch (Exception ex) {
				log.warn("Failed to read file from Google Drive (fileId={}); falling back to local filesystem", driveFileId, ex);
			}
		}

		if (isSet(localPath)) {
			try {
				log.info("Reading file from local filesystem: path={}", localPath);
				return Files.readAllBytes(Path.of(localPath));
			} catch (Exception ex) {
				log.warn("Failed to read file from local filesystem (path={})", localPath, ex);
			}
		}

		throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_ERROR,
				"Failed to read file from all sources (S3, Google Drive, local filesystem)",
				HttpStatus.SERVICE_UNAVAILABLE);
	}

	// Streams the object fully into memory - acceptable for the audio-clip/script sizes this reader targets.
	private byte[] readFromS3(String bucket, String key) throws IOException {
		try (InputStream in = s3Client.getObject(GetObjectRequest.builder().bucket(bucket).key(key).build())) {
			return in.readAllBytes();
		}
	}

	private static boolean isSet(String value) {
		return value != null && !value.isBlank();
	}
}
