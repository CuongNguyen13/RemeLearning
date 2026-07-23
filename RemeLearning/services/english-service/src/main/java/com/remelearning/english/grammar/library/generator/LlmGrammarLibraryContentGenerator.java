package com.remelearning.english.grammar.library.generator;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.remelearning.english.grammar.learn.domain.GrammarQuestionType;
import com.remelearning.english.grammar.library.domain.GrammarLibraryExample;
import com.remelearning.english.learn.common.AiContentClient;
import com.remelearning.english.learn.common.AiContentException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The only {@link GrammarLibraryContentGenerator}: same "AI-only, static-template fallback on
 * failure" shape as {@code LlmGrammarPracticeGenerator} (grammar.learn), so a topic's theory page
 * or a retry question always gets produced even with the LLM unreachable.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LlmGrammarLibraryContentGenerator implements GrammarLibraryContentGenerator {

	private static final String TOPIC_CONTENT_SYSTEM_PROMPT = """
			You are an English-grammar teacher building a self-study "theory + practice" page for one
			grammar topic. Respond with STRICTLY a raw JSON object (no markdown fences, no commentary) of
			the shape:
			{"explanationEn": "...", "explanationVi": "...", "illustrationText": "...",
			 "examples": [{"en": "...", "vi": "..."}, ...],
			 "questions": [{"type": "ERROR_CORRECTION|FILL_TENSE|TRANSFORM|MCQ", "prompt": "...",
			 "options": ["...", "..."] or null, "answer": "...", "explanationVi": "...",
			 "translationVi": "..."}]}
			- "explanationEn": a VERY DETAILED, thorough English explanation of the rule, covering every
			  case. It is rendered as MARKDOWN on the frontend, so use markdown formatting for emphasis:
			  wrap the section label AND every key term, formula, keyword and signal word in **double
			  asterisks** to bold it, and use "- " bullet lists where you enumerate items. Do NOT use
			  markdown headers (#) — only bold, bullet lists and blank-line-separated paragraphs. Put each
			  labeled section below on its own paragraph, separated by a blank line, in this exact order:
			  "**Usage:**" enumerate ALL of this topic's use cases as a bullet list (e.g. for a tense:
			  habits, routines, general truths, permanent states, scheduled/timetabled events, etc. — one
			  bullet per case, list every case that actually applies to this specific topic, and give a
			  short example sentence for each bullet).
			  "**Affirmative form:**" the positive sentence formula in bold (e.g. **S + V(s/es) + O**), the
			  subject-verb agreement rule, and at least one full example sentence.
			  "**Negative form:**" how to negate it, formula in bold, plus an example sentence.
			  "**Question form:**" both the Yes/No question and the Wh-question structures, each formula in
			  bold, plus an example of each.
			  "**Signal words:**" the time expressions/markers that cue this rule as a bullet list, each
			  marker bolded (e.g. **every day**, **always**, **usually**, **often**, **never**) — omit
			  this section only if the topic genuinely has no such markers.
			  "**Notes:**" a bullet list of spelling rules (e.g. -s/-es/-ies endings), irregular forms, and
			  the most common mistakes learners make with this rule (state the wrong form vs the correct
			  form for each mistake).
			  Each section is 3-6 sentences (Usage/Signal words/Notes may be longer as bullet lists); the
			  whole field is roughly 25-40 sentences total — err on the side of MORE detail.
			- "explanationVi": the Vietnamese translation of explanationEn, same section order, same bold
			  emphasis and bullet lists, with these exact bolded Vietnamese section labels: "**Cách
			  dùng:**", "**Câu khẳng định:**", "**Câu phủ định:**", "**Câu nghi vấn:**", "**Dấu hiệu nhận
			  biết:**", "**Lưu ý:**".
			- "illustrationText": a DETAILED mermaid diagram (fenced with ```mermaid) that visualizes the
			  sentence structure for ALL THREE forms — affirmative, negative and question — with a
			  subgraph per form, each showing the ordered building blocks of the formula (e.g. S, V, O)
			  AND one concrete example sentence broken into the same blocks underneath, so the learner can
			  map the formula onto a real sentence. Use `flowchart LR` (or `TD`). It MUST be syntactically
			  valid mermaid: wrap every node label that contains parentheses, slashes, plus signs or other
			  special characters in double quotes, e.g. A["V(s/es)"] rather than A[V(s/es)] — an unquoted
			  label like that is invalid mermaid syntax and will fail to render.
			- "examples": 5-6 example sentences in English, each with its Vietnamese meaning; cover the
			  affirmative, negative and question forms across the set.
			- "questions": EXACTLY 8 to 10 practice items, each of a randomly varied type (mix
			  ERROR_CORRECTION/FILL_TENSE/TRANSFORM/MCQ). Same field meaning as grammar practice
			  generation: "ERROR_CORRECTION" prompt is one sentence with a grammar mistake, options null,
			  answer is the corrected sentence; "FILL_TENSE" prompt has the verb in brackets, options
			  null, answer is the complete correct sentence; "TRANSFORM" prompt is the original English
			  sentence followed by the transformation instruction written IN VIETNAMESE inside parentheses
			  (the English sentence stays English, only the instruction is Vietnamese, e.g. "He watches TV
			  every evening. (Chuyển sang thì hiện tại tiếp diễn)"), options null, answer is the transformed
			  sentence; "MCQ" prompt has a blank, options is 4 candidate structures, answer matches one
			  option exactly. "explanationVi" on each question is a short Vietnamese explanation of why
			  the answer is correct. "translationVi" on each question is the plain Vietnamese translation
			  of the full correct sentence in "answer" (not a grammar explanation - an actual meaning
			  translation, e.g. answer "The climate is changing." -> translationVi "Khí hậu đang thay
			  đổi.").
			- Keep sentences natural, 6-16 words, appropriate for the given level.""";

	private static final String RETRY_QUESTION_SYSTEM_PROMPT = """
			You are an English-grammar teacher generating ONE replacement practice question for a
			learner who got a previous question on the same rule wrong. Respond with STRICTLY a raw JSON
			object (no markdown fences, no commentary) of the shape:
			{"prompt": "...", "options": ["...", "..."] or null, "answer": "...", "explanationVi": "...",
			"translationVi": "..."}
			The question must be of the exact type requested, target the same grammar rule, and must NOT
			repeat the sentence the learner already saw. Keep it natural, 6-16 words. For a TRANSFORM
			question, the "prompt" is the original English sentence followed by the transformation
			instruction written IN VIETNAMESE inside parentheses - the English sentence stays English,
			only the instruction is Vietnamese (e.g. "He watches TV every evening. (Chuyển sang thì hiện
			tại tiếp diễn)"). "translationVi" is the plain Vietnamese translation of the full correct
			sentence in "answer" (an actual meaning translation, not a grammar explanation).""";

	// Matches an unquoted mermaid node label (e.g. "V(s/es)" inside A[V(s/es)]) containing a
	// character that breaks mermaid's flowchart node-label grammar if left unquoted.
	private static final Pattern UNQUOTED_SPECIAL_LABEL =
			Pattern.compile("\\[(?!\")([^\\[\\]\"]*[()/+][^\\[\\]\"]*)]");

	private final AiContentClient aiContentClient;

	// Generates the full theory page + question pool in one LLM call; falls back to a minimal
	// static page (still usable, just less rich) so a topic never fails to load.
	@Override
	public GeneratedGrammarTopicContent generateTopicContent(String topicName, String level) {
		try {
			String userPrompt = "Grammar topic: %s\nLevel: %s".formatted(topicName, level == null ? "(unspecified)" : level);
			// Bumped to 5200: the explanation is now VERY detailed (25-40 sentences per language, with
			// bullet lists + inline examples + bold markdown) on top of the 3-form illustration diagram,
			// the examples list and the 8-10 question pool JSON — it needs the extra headroom or the
			// JSON gets truncated mid-object and fails to parse.
			TopicContentPayload payload = aiContentClient.completeJson(
					TOPIC_CONTENT_SYSTEM_PROMPT, userPrompt, 0.6, 5200, TopicContentPayload.class);
			GeneratedGrammarTopicContent result = toResult(payload);
			if (result.questions().isEmpty()) {
				throw new AiContentException("LLM returned no grammar library questions");
			}
			return result;
		} catch (AiContentException ex) {
			log.warn("LLM grammar library content generation failed for topic '{}', falling back to a minimal template", topicName, ex);
			return fallbackContent(topicName, level);
		}
	}

	// Generates one fresh question for a RETRY session; falls back to a templated question of the
	// same type so finishing a session with wrong answers never fails outright.
	@Override
	public GrammarLibraryQuestionSeed generateRetryQuestion(String topicName, String level, GrammarQuestionType questionType, String avoidPrompt) {
		try {
			String userPrompt = "Grammar topic: %s\nLevel: %s\nQuestion type: %s\nDo not repeat this sentence: %s".formatted(
					topicName, level == null ? "(unspecified)" : level, questionType, avoidPrompt == null ? "(none)" : avoidPrompt);
			QuestionPayload payload = aiContentClient.completeJson(RETRY_QUESTION_SYSTEM_PROMPT, userPrompt, 0.7, 400, QuestionPayload.class);
			return new GrammarLibraryQuestionSeed(
					questionType, payload.prompt, payload.options, payload.answer, payload.explanationVi, payload.translationVi);
		} catch (AiContentException ex) {
			log.warn("LLM retry-question generation failed for topic '{}', falling back to a template", topicName, ex);
			return fallbackQuestion(topicName, questionType);
		}
	}

	private GeneratedGrammarTopicContent toResult(TopicContentPayload payload) {
		List<GrammarLibraryExample> examples = new ArrayList<>();
		for (ExamplePayload raw : nullToEmpty(payload.examples)) {
			examples.add(GrammarLibraryExample.builder().en(raw.en).vi(raw.vi).build());
		}
		List<GrammarLibraryQuestionSeed> questions = new ArrayList<>();
		for (QuestionPayload raw : nullToEmpty(payload.questions)) {
			questions.add(new GrammarLibraryQuestionSeed(
					parseType(raw.type), raw.prompt, raw.options, raw.answer, raw.explanationVi, raw.translationVi));
		}
		return new GeneratedGrammarTopicContent(
				payload.explanationEn, payload.explanationVi, sanitizeIllustration(payload.illustrationText), examples, questions);
	}

	// Defensive net on top of the prompt instruction: the LLM sometimes still emits a mermaid node
	// label with an unquoted special character (e.g. A[V(s/es)]), which mermaid's parser rejects
	// outright at render time. Auto-quoting those labels here means a topic's illustration renders
	// as an actual diagram instead of silently falling back to plain text on the frontend.
	private String sanitizeIllustration(String illustrationText) {
		if (illustrationText == null || !illustrationText.contains("```mermaid")) {
			return illustrationText;
		}
		Matcher matcher = UNQUOTED_SPECIAL_LABEL.matcher(illustrationText);
		return matcher.replaceAll(result -> "[\"" + result.group(1) + "\"]");
	}

	private GrammarQuestionType parseType(String raw) {
		if (raw == null) {
			return GrammarQuestionType.MCQ;
		}
		try {
			return GrammarQuestionType.valueOf(raw.trim().toUpperCase());
		} catch (IllegalArgumentException ex) {
			log.warn("Unrecognized grammar library question type '{}', defaulting to MCQ", raw);
			return GrammarQuestionType.MCQ;
		}
	}

	// A minimal but usable fallback page: one templated ERROR_CORRECTION question, table-style
	// illustration, so the topic still opens even with the LLM unreachable.
	private GeneratedGrammarTopicContent fallbackContent(String topicName, String level) {
		String explanation = "Study the '%s' rule (level: %s). Practice applying it in the questions below.".formatted(
				topicName, level == null ? "unspecified" : level);
		List<GrammarLibraryExample> examples = List.of(
				GrammarLibraryExample.builder().en("This is an example sentence about " + topicName + ".")
						.vi("Đây là câu ví dụ về " + topicName + ".").build());
		List<GrammarLibraryQuestionSeed> questions = List.of(
				fallbackQuestion(topicName, GrammarQuestionType.ERROR_CORRECTION));
		return new GeneratedGrammarTopicContent(explanation, explanation, "S + V + O", examples, questions);
	}

	private GrammarLibraryQuestionSeed fallbackQuestion(String topicName, GrammarQuestionType type) {
		return new GrammarLibraryQuestionSeed(type, "Write one correct sentence practicing: " + topicName + ".",
				null, topicName, null, null);
	}

	private static <T> List<T> nullToEmpty(List<T> list) {
		return list == null ? List.of() : list;
	}

	@Getter
	@Setter
	@JsonIgnoreProperties(ignoreUnknown = true)
	private static class TopicContentPayload {
		private String explanationEn;
		private String explanationVi;
		private String illustrationText;
		private List<ExamplePayload> examples;
		private List<QuestionPayload> questions;
	}

	@Getter
	@Setter
	@JsonIgnoreProperties(ignoreUnknown = true)
	private static class ExamplePayload {
		private String en;
		private String vi;
	}

	@Getter
	@Setter
	@JsonIgnoreProperties(ignoreUnknown = true)
	private static class QuestionPayload {
		private String type;
		private String prompt;
		private List<String> options;
		private String answer;
		private String explanationVi;
		private String translationVi;
	}
}
