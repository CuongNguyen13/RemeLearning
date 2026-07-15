package com.remelearning.common.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Mirrors ai-service's {@code app.schemas.events.Segment}, used inside {@link AnalysisRequestedEvent}. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SegmentPayload {
	private String speaker;
	private String text;
	private double startSeconds;
	private double endSeconds;
	private String language;
}
