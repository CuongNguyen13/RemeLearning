package com.remelearning.common.queue;

public interface EventPublisher {

	void publish(String topic, String key, BaseEvent event);
}
