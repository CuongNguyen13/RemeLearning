package com.remelearning.english.listening.library.mapper;

import com.remelearning.english.listening.library.domain.ListeningLibraryAttemptAnswer;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ListeningLibraryAttemptAnswerMapper {

	/** Inserts one per-question answer row; the generated id is written back into {@code answer.id}. */
	void insert(ListeningLibraryAttemptAnswer answer);

	List<ListeningLibraryAttemptAnswer> findByAttemptId(@Param("attemptId") Long attemptId);
}
