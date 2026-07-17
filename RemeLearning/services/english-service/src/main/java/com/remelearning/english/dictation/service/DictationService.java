package com.remelearning.english.dictation.service;

import com.remelearning.english.dictation.dto.DictationAttemptRequest;
import com.remelearning.english.dictation.dto.DictationAttemptResultDto;
import com.remelearning.english.dictation.dto.DictationAudioResource;
import com.remelearning.english.dictation.dto.DictationClipDto;
import com.remelearning.english.dictation.dto.DictationFacetsDto;
import com.remelearning.english.dictation.dto.DictationHistoryEntryDto;
import com.remelearning.english.dictation.dto.DictationPracticeItemDto;
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

	/** The learner's AI-practice items (audio URL present once synthesized). */
	List<DictationPracticeItemDto> getAiPractice(String userId);

	/**
	 * Ensures the learner has AI-practice clips with audio: synthesizes any pending items (or first
	 * generates new ones from their most-missed words), then returns the full list.
	 */
	List<DictationPracticeItemDto> generateAiPractice(String userId);

	/** Streams one AI-practice item's synthesized audio from storage. */
	DictationAudioResource loadPracticeAudio(Long practiceItemId);
}
