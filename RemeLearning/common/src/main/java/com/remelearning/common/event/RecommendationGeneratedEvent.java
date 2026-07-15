package com.remelearning.common.event;

import com.remelearning.common.queue.BaseEvent;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * Published to {@link com.remelearning.common.constants.KafkaTopics#RECOMMENDATION_GENERATED}
 * once a batch of weak points from a {@code learning.gap.analyzed} event has been persisted as
 * recommendations. recommendation-service is the first (and so far only) producer of this topic,
 * constructing it via the 3-arg constructor below. dashboard-service is a consumer: it decodes the
 * full JSON envelope (including the inherited {@code eventId}/{@code eventType}/{@code occurredAt}
 * fields) with a plain camelCase {@code ObjectMapper} rather than the snake_case
 * {@link EventCodec} — the no-args constructor and setters (on this class and on
 * {@link BaseEvent}) exist for that Jackson round trip, not for producer-side use.
 */
@Getter
@Setter
@NoArgsConstructor
public class RecommendationGeneratedEvent extends BaseEvent {

	private String recordingId;
	private String userId;
	private List<RecommendationPayload> recommendations;

	public RecommendationGeneratedEvent(String recordingId, String userId, List<RecommendationPayload> recommendations) {
		super("recommendation.generated");
		this.recordingId = recordingId;
		this.userId = userId;
		this.recommendations = recommendations;
	}
}
