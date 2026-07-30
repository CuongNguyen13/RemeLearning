package com.remelearning.english.writing.library.domain;

import com.remelearning.english.writing.domain.WritingTaskType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** One writing/translation prompt in a topic's chain (row in {@code writing_library_prompts}). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WritingLibraryPrompt {
	private Long id;
	private Long topicId;
	private WritingTaskType taskType;
	private String promptText;
	/** Used only for grading; never sent to the client before submission. */
	private String referenceAnswer;
	private Integer minWords;
	private String explanation;
	private Instant createdAt;
}
