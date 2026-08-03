package com.remelearning.english.listening.scoring;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.remelearning.english.learn.common.AiContentClient;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class LlmOpenAnswerGrader implements OpenAnswerGrader {

	private static final String SYSTEM_PROMPT = """
			You are an English-listening comprehension grader. Given the passage transcript, the
			question, a model answer, and the learner's free-text response, judge how well the
			learner's response demonstrates understanding - it does not need to match the model answer
			word for word, only be substantively correct. Respond with STRICTLY a raw JSON object (no
			markdown fences, no commentary) of the shape: {"score": 0.0-1.0, "feedback": "..."}
			("feedback" in Vietnamese, one short sentence).""";

	private final AiContentClient aiContentClient;
	private final int maxOutputTokens;

	public LlmOpenAnswerGrader(
			AiContentClient aiContentClient,
			@Value("${listening.scoring.max-output-tokens:300}") int maxOutputTokens) {
		this.aiContentClient = aiContentClient;
		this.maxOutputTokens = maxOutputTokens;
	}

	// No neutral-score fallback: a failed call propagates as AiContentException rather than
	// recording a made-up 0.5 as if the learner's answer had really been graded.
	@Override
	public OpenAnswerGrade grade(String passageTranscript, String question, String modelAnswer, String submittedAnswer) {
		String userPrompt = "Passage:\n%s\n\nQuestion: %s\nModel answer: %s\nLearner's response: %s".formatted(
				passageTranscript, question, modelAnswer, submittedAnswer == null || submittedAnswer.isBlank() ? "(no answer)" : submittedAnswer);
		LlmPayload payload = aiContentClient.completeJson(SYSTEM_PROMPT, userPrompt, 0.2, maxOutputTokens, LlmPayload.class);
		double score = Math.max(0.0, Math.min(1.0, payload.score));
		return new OpenAnswerGrade(score, payload.feedback);
	}

	@Getter
	@Setter
	@JsonIgnoreProperties(ignoreUnknown = true)
	private static class LlmPayload {
		private double score;
		private String feedback;
	}
}
