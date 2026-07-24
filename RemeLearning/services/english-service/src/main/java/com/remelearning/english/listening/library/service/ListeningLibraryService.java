package com.remelearning.english.listening.library.service;

import com.remelearning.english.listening.dto.ListeningPracticeItemDto;
import com.remelearning.english.listening.library.domain.ListeningLibraryAttempt;
import com.remelearning.english.listening.library.dto.ListeningLibrarySectionDto;
import com.remelearning.english.listening.library.dto.ListeningLibraryTopicDto;
import com.remelearning.english.listening.library.dto.SubmitListeningAnswersRequest;
import com.remelearning.english.listening.library.dto.SubmitListeningAnswersResponse;

import java.util.List;

/**
 * Orchestrates the Listening Library skill: a fixed catalog of topics, gated per learner
 * (LOCKED/UNLOCKED/IN_PROGRESS/PASSED), each with an AI-generated passage+audio Section and its
 * reusable multiple-choice question pool, scored on submission with next-topic unlocking on pass.
 */
public interface ListeningLibraryService {

	/** All catalog topics with this learner's gating status (bootstraps the first topic to UNLOCKED if needed). */
	List<ListeningLibraryTopicDto> getTopics(String userId);

	/** Starts (or resumes, if one already exists) the Section for a topic; rejects LOCKED topics. */
	ListeningLibrarySectionDto startOrResumeSection(String userId, Long topicId);

	/** Scores a submitted answer set for one section, persists the attempt, and unlocks the next topic on pass. */
	SubmitListeningAnswersResponse submitAnswers(String userId, Long sectionId, SubmitListeningAnswersRequest req);

	/** This learner's completed attempts, across all topics/sections. */
	List<ListeningLibraryAttempt> getHistory(String userId);

	/**
	 * Generates AI practice targeted at this learner's own most recent completed attempt on one
	 * section's missed questions (the "Luyện tập với AI" action). A section has no per-question
	 * topic tag of its own, so the target is the section's owning topic name - persists into the
	 * same {@code listening_practice_items} bank the learn flow uses. Returns an empty list if the
	 * learner has no completed attempt on this section, or if their most recent attempt had no
	 * mistakes.
	 */
	List<ListeningPracticeItemDto> generatePracticeFromSection(String userId, Long sectionId);
}
