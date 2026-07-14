package com.remelearning.english.vocabulary.dto;

import com.remelearning.english.vocabulary.domain.TranscriptSegment;

import java.util.List;

public record TranscriptResponse(
		String recordingId,
		String userId,
		String fullText,
		List<TranscriptSegment> segments) {
}
