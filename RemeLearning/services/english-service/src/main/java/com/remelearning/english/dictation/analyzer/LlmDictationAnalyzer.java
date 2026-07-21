package com.remelearning.english.dictation.analyzer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.remelearning.common.ai.LlmClient;
import com.remelearning.common.ai.LlmRequest;
import com.remelearning.common.ai.LlmResponse;
import com.remelearning.english.dictation.dto.WordDiffDto;
import com.remelearning.english.dictation.dto.WordDiffTag;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * LLM-backed {@link DictationAnalyzer} using whichever {@link LlmClient} is configured (Gemini
 * today), acting as a listening coach that classifies each mistake by root cause (vocabulary,
 * grammar, or connected-speech phonology) instead of giving generic tips. Enabled with
 * {@code dictation.analyzer.mode=llm}; otherwise {@link RuleBasedDictationAnalyzer} stays active.
 * Every call falls back to the static templates on any LLM/parse failure, honoring the interface's
 * never-throw contract.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "dictation.analyzer", name = "mode", havingValue = "llm")
public class LlmDictationAnalyzer implements DictationAnalyzer {

	private static final String ANALYZE_SYSTEM_PROMPT = """
			You are an expert linguist and English-listening coach. A learner did a dictation
			(listen-and-type) exercise. You're given the reference text, what the learner actually typed,
			and a list of the word-level mismatches (expected -> transcribed). Group nearby mismatches into
			the phrase they belong to, then classify the ROOT CAUSE of each mistake and give concrete
			advice. Respond with STRICTLY a raw JSON object (no markdown fences, no commentary) of the shape:
			{"errorTable": [{"original": "...", "transcribed": "...", "category": "LEXICON|GRAMMAR|PHONOLOGY", "note": "..."}],
			 "rootCauses": [{"category": "LEXICON|GRAMMAR|PHONOLOGY", "summary": "...", "examples": ["..."]}],
			 "actionAdvice": ["...", "..."],
			 "practiceSentences": ["...", "..."]}
			- "category": LEXICON for homophones/unknown collocations/idioms/slang; GRAMMAR for dropped
			  inflections (s/ed/ing), wrong tense, or structures the learner doesn't recognize; PHONOLOGY
			  for elision, linking, assimilation, or weak forms of function words in natural speech.
			- "rootCauses": one entry per category that ACTUALLY occurred in errorTable - omit categories
			  with no mistakes.
			- "errorTable"/"rootCauses"/"summary"/"note"/"actionAdvice" in Vietnamese; "practiceSentences"
			  in English (3-5 short natural sentences, 6-14 words, each reusing a missed word/phrase).""";

	private static final String PRACTICE_SYSTEM_PROMPT = """
			You are an English-listening coach. Given a learner's recurring hard-to-hear words, write
			5 short, natural English sentences (6-14 words) that each reuse one or more of them, for a
			listen-and-type practice. Respond with STRICTLY a raw JSON array of strings, no markdown
			fences, no commentary.""";

	private static final ObjectMapper MAPPER = new ObjectMapper();

	private final LlmClient llmClient;

	// Asks the LLM to classify the diff's mismatches by root cause and give advice; any failure/empty
	// parse falls back to the rule-based heuristic so grading an attempt never breaks.
	@Override
	public DictationAnalysis analyzeAttempt(String referenceText, String userTranscript, List<WordDiffDto> diff) {
		try {
			LlmRequest request = LlmRequest.builder()
					.systemPrompt(ANALYZE_SYSTEM_PROMPT)
					.userPrompt("Reference text:\n%s\n\nLearner's transcript:\n%s\n\nWord mismatches: %s"
							.formatted(referenceText, userTranscript, describeMismatches(diff)))
					.temperature(0.4)
					.maxOutputTokens(700)
					.build();
			LlmResponse response = llmClient.complete(request);
			LlmAnalysisPayload payload = MAPPER.readValue(stripCodeFences(response.getContent()), LlmAnalysisPayload.class);
			DictationAnalysis analysis = toAnalysis(payload);
			if (analysis.getErrorTable().isEmpty() && analysis.getActionAdvice().isEmpty()) {
				throw new IllegalStateException("LLM returned an empty analysis");
			}
			return analysis;
		} catch (JsonProcessingException | IllegalStateException | RestClientException ex) {
			log.warn("LLM dictation analysis failed, falling back to rule-based heuristic", ex);
			return new RuleBasedDictationAnalyzer().analyzeAttempt(referenceText, userTranscript, diff);
		}
	}

	// Asks the LLM for a JSON array of practice sentences; falls back to templates on any failure.
	@Override
	public List<String> generatePracticeSentences(List<String> missedWords) {
		LlmRequest request = LlmRequest.builder()
				.systemPrompt(PRACTICE_SYSTEM_PROMPT)
				.userPrompt("Hard-to-hear words: " + missedWords)
				.temperature(0.5)
				.maxOutputTokens(400)
				.build();
		try {
			LlmResponse response = llmClient.complete(request);
			List<String> sentences = MAPPER.readValue(stripCodeFences(response.getContent()), new TypeReference<List<String>>() { });
			if (sentences == null || sentences.isEmpty()) {
				throw new IllegalStateException("LLM returned no practice sentences");
			}
			return sentences;
		} catch (JsonProcessingException | IllegalStateException | RestClientException ex) {
			log.warn("LLM practice-sentence generation failed, falling back to templates", ex);
			return DictationAnalysisTemplates.practiceSentencesFor(missedWords);
		}
	}

	// Renders the wrong diff slots as a compact "expected -> actual" list, giving the LLM a positional
	// hint without forcing it to classify strictly per-word (it groups them into phrases itself).
	private static String describeMismatches(List<WordDiffDto> diff) {
		List<String> parts = new ArrayList<>();
		for (WordDiffDto slot : diff) {
			if (slot.getTag() == WordDiffTag.MISSING || slot.getTag() == WordDiffTag.SUBSTITUTED) {
				parts.add("%s -> %s".formatted(slot.getExpectedWord(), slot.getActualWord()));
			}
		}
		return parts.isEmpty() ? "(none)" : String.join(", ", parts);
	}

	private static DictationAnalysis toAnalysis(LlmAnalysisPayload payload) {
		List<DictationErrorEntry> errorTable = new ArrayList<>();
		for (LlmErrorEntry entry : nullToEmpty(payload.errorTable)) {
			errorTable.add(DictationErrorEntry.builder()
					.original(entry.original)
					.transcribed(entry.transcribed)
					.category(parseCategory(entry.category))
					.note(entry.note)
					.build());
		}
		List<DictationRootCauseGroup> rootCauses = new ArrayList<>();
		for (LlmRootCause cause : nullToEmpty(payload.rootCauses)) {
			rootCauses.add(DictationRootCauseGroup.builder()
					.category(parseCategory(cause.category))
					.summary(cause.summary)
					.examples(nullToEmpty(cause.examples))
					.build());
		}
		return DictationAnalysis.builder()
				.errorTable(errorTable)
				.rootCauses(rootCauses)
				.actionAdvice(nullToEmpty(payload.actionAdvice))
				.practiceSentences(nullToEmpty(payload.practiceSentences))
				.build();
	}

	// Unrecognized/missing category text degrades to LEXICON rather than failing the whole analysis.
	private static DictationErrorCategory parseCategory(String raw) {
		if (raw == null) {
			return DictationErrorCategory.LEXICON;
		}
		try {
			return DictationErrorCategory.valueOf(raw.trim().toUpperCase());
		} catch (IllegalArgumentException ex) {
			log.warn("Unrecognized dictation error category '{}', defaulting to LEXICON", raw);
			return DictationErrorCategory.LEXICON;
		}
	}

	private static <T> List<T> nullToEmpty(List<T> list) {
		return list == null ? List.of() : list;
	}

	// Gemini occasionally wraps JSON in a ```json ... ``` fence despite being asked not to; strip it.
	private static String stripCodeFences(String content) {
		String trimmed = content.trim();
		if (trimmed.startsWith("```")) {
			trimmed = trimmed.substring(trimmed.indexOf('\n') + 1);
			int lastFence = trimmed.lastIndexOf("```");
			if (lastFence >= 0) {
				trimmed = trimmed.substring(0, lastFence);
			}
		}
		return trimmed.trim();
	}

	// Raw JSON shape the LLM is asked for; kept separate from the domain DictationAnalysis so Jackson
	// annotations don't leak into it.
	@Getter
	@Setter
	@JsonIgnoreProperties(ignoreUnknown = true)
	private static class LlmAnalysisPayload {
		private List<LlmErrorEntry> errorTable;
		private List<LlmRootCause> rootCauses;
		private List<String> actionAdvice;
		private List<String> practiceSentences;
	}

	@Getter
	@Setter
	@JsonIgnoreProperties(ignoreUnknown = true)
	private static class LlmErrorEntry {
		private String original;
		private String transcribed;
		private String category;
		private String note;
	}

	@Getter
	@Setter
	@JsonIgnoreProperties(ignoreUnknown = true)
	private static class LlmRootCause {
		private String category;
		private String summary;
		private List<String> examples;
	}
}
