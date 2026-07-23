package com.remelearning.english.listening.library.generator;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.remelearning.common.storage.StorageClient;
import com.remelearning.english.learn.common.AiContentClient;
import com.remelearning.english.learn.common.AiContentException;
import com.remelearning.english.learn.common.DialogueAudioSynthesizer;
import com.remelearning.english.learn.common.DialogueLine;
import com.remelearning.english.learn.common.SynthesizedDialogue;
import com.remelearning.english.listening.library.domain.ListeningLibraryQuestion;
import com.remelearning.english.listening.library.domain.ListeningLibrarySection;
import com.remelearning.english.listening.library.domain.ListeningLibraryTopic;
import com.remelearning.english.listening.library.mapper.ListeningLibraryQuestionMapper;
import com.remelearning.english.listening.library.mapper.ListeningLibrarySectionMapper;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.UUID;

/**
 * Generates one Listening-library Section (a short passage + its reusable MCQ question pool) for a
 * topic: one Gemini call for the passage+questions (via {@link AiContentClient}, mirroring this
 * package's sibling {@code LlmListeningPracticeGenerator} rather than calling {@code LlmClient}
 * directly, since {@code AiContentClient} already centralizes the LLM call + JSON parsing + code-
 * fence stripping every other "learn"/"library" generator in this service uses), then
 * Supertonic-synthesizes the passage into audio as a single-speaker monologue (via
 * {@link DialogueAudioSynthesizer}, same as {@code ListeningLearnServiceImpl}). Unlike the "learn"
 * skill's practice-item flow (which inserts first, then calls a separate update-storage-key mapper
 * method once the id exists), {@link ListeningLibrarySectionMapper} only exposes {@code insert} - so
 * here the audio is synthesized and stored *before* the section row is inserted, and the storage key
 * is addressed by topic id + a random suffix instead of the (not-yet-known) section id.
 *
 * <p>This is a content-authoring/seeding generator, not a learner-facing request: unlike the
 * "learn" generators' silent template fallback (which would insert placeholder junk into the
 * library), any LLM/parse failure here is left to propagate as {@link AiContentException} so a
 * failed generation simply doesn't create a Section, rather than persisting bad content.
 */
@Slf4j
@Component
public class LlmListeningLibraryGenerator {

	private static final String SYSTEM_PROMPT = """
			You are an English-listening content writer building a reusable library passage for
			learners. Given a topic name and a CEFR level, write ONE short listening passage (a
			monologue, 3-6 sentences, natural spoken English) about the topic, then write exactly 4
			multiple-choice comprehension questions, each with 4 options (A-D). Respond with STRICTLY a
			raw JSON object (no markdown fences, no commentary) of the shape:
			{"passage": "...",
			 "questions": [{"question": "...", "options": ["...","...","...","..."], "correctOption": "A"|"B"|"C"|"D", "explanation": "..."}]}
			- "options" must have exactly 4 entries.
			- "correctOption" is the letter of the correct option, matching its position (A=1st, ...).
			- "explanation" in Vietnamese, one short sentence; passage/question text in English.""";

	// Single stable speaker label for the monologue passage, matching DialogueAudioSynthesizer's
	// single-voice behavior when only one distinct speaker is present.
	private static final String NARRATOR = "Narrator";
	private static final String GENERATED_KEY = "listening-library/%d/%s.wav";

	private final AiContentClient aiContentClient;
	private final DialogueAudioSynthesizer audioSynthesizer;
	private final StorageClient storageClient;
	private final ListeningLibrarySectionMapper sectionMapper;
	private final ListeningLibraryQuestionMapper questionMapper;
	private final ObjectMapper objectMapper = new ObjectMapper();
	private final String ttsLang;

	public LlmListeningLibraryGenerator(
			AiContentClient aiContentClient,
			DialogueAudioSynthesizer audioSynthesizer,
			StorageClient storageClient,
			ListeningLibrarySectionMapper sectionMapper,
			ListeningLibraryQuestionMapper questionMapper,
			@Value("${listening.tts.lang:en}") String ttsLang) {
		this.aiContentClient = aiContentClient;
		this.audioSynthesizer = audioSynthesizer;
		this.storageClient = storageClient;
		this.sectionMapper = sectionMapper;
		this.questionMapper = questionMapper;
		this.ttsLang = ttsLang;
	}

	/**
	 * Generates the passage+questions for {@code topic}, synthesizes and stores the passage's audio,
	 * persists the Section (with its audio key already set) and each Question, then returns the
	 * fully populated Section. Throws {@link AiContentException} if the LLM call/parse fails or
	 * returns no passage - callers should not retry-loop this synchronously in a request path.
	 */
	public ListeningLibrarySection generateSection(ListeningLibraryTopic topic) {
		String userPrompt = "Topic: %s\nLevel: %s".formatted(
				topic.getName(), topic.getLevel() == null || topic.getLevel().isBlank() ? "(unspecified)" : topic.getLevel());
		LlmPayload payload = aiContentClient.completeJson(SYSTEM_PROMPT, userPrompt, 0.6, 1200, LlmPayload.class);

		String passage = payload.passage == null ? "" : payload.passage.trim();
		if (passage.isBlank()) {
			throw new AiContentException("LLM returned no listening library passage for topic '" + topic.getName() + "'");
		}

		// Synthesize + store the audio before insert, since ListeningLibrarySectionMapper has no
		// update-storage-key method to call once an id exists (unlike the practice-item flow).
		String audioKey = synthesizeAndStoreAudio(topic.getId(), passage);

		ListeningLibrarySection section = ListeningLibrarySection.builder()
				.topicId(topic.getId())
				.passageText(passage)
				.audioStorageKey(audioKey)
				.build();
		sectionMapper.insert(section);

		for (LlmQuestion raw : nullToEmpty(payload.questions)) {
			ListeningLibraryQuestion question = ListeningLibraryQuestion.builder()
					.sectionId(section.getId())
					.questionText(raw.question)
					.optionsJson(writeOptionsJson(raw.options))
					.correctOption(raw.correctOption)
					.explanation(raw.explanation)
					.build();
			questionMapper.insert(question);
		}

		return section;
	}

	// Synthesizes the passage as a single-speaker monologue and writes it to storage under a key
	// addressed by topic id + a random suffix (the section id doesn't exist yet at this point).
	private String synthesizeAndStoreAudio(Long topicId, String passage) {
		SynthesizedDialogue synthesized = audioSynthesizer.synthesize(List.of(new DialogueLine(NARRATOR, passage, null)), ttsLang);
		String key = GENERATED_KEY.formatted(topicId, UUID.randomUUID());
		storageClient.write(key, new ByteArrayInputStream(synthesized.audioBytes()), synthesized.audioBytes().length);
		return key;
	}

	private String writeOptionsJson(List<String> options) {
		try {
			return objectMapper.writeValueAsString(options == null ? List.of() : options);
		} catch (JsonProcessingException ex) {
			throw new AiContentException("Failed to serialize listening library question options", ex);
		}
	}

	private static <T> List<T> nullToEmpty(List<T> list) {
		return list == null ? List.of() : list;
	}

	@Getter
	@Setter
	@JsonIgnoreProperties(ignoreUnknown = true)
	static class LlmPayload {
		private String passage;
		private List<LlmQuestion> questions;
	}

	@Getter
	@Setter
	@JsonIgnoreProperties(ignoreUnknown = true)
	static class LlmQuestion {
		private String question;
		private List<String> options;
		private String correctOption;
		private String explanation;
	}
}
