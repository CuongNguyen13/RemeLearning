package com.remelearning.common.storage.fallback;

import com.remelearning.common.exception.BusinessException;
import com.remelearning.common.exception.ErrorCode;
import com.remelearning.common.storage.drive.DriveItem;
import com.remelearning.common.storage.drive.GoogleDriveClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CommonPrefix;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/**
 * Default {@link FallbackDirectoryLister}: tries S3 (using a "/" delimiter to get one level of
 * children), then Google Drive, then the local filesystem, logging which source served the listing
 * (or failed) and moving to the next one on any error, same as {@link FallbackFileReaderImpl}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FallbackDirectoryListerImpl implements FallbackDirectoryLister {

	private final S3Client s3Client;
	private final GoogleDriveClient driveClient;

	@Override
	public List<String> listFolders(String s3Bucket, String s3Prefix, String driveFolderId, String localDir) {
		return listWithFallback(s3Bucket, s3Prefix, driveFolderId, localDir, true);
	}

	@Override
	public List<String> listFiles(String s3Bucket, String s3Prefix, String driveFolderId, String localDir) {
		return listWithFallback(s3Bucket, s3Prefix, driveFolderId, localDir, false);
	}

	// Shared S3 -> Google Drive -> local filesystem cascade; `folders` selects which kind of child to return.
	private List<String> listWithFallback(String s3Bucket, String s3Prefix, String driveFolderId, String localDir,
			boolean folders) {
		String kind = folders ? "folders" : "files";

		if (isSet(s3Bucket)) {
			try {
				log.info("Listing {} from S3: bucket={}, prefix={}", kind, s3Bucket, s3Prefix);
				return folders ? listS3Folders(s3Bucket, s3Prefix) : listS3Files(s3Bucket, s3Prefix);
			} catch (Exception ex) {
				log.warn("Failed to list {} from S3 (bucket={}, prefix={}); falling back to Google Drive",
						kind, s3Bucket, s3Prefix, ex);
			}
		}

		if (isSet(driveFolderId)) {
			try {
				log.info("Listing {} from Google Drive: folderId={}", kind, driveFolderId);
				return driveClient.listChildren(driveFolderId).stream()
						.filter(item -> item.folder() == folders)
						.map(DriveItem::name)
						.toList();
			} catch (Exception ex) {
				log.warn("Failed to list {} from Google Drive (folderId={}); falling back to local filesystem",
						kind, driveFolderId, ex);
			}
		}

		if (isSet(localDir)) {
			try {
				log.info("Listing {} from local filesystem: dir={}", kind, localDir);
				return listLocal(localDir, folders);
			} catch (Exception ex) {
				log.warn("Failed to list {} from local filesystem (dir={})", kind, localDir, ex);
			}
		}

		throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_ERROR,
				"Failed to list content from all sources (S3, Google Drive, local filesystem)",
				HttpStatus.SERVICE_UNAVAILABLE);
	}

	// Sub-"directories" one level under prefix, derived from S3's CommonPrefixes when listing with a "/" delimiter.
	private List<String> listS3Folders(String bucket, String prefix) {
		String normalizedPrefix = normalizeS3Prefix(prefix);
		ListObjectsV2Response response = s3Client.listObjectsV2(ListObjectsV2Request.builder()
				.bucket(bucket).prefix(normalizedPrefix).delimiter("/").build());
		return response.commonPrefixes().stream()
				.map(CommonPrefix::prefix)
				.map(commonPrefix -> stripPrefixAndTrailingSlash(commonPrefix, normalizedPrefix))
				.filter(name -> !name.isBlank())
				.toList();
	}

	// Object keys directly under prefix (one level deep), derived from S3's Contents when listing with a "/" delimiter.
	private List<String> listS3Files(String bucket, String prefix) {
		String normalizedPrefix = normalizeS3Prefix(prefix);
		ListObjectsV2Response response = s3Client.listObjectsV2(ListObjectsV2Request.builder()
				.bucket(bucket).prefix(normalizedPrefix).delimiter("/").build());
		return response.contents().stream()
				.map(S3Object::key)
				.map(key -> key.substring(normalizedPrefix.length()))
				.filter(name -> !name.isBlank())
				.toList();
	}

	private static String normalizeS3Prefix(String prefix) {
		if (prefix == null || prefix.isBlank()) {
			return "";
		}
		return prefix.endsWith("/") ? prefix : prefix + "/";
	}

	private static String stripPrefixAndTrailingSlash(String commonPrefix, String prefix) {
		String withoutPrefix = commonPrefix.startsWith(prefix) ? commonPrefix.substring(prefix.length()) : commonPrefix;
		return withoutPrefix.endsWith("/") ? withoutPrefix.substring(0, withoutPrefix.length() - 1) : withoutPrefix;
	}

	// Immediate children of a local directory, filtered to sub-directories or regular files depending on `folders`.
	private static List<String> listLocal(String dir, boolean folders) throws IOException {
		Path base = Path.of(dir);
		if (!Files.isDirectory(base)) {
			return List.of();
		}
		try (Stream<Path> paths = Files.list(base)) {
			return paths.filter(path -> folders ? Files.isDirectory(path) : Files.isRegularFile(path))
					.map(path -> path.getFileName().toString())
					.sorted()
					.toList();
		}
	}

	private static boolean isSet(String value) {
		return value != null && !value.isBlank();
	}
}
