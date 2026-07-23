package com.remelearning.english.listening.scoring;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.remelearning.english.learn.common.AiContentClient;
import com.remelearning.english.learn.common.AiContentException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class LlmOpenAnswerGrader implements OpenAnswerGrader {

	private static final String SYSTEM_PROMPT = """
			You are an English-listening comprehension grader. Given the passage transcript, the
			question, a model answer, and the learner's free-text response, judge how well the
			learner's response demonstrates understanding - it does not need to match the model answer
			word for word, only be substantively correct. Respond with STRICTLY a raw JSON object (no
			markdown fences, no commentary) of the shape: {"score": 0.0-1.0, "feedback": "..."}
			("feedback" in Vietnamese, one short sentence).""";

	private final AiContentClient aiContentClient;

	@Override
	public OpenAnswerGrade grade(String passageTranscript, String question, String modelAnswer, String submittedAnswer) {
		try {
			String userPrompt = "Passage:\n%s\n\nQuestion: %s\nModel answer: %s\nLearner's response: %s".formatted(
					passageTranscript, question, modelAnswer, submittedAnswer == null || submittedAnswer.isBlank() ? "(no answer)" : submittedAnswer);
			LlmPayload payload = aiContentClient.completeJson(SYSTEM_PROMPT, userPrompt, 0.2, 300, LlmPayload.class);
			double score = Math.max(0.0, Math.min(1.0, payload.score));
			return new OpenAnswerGrade(score, payload.feedback);
		} catch (AiContentException ex) {
			log.warn("LLM open-answer grading failed, falling back to a neutral score", ex);
			return new OpenAnswerGrade(0.5, "Không thể tự động chấm câu trả lời này.");
		}
	}

	@Getter
	@Setter
	@JsonIgnoreProperties(ignoreUnknown = true)
	private static class LlmPayload {
		private double score;
		private String feedback;
	}
}
