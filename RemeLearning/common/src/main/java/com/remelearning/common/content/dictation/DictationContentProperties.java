package com.remelearning.common.content.dictation;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Binds {@code reme.dictation.*}: where the dictation content library (topics/lessons) lives across
 * each of the three fallback sources, plus the file-naming convention shared by all of them - one
 * folder per topic, one audio file per lesson, and a matching script file under a "scripts"
 * sub-folder (see {@code content/dictation/README.md}).
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "reme.dictation")
public class DictationContentProperties {

	/** File extension (with leading dot) every lesson's audio file uses, e.g. {@code .mp3}. */
	private String audioExtension = ".mp3";

	/** Sub-folder name under each topic that holds the matching {@code .txt} scripts. */
	private String scriptsSubfolder = "scripts";

	private final S3 s3 = new S3();
	private final Drive drive = new Drive();
	private final Local local = new Local();

	/** S3 location of the library. */
	@Getter
	@Setter
	public static class S3 {
		private String bucket;
		private String rootPrefix = "";
	}

	/** Google Drive location of the library. */
	@Getter
	@Setter
	public static class Drive {
		/** Id of the Drive folder containing one sub-folder per topic. */
		private String rootFolderId;
	}

	/** Local-filesystem location of the library. */
	@Getter
	@Setter
	public static class Local {
		private String rootPath;
	}
}
