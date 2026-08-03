package com.remelearning.english.writing.grading;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.remelearning.english.learn.common.AiContentClient;
import com.remelearning.english.learn.common.AiContentException;
import com.remelearning.english.writing.domain.WritingTaskType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LlmWritingGraderTest {

	private final AiContentClient aiContentClient = mock(AiContentClient.class);
	private final LlmWritingGrader grader = new LlmWritingGrader(aiContentClient, 16000);

	@Test
	void parsesCriteriaCorrectionAndLabelledErrors() {
		stubLlm("""
				{"criteria": {"grammar": 0.4, "vocabulary": 0.7, "coherence": 0.9, "accuracy": null, "taskResponse": 0.8},
				 "correctedText": "I went to Hanoi.",
				 "feedbackVi": "Khá tốt.",
				 "errors": [{"wrong": "I have went to Hanoi", "corrected": "I went to Hanoi",
				             "label": "past simple vs present perfect", "category": "grammar",
				             "explanationVi": "Có mốc thời gian quá khứ.", "severity": "major"}]}""");

		WritingGrade grade = grader.grade(WritingTaskType.COMPOSE, "Brief", "Model", "I have went to Hanoi");

		assertThat(grade.criteria().getGrammar()).isEqualTo(0.4);
		assertThat(grade.criteria().getTaskResponse()).isEqualTo(0.8);
		assertThat(grade.correctedText()).isEqualTo("I went to Hanoi.");
		assertThat(grade.feedbackVi()).isEqualTo("Khá tốt.");
		assertThat(grade.errors()).singleElement().satisfies(error -> {
			assertThat(error.getLabel()).isEqualTo("past simple vs present perfect");
			assertThat(error.getCategory()).isEqualTo("grammar");
			assertThat(error.getWrong()).isEqualTo("I have went to Hanoi");
		});
	}

	@Test
	void keepsOnlyTheFourthCriterionThatAppliesToTheTaskType() {
		stubLlm("""
				{"criteria": {"grammar": 1.0, "vocabulary": 1.0, "coherence": 1.0, "accuracy": 0.6, "taskResponse": 0.2},
				 "correctedText": "x", "feedbackVi": "y", "errors": []}""");

		WritingGrade translation = grader.grade(WritingTaskType.TRANSLATE_VI_EN, "Passage", "Ref", "Attempt");

		// A model that fills in both must not leave taskResponse dangling on a translation attempt -
		// the caller averages every populated criterion into the overall score.
		assertThat(translation.criteria().getAccuracy()).isEqualTo(0.6);
		assertThat(translation.criteria().getTaskResponse()).isNull();
	}

	@Test
	void clampsOutOfRangeScoresAndTreatsAMissingCriterionAsZero() {
		stubLlm("""
				{"criteria": {"grammar": 1.7, "vocabulary": -0.5, "coherence": null},
				 "correctedText": "x", "feedbackVi": "y", "errors": []}""");

		WritingGrade grade = grader.grade(WritingTaskType.COMPOSE, "Brief", null, "Attempt");

		assertThat(grade.criteria().getGrammar()).isEqualTo(1.0);
		assertThat(grade.criteria().getVocabulary()).isEqualTo(0.0);
		// Scoring a missing criterion 0 rather than skipping it keeps the overall score honest.
		assertThat(grade.criteria().getCoherence()).isEqualTo(0.0);
	}

	@Test
	void dropsErrorsThatCouldNotBeRoutedToAWeakPointDomain() {
		stubLlm("""
				{"criteria": {"grammar": 0.5, "vocabulary": 0.5, "coherence": 0.5, "taskResponse": 0.5},
				 "correctedText": "x", "feedbackVi": "y",
				 "errors": [{"label": "run-on sentences", "category": "style"},
				            {"label": "", "category": "grammar"},
				            {"label": null, "category": "grammar"},
				            {"label": "past perfect", "category": null},
				            {"label": "  article usage  ", "category": "Grammar"}]}""");

		WritingGrade grade = grader.grade(WritingTaskType.COMPOSE, "Brief", null, "Attempt");

		// Only the routable one survives, with its label trimmed and category lower-cased.
		assertThat(grade.errors()).singleElement().satisfies(error -> {
			assertThat(error.getLabel()).isEqualTo("article usage");
			assertThat(error.getCategory()).isEqualTo("grammar");
		});
	}

	@Test
	void acceptsSnakeCaseKeysIfTheModelIgnoresTheCamelCaseContract() {
		stubLlm("""
				{"criteria": {"grammar": 0.5, "vocabulary": 0.5, "coherence": 0.5, "task_response": 0.5},
				 "corrected_text": "fixed", "feedback_vi": "nhận xét",
				 "errors": [{"label": "past perfect", "category": "grammar", "explanation_vi": "giải thích"}]}""");

		WritingGrade grade = grader.grade(WritingTaskType.COMPOSE, "Brief", null, "Attempt");

		assertThat(grade.correctedText()).isEqualTo("fixed");
		assertThat(grade.feedbackVi()).isEqualTo("nhận xét");
		assertThat(grade.criteria().getTaskResponse()).isEqualTo(0.5);
		assertThat(grade.errors()).singleElement()
				.satisfies(error -> assertThat(error.getExplanationVi()).isEqualTo("giải thích"));
	}

	@Test
	void treatsAnEmptyErrorListAsAGenuinelyCleanSubmission() {
		stubLlm("""
				{"criteria": {"grammar": 1.0, "vocabulary": 1.0, "coherence": 1.0, "taskResponse": 1.0},
				 "correctedText": "unchanged", "feedbackVi": "Rất tốt!", "errors": []}""");

		WritingGrade grade = grader.grade(WritingTaskType.COMPOSE, "Brief", null, "Flawless text");

		assertThat(grade.errors()).isEmpty();
		assertThat(grade.criteria().getGrammar()).isEqualTo(1.0);
	}

	@Test
	void throwsRatherThanReturningANeutralGradeWhenTheLlmCallFails() {
		when(aiContentClient.completeJson(
				anyString(), anyString(), anyDouble(), anyInt(), eq(LlmWritingGrader.LlmPayload.class)))
				.thenThrow(new AiContentException("boom"));

		// A fabricated 0.5-across-the-board grade would be stored as if the text had really been marked.
		assertThatThrownBy(() -> grader.grade(WritingTaskType.TRANSLATE_EN_VI, "Passage", "Ref", "Attempt"))
				.isInstanceOf(AiContentException.class);
	}

	@Test
	void gradingPromptCarriesTheReferenceAnswerSinceItIsNeededToMark() {
		stubLlm("""
				{"criteria": {"grammar": 1.0, "vocabulary": 1.0, "coherence": 1.0, "accuracy": 1.0},
				 "correctedText": "x", "feedbackVi": "y", "errors": []}""");

		grader.grade(WritingTaskType.TRANSLATE_VI_EN, "Nguồn", "Reference translation", "My attempt");

		// Unlike the suggester, the grader legitimately needs the reference answer - it cannot mark a
		// translation without it. Low temperature keeps marking reproducible.
		verify(aiContentClient).completeJson(
				anyString(), contains("Reference translation"), eq(0.2), anyInt(),
				eq(LlmWritingGrader.LlmPayload.class));
	}

	// Parses the stubbed raw JSON into the real payload class, so these tests exercise the same
	// deserialization (including the @JsonAlias fallbacks) that production does.
	private void stubLlm(String json) {
		when(aiContentClient.completeJson(
				anyString(), anyString(), anyDouble(), anyInt(), eq(LlmWritingGrader.LlmPayload.class)))
				.thenAnswer(invocation -> new ObjectMapper().readValue(json, LlmWritingGrader.LlmPayload.class));
	}
}
