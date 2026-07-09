package com.remelearning.common.ai;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class TranscriptionResult {

	private String fullText;
	@Builder.Default
	private List<Segment> segments = List.of();

	@Getter
	@Builder
	public static class Segment {
		private String speaker;
		private String text;
		private double startSeconds;
		private double endSeconds;
	}
}
