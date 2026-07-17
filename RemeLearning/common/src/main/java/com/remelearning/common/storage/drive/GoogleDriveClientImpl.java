package com.remelearning.common.storage.drive;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.util.List;

/**
 * {@link GoogleDriveClient} backed by the real Drive API v3, authenticated via a service-account
 * JSON key file ({@code reme.drive.credentials-file}). The {@link Drive} client is built lazily on
 * first use rather than at startup, so a service that never actually reads from Drive can still
 * start with no credentials configured - only an actual call fails, which the fallback file
 * reader/lister already treat like any other unreachable source and fall through to the next one.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GoogleDriveClientImpl implements GoogleDriveClient {

	private static final String FOLDER_MIME_TYPE = "application/vnd.google-apps.folder";

	private final GoogleDriveProperties properties;
	private volatile Drive drive;

	// Streams the file's media content fully into memory - acceptable for the audio-clip/script sizes this targets.
	@Override
	public byte[] downloadFile(String fileId) {
		try (InputStream in = drive().files().get(fileId).executeMediaAsInputStream()) {
			return in.readAllBytes();
		} catch (IOException ex) {
			throw new UncheckedIOException("Failed to download Google Drive file: " + fileId, ex);
		}
	}

	// Lists the immediate (non-trashed) children of a Drive folder, tagging each as folder or file.
	@Override
	public List<DriveItem> listChildren(String folderId) {
		try {
			List<File> files = drive().files().list()
					.setQ("'" + escapeQueryValue(folderId) + "' in parents and trashed = false")
					.setFields("files(id, name, mimeType)")
					.setSpaces("drive")
					.execute()
					.getFiles();
			return files.stream()
					.map(file -> new DriveItem(file.getId(), file.getName(), FOLDER_MIME_TYPE.equals(file.getMimeType())))
					.toList();
		} catch (IOException ex) {
			throw new UncheckedIOException("Failed to list Google Drive folder: " + folderId, ex);
		}
	}

	// Escapes a single quote so a folder/file id can never break out of the Drive query's 'x' literal.
	private static String escapeQueryValue(String value) {
		return value.replace("'", "\\'");
	}

	// Lazily builds and caches the authenticated Drive client; double-checked locking keeps concurrent
	// first-callers from each building (and discarding) their own instance.
	private Drive drive() {
		Drive current = drive;
		if (current == null) {
			synchronized (this) {
				current = drive;
				if (current == null) {
					current = buildDrive();
					drive = current;
				}
			}
		}
		return current;
	}

	private Drive buildDrive() {
		String credentialsFile = properties.getCredentialsFile();
		if (credentialsFile == null || credentialsFile.isBlank()) {
			throw new IllegalStateException("Google Drive credentials file is not configured (reme.drive.credentials-file)");
		}
		try (InputStream credentialsStream = Files.newInputStream(Path.of(credentialsFile))) {
			GoogleCredentials credentials = GoogleCredentials.fromStream(credentialsStream)
					.createScoped(List.of(DriveScopes.DRIVE_READONLY));
			return new Drive.Builder(GoogleNetHttpTransport.newTrustedTransport(), GsonFactory.getDefaultInstance(),
					new HttpCredentialsAdapter(credentials))
					.setApplicationName(properties.getApplicationName())
					.build();
		} catch (IOException | GeneralSecurityException ex) {
			throw new IllegalStateException("Failed to initialize Google Drive client", ex);
		}
	}
}
