package com.remelearning.vocabulary.mapper;

import com.remelearning.vocabulary.domain.Transcript;
import com.remelearning.vocabulary.domain.TranscriptSegment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

@Mapper
public interface TranscriptMapper {

	/** Sets the generated id back onto {@code transcript}. */
	void insertTranscript(Transcript transcript);

	void insertSegment(TranscriptSegment segment);

	Optional<Transcript> findByRecordingId(@Param("recordingId") String recordingId);

	List<TranscriptSegment> findSegmentsByTranscriptId(@Param("transcriptId") Long transcriptId);
}
