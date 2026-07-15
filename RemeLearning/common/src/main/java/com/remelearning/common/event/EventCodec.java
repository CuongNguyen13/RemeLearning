package com.remelearning.common.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;

/**
 * JSON codec for Kafka event payloads. ai-service publishes plain pydantic
 * {@code model_dump()} JSON (snake_case keys, no envelope) — a dedicated snake_case
 * mapper is used here instead of the app-wide Jackson bean so REST controller
 * responses (camelCase) are unaffected. Shared across services (english-service,
 * recommendation-service, dashboard-service, recording-service) that all decode/encode
 * this same snake_case shape, rather than each redeclaring an identical mapper.
 */
public final class EventCodec {

	public static final ObjectMapper MAPPER = new ObjectMapper()
			.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

	private EventCodec() {
	}
}
