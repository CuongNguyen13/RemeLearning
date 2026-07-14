package com.remelearning.english.vocabulary.service;

import com.remelearning.english.vocabulary.dto.TranscriptResponse;
import com.remelearning.english.vocabulary.event.TranscriptReadyEvent;

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
