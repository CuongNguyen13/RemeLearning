package com.remelearning.common.storage.drive;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GoogleDriveClientImplTest {

	private final GoogleDriveProperties properties = new GoogleDriveProperties();
	private final GoogleDriveClientImpl client = new GoogleDriveClientImpl(properties);

	@Test
	void downloadFileFailsFastWhenCredentialsNotConfigured() {
		assertThatThrownBy(() -> client.downloadFile("file-id"))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("reme.drive.credentials-file");
	}

	@Test
	void listChildrenFailsFastWhenCredentialsNotConfigured() {
		assertThatThrownBy(() -> client.listChildren("folder-id"))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("reme.drive.credentials-file");
	}
}
