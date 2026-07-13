package com.remelearning.vocabulary.event;

import lombok.Data;

/** Mirrors ai-service's {@code app.schemas.events.Segment}. */
@Data
public class SegmentPayload {
	private String speaker;
	private String text;
	private double startSeconds;
	private double endSeconds;
}
