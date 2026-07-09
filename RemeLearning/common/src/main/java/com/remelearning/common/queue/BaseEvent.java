package com.remelearning.common.queue;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Base envelope for every domain event published through {@link EventPublisher}.
 * Broker-agnostic: extend this for a concrete event payload rather than depending
 * on any specific queue technology.
 */
@Getter
@NoArgsConstructor
public class BaseEvent {

	private String eventId = UUID.randomUUID().toString();
	private String eventType;
	private Instant occurredAt = Instant.now();

	public BaseEvent(String eventType) {
		this.eventType = eventType;
	}
}
