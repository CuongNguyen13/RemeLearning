package com.remelearning.vocabulary.service;

import com.remelearning.vocabulary.dto.TranscriptResponse;
import com.remelearning.vocabulary.event.TranscriptReadyEvent;

/**
 * Persists and reads back transcripts delivered via {@code transcript.ready}.
 * Callers (controller/Kafka consumer) depend on this interface, not
 * {@link TranscriptServiceImpl}, so the persistence/implementation strategy can change later
 * without touching them.
 */
public interface TranscriptService {

	void saveTranscript(TranscriptReadyEvent event);

	TranscriptResponse getByRecordingId(String recordingId);
}
