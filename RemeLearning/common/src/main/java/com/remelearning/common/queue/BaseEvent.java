package com.remelearning.common.queue;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Base envelope for every domain event published through {@link EventPublisher}.
 * Broker-agnostic: extend this for a concrete event payload rather than depending
 * on any specific queue technology. Setters are present (alongside the getters) so a
 * plain Jackson {@code ObjectMapper} can also deserialize a subclass back from JSON on
 * the consumer side (e.g. dashboard-service decoding {@code recommendation.generated}),
 * not just construct one via the producer-side constructor.
 */
@Getter
@Setter
@NoArgsConstructor
public class BaseEvent {

	private String eventId = UUID.randomUUID().toString();
	private String eventType;
	private Instant occurredAt = Instant.now();

	public BaseEvent(String eventType) {
		this.eventType = eventType;
	}
}
