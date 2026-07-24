package com.remelearning.english.speaking.library.service;

import com.remelearning.english.speaking.dto.SpeakingPracticeItemDto;
import com.remelearning.english.speaking.library.domain.SpeakingLibraryAttempt;
import com.remelearning.english.speaking.library.dto.FinishSectionResponse;
import com.remelearning.english.speaking.library.dto.SentenceAttemptResultDto;
import com.remelearning.english.speaking.library.dto.SpeakingLibrarySectionDto;
import com.remelearning.english.speaking.library.dto.SpeakingLibraryTopicDto;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Orchestrates the Speaking Library skill: a fixed catalog of topics, gated per learner
 * (LOCKED/UNLOCKED/IN_PROGRESS/PASSED), each with an AI-generated Section (a pool of sample
 * sentences with IPA + sample audio), scored one sentence at a time via the same GOP
 * (Goodness-of-Pronunciation) scoring service {@code speaking.learn} uses - unlike
 * {@code ListeningLibraryService}, gating only advances via a separate {@link #finishSection}
 * call, not on every submitted attempt.
 */
public interface SpeakingLibraryService {

	/** All catalog topics with this learner's gating status (bootstraps the first topic to UNLOCKED if needed). */
	List<SpeakingLibraryTopicDto> getTopics(String userId);

	/** Starts (or resumes, if one already exists) the Section for a topic; rejects LOCKED topics. */
	SpeakingLibrarySectionDto startOrResumeSection(String userId, Long topicId);

	/**
	 * Scores one recorded sentence attempt via the reused GOP scoring service and persists it; does
	 * not itself touch topic progress - see {@link #finishSection}.
	 */
	SentenceAttemptResultDto submitSentenceAttempt(String userId, Long sectionId, Long sentenceId, MultipartFile recordedAudio);

	/**
	 * Checks whether every sentence in the section has at least one attempt scoring above threshold
	 * on both phoneme and word score; if so, marks the topic PASSED and unlocks the next topic.
	 */
	FinishSectionResponse finishSection(String userId, Long sectionId);

	/** This learner's scored sentence attempts, across all topics/sections. */
	List<SpeakingLibraryAttempt> getHistory(String userId);

	/**
	 * Resolves the owning topicId for a section (the merged-history "Làm lại" deep-link target,
	 * since {@code SpeakingLibraryAttempt} only carries {@code sectionId}). Returns {@code null} if
	 * the section no longer exists rather than throwing, so a stale history row degrades to the
	 * library topic list instead of erroring.
	 */
	Long resolveTopicId(Long sectionId);

	/**
	 * Generates AI practice targeted at this learner's own mispronunciations across every sentence
	 * attempt they've made on this section (unioned - speaking-library scores per-sentence, not
	 * per-section like {@code ListeningLibraryService}, so multiple attempts/sentences can each
	 * contribute their own weak phonemes). Persists into the same {@code speaking_practice_items}
	 * bank the learn flow uses. Empty if the learner has no attempts on this section, or none of
	 * them had a mispronounced phoneme.
	 */
	List<SpeakingPracticeItemDto> generatePracticeFromSection(String userId, Long sectionId);
}
