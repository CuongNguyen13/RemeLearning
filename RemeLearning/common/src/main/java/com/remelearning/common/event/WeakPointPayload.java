package com.remelearning.common.event;

import lombok.Data;

/** Mirrors ai-service's {@code app.schemas.events.WeakPoint}. {@code category} may be "grammar",
 * "vocabulary" or "pronunciation" — each consuming service decides whether to filter by category
 * or keep every item. */
@Data
public class WeakPointPayload {
	private String itemId;
	private String category;
	private String label;
	private double forgettingScore;
	private String recommendation;
}
