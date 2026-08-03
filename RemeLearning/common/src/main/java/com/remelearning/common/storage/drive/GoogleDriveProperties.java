package com.remelearning.common.storage.drive;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Binds {@code reme.drive.*}: the service-account credentials used to authenticate {@link GoogleDriveClient}. */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "reme.drive")
public class GoogleDriveProperties {

	/** Path to a Google service-account JSON key file with read access to the target Drive content. */
	private String credentialsFile;

	/**
	 * Base64-encoded service-account JSON key, as an alternative to {@link #credentialsFile} for
	 * deploy targets where writing the raw key to disk isn't practical - takes precedence over
	 * {@code credentialsFile} when both are set.
	 */
	private String credentialsBase64;

	/** Application name sent to the Drive API, for quota/usage attribution. */
	private String applicationName = "RemeLearning";
}
