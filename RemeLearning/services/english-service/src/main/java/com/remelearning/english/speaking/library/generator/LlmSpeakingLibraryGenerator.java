package com.remelearning.english.speaking.library.generator;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.remelearning.common.storage.StorageClient;
import com.remelearning.english.learn.common.AiContentClient;
import com.remelearning.english.learn.common.AiContentException;
import com.remelearning.english.learn.common.DialogueAudioSynthesizer;
import com.remelearning.english.learn.common.DialogueLine;
import com.remelearning.english.learn.common.SynthesizedDialogue;
import com.remelearning.english.speaking.library.domain.SpeakingLibrarySection;
import com.remelearning.english.speaking.library.domain.SpeakingLibrarySentence;
import com.remelearning.english.speaking.library.domain.SpeakingLibraryTopic;
import com.remelearning.english.speaking.library.mapper.SpeakingLibrarySectionMapper;
import com.remelearning.english.speaking.library.mapper.SpeakingLibrarySentenceMapper;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.UUID;

/**
 * Generates one Speaking-library Section (a pool of sample sentences to practice reading aloud) for
 * a topic: one Gemini call for the sentences+IPA (via {@link AiContentClient}, same plumbing
 * {@code LlmListeningLibraryGenerator} uses), then Supertonic-synthesizes each sentence individually
 * as a single-speaker monologue (via {@link DialogueAudioSynthesizer}) - unlike listening's one
 * passage-level audio clip, speaking needs one sample recording *per sentence* so a learner can mimic
 * each one on its own. The Section row itself carries no content (just {@code topicId}), so it is
 * inserted first and each sentence's own {@code sampleAudioStorageKey} is set before that sentence's
 * insert - simpler than listening's ordering constraint, since {@code SpeakingLibrarySectionMapper}
 * has no columns that depend on the generated content.
 *
 * <p>Like {@code LlmListeningLibraryGenerator}, this is a content-authoring/seeding generator, not a
 * learner-facing request: any LLM/parse failure is left to propagate as {@link AiContentException} so
 * a failed generation simply doesn't create a Section, rather than persisting bad content.
 */
@Slf4j
@Component
public class LlmSpeakingLibraryGenerator {

	private static final String SYSTEM_PROMPT = """
			You are an English-speaking content writer building a reusable library section for
			learners practicing pronunciation. Given a topic name and a CEFR level, write exactly 5
			short English sentences (natural spoken English, one sentence each) a learner should
			practice reading aloud about the topic, each with its IPA transcription. Respond with
			STRICTLY a raw JSON object (no markdown fences, no commentary) of the shape:
			{"sentences": [{"text": "...", "ipa": "..."}]}
			- Exactly 5 entries in "sentences".
			- "text" in English; "ipa" is the IPA transcription of "text".""";

	// Single stable speaker label for each sentence's monologue, matching DialogueAudioSynthesizer's
	// single-voice behavior when only one distinct speaker is present.
	private static final String NARRATOR = "Narrator";
	private static final String GENERATED_KEY = "speaking-library/%d/%s.wav";

	private final AiContentClient aiContentClient;
	private final DialogueAudioSynthesizer audioSynthesizer;
	private final StorageClient storageClient;
	private final SpeakingLibrarySectionMapper sectionMapper;
	private final SpeakingLibrarySentenceMapper sentenceMapper;
	private final String ttsLang;

	public LlmSpeakingLibraryGenerator(
			AiContentClient aiContentClient,
			DialogueAudioSynthesizer audioSynthesizer,
			StorageClient storageClient,
			SpeakingLibrarySectionMapper sectionMapper,
			SpeakingLibrarySentenceMapper sentenceMapper,
			@Value("${speaking.tts.lang:en}") String ttsLang) {
		this.aiContentClient = aiContentClient;
		this.audioSynthesizer = audioSynthesizer;
		this.storageClient = storageClient;
		this.sectionMapper = sectionMapper;
		this.sentenceMapper = sentenceMapper;
		this.ttsLang = ttsLang;
	}

	/**
	 * Generates the sample-sentence pool for {@code topic}, synthesizes and stores each sentence's own
	 * audio, persists the Section then each Sentence, and returns the fully populated Section. Throws
	 * {@link AiContentException} if the LLM call/parse fails or returns no sentences - callers should
	 * not retry-loop this synchronously in a request path.
	 */
	public SpeakingLibrarySection generateSection(SpeakingLibraryTopic topic) {
		String userPrompt = "Topic: %s\nLevel: %s".formatted(
				topic.getName(), topic.getLevel() == null || topic.getLevel().isBlank() ? "(unspecified)" : topic.getLevel());
		LlmPayload payload = aiContentClient.completeJson(SYSTEM_PROMPT, userPrompt, 0.6, 1200, LlmPayload.class);

		List<LlmSentence> rawSentences = nullToEmpty(payload.sentences);
		if (rawSentences.isEmpty()) {
			throw new AiContentException("LLM returned no speaking library sentences for topic '" + topic.getName() + "'");
		}

		SpeakingLibrarySection section = SpeakingLibrarySection.builder()
				.topicId(topic.getId())
				.build();
		sectionMapper.insert(section);

		for (LlmSentence raw : rawSentences) {
			String text = raw.text == null ? "" : raw.text.trim();
			if (text.isBlank()) {
				continue;
			}
			// Synthesized + stored before insert since the sentence's own row carries its audio key
			// (unlike listening's section-level audio, no separate update-key call is needed here).
			String audioKey = synthesizeAndStoreAudio(topic.getId(), text);
			SpeakingLibrarySentence sentence = SpeakingLibrarySentence.builder()
					.sectionId(section.getId())
					.sentenceText(text)
					.ipa(raw.ipa)
					.sampleAudioStorageKey(audioKey)
					.build();
			sentenceMapper.insert(sentence);
		}

		return section;
	}

	// Synthesizes one sentence as a single-speaker monologue and writes it to storage under a key
	// addressed by topic id + a random suffix per sentence, so every sentence gets its own sample clip.
	private String synthesizeAndStoreAudio(Long topicId, String sentenceText) {
		SynthesizedDialogue synthesized = audioSynthesizer.synthesize(List.of(new DialogueLine(NARRATOR, sentenceText, null)), ttsLang);
		String key = GENERATED_KEY.formatted(topicId, UUID.randomUUID());
		storageClient.write(key, new ByteArrayInputStream(synthesized.audioBytes()), synthesized.audioBytes().length);
		return key;
	}

	private static <T> List<T> nullToEmpty(List<T> list) {
		return list == null ? List.of() : list;
	}

	@Getter
	@Setter
	@JsonIgnoreProperties(ignoreUnknown = true)
	static class LlmPayload {
		private List<LlmSentence> sentences;
	}

	@Getter
	@Setter
	@JsonIgnoreProperties(ignoreUnknown = true)
	static class LlmSentence {
		private String text;
		private String ipa;
	}
}
