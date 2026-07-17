package com.remelearning.english.dictation.importer;

import com.remelearning.common.storage.StorageClient;
import com.remelearning.english.dictation.domain.DictationClip;
import com.remelearning.english.dictation.mapper.DictationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Imports the fixed dictation library into {@code dictation_clips} on startup, reading files through
 * the active {@link StorageClient} (local filesystem by default). Off unless
 * {@code dictation.library.import-on-startup=true}, so tests and non-owning environments skip it.
 *
 * <p>Taxonomy is derived deterministically from the local layout - no AI, since this is the "fixed"
 * section: {@code examType} from the top folder, and {@code level}/{@code skill}/{@code topic} from
 * the folder path where the convention {@code <examType>/<level>/<skill>/<topic>/<code>.mp3} is used,
 * otherwise from the filename with sensible defaults. Upsert-by-code makes re-runs idempotent.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "dictation.library", name = "import-on-startup", havingValue = "true")
public class DictationLibraryImporter implements ApplicationRunner {

	private static final Pattern LEVEL_PATTERN = Pattern.compile("(?i)^[ABC][12]$");
	private static final Pattern SEQUENCE_PATTERN = Pattern.compile("^\\d{2,}$");
	private static final Set<String> SKILLS = Set.of("listening", "speaking", "reading", "writing");
	private static final String DEFAULT_SKILL = "Listening";

	private final StorageClient storageClient;
	private final DictationMapper dictationMapper;

	// Scans every .mp3 under the storage root, pairs it with its script, derives the taxonomy, and
	// upserts a clip row. Clips whose script is missing/blank are skipped with a warning.
	@Override
	public void run(ApplicationArguments args) {
		int imported = 0;
		int skipped = 0;
		for (String key : storageClient.list("")) {
			if (!key.toLowerCase(Locale.ROOT).endsWith(".mp3")) {
				continue;
			}
			try {
				if (importClip(key)) {
					imported++;
				} else {
					skipped++;
				}
			} catch (RuntimeException ex) {
				skipped++;
				log.warn("Failed to import dictation clip from key '{}', skipping", key, ex);
			}
		}
		log.info("Dictation library import complete: {} clips imported/updated, {} skipped", imported, skipped);
	}

	// Reads the clip's script and upserts one row; returns false if the script is absent/blank.
	private boolean importClip(String audioKey) {
		String scriptKey = scriptKeyFor(audioKey);
		if (!storageClient.exists(scriptKey)) {
			log.warn("No script found for dictation clip '{}' (expected '{}'), skipping", audioKey, scriptKey);
			return false;
		}
		String scriptText = readScript(scriptKey);
		if (scriptText.isBlank()) {
			return false;
		}

		String[] segments = audioKey.split("/");
		String fileName = segments[segments.length - 1];
		String code = stripExtension(fileName);

		dictationMapper.upsertClip(DictationClip.builder()
				.code(code)
				.title(deriveTitle(code))
				.skill(deriveSkill(segments))
				.level(deriveLevel(segments))
				.topic(deriveTopic(code))
				.examType(deriveExamType(segments))
				.scriptText(scriptText)
				.storageKey(audioKey)
				.source("library")
				.build());
		return true;
	}

	// Script lives under a sibling scripts/ folder with the same base name and a .txt extension.
	private String scriptKeyFor(String audioKey) {
		int lastSlash = audioKey.lastIndexOf('/');
		String parent = lastSlash < 0 ? "" : audioKey.substring(0, lastSlash);
		String base = stripExtension(audioKey.substring(lastSlash + 1));
		return (parent.isEmpty() ? "" : parent + "/") + "scripts/" + base + ".txt";
	}

	// Reads the whole script file as UTF-8 text.
	private String readScript(String scriptKey) {
		try (InputStream in = storageClient.read(scriptKey)) {
			return new String(in.readAllBytes(), StandardCharsets.UTF_8).trim();
		} catch (Exception ex) {
			throw new IllegalStateException("Failed to read script: " + scriptKey, ex);
		}
	}

	// Top-level folder is the exam/practice type; a known 'toeic' folder is upper-cased, others are humanized.
	private String deriveExamType(String[] segments) {
		String folder = segments.length > 1 ? segments[0] : "general";
		if ("toeic".equalsIgnoreCase(folder)) {
			return "TOEIC";
		}
		if ("ielts".equalsIgnoreCase(folder)) {
			return "IELTS";
		}
		return humanize(folder);
	}

	// A path segment matching a CEFR level (A1..C2) sets the level; else unset (editable later).
	private String deriveLevel(String[] segments) {
		for (String segment : pathSegments(segments)) {
			if (LEVEL_PATTERN.matcher(segment).matches()) {
				return segment.toUpperCase(Locale.ROOT);
			}
		}
		return null;
	}

	// A path segment naming one of the four macro skills sets the skill; else defaults to Listening.
	private String deriveSkill(String[] segments) {
		for (String segment : pathSegments(segments)) {
			if (SKILLS.contains(segment.toLowerCase(Locale.ROOT))) {
				return capitalize(segment);
			}
		}
		return DEFAULT_SKILL;
	}

	// Folder path segments between the exam-type folder and the filename (the convention slots).
	private List<String> pathSegments(String[] segments) {
		if (segments.length <= 2) {
			return List.of();
		}
		return Arrays.asList(segments).subList(1, segments.length - 1);
	}

	// Humanizes the code into a readable topic: drops the leading '<category>-<seq>-' prefix and any
	// bare ordinal, then title-cases the rest (e.g. "english-conversations-0001-1-at-home-1" -> "At home 1").
	private String deriveTopic(String code) {
		String[] parts = code.split("-");
		StringBuilder topic = new StringBuilder();
		boolean seenSequence = false;
		for (String part : parts) {
			if (!seenSequence) {
				if (SEQUENCE_PATTERN.matcher(part).matches()) {
					seenSequence = true;
				}
				continue;
			}
			if (part.matches("^\\d+$") && topic.length() == 0) {
				continue;
			}
			topic.append(topic.length() == 0 ? "" : " ").append(part);
		}
		String result = topic.toString().trim();
		return result.isEmpty() ? humanize(code) : capitalize(result);
	}

	private String deriveTitle(String code) {
		return deriveTopic(code);
	}

	private String humanize(String value) {
		return capitalize(value.replace('-', ' ').replace('_', ' ').trim());
	}

	private String capitalize(String value) {
		if (value.isEmpty()) {
			return value;
		}
		return Character.toUpperCase(value.charAt(0)) + value.substring(1);
	}

	private String stripExtension(String fileName) {
		int dot = fileName.lastIndexOf('.');
		return dot < 0 ? fileName : fileName.substring(0, dot);
	}
}
