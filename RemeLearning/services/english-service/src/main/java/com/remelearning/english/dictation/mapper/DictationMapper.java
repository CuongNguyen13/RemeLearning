package com.remelearning.english.dictation.mapper;

import com.remelearning.english.dictation.domain.DictationAttempt;
import com.remelearning.english.dictation.domain.DictationClip;
import com.remelearning.english.dictation.domain.DictationHistoryRow;
import com.remelearning.english.dictation.domain.DictationMiss;
import com.remelearning.english.dictation.domain.DictationPracticeItem;
import com.remelearning.english.dictation.domain.MissWordCount;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface DictationMapper {

	// --- Library clips ---

	/** Inserts or, if the {@code code} already exists, updates a library clip (idempotent re-import). */
	void upsertClip(DictationClip clip);

	DictationClip findClipById(@Param("clipId") Long clipId);

	/** Clips matching any subset of facets (null facet = unfiltered on that dimension), newest first, capped at {@code limit}. */
	List<DictationClip> findClipsByFacets(
			@Param("skill") String skill,
			@Param("level") String level,
			@Param("topic") String topic,
			@Param("examType") String examType,
			@Param("limit") int limit);

	/** Random clips matching the facets, capped at {@code limit} - used to build a session. */
	List<DictationClip> findRandomClipsByFacets(
			@Param("skill") String skill,
			@Param("level") String level,
			@Param("topic") String topic,
			@Param("examType") String examType,
			@Param("limit") int limit);

	List<String> findDistinctSkills();

	List<String> findDistinctLevels();

	List<String> findDistinctTopics();

	List<String> findDistinctExamTypes();

	// --- Attempts & misses ---

	/** Inserts one attempt row; the generated id is written back into {@code attempt.id}. */
	void insertAttempt(DictationAttempt attempt);

	/** Batch-inserts the misses recorded for one attempt; no-op for an empty list. */
	void insertMisses(@Param("misses") List<DictationMiss> misses);

	/** Running count of how many times this learner has missed {@code word} (case-normalized by the caller). */
	int countMissesForWord(@Param("userId") String userId, @Param("word") String word);

	/** The learner's most-frequently-missed words, most-missed first, capped at {@code limit}. */
	List<MissWordCount> findTopMissedWords(@Param("userId") String userId, @Param("limit") int limit);

	List<DictationHistoryRow> findHistoryByUserId(@Param("userId") String userId);

	// --- AI-practice items ---

	/** Inserts one practice item; the generated id is written back into {@code item.id}. */
	void insertPracticeItem(DictationPracticeItem item);

	DictationPracticeItem findPracticeItemById(@Param("practiceItemId") Long practiceItemId);

	List<DictationPracticeItem> findPracticeItemsByUserId(@Param("userId") String userId);

	/** Practice items for the learner that don't yet have synthesized audio (storage_key IS NULL). */
	List<DictationPracticeItem> findPracticeItemsWithoutAudio(@Param("userId") String userId);

	/** Sets the storage key once Supertonic has synthesized the audio for a practice item. */
	void updatePracticeItemStorageKey(@Param("practiceItemId") Long practiceItemId, @Param("storageKey") String storageKey);
}
