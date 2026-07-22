package com.remelearning.english.vocabulary.library.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * One row in {@code vocabulary_section_attempts} — a learner's in-progress or finished Section
 * (a queue of library words drilled with intra-session repetition). {@code queueStateJson} is the
 * serialized {@code List<SectionQueueEntry>} driving {@code SectionQueue}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VocabularySectionAttempt {
	private Long id;
	private String userId;
	private Long topicId;
	private SectionStatus status;
	private int sectionSize;
	private String libraryWordIdsJson;
	private String queueStateJson;
	private int correctCount;
	private int totalAnswers;
	private Instant startedAt;
	private Instant completedAt;
}
