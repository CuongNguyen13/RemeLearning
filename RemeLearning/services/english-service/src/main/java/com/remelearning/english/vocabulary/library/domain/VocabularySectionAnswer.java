package com.remelearning.english.vocabulary.library.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** One graded answer inside a Section (row in {@code vocabulary_section_answers}). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VocabularySectionAnswer {
	private Long id;
	private Long sectionAttemptId;
	private Long libraryWordId;
	private SectionExerciseType exerciseType;
	private String submittedAnswer;
	private double score;
	private boolean correct;
	private Instant answeredAt;
}
