package com.remelearning.english.practice.session.mapper;

import com.remelearning.english.practice.session.domain.PracticeSession;
import com.remelearning.english.practice.session.domain.PracticeSessionExercise;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** MyBatis mapper for the practice_sessions / practice_session_exercises tables. */
@Mapper
public interface PracticeSessionMapper {

	/** Inserts a session header, populating the generated id back onto the argument. */
	void insertSession(PracticeSession session);

	/** Inserts one exercise slot, populating the generated id back onto the argument. */
	void insertExercise(PracticeSessionExercise exercise);

	PracticeSession findSessionById(@Param("id") Long id);

	/** The learner's most recent still-in-progress session, or null - used to offer "resume". */
	PracticeSession findLatestInProgressByUserId(@Param("userId") String userId);

	List<PracticeSessionExercise> findExercisesBySessionId(@Param("sessionId") Long sessionId);

	/** Marks one slot DONE with its score; no-op if it's already done or the order doesn't exist. */
	int markExerciseDone(
			@Param("sessionId") Long sessionId, @Param("exerciseOrder") int exerciseOrder, @Param("score") Double score);

	/** How many slots in a session are still PENDING - session completes when this hits zero. */
	int countPendingBySessionId(@Param("sessionId") Long sessionId);

	/** Flips a session to COMPLETED and stamps completed_at. */
	void completeSession(@Param("id") Long id);
}
