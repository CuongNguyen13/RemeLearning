package com.remelearning.vocabulary.service;

import com.remelearning.common.exception.BusinessException;
import com.remelearning.vocabulary.domain.Transcript;
import com.remelearning.vocabulary.domain.TranscriptSegment;
import com.remelearning.vocabulary.dto.TranscriptResponse;
import com.remelearning.vocabulary.event.SegmentPayload;
import com.remelearning.vocabulary.event.TranscriptReadyEvent;
import com.remelearning.vocabulary.mapper.TranscriptMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class TranscriptService {

	private final TranscriptMapper transcriptMapper;

	@Transactional
	public void saveTranscript(TranscriptReadyEvent event) {
		if (transcriptMapper.findByRecordingId(event.getRecordingId()).isPresent()) {
			log.info("Transcript for recording {} already stored, skipping", event.getRecordingId());
			return;
		}

		Transcript transcript = Transcript.builder()
				.recordingId(event.getRecordingId())
				.userId(event.getUserId())
				.fullText(event.getFullText())
				.build();
		transcriptMapper.insertTranscript(transcript);

		int order = 0;
		for (SegmentPayload segment : event.getSegments()) {
			transcriptMapper.insertSegment(TranscriptSegment.builder()
					.transcriptId(transcript.getId())
					.speaker(segment.getSpeaker())
					.content(segment.getText())
					.startSeconds(segment.getStartSeconds())
					.endSeconds(segment.getEndSeconds())
					.segmentOrder(order++)
					.build());
		}
	}

	public TranscriptResponse getByRecordingId(String recordingId) {
		Transcript transcript = transcriptMapper.findByRecordingId(recordingId)
				.orElseThrow(() -> BusinessException.notFound("Transcript not found for recordingId=" + recordingId));
		var segments = transcriptMapper.findSegmentsByTranscriptId(transcript.getId());
		return new TranscriptResponse(transcript.getRecordingId(), transcript.getUserId(),
				transcript.getFullText(), segments);
	}
}
