package com.remelearning.english.vocabulary.library.mapper;

import com.remelearning.english.vocabulary.library.domain.VocabularySectionAnswer;
import com.remelearning.english.vocabulary.library.domain.VocabularySectionAttempt;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface VocabularySectionMapper {

	/** Inserts one section attempt; the generated id is written back into {@code attempt.id}. */
	void insertAttempt(VocabularySectionAttempt attempt);

	VocabularySectionAttempt findAttemptById(@Param("id") Long id);

	void updateAttemptQueueState(@Param("id") Long id, @Param("queueStateJson") String queueStateJson,
			@Param("correctCount") int correctCount, @Param("totalAnswers") int totalAnswers);

	/** Marks the attempt finished (COMPLETED or ABANDONED) and stamps completed_at = now(). */
	void completeAttempt(@Param("id") Long id, @Param("status") String status);

	/** Inserts one graded in-section answer; the generated id is written back into {@code answer.id}. */
	void insertAnswer(VocabularySectionAnswer answer);

	List<VocabularySectionAnswer> findAnswersByAttemptId(@Param("attemptId") Long attemptId);

	/** Finished (COMPLETED/ABANDONED) attempts for a learner, newest first. */
	List<VocabularySectionAttempt> findHistoryByUserId(@Param("userId") String userId);
}
