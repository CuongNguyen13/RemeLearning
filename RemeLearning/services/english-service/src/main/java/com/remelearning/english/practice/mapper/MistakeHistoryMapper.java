package com.remelearning.english.practice.mapper;

import com.remelearning.english.practice.domain.MistakeHistoryEntry;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

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

	/**
	 * Locks and reads the row's scoring state BEFORE the current attempt updates it, via
	 * {@code SELECT ... FOR UPDATE} - required so two concurrent redo() calls for the same
	 * (userId, itemId) serialize instead of racing on a read-modify-write of scoring state.
	 * Empty when the item has no history yet (first-ever attempt).
	 */
	Optional<MistakeHistoryEntry> findOneForUpdate(@Param("userId") String userId, @Param("itemId") String itemId);

	/**
	 * Persists the scoring engine's derived state for this item, separately from
	 * {@link #recordAttempt}'s occurrence-count/recency bookkeeping so the two concerns stay
	 * independently reviewable.
	 */
	void updateScoringState(@Param("userId") String userId, @Param("itemId") String itemId,
			@Param("easeFactor") double easeFactor, @Param("halfLifeDays") double halfLifeDays,
			@Param("mastery") double mastery, @Param("leitnerBox") int leitnerBox,
			@Param("nextReviewAt") Instant nextReviewAt, @Param("lastWeakScore") double lastWeakScore,
			@Param("labelKey") String labelKey);

	/** Items due for review now or earlier, ordered soonest-first - the Leitner schedule surfaced. */
	List<MistakeHistoryEntry> findDueForReview(@Param("userId") String userId, @Param("now") Instant now);
}
