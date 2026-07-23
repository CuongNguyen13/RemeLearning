package com.remelearning.english.speaking.mapper;

import com.remelearning.english.speaking.domain.SpeakingAttempt;
import com.remelearning.english.speaking.domain.SpeakingAttemptDetailRow;
import com.remelearning.english.speaking.domain.SpeakingAttemptHistoryRow;
import com.remelearning.english.speaking.domain.SpeakingPracticeItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SpeakingMapper {

	/** Inserts one practice item; the generated id is written back into {@code item.id}. */
	void insertItem(SpeakingPracticeItem item);

	SpeakingPracticeItem findItemById(@Param("itemId") Long itemId);

	List<SpeakingPracticeItem> findItemsByUserId(@Param("userId") String userId);

	/** Sets the storage key once Supertonic has synthesized the sample audio for a practice item. */
	void updateItemStorageKey(@Param("itemId") Long itemId, @Param("storageKey") String storageKey);

	/** Inserts one graded attempt; the generated id is written back into {@code attempt.id}. */
	void insertAttempt(SpeakingAttempt attempt);

	List<SpeakingAttemptHistoryRow> findHistoryByUserId(@Param("userId") String userId);

	/** Null if the id doesn't exist or belongs to a different user. */
	SpeakingAttemptDetailRow findAttemptDetailByIdAndUserId(@Param("attemptId") Long attemptId, @Param("userId") String userId);
}
