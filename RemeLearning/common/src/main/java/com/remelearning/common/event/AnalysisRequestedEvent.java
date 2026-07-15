package com.remelearning.common.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Mirrors ai-service's {@code app.schemas.events.AnalysisRequestedEvent}, published to
 * {@link com.remelearning.common.constants.KafkaTopics#LEARNING_GAP_ANALYSIS_REQUESTED} once a
 * backend service has bundled a learner's mistake history (and, if available, session
 * transcript segments) for re-analysis. Plain POJO, not a {@code BaseEvent} — like
 * {@code RecordingUploadedEvent}, it must serialize to no-envelope snake_case JSON (via
 * {@link EventCodec}) to match what ai-service's pydantic model expects.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalysisRequestedEvent {
	private String recordingId;
	private String userId;
	private List<SegmentPayload> segments;
	private List<MistakeHistoryItemPayload> history;
}
