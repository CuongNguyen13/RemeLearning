package com.remelearning.english.practice.mapper;

import com.remelearning.english.practice.domain.MistakeHistoryEntry;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface MistakeHistoryMapper {

	/**
	 * Inserts a fresh mistake-history row the first time an item is ever surfaced; a no-op if
	 * the item already has history (keyed on (userId, itemId)), so this never double-counts.
	 */
	void seedIfAbsent(MistakeHistoryEntry entry);

	/**
	 * Records one graded redo attempt: increments {@code occurrenceCount} only when the answer
	 * was wrong, and always refreshes {@code lastSeenAt} to now. Upserts if the item has no
	 * history yet.
	 */
	void recordAttempt(@Param("userId") String userId, @Param("itemId") String itemId,
			@Param("category") String category, @Param("label") String label, @Param("correct") boolean correct);

	List<MistakeHistoryEntry> findByUserId(@Param("userId") String userId);
}
