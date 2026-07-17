package com.remelearning.common.storage.fallback;

/**
 * Reads a file's raw bytes from whichever of three sources actually has it, in priority order:
 * Amazon S3, then Google Drive, then the local filesystem. Built for dictation content (audio
 * clips, transcript scripts) that may live in different places depending on environment/migration
 * state, without callers having to know which one currently holds a given file.
 */
public interface FallbackFileReader {

	/**
	 * Tries S3 ({@code s3Bucket}/{@code s3Key}), then Google Drive ({@code driveFileId}), then the
	 * local filesystem ({@code localPath}), in that order, skipping any source whose parameters are
	 * blank. Returns the first successful read.
	 *
	 * @throws com.remelearning.common.exception.BusinessException if every configured source fails
	 */
	byte[] readFile(String s3Bucket, String s3Key, String driveFileId, String localPath);
}
