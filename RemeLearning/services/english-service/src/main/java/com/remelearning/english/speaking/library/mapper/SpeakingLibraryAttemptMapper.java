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

	/**
	 * Every attempt recorded for one section by ONE learner only. Used by {@code finishSection} and
	 * {@code generatePracticeFromSection} instead of {@link #findBySectionId} so another learner's
	 * attempts on a shared section never cross into this learner's pass/fail determination or
	 * practice generation — {@code findBySectionId} returns rows from every learner who has ever
	 * attempted the section, which would let Learner B get credited (or targeted for practice) based
	 * on Learner A's recordings.
	 */
	List<SpeakingLibraryAttempt> findBySectionIdAndUserId(@Param("sectionId") Long sectionId, @Param("userId") String userId);
}
