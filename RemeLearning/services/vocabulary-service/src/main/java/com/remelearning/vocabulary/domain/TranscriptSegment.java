package com.remelearning.vocabulary.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** One speaker-attributed, timestamped segment of a {@link Transcript}. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TranscriptSegment {
	private Long id;
	private Long transcriptId;
	private String speaker;
	private String content;
	private double startSeconds;
	private double endSeconds;
	private int segmentOrder;
}
