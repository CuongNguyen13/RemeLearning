package com.remelearning.english.speaking.library.mapper;

import com.remelearning.english.speaking.library.domain.SpeakingLibraryAttempt;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SpeakingLibraryAttemptMapper {

	/** Inserts one scored sentence-attempt; the generated id is written back into {@code attempt.id}. */
	void insert(SpeakingLibraryAttempt attempt);

	List<SpeakingLibraryAttempt> findByUserId(@Param("userId") String userId);

	/** Every attempt recorded for one section, across every sentence in it — used by {@code finishSection}. */
	List<SpeakingLibraryAttempt> findBySectionId(@Param("sectionId") Long sectionId);
}
