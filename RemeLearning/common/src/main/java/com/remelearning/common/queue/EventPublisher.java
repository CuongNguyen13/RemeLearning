package com.remelearning.common.queue;

/**
 * Broker-neutral contract for publishing domain events.
 * The current implementation is Kafka-backed ({@code queue.kafka.KafkaEventPublisher}),
 * but other brokers can be added later as sibling subpackages without changing callers.
 */
public interface EventPublisher {

	/** Publishes {@code event} to {@code topic}, partitioned/routed by {@code key}. */
	void publish(String topic, String key, BaseEvent event);
}
