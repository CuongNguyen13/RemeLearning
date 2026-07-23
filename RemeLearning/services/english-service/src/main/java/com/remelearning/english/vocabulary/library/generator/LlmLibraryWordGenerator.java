package com.remelearning.english.vocabulary.library.generator;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.remelearning.english.learn.common.AiContentClient;
import com.remelearning.english.learn.common.AiContentException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * The only {@link LibraryWordGenerator}: calls Gemini (via {@link AiContentClient}) for a batch of
 * new topic words. No distractor generation here - MCQ/MATCHING distractors are sampled at query
 * time from sibling words in the same topic (see {@code SectionCardBuilder}/{@code
 * VocabularyLibraryWordMapper#findRandomByTopicIdExcluding}), so the prompt only needs to produce
 * the word itself, its type, its Vietnamese meaning, and one example sentence that contains it
 * verbatim (so it can be blanked out later for CLOZE/MCQ).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LlmLibraryWordGenerator implements LibraryWordGenerator {

	private static final String SYSTEM_PROMPT = """
			You are an English-vocabulary content writer building a topic word bank for learners. Given
			a topic name and a list of words already in the bank (to avoid repeating), produce the
			requested number of NEW English words/short phrases for that topic, each natural and useful
			for an intermediate learner. Respond with STRICTLY a raw JSON object (no markdown fences, no
			commentary) of the shape:
			{"words": [{"word": "...", "wordType": "NOUN|VERB|ADJECTIVE|ADVERB|PHRASAL_VERB|COLLOCATION|IDIOM|OTHER",
			"ipa": "...", "meaningVi": "...", "exampleEn": "..."}]}
			- "word": the target English word or short phrase, lowercase unless a proper noun.
			- "ipa": the word's IPA phonetic transcription (General American or RP), without surrounding
			  slashes, e.g. "rɪˈvɛnjuː" for "revenue" - required, never omit it.
			- "exampleEn": one natural English sentence (6-16 words) that uses "word" verbatim (same
			  casing/word-form) so it can be blanked out later - this is required, never omit it.
			- "meaningVi": the Vietnamese meaning, one short phrase.
			- Never repeat any word already in the bank.""";

	private final AiContentClient aiContentClient;

	@Override
	public List<GeneratedLibraryWord> generate(String topicName, List<String> existingWords, int count) {
		try {
			String userPrompt = "Topic: %s\nWords already in the bank: %s\nGenerate %d new words.".formatted(
					topicName, existingWords.isEmpty() ? "(none)" : existingWords, count);
			LlmPayload payload = aiContentClient.completeJson(SYSTEM_PROMPT, userPrompt, 0.6, 1500, LlmPayload.class);
			List<GeneratedLibraryWord> result = toResult(payload);
			if (result.isEmpty()) {
				throw new AiContentException("LLM returned no library words");
			}
			return result;
		} catch (AiContentException ex) {
			log.warn("LLM library word generation failed for topic '{}', returning no new words", topicName, ex);
			return List.of();
		}
	}

	private List<GeneratedLibraryWord> toResult(LlmPayload payload) {
		if (payload.words == null) {
			return List.of();
		}
		List<GeneratedLibraryWord> result = new ArrayList<>();
		for (LlmItem item : payload.words) {
			result.add(new GeneratedLibraryWord(item.word, item.wordType, item.ipa, item.meaningVi, item.exampleEn));
		}
		return result;
	}

	@Getter
	@Setter
	@JsonIgnoreProperties(ignoreUnknown = true)
	static class LlmPayload {
		private List<LlmItem> words;
	}

	@Getter
	@Setter
	@JsonIgnoreProperties(ignoreUnknown = true)
	static class LlmItem {
		private String word;
		private String wordType;
		private String ipa;
		private String meaningVi;
		private String exampleEn;
	}
}
