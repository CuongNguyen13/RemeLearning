package com.remelearning.english.writing.library.mapper;

import com.remelearning.english.writing.library.domain.WritingTopicProgress;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface WritingTopicProgressMapper {

	WritingTopicProgress findByUserIdAndTopicId(@Param("userId") String userId, @Param("topicId") Long topicId);

	List<WritingTopicProgress> findByUserId(@Param("userId") String userId);

	/** Opens the first topic of ONE axis for a new learner; no-op if a row already exists. */
	void bootstrapFirstTopic(@Param("userId") String userId, @Param("topicId") Long topicId);

	/** Only ever moves a LOCKED row forward - never regresses UNLOCKED/IN_PROGRESS/PASSED. */
	void unlockIfLocked(@Param("userId") String userId, @Param("topicId") Long topicId);

	void markInProgress(@Param("userId") String userId, @Param("topicId") Long topicId);

	void markPassed(@Param("userId") String userId, @Param("topicId") Long topicId);
}
