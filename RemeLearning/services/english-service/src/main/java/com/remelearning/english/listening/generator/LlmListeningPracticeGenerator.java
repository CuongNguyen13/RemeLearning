package com.remelearning.english.listening.generator;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.remelearning.english.learn.common.AiContentClient;
import com.remelearning.english.learn.common.AiContentException;
import com.remelearning.english.learn.common.DialogueLine;
import com.remelearning.english.listening.domain.ListeningQuestionItem;
import com.remelearning.english.listening.domain.ListeningQuestionType;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * The only {@link ListeningPracticeGenerator}: this skill is AI-only, one Gemini call producing
 * both the passage (monologue or dialogue lines, naturally reusing the target keywords) and its
 * MCQ/keyword/open questions - a static-template fallback covers any LLM/parse failure so
 * generating a passage never breaks (the fallback still needs synthesizing by the caller, exactly
 * like the normal path).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LlmListeningPracticeGenerator implements ListeningPracticeGenerator {

	private static final String SYSTEM_PROMPT = """
			You are an English-listening coach building a short comprehension passage for a learner.
			You're given a list of target words/phrases to naturally reuse (possibly empty - if empty,
			pick a suitable topic yourself for the requested level) plus an optional CEFR level, exam
			style, and translation language. Write ONE passage - either a monologue (a single speaker,
			e.g. an announcement or short talk) or a short dialogue (2 speakers) - 4 to 8 lines, natural
			spoken English. Then write exactly 5 comprehension questions: 2 MCQ (one testing the main
			idea, one testing a specific detail or the speaker's attitude), 2 KEYWORD (a specific word/
			phrase the learner must catch by ear), and 1 OPEN (a short free-response question about the
			passage). Respond with STRICTLY a raw JSON object (no markdown fences, no commentary) of the
			shape:
			{"topic": "...",
			 "lines": [{"speaker": "A", "text": "...", "translation": "..." or null}],
			 "questions": [
			   {"type": "MCQ", "skill": "main-idea|detail|attitude", "prompt": "...", "options": ["...","...","...","..."], "answer": "...", "explanation": "..."},
			   {"type": "KEYWORD", "skill": "keyword", "prompt": "...", "options": null, "answer": "...", "explanation": "..."},
			   {"type": "OPEN", "skill": "open-response", "prompt": "...", "options": null, "answer": "model answer used only for grading", "explanation": "..."}
			 ]}
			- "lines[].speaker": a single stable label per speaker (e.g. "A"/"B" for a dialogue, or one
			  constant label for a monologue).
			- "lines[].translation": only fill in when a translation language was requested; otherwise null.
			- "questions[].answer" for MCQ must exactly match one of its own options.
			- "explanation" in Vietnamese; passage/question prompts/options in English.""";

	private final AiContentClient aiContentClient;

	@Override
	public GeneratedListeningPractice generate(
			List<String> targetKeywords, String level, String examType, String translationLang) {
		try {
			String userPrompt = "Target keywords: %s\nLevel: %s\nExam style: %s\nTranslation language: %s".formatted(
					targetKeywords.isEmpty() ? "(none - please choose a suitable topic yourself)" : targetKeywords,
					level == null ? "(unspecified)" : level,
					examType == null ? "(unspecified)" : examType,
					translationLang == null || translationLang.equalsIgnoreCase("en") ? "(none)" : translationLang);
			LlmPayload payload = aiContentClient.completeJson(SYSTEM_PROMPT, userPrompt, 0.6, 1600, LlmPayload.class);
			GeneratedListeningPractice result = toResult(payload);
			if (result.lines().isEmpty() || result.questions().isEmpty()) {
				throw new AiContentException("LLM returned an incomplete listening passage");
			}
			return result;
		} catch (AiContentException ex) {
			log.warn("LLM listening practice generation failed, falling back to a template", ex);
			return fallback(level);
		}
	}

	private GeneratedListeningPractice toResult(LlmPayload payload) {
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

	// A tiny, fixed template passage + one MCQ question, so generation never fails even with the
	// LLM unreachable. The caller still runs this through TTS exactly like the normal path.
	private GeneratedListeningPractice fallback(String level) {
		List<DialogueLine> lines = List.of(new DialogueLine("A",
				"Welcome aboard flight 204 to London. We will be departing shortly.", null));
		List<ListeningQuestionItem> questions = List.of(ListeningQuestionItem.builder()
				.type(ListeningQuestionType.MCQ)
				.skill("main-idea")
				.prompt("What is this announcement about?")
				.options(List.of("A flight departure", "A weather forecast", "A train delay", "A hotel booking"))
				.answer("A flight departure")
				.explanation("Thông báo nói về chuyến bay sắp khởi hành.")
				.build());
		return new GeneratedListeningPractice(level == null ? "Listening practice" : level + " listening practice", lines, questions);
	}

	private static <T> List<T> nullToEmpty(List<T> list) {
		return list == null ? List.of() : list;
	}

	@Getter
	@Setter
	@JsonIgnoreProperties(ignoreUnknown = true)
	private static class LlmPayload {
		private String topic;
		private List<LlmLine> lines;
		private List<LlmQuestion> questions;
	}

	@Getter
	@Setter
	@JsonIgnoreProperties(ignoreUnknown = true)
	private static class LlmLine {
		private String speaker;
		private String text;
		private String translation;
	}

	@Getter
	@Setter
	@JsonIgnoreProperties(ignoreUnknown = true)
	private static class LlmQuestion {
		private String type;
		private String skill;
		private String prompt;
		private List<String> options;
		private String answer;
		private String explanation;
	}
}
