package com.remelearning.english.grammar.learn.mapper;

import com.remelearning.english.grammar.learn.domain.GrammarAttemptDetailRow;
import com.remelearning.english.grammar.learn.domain.GrammarAttemptHistoryRow;
import com.remelearning.english.grammar.learn.domain.GrammarPracticeAttempt;
import com.remelearning.english.grammar.learn.domain.GrammarPracticeItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface GrammarPracticeMapper {

	/** Inserts one practice item; the generated id is written back into {@code item.id}. */
	void insertItem(GrammarPracticeItem item);

	GrammarPracticeItem findItemById(@Param("itemId") Long itemId);

	List<GrammarPracticeItem> findItemsByUserId(@Param("userId") String userId);

	/** Inserts one graded attempt; the generated id is written back into {@code attempt.id}. */
	void insertAttempt(GrammarPracticeAttempt attempt);

	List<GrammarAttemptHistoryRow> findHistoryByUserId(@Param("userId") String userId);

	/** Null if the id doesn't exist or belongs to a different user. */
	GrammarAttemptDetailRow findAttemptDetailByIdAndUserId(@Param("attemptId") Long attemptId, @Param("userId") String userId);
}
