package com.remelearning.english.grammar.library.mapper;

import com.remelearning.english.grammar.library.domain.GrammarTopicProgress;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface GrammarTopicProgressMapper {

	GrammarTopicProgress findByUserIdAndTopicId(@Param("userId") String userId, @Param("topicId") Long topicId);

	List<GrammarTopicProgress> findByUserId(@Param("userId") String userId);

	/** Inserts an UNLOCKED row for the very first topic if one doesn't already exist; no-op otherwise. */
	void bootstrapFirstTopic(@Param("userId") String userId, @Param("topicId") Long topicId);

	/**
	 * Unlocks a topic for a learner: inserts a fresh UNLOCKED row if none exists yet, or flips an
	 * existing LOCKED row to UNLOCKED; a row already UNLOCKED/IN_PROGRESS/PASSED is left untouched.
	 */
	void unlockIfLocked(@Param("userId") String userId, @Param("topicId") Long topicId);

	void markInProgress(@Param("userId") String userId, @Param("topicId") Long topicId);

	void markPassed(@Param("userId") String userId, @Param("topicId") Long topicId);
}
