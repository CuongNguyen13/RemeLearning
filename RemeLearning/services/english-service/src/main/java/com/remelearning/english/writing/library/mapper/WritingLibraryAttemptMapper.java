package com.remelearning.english.writing.library.mapper;

import com.remelearning.english.writing.library.domain.WritingLibraryAttempt;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface WritingLibraryAttemptMapper {

	/** Inserts one graded attempt; the generated id is written back into {@code attempt.id}. */
	void insert(WritingLibraryAttempt attempt);

	List<WritingLibraryAttempt> findByUserId(@Param("userId") String userId);

	/** Null if the id doesn't exist or belongs to a different learner. */
	WritingLibraryAttempt findByIdAndUserId(@Param("attemptId") Long attemptId, @Param("userId") String userId);
}
