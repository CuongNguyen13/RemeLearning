package com.remelearning.common.content.dictation;

import com.remelearning.common.exception.BusinessException;
import com.remelearning.common.storage.drive.GoogleDriveClient;
import com.remelearning.common.storage.fallback.FallbackDirectoryLister;
import com.remelearning.common.storage.fallback.FallbackFileReader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Default {@link DictationContentService}. Topic/lesson ids are plain folder/file names shared by
 * all three sources (see {@code content/dictation/README.md} for the layout convention); Google
 * Drive is the only source addressed by opaque id rather than name, so every Drive lookup here first
 * resolves a folder/file id by name under its parent, returning {@code null} on any failure so the
 * caller falls through to the next source exactly as if Drive were unreachable.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DictationContentServiceImpl implements DictationContentService {

	private final FallbackFileReader fileReader;
	private final FallbackDirectoryLister directoryLister;
	private final GoogleDriveClient driveClient;
	private final DictationContentProperties properties;

	// Lists topic folder names directly under the configured library root.
	@Override
	public List<DictationTopic> listTopics() {
		List<String> topicNames = directoryLister.listFolders(
				properties.getS3().getBucket(), properties.getS3().getRootPrefix(),
				properties.getDrive().getRootFolderId(), properties.getLocal().getRootPath());
		return topicNames.stream().map(name -> new DictationTopic(name, name)).toList();
	}

	// Lists lesson audio files directly under the given topic folder.
	@Override
	public List<DictationLessonSummary> listLessons(String topicId) {
		requireNotBlank(topicId, "topicId");
		String driveTopicFolderId = resolveDriveId(properties.getDrive().getRootFolderId(), topicId);

		String audioExtension = properties.getAudioExtension().toLowerCase(Locale.ROOT);
		List<String> fileNames = directoryLister.listFiles(
				properties.getS3().getBucket(), joinPath(properties.getS3().getRootPrefix(), topicId),
				driveTopicFolderId, joinPath(properties.getLocal().getRootPath(), topicId));

		return fileNames.stream()
				.filter(fileName -> fileName.toLowerCase(Locale.ROOT).endsWith(audioExtension))
				.map(fileName -> {
					String lessonId = stripExtension(fileName);
					return new DictationLessonSummary(lessonId, lessonId);
				})
				.toList();
	}

	// Loads one lesson's audio bytes plus its script parsed into dictation sentences.
	@Override
	public DictationLesson getLesson(String topicId, String lessonId) {
		requireNotBlank(topicId, "topicId");
		requireNotBlank(lessonId, "lessonId");

		String driveTopicFolderId = resolveDriveId(properties.getDrive().getRootFolderId(), topicId);

		byte[] audio = readLessonAudio(topicId, lessonId, driveTopicFolderId);
		List<String> sentences = readLessonSentences(topicId, lessonId, driveTopicFolderId);

		return new DictationLesson(lessonId, lessonId, audio, sentences);
	}

	private byte[] readLessonAudio(String topicId, String lessonId, String driveTopicFolderId) {
		String fileName = lessonId + properties.getAudioExtension();
		return fileReader.readFile(
				properties.getS3().getBucket(), joinPath(properties.getS3().getRootPrefix(), topicId, fileName),
				resolveDriveId(driveTopicFolderId, fileName),
				joinPath(properties.getLocal().getRootPath(), topicId, fileName));
	}

	// Reads the lesson's script file and splits it into one dictation sentence per non-blank line.
	private List<String> readLessonSentences(String topicId, String lessonId, String driveTopicFolderId) {
		String scriptsSubfolder = properties.getScriptsSubfolder();
		String scriptFileName = lessonId + ".txt";
		String driveScriptsFolderId = resolveDriveId(driveTopicFolderId, scriptsSubfolder);

		byte[] scriptBytes = fileReader.readFile(
				properties.getS3().getBucket(),
				joinPath(properties.getS3().getRootPrefix(), topicId, scriptsSubfolder, scriptFileName),
				resolveDriveId(driveScriptsFolderId, scriptFileName),
				joinPath(properties.getLocal().getRootPath(), topicId, scriptsSubfolder, scriptFileName));

		return Arrays.stream(new String(scriptBytes, StandardCharsets.UTF_8).split("\\r?\\n"))
				.map(String::trim)
				.filter(line -> !line.isEmpty())
				.toList();
	}

	// Resolves a child folder/file's Drive id by name under a parent folder id; returns null (rather than
	// throwing) whenever Drive is unreachable, unconfigured, or the child doesn't exist, so callers
	// naturally skip Drive and fall through to the local filesystem the same as any other failed source.
	private String resolveDriveId(String parentFolderId, String name) {
		if (parentFolderId == null || parentFolderId.isBlank()) {
			return null;
		}
		try {
			return driveClient.listChildren(parentFolderId).stream()
					.filter(item -> item.name().equals(name))
					.map(item -> item.id())
					.findFirst()
					.orElse(null);
		} catch (Exception ex) {
			log.debug("Could not resolve Google Drive id for '{}' under parent {}", name, parentFolderId, ex);
			return null;
		}
	}

	// Joins non-blank segments with "/", trimming each segment's own trailing slash to avoid doubling
	// up at the join point - deliberately does NOT strip a segment's leading slash, so an absolute
	// root path (e.g. the local filesystem root) stays absolute in the result.
	private static String joinPath(String... segments) {
		return Arrays.stream(segments)
				.filter(segment -> segment != null && !segment.isBlank())
				.map(segment -> segment.replaceAll("/+$", ""))
				.reduce((a, b) -> a + "/" + b)
				.orElse("");
	}

	private static String stripExtension(String fileName) {
		int dot = fileName.lastIndexOf('.');
		return dot > 0 ? fileName.substring(0, dot) : fileName;
	}

	private static void requireNotBlank(String value, String fieldName) {
		if (value == null || value.isBlank()) {
			throw BusinessException.badRequest(fieldName + " must not be blank");
		}
	}
}
