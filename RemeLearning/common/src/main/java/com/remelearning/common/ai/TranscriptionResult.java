package com.remelearning.common.ai;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/** Output of an {@link SttClient#transcribe} call. */
@Getter
@Builder
public class TranscriptionResult {

	/** Full transcript concatenated across all segments. */
	private String fullText;
	/** Timestamped, speaker-attributed segments, ordered by start time. */
	@Builder.Default
	private List<Segment> segments = List.of();

	/** One speaker turn, timestamped so it can be re-aligned with the source video. */
	@Getter
	@Builder
	public static class Segment {
		private String speaker;
		private String text;
		private double startSeconds;
		private double endSeconds;
	}
}
