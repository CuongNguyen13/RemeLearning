package com.remelearning.common.storage.fallback;

import java.util.List;

/**
 * Lists the immediate children of a "directory" from whichever of three sources actually has it, in
 * the same S3 -> Google Drive -> local filesystem priority order as {@link FallbackFileReader}.
 * Names are returned relative to the given prefix/folder/dir (no path separators), sorted where the
 * backing source allows it.
 */
public interface FallbackDirectoryLister {

	/** Lists sub-folder names directly under the given S3 prefix / Drive folder / local directory. */
	List<String> listFolders(String s3Bucket, String s3Prefix, String driveFolderId, String localDir);

	/** Lists file names directly under the given S3 prefix / Drive folder / local directory. */
	List<String> listFiles(String s3Bucket, String s3Prefix, String driveFolderId, String localDir);
}
