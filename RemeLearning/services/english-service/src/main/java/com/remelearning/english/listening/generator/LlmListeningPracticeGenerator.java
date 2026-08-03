package com.remelearning.english.listening.generator;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.remelearning.common.exception.BusinessException;
import com.remelearning.common.exception.ErrorCode;
import com.remelearning.english.learn.common.AiContentClient;
import com.remelearning.english.learn.common.AiContentException;
import com.remelearning.english.learn.common.DialogueLine;
import com.remelearning.english.listening.domain.ListeningQuestionItem;
import com.remelearning.english.listening.domain.ListeningQuestionType;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The only {@link ListeningPracticeGenerator}: this skill is AI-only, one Gemini call producing a
 * whole session of passages (each a monologue or dialogue naturally reusing the target keywords)
 * with their MCQ/keyword/open questions. Any LLM/parse failure is surfaced as a
 * {@link BusinessException} ({@code EXTERNAL_SERVICE_ERROR}) rather than masked by a static-template
 * fallback, so a misconfigured/unreachable LLM provider fails loudly instead of always returning the
 * same canned passage.
 *
 * <p>One call for the whole session rather than N calls: the caller persists all of them at once and
 * only synthesizes each passage's audio on first play, so a session must not cost N round trips.
 * Asking for all passages together is also what lets the prompt demand they differ from each other.
 *
 * <p>Variety is enforced explicitly, not left to sampling: the prompt carries the topics this
 * learner already practised (never reuse), a batch of randomly-drawn scenario hints, and a
 * requirement to alternate monologue/dialogue - without those, the same keyword set produced a
 * byte-for-byte identical prompt on every generation and Gemini kept returning the same passage.
 */
@Slf4j
@Component
public class LlmListeningPracticeGenerator implements ListeningPracticeGenerator {

	private static final String SYSTEM_PROMPT = """
			You are an English-listening coach building one practice session's passages for a learner.
			You're given a passage count N, a list of target words/phrases to reuse across the session
			(possibly empty - if empty, pick suitable topics yourself for the requested level), an
			optional CEFR level, exam style and translation language, a list of scenario hints, and a
			list of topics the learner already practised.
			Write EXACTLY N passages. Variety is a hard requirement:
			- every passage must cover a clearly DIFFERENT topic, setting and storyline - no two
			  passages in the batch may share a scenario, and none may reuse a topic the learner
			  already practised;
			- alternate the format: roughly half monologues (one speaker - an announcement, a voicemail,
			  a short talk) and half dialogues (2 speakers), never all of one kind;
			- spread the target words/phrases across the session instead of cramming every one of them
			  into every passage;
			- use the scenario hints as inspiration, at most one per passage, reworded - do not copy them
			  verbatim as topics.
			Each passage is 4 to 8 lines of natural spoken English, followed by exactly 5 comprehension
			questions: 2 MCQ (one testing the main idea, one testing a specific detail or the speaker's
			attitude), 2 KEYWORD (a specific word/phrase the learner must catch by ear), and 1 OPEN (a
			short free-response question about the passage). Respond with STRICTLY a raw JSON object
			(no markdown fences, no commentary) of the shape:
			{"passages": [
			  {"topic": "...",
			   "lines": [{"speaker": "A", "text": "...", "translation": "..." or null}],
			   "questions": [
			     {"type": "MCQ", "skill": "main-idea|detail|attitude", "prompt": "...", "options": ["...","...","...","..."], "answer": "...", "explanation": "..."},
			     {"type": "KEYWORD", "skill": "keyword", "prompt": "...", "options": null, "answer": "...", "explanation": "..."},
			     {"type": "OPEN", "skill": "open-response", "prompt": "...", "options": null, "answer": "model answer used only for grading", "explanation": "..."}
			   ]}
			]}
			- "passages" must have exactly N entries.
			- "topic": a short, specific label for that passage, distinct from every other passage's.
			- "lines[].speaker": a single stable label per speaker (e.g. "A"/"B" for a dialogue, or one
			  constant label for a monologue).
			- "lines[].translation": only fill in when a translation language was requested; otherwise null.
			- "questions[].answer" for MCQ must exactly match one of its own options.
			- "explanation" in Vietnamese; passage/question prompts/options in English.""";

	/**
	 * Everyday listening situations drawn from at random, a few per call, so two sessions built from
	 * the same keywords still start the model from different places. Deliberately broad and
	 * scenario-shaped rather than topic-shaped, since the passage's actual topic must still come from
	 * the learner's target keywords.
	 */
	private static final List<String> SCENARIO_HINTS = List.of(
			"an airport boarding announcement", "a job interview", "a doctor's appointment",
			"ordering at a restaurant", "a hotel check-in problem", "a university lecture excerpt",
			"a voicemail from a colleague", "a radio weather bulletin", "a podcast about a hobby",
			"a customer-service phone call", "a train delay announcement", "planning a weekend trip",
			"a team stand-up meeting", "a museum audio guide", "renting an apartment",
			"returning a faulty product", "signing up at a gym", "asking for directions in a new city",
			"a short news report", "negotiating a delivery date", "a parents' evening at school",
			"opening a bank account", "a flatmate discussing chores", "a supermarket loyalty-card offer");

	/** How many scenario hints to hand the model per passage requested. */
	private static final int HINTS_PER_PASSAGE = 2;
	/**
	 * Higher than the other generators' 0.6: this call must produce N passages that differ from each
	 * other and from previous sessions, which flatter sampling actively works against.
	 */
	private static final double TEMPERATURE = 0.9;

	private final AiContentClient aiContentClient;
	// Output-token budget per requested passage (4-8 lines, optional translations, 5 questions with
	// options and Vietnamese explanations), plus a fixed allowance for the JSON envelope.
	private final int maxOutputTokensPerPassage;
	private final int maxOutputTokensOverhead;

	public LlmListeningPracticeGenerator(
			AiContentClient aiContentClient,
			@Value("${listening.practice.max-output-tokens-per-passage:1300}") int maxOutputTokensPerPassage,
			@Value("${listening.practice.max-output-tokens-overhead:600}") int maxOutputTokensOverhead) {
		this.aiContentClient = aiContentClient;
		this.maxOutputTokensPerPassage = maxOutputTokensPerPassage;
		this.maxOutputTokensOverhead = maxOutputTokensOverhead;
	}

	@Override
	public List<GeneratedListeningPractice> generate(ListeningSessionRequest request) {
		int passageCount = Math.max(1, request.passageCount());
		try {
			LlmPayload payload = aiContentClient.completeJson(
					SYSTEM_PROMPT, buildUserPrompt(request, passageCount), TEMPERATURE,
					maxOutputTokensOverhead + passageCount * maxOutputTokensPerPassage, LlmPayload.class);
			List<GeneratedListeningPractice> passages = toResults(payload);
			if (passages.isEmpty()) {
				throw new AiContentException("LLM returned no usable listening passage");
			}
			if (passages.size() < passageCount) {
				log.warn("LLM returned {} of the {} requested listening passages", passages.size(), passageCount);
			}
			return passages;
		} catch (AiContentException ex) {
			log.error("LLM listening session generation failed", ex);
			throw new BusinessException(
					ErrorCode.EXTERNAL_SERVICE_ERROR, "Không tạo được bài luyện nghe: " + ex.getMessage(),
					HttpStatus.BAD_GATEWAY);
		}
	}

	// The per-call varying half of the prompt: what to reuse, what to avoid, and a fresh draw of
	// scenario hints. Every "(none)"/"(unspecified)" placeholder is spelled out rather than omitted
	// so the model never has to guess whether a missing field means "any" or "skip".
	private String buildUserPrompt(ListeningSessionRequest request, int passageCount) {
		List<String> keywords = nullToEmpty(request.targetKeywords());
		List<String> avoidTopics = nullToEmpty(request.avoidTopics());
		return """
				Passage count: %d
				Target keywords: %s
				Level: %s
				Exam style: %s
				Translation language: %s
				Scenario hints: %s
				Topics already practised (do NOT reuse any of these): %s""".formatted(
				passageCount,
				keywords.isEmpty() ? "(none - please choose suitable topics yourself)" : keywords,
				blankToUnspecified(request.level()),
				blankToUnspecified(request.examType()),
				request.translationLang() == null || request.translationLang().equalsIgnoreCase("en")
						? "(none)" : request.translationLang(),
				drawScenarioHints(passageCount),
				avoidTopics.isEmpty() ? "(none yet)" : avoidTopics);
	}

	// Draws hints without repeats (the pool is far larger than any session), so the model gets a
	// different starting point on each call even for an unchanged keyword set.
	private List<String> drawScenarioHints(int passageCount) {
		List<String> pool = new ArrayList<>(SCENARIO_HINTS);
		Collections.shuffle(pool, ThreadLocalRandom.current());
		return pool.subList(0, Math.min(pool.size(), passageCount * HINTS_PER_PASSAGE));
	}

	// Maps the payload into domain results, skipping any passage the model returned without lines or
	// questions rather than letting an unplayable/unanswerable item reach the learner.
	private List<GeneratedListeningPractice> toResults(LlmPayload payload) {
		List<GeneratedListeningPractice> results = new ArrayList<>();
		for (LlmPassage raw : nullToEmpty(payload.passages)) {
			GeneratedListeningPractice passage = toResult(raw);
			if (passage.lines().isEmpty() || passage.questions().isEmpty()) {
				log.warn("Skipping incomplete LLM listening passage (topic='{}')", raw.topic);
				continue;
			}
			results.add(passage);
		}
		return results;
	}

	private GeneratedListeningPractice toResult(LlmPassage payload) {
		List<DialogueLine> lines = new ArrayList<>();
		for (LlmLine raw : nullToEmpty(payload.lines)) {
			lines.add(new DialogueLine(raw.speaker, raw.text, raw.translation));
		}
		List<ListeningQuestionItem> questions = new ArrayList<>();
		for (LlmQuestion raw : nullToEmpty(payload.questions)) {
			questions.add(ListeningQuestionItem.builder()
					.type(parseType(raw.type))
					.skill(raw.skill)
					.prompt(raw.prompt)
					.options(raw.options)
					.answer(raw.answer)
					.explanation(raw.explanation)
					.build());
		}
		return new GeneratedListeningPractice(payload.topic, lines, questions);
	}

	private ListeningQuestionType parseType(String raw) {
		if (raw == null) {
			return ListeningQuestionType.MCQ;
		}
		try {
			return ListeningQuestionType.valueOf(raw.trim().toUpperCase());
		} catch (IllegalArgumentException ex) {
			log.warn("Unrecognized listening question type '{}', defaulting to MCQ", raw);
			return ListeningQuestionType.MCQ;
		}
	}

	private static String blankToUnspecified(String value) {
		return value == null || value.isBlank() ? "(unspecified)" : value;
	}

	private static <T> List<T> nullToEmpty(List<T> list) {
		return list == null ? List.of() : list;
	}

	// Package-private (not private) so the unit test can name the type in its completeJson stub, the
	// same arrangement LlmListeningLibraryGenerator uses.
	@Getter
	@Setter
	@JsonIgnoreProperties(ignoreUnknown = true)
	static class LlmPayload {
		private List<LlmPassage> passages;
	}

	@Getter
	@Setter
	@JsonIgnoreProperties(ignoreUnknown = true)
	static class LlmPassage {
		private String topic;
		private List<LlmLine> lines;
		private List<LlmQuestion> questions;
	}

	@Getter
	@Setter
	@JsonIgnoreProperties(ignoreUnknown = true)
	static class LlmLine {
		private String speaker;
		private String text;
		private String translation;
	}

	@Getter
	@Setter
	@JsonIgnoreProperties(ignoreUnknown = true)
	static class LlmQuestion {
		private String type;
		private String skill;
		private String prompt;
		private List<String> options;
		private String answer;
		private String explanation;
	}
}
