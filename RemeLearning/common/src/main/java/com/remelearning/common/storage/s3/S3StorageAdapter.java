package com.remelearning.common.storage.s3;

import com.remelearning.common.storage.StorageClient;
import com.remelearning.common.storage.StorageProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;

/**
 * S3/MinIO-backed {@link StorageClient}, keying every object under a single configured bucket
 * ({@code reme.storage.s3.bucket}). Only registered when {@code reme.storage.provider=s3} is set
 * explicitly, so exactly one {@link StorageClient} bean exists at a time - the default is the
 * local-filesystem {@code storage.local.LocalStorageClient}. Delegates to the shared {@link S3Client}
 * bean built by {@code storage.S3ClientConfig}.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "reme.storage", name = "provider", havingValue = "s3")
public class S3StorageAdapter implements StorageClient {

	private final S3Client s3Client;
	private final String bucket;

	// Constructor injection of the shared S3 client and the configured bucket name.
	public S3StorageAdapter(S3Client s3Client, StorageProperties properties) {
		this.s3Client = s3Client;
		this.bucket = properties.getS3().getBucket();
	}

	// Uploads stream content as a single object under the configured bucket.
	@Override
	public void write(String key, InputStream content, long contentLength) {
		s3Client.putObject(PutObjectRequest.builder().bucket(bucket).key(key).build(),
				RequestBody.fromInputStream(content, contentLength));
	}

	// Uploads an on-disk file as a single object under the configured bucket.
	@Override
	public void write(String key, Path filePath) {
		s3Client.putObject(PutObjectRequest.builder().bucket(bucket).key(key).build(), filePath);
	}

	@Override
	public InputStream read(String key) {
		return s3Client.getObject(GetObjectRequest.builder().bucket(bucket).key(key).build());
	}

	// Uses a HEAD request so a missing object is reported as false rather than throwing.
	@Override
	public boolean exists(String key) {
		try {
			s3Client.headObject(HeadObjectRequest.builder().bucket(bucket).key(key).build());
			return true;
		} catch (NoSuchKeyException ex) {
			return false;
		}
	}

	@Override
	public long size(String key) {
		return s3Client.headObject(HeadObjectRequest.builder().bucket(bucket).key(key).build()).contentLength();
	}

	// Lists every object key under the prefix, paginating so more than 1000 objects are all returned.
	@Override
	public List<String> list(String prefix) {
		return s3Client.listObjectsV2Paginator(ListObjectsV2Request.builder().bucket(bucket).prefix(prefix).build())
				.contents().stream()
				.map(S3Object::key)
				.toList();
	}

	// Returns the object's public S3 URL, e.g. for handing a playable link straight to the browser.
	@Override
	public String url(String key) {
		return s3Client.utilities().getUrl(b -> b.bucket(bucket).key(key)).toString();
	}
}
