package com.remelearning.english.dictation.service;

import com.remelearning.english.dictation.dto.DictationAttemptDetailDto;
import com.remelearning.english.dictation.dto.DictationAttemptRequest;
import com.remelearning.english.dictation.dto.DictationAttemptResultDto;
import com.remelearning.english.dictation.dto.DictationAudioResource;
import com.remelearning.english.dictation.dto.DictationClipDetailDto;
import com.remelearning.english.dictation.dto.DictationClipDto;
import com.remelearning.english.dictation.dto.DictationFacetsDto;
import com.remelearning.english.dictation.dto.DictationFolderDto;
import com.remelearning.english.dictation.dto.DictationHistoryEntryDto;
import com.remelearning.english.dictation.dto.DictationLessonSummaryDto;
import com.remelearning.english.dictation.dto.DictationPracticeItemDetailDto;
import com.remelearning.english.dictation.dto.DictationPracticeItemDto;
import com.remelearning.english.dictation.dto.GenerateAiPracticeRequest;
import com.remelearning.english.dictation.dto.StartDictationSessionRequest;

import java.util.List;

/**
 * Drives the two dictation sections: the fixed real-audio library (browse/session/attempt) and the
 * "Luyện nghe với AI" section (Gemini-suggested, Supertonic-voiced practice). Callers (controller)
 * depend on this interface, not {@link DictationServiceImpl}, so the analysis/TTS/storage providers
 * can change without touching them.
 */
public interface DictationService {

	/** Distinct facet values available across the library, to populate the UI filters. */
	DictationFacetsDto getFacets();

	/** Library clips matching the given facets (any null = unfiltered), capped at {@code limit}. */
	List<DictationClipDto> listClips(String skill, String level, String topic, String examType, int limit);

	/** Streams one library clip's audio from storage. */
	DictationAudioResource loadClipAudio(Long clipId);

	/** Picks a batch of library clips for a new dictation session (filtered by the request's facets). */
	List<DictationClipDto> startSession(String userId, StartDictationSessionRequest request);

	/**
	 * Grades a typed transcript for a library clip or an AI-practice clip, records the misses, returns
	 * immediate AI feedback, and publishes the misses into the recommendation pipeline.
	 */
	DictationAttemptResultDto submitAttempt(DictationAttemptRequest request);

	/** A learner's past dictation attempts, newest first. */
	List<DictationHistoryEntryDto> getHistory(String userId);

	/**
	 * Full detail for one of the learner's own past attempts (reference text, transcript, mistakes,
	 * AI suggestions); throws {@code BusinessException.notFound} if it doesn't exist or belongs to a
	 * different user.
	 */
	DictationAttemptDetailDto getAttemptDetail(String userId, Long attemptId);

	/** The learner's AI-practice items (audio URL present once synthesized). */
	List<DictationPracticeItemDto> getAiPractice(String userId);

	/**
	 * Ensures the learner has AI-practice clips with audio: synthesizes any pending items (or first
	 * generates new ones from their most-missed words), honoring the request's level/examType facets
	 * (each may be a concrete value, "RANDOM", or null for no preference) and translation language,
	 * then returns the full list.
	 */
	List<DictationPracticeItemDto> generateAiPractice(String userId, GenerateAiPracticeRequest request);

	/**
	 * Generates AI-practice sentences targeted at one specific past attempt's mistakes (the "Luyện
	 * tập với AI" action from a history row), synthesizes their audio, then returns the learner's
	 * refreshed AI-practice list; throws {@code BusinessException.notFound} if the attempt doesn't
	 * exist or belongs to a different user. {@code translationLang} is the learner's current UI
	 * language; a translation is only generated when it's not "en".
	 */
	List<DictationPracticeItemDto> generateAiPracticeFromAttempt(String userId, Long attemptId, String translationLang);

	/** Streams one AI-practice item's synthesized audio from storage. */
	DictationAudioResource loadPracticeAudio(Long practiceItemId);

	/**
	 * Full detail for one AI-practice item - passage text split into sentences, mirroring
	 * {@link #getClipDetail(Long, String)} - shown once the learner opens it to practice sentence-by-sentence;
	 * throws {@code BusinessException.notFound} if it doesn't exist.
	 */
	DictationPracticeItemDetailDto getAiPracticeDetail(Long practiceItemId);

	/** The distinct clip folders (topic groupings), each with its lesson count, for folder->file browsing. */
	List<DictationFolderDto> listFolders();

	/** The lessons (clips) inside one folder, light-weight (no script), joined with {@code userId}'s
	 * own progress (attempt count, latest accuracy) for the folder's lesson listing. */
	List<DictationLessonSummaryDto> listFolderLessons(String folder, String userId);

	/**
	 * Full detail for one clip - script + split sentences - shown once the learner opens it to
	 * practice. {@code translationLang} is the learner's current UI language; a per-sentence
	 * translation is lazily generated (and cached) only when it's not "en".
	 */
	DictationClipDetailDto getClipDetail(Long clipId, String translationLang);
}
