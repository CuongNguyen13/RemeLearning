package com.remelearning.vocabulary.dto;

import com.remelearning.vocabulary.domain.TranscriptSegment;

import java.util.List;

public record TranscriptResponse(
		String recordingId,
		String userId,
		String fullText,
		List<TranscriptSegment> segments) {
}
