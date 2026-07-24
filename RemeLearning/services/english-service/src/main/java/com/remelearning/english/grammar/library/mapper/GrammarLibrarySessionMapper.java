package com.remelearning.english.grammar.library.mapper;

import com.remelearning.english.grammar.library.domain.GrammarLibrarySession;
import com.remelearning.english.grammar.library.domain.GrammarLibrarySessionAnswer;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface GrammarLibrarySessionMapper {

	/** Inserts one session; the generated id is written back into {@code session.id}. */
	void insertSession(GrammarLibrarySession session);

	GrammarLibrarySession findById(@Param("id") Long id);

	/** Marks the session COMPLETED and stamps its final correct/total counts. */
	void completeSession(@Param("id") Long id, @Param("correctCount") int correctCount, @Param("totalCount") int totalCount);

	/** Completed sessions for a learner's topic, newest first. */
	List<GrammarLibrarySession> findCompletedByUserIdAndTopicId(@Param("userId") String userId, @Param("topicId") Long topicId);

	/** Completed sessions for a learner across every topic, newest first - used by the merged history endpoint. */
	List<GrammarLibrarySession> findCompletedByUserId(@Param("userId") String userId);

	/** Inserts one graded in-session answer; the generated id is written back into {@code answer.id}. */
	void insertAnswer(GrammarLibrarySessionAnswer answer);

	/** Every submitted answer for a session, oldest first (so the latest submission for a given questionRef wins on replay). */
	List<GrammarLibrarySessionAnswer> findAnswersBySessionId(@Param("sessionId") Long sessionId);
}
