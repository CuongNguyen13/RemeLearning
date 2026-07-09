package com.remelearning.common.constants;

public final class KafkaTopics {

	private KafkaTopics() {
	}

	public static final String RECORDING_UPLOADED = "recording.uploaded";
	public static final String TRANSCRIPT_READY = "transcript.ready";
	public static final String PRONUNCIATION_ANALYZED = "pronunciation.analyzed";
	public static final String GRAMMAR_ANALYZED = "grammar.analyzed";
	public static final String VOCABULARY_ANALYZED = "vocabulary.analyzed";
	public static final String RECOMMENDATION_GENERATED = "recommendation.generated";
}
