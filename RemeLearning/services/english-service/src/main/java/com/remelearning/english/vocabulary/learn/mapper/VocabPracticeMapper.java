package com.remelearning.english.vocabulary.learn.mapper;

import com.remelearning.english.vocabulary.learn.domain.VocabAttemptDetailRow;
import com.remelearning.english.vocabulary.learn.domain.VocabAttemptHistoryRow;
import com.remelearning.english.vocabulary.learn.domain.VocabPracticeAttempt;
import com.remelearning.english.vocabulary.learn.domain.VocabPracticeItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface VocabPracticeMapper {

	/** Inserts one practice item; the generated id is written back into {@code item.id}. */
	void insertItem(VocabPracticeItem item);

	VocabPracticeItem findItemById(@Param("itemId") Long itemId);

	List<VocabPracticeItem> findItemsByUserId(@Param("userId") String userId);

	/** Inserts one graded attempt; the generated id is written back into {@code attempt.id}. */
	void insertAttempt(VocabPracticeAttempt attempt);

	List<VocabAttemptHistoryRow> findHistoryByUserId(@Param("userId") String userId);

	/** Null if the id doesn't exist or belongs to a different user. */
	VocabAttemptDetailRow findAttemptDetailByIdAndUserId(@Param("attemptId") Long attemptId, @Param("userId") String userId);
}
