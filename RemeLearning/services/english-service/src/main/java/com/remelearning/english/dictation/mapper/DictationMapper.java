package com.remelearning.english.dictation.mapper;

import com.remelearning.english.dictation.domain.DictationAttempt;
import com.remelearning.english.dictation.domain.DictationAttemptDetailRow;
import com.remelearning.english.dictation.domain.DictationClip;
import com.remelearning.english.dictation.domain.DictationClipSentence;
import com.remelearning.english.dictation.domain.DictationHistoryRow;
import com.remelearning.english.dictation.domain.DictationLessonRow;
import com.remelearning.english.dictation.domain.DictationMiss;
import com.remelearning.english.dictation.domain.DictationPracticeItem;
import com.remelearning.english.dictation.domain.FolderCount;
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

	/** Distinct clip folders with their lesson count, for the folder-browse listing. */
	List<FolderCount> findDistinctFolders();

	/** Clips in one folder, ordered by code, for the lesson-browse listing within that folder. */
	/** Lessons in one folder joined with the given learner's own progress (sentence count,
	 * attempt count, latest-attempt accuracy) - null progress fields mean never attempted. */
	List<DictationLessonRow> findLessonSummariesByFolder(@Param("folder") String folder, @Param("userId") String userId);

	// --- Clip sentences (sentence-mode dictation) ---

	/**
	 * Inserts or, keyed on {@code (clipId, seq)}, updates a sentence's text - leaves
	 * {@code start_ms}/{@code end_ms} untouched so a re-import never wipes out prior AI-alignment results.
	 */
	void upsertSentence(@Param("clipId") Long clipId, @Param("seq") int seq, @Param("text") String text);

	/** Sets the AI-aligned start/end timestamps for one sentence, once alignment has run. */
	void updateSentenceTimestamps(
			@Param("clipId") Long clipId, @Param("seq") int seq,
			@Param("startMs") Integer startMs, @Param("endMs") Integer endMs);

	/** Sets one sentence's translation once it's been lazily generated (see getClipDetail). */
	void updateSentenceTranslation(@Param("clipId") Long clipId, @Param("seq") int seq, @Param("translation") String translation);

	/** One clip's sentences in order, for the clip-detail endpoint. */
	List<DictationClipSentence> findSentencesByClipId(@Param("clipId") Long clipId);

	// --- Attempts & misses ---

	/** Inserts one attempt row; the generated id is written back into {@code attempt.id}. */
	void insertAttempt(DictationAttempt attempt);

	/** Batch-inserts the misses recorded for one attempt; no-op for an empty list. */
	void insertMisses(@Param("misses") List<DictationMiss> misses);

	/** Persists the JSON-encoded AI suggestions generated for one attempt, so History detail can replay them later. */
	void updateAttemptAiSuggestions(@Param("attemptId") Long attemptId, @Param("aiSuggestions") String aiSuggestions);

	/** Running count of how many times this learner has missed {@code word} (case-normalized by the caller). */
	int countMissesForWord(@Param("userId") String userId, @Param("word") String word);

	/** The learner's most-frequently-missed words, most-missed first, capped at {@code limit}. */
	List<MissWordCount> findTopMissedWords(@Param("userId") String userId, @Param("limit") int limit);

	List<DictationHistoryRow> findHistoryByUserId(@Param("userId") String userId);

	/**
	 * One attempt's full detail (resolved reference text + taxonomy), scoped by user so a learner can
	 * only ever read their own attempts; null if the id doesn't exist or belongs to a different user.
	 */
	DictationAttemptDetailRow findAttemptDetailByIdAndUserId(@Param("attemptId") Long attemptId, @Param("userId") String userId);

	/** Every miss recorded for one attempt, in insertion order. */
	List<DictationMiss> findMissesByAttemptId(@Param("attemptId") Long attemptId);

	// --- AI-practice items ---

	/** Inserts one practice item; the generated id is written back into {@code item.id}. */
	void insertPracticeItem(DictationPracticeItem item);

	DictationPracticeItem findPracticeItemById(@Param("practiceItemId") Long practiceItemId);

	List<DictationPracticeItem> findPracticeItemsByUserId(@Param("userId") String userId);

	/** Practice items for the learner that don't yet have synthesized audio (storage_key IS NULL). */
	List<DictationPracticeItem> findPracticeItemsWithoutAudio(@Param("userId") String userId);

	/** Sets the storage key once Supertonic has synthesized the audio for a practice item. */
	void updatePracticeItemStorageKey(@Param("practiceItemId") Long practiceItemId, @Param("storageKey") String storageKey);

	/**
	 * Deletes all of the learner's practice items still without audio - called once
	 * {@code generateAiPractice} has replaced them with one newly-generated, fully-synthesized
	 * dialogue item.
	 */
	void deletePracticeItemsWithoutAudio(@Param("userId") String userId);
}
