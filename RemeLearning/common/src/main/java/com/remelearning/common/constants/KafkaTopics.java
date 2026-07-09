package com.remelearning.common.constants;

/** Shared Kafka topic names used across services to publish/consume domain events. */
public final class KafkaTopics {

	private KafkaTopics() {
	}

	/** Published by recording-service once a video/audio file lands in S3. */
	public static final String RECORDING_UPLOADED = "recording.uploaded";
	/** Published by the STT pipeline once a transcript is ready. */
	public static final String TRANSCRIPT_READY = "transcript.ready";
	public static final String PRONUNCIATION_ANALYZED = "pronunciation.analyzed";
	public static final String GRAMMAR_ANALYZED = "grammar.analyzed";
	public static final String VOCABULARY_ANALYZED = "vocabulary.analyzed";
	/** Published by recommendation-service once personalized exercises are generated. */
	public static final String RECOMMENDATION_GENERATED = "recommendation.generated";
}
