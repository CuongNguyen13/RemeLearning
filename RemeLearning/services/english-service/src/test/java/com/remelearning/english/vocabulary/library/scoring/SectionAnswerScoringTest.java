package com.remelearning.english.vocabulary.library.scoring;

import com.remelearning.english.vocabulary.library.domain.SectionExerciseType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SectionAnswerScoringTest {

	@Test
	void mcqClozeMatchingAndTranslateViToEnScoreExactCaseInsensitiveMatch() {
		assertThat(SectionAnswerScoring.scoreClosed(SectionExerciseType.MCQ, "brief", "Brief")).isEqualTo(1.0);
		assertThat(SectionAnswerScoring.scoreClosed(SectionExerciseType.CLOZE, "brief", "long")).isEqualTo(0.0);
		assertThat(SectionAnswerScoring.scoreClosed(SectionExerciseType.MATCHING, "ngắn gọn", "Ngắn gọn")).isEqualTo(1.0);
		assertThat(SectionAnswerScoring.scoreClosed(SectionExerciseType.TRANSLATE_VI_TO_EN, "brief", null)).isEqualTo(0.0);
	}

	@Test
	void listeningDictationScoresViaWordErrorRateAccuracy() {
		double score = SectionAnswerScoring.scoreClosed(SectionExerciseType.LISTENING_DICTATION, "reluctant", "reluctant");

		assertThat(score).isEqualTo(1.0);
	}

	@Test
	void translateEnToViMustBeGradedByTheLlmNotThisPureDispatcher() {
		assertThatThrownBy(() -> SectionAnswerScoring.scoreClosed(SectionExerciseType.TRANSLATE_EN_TO_VI, "brief", "ngắn gọn"))
				.isInstanceOf(IllegalArgumentException.class);
	}
}
