package com.remelearning.recording.mapper;

import com.remelearning.recording.domain.Recording;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

@Mapper
public interface RecordingMapper {

	/** Sets the generated id back onto {@code recording}. */
	void insert(Recording recording);

	/** Looks up one recording by its public recordingId (UUID), or empty if never stored. */
	Optional<Recording> findByRecordingId(@Param("recordingId") String recordingId);

	/** Lists every recording uploaded by a user, most recent first. */
	List<Recording> findByUserId(@Param("userId") String userId);
}
