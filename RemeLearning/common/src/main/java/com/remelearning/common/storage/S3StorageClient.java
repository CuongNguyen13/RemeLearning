package com.remelearning.common.storage;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.InputStream;
import java.nio.file.Path;

/** Thin convenience wrapper around {@link S3Client} for uploading/downloading video, audio and derived assets. */
@Component
@RequiredArgsConstructor
public class S3StorageClient {

	private final S3Client s3Client;

	/** Uploads a file already on disk (e.g. after FFmpeg normalization). */
	public void upload(String bucket, String key, Path filePath) {
		s3Client.putObject(PutObjectRequest.builder().bucket(bucket).key(key).build(), filePath);
	}

	/** Uploads content directly from a stream, e.g. a chunked/resumable upload buffer. */
	public void upload(String bucket, String key, InputStream content, long contentLength) {
		s3Client.putObject(PutObjectRequest.builder().bucket(bucket).key(key).build(),
				RequestBody.fromInputStream(content, contentLength));
	}

	public InputStream download(String bucket, String key) {
		return s3Client.getObject(GetObjectRequest.builder().bucket(bucket).key(key).build());
	}

	/** Returns the object's S3 URL, e.g. for persisting alongside a recording record. */
	public String objectUrl(String bucket, String key) {
		return s3Client.utilities().getUrl(b -> b.bucket(bucket).key(key)).toString();
	}
}
