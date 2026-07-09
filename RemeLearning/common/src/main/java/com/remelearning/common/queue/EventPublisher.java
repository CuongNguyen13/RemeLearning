package com.remelearning.common.queue;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class EventPublisher {

	private final KafkaTemplate<String, Object> kafkaTemplate;

	public void publish(String topic, String key, BaseEvent event) {
		kafkaTemplate.send(topic, key, event)
				.whenComplete((result, ex) -> {
					if (ex != null) {
						log.error("Failed to publish event {} to topic {}", event.getEventType(), topic, ex);
					} else {
						log.debug("Published event {} to topic {}", event.getEventType(), topic);
					}
				});
	}
}
