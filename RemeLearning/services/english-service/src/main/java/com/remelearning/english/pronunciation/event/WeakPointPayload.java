package com.remelearning.english.pronunciation.event;

import lombok.Data;

/** Mirrors ai-service's {@code app.schemas.events.WeakPoint}. {@code category} may be "grammar",
 * "vocabulary" or "pronunciation" — this service only persists items where category = "pronunciation". */
@Data
public class WeakPointPayload {
	private String itemId;
	private String category;
	private String label;
	private double forgettingScore;
	private String recommendation;
}
