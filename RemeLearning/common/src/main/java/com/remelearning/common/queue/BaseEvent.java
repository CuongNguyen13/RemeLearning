package com.remelearning.common.queue;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

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
