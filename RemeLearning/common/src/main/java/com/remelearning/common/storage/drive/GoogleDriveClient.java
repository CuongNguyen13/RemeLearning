package com.remelearning.common.storage.drive;

import java.util.List;

/**
 * Narrow, read-only contract for the subset of Google Drive access the fallback file reader/lister
 * needs: downloading a file's bytes and listing a folder's immediate children. Callers depend on
 * this interface, never on {@link GoogleDriveClientImpl} directly.
 */
public interface GoogleDriveClient {

	/** Downloads the raw bytes of the file identified by {@code fileId}. */
	byte[] downloadFile(String fileId);

	/** Lists the immediate, non-trashed children (folders and files) of the folder identified by {@code folderId}. */
	List<DriveItem> listChildren(String folderId);
}
