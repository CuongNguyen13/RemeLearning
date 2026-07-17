package com.remelearning.common.storage.drive;

/** One immediate child of a Google Drive folder, as returned by {@link GoogleDriveClient#listChildren}. */
public record DriveItem(String id, String name, boolean folder) {
}
