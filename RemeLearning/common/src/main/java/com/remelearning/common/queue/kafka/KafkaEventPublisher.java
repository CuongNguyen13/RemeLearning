package com.remelearning.common.queue.kafka;

import com.remelearning.common.queue.BaseEvent;
import com.remelearning.common.queue.EventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Kafka-backed {@link EventPublisher} implementation. Conditional on a {@link KafkaTemplate} bean
 * being present - services without Kafka configured (e.g. bff-service, which only proxies REST
 * calls) still get common's component scan but shouldn't fail to start over an unused publisher.
 */
@Slf4j
@Component
@ConditionalOnBean(KafkaTemplate.class)
@RequiredArgsConstructor
public class KafkaEventPublisher implements EventPublisher {

	private final KafkaTemplate<String, Object> kafkaTemplate;

	@Override
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
