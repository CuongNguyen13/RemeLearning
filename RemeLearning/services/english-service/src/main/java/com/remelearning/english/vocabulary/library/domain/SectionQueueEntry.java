package com.remelearning.english.vocabulary.library.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One entry in a Section's in-session queue (the unit {@code SectionQueue} reorders). Serialized
 * as JSON inside {@link VocabularySectionAttempt#getQueueStateJson()}.
 *
 * <p>{@code pendingExerciseType} freezes the randomly-chosen {@link SectionExerciseType} for this
 * word's *current* occurrence at the front of the queue — set once when its QUIZ card is built,
 * read back when the learner's answer is graded, and always {@code null} on a freshly-requeued
 * entry so the next occurrence re-rolls a (possibly different) exercise type.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SectionQueueEntry {
	private Long libraryWordId;
	private int streak;
	private boolean introShown;
	private SectionExerciseType pendingExerciseType;
}
