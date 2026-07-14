package com.remelearning.english.pronunciation.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;

/**
 * JSON codec for Kafka event payloads. ai-service publishes plain pydantic
 * {@code model_dump()} JSON (snake_case keys, no envelope) — a dedicated snake_case
 * mapper is used here instead of the app-wide Jackson bean so REST controller
 * responses (camelCase) are unaffected.
 */
public final class EventCodec {

	public static final ObjectMapper MAPPER = new ObjectMapper()
			.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

	private EventCodec() {
	}
}
