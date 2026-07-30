package com.remelearning.english.writing.suggestion;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.remelearning.english.learn.common.AiContentClient;
import com.remelearning.english.learn.common.AiContentException;
import com.remelearning.english.writing.domain.WritingSuggestion;
import com.remelearning.english.writing.domain.WritingTaskType;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * The only {@link NextSentenceSuggester}: one Gemini call per press of the "Gợi ý câu tiếp theo"
 * button - there is no debounce, no ghost-text and no background polling, so a learner who never
 * asks for a hint costs nothing.
 *
 * <p>Two separate system prompts, because the two situations are genuinely different: for COMPOSE a
 * hint is scaffolding, while for a translation task an unconstrained hint would simply be the
 * answer. The translation prompt is therefore restricted to naming the structure needed and glossing
 * a hard word, and this class is never handed the reference answer at all.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LlmNextSentenceSuggester implements NextSentenceSuggester {

	private static final String COMPOSE_SYSTEM_PROMPT = """
			You are an English writing coach helping a Vietnamese learner who is midway through a
			writing task. You're given the task brief, their draft so far (possibly empty), and an
			optional CEFR level. Suggest 3 different directions for their NEXT sentence.

			Hard rule: never write the next sentence for them. Give the idea in Vietnamese, name the
			English structure to reach for, and offer a few words/collocations - never a complete
			English sentence.

			Respond with STRICTLY a raw JSON array (no markdown fences, no commentary):
			[{"ideaVi": "ý tưởng cho câu tiếp theo, bằng tiếng Việt",
			  "structureHint": "the English sentence pattern, e.g. \\"Although + clause, + main clause\\"",
			  "usefulPhrases": ["word or collocation", "..."]}]""";

	private static final String TRANSLATE_SYSTEM_PROMPT = """
			You are an English writing coach helping a Vietnamese learner who is midway through a
			TRANSLATION exercise. You're given the source passage, their draft translation so far
			(possibly empty), and an optional CEFR level.

			Hard rules - the learner must still do the translating:
			- NEVER translate the next sentence, or any part of the source passage, for them.
			- NEVER output a complete sentence in the target language.
			- You may only: name the grammatical structure the next sentence requires, and gloss at
			  most two individually difficult words or collocations.

			Identify which part of the source passage they should tackle next and give 2-3 hints.

			Respond with STRICTLY a raw JSON array (no markdown fences, no commentary):
			[{"ideaVi": "phần nào của đoạn nguồn cần dịch tiếp và cần lưu ý gì, bằng tiếng Việt",
			  "structureHint": "the structure required, e.g. \\"past perfect: had + V3\\"",
			  "usefulPhrases": ["single word or collocation", "..."]}]""";

	private final AiContentClient aiContentClient;

	// Note the deliberately narrow signature: no reference answer is accepted by this class at all,
	// so there is no way for the model translation to leak into a hint even by mistake.
	@Override
	public List<WritingSuggestion> suggest(
			WritingTaskType taskType, String promptText, String draftText, String level) {
		try {
			String userPrompt = """
					%s:
					%s

					Learner's draft so far:
					%s

					Level: %s""".formatted(
					taskType.isTranslation() ? "Source passage to translate" : "Task brief",
					promptText,
					draftText == null || draftText.isBlank() ? "(nothing yet - they need help starting)" : draftText,
					level == null ? "(unspecified)" : level);
			LlmSuggestion[] payload = aiContentClient.completeJson(
					taskType.isTranslation() ? TRANSLATE_SYSTEM_PROMPT : COMPOSE_SYSTEM_PROMPT,
					userPrompt, 0.7, 900, LlmSuggestion[].class);
			return toSuggestions(payload);
		} catch (AiContentException ex) {
			log.warn("LLM next-sentence suggestion failed for {}, returning no suggestions", taskType, ex);
			return List.of();
		}
	}

	// Keeps only suggestions that actually say something (a blank ideaVi is useless to the learner).
	private List<WritingSuggestion> toSuggestions(LlmSuggestion[] payload) {
		List<WritingSuggestion> suggestions = new ArrayList<>();
		if (payload == null) {
			return suggestions;
		}
		for (LlmSuggestion raw : payload) {
			if (raw.ideaVi == null || raw.ideaVi.isBlank()) {
				continue;
			}
			suggestions.add(WritingSuggestion.builder()
					.ideaVi(raw.ideaVi.trim())
					.structureHint(raw.structureHint)
					.usefulPhrases(raw.usefulPhrases == null ? List.of() : raw.usefulPhrases)
					.build());
		}
		return suggestions;
	}

	@Getter
	@Setter
	@JsonIgnoreProperties(ignoreUnknown = true)
	static class LlmSuggestion {
		@JsonAlias("idea_vi")
		private String ideaVi;
		@JsonAlias("structure_hint")
		private String structureHint;
		@JsonAlias("useful_phrases")
		private List<String> usefulPhrases;
	}
}
