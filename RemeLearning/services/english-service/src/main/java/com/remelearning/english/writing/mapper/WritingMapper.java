package com.remelearning.english.writing.mapper;

import com.remelearning.english.writing.domain.WritingAttempt;
import com.remelearning.english.writing.domain.WritingAttemptDetailRow;
import com.remelearning.english.writing.domain.WritingAttemptHistoryRow;
import com.remelearning.english.writing.domain.WritingPracticeItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface WritingMapper {

	/** Inserts one practice item; the generated id is written back into {@code item.id}. */
	void insertItem(WritingPracticeItem item);

	WritingPracticeItem findItemById(@Param("itemId") Long itemId);

	List<WritingPracticeItem> findItemsByUserId(@Param("userId") String userId);

	/** Inserts one graded attempt; the generated id is written back into {@code attempt.id}. */
	void insertAttempt(WritingAttempt attempt);

	List<WritingAttemptHistoryRow> findHistoryByUserId(@Param("userId") String userId);

	/** Null if the id doesn't exist or belongs to a different user. */
	WritingAttemptDetailRow findAttemptDetailByIdAndUserId(@Param("attemptId") Long attemptId, @Param("userId") String userId);
}
