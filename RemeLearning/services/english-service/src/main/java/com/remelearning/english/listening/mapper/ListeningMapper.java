package com.remelearning.english.listening.mapper;

import com.remelearning.english.listening.domain.ListeningAttempt;
import com.remelearning.english.listening.domain.ListeningAttemptDetailRow;
import com.remelearning.english.listening.domain.ListeningAttemptHistoryRow;
import com.remelearning.english.listening.domain.ListeningPracticeItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ListeningMapper {

	/** Inserts one practice item; the generated id is written back into {@code item.id}. */
	void insertItem(ListeningPracticeItem item);

	ListeningPracticeItem findItemById(@Param("itemId") Long itemId);

	List<ListeningPracticeItem> findItemsByUserId(@Param("userId") String userId);

	/** Sets the storage key once Supertonic has synthesized the audio for a practice item. */
	void updateItemStorageKey(@Param("itemId") Long itemId, @Param("storageKey") String storageKey);

	/** Inserts one graded attempt; the generated id is written back into {@code attempt.id}. */
	void insertAttempt(ListeningAttempt attempt);

	List<ListeningAttemptHistoryRow> findHistoryByUserId(@Param("userId") String userId);

	/** Null if the id doesn't exist or belongs to a different user. */
	ListeningAttemptDetailRow findAttemptDetailByIdAndUserId(@Param("attemptId") Long attemptId, @Param("userId") String userId);
}
