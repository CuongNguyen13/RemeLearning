package com.remelearning.english.writing.library.service;

import com.remelearning.english.writing.domain.WritingTaskType;
import com.remelearning.english.writing.dto.WritingPracticeItemDto;
import com.remelearning.english.writing.library.dto.SubmitWritingLibraryAnswerRequest;
import com.remelearning.english.writing.library.dto.SubmitWritingLibraryAnswerResponse;
import com.remelearning.english.writing.library.dto.WritingLibraryPromptDto;
import com.remelearning.english.writing.library.dto.WritingLibraryTopicDto;

import java.util.List;

/**
 * The fixed-catalogue "Thư viện" side of the writing skill: browsing topics along one of three
 * taxonomy axes, working through a topic's prompt chain, and unlocking the next topic on that same
 * axis. Controllers depend on this interface, not the implementation.
 */
public interface WritingLibraryService {

	/** Every topic on one axis with this learner's gating status and chain progress. */
	List<WritingLibraryTopicDto> getTopics(String userId, String taxonomy);

	/**
	 * The next not-yet-passed prompt in the topic's chain, generating one via AI when the chain hasn't
	 * reached its target length yet; resumes an already-started prompt otherwise.
	 */
	WritingLibraryPromptDto startOrResumePrompt(String userId, Long topicId, WritingTaskType taskType);

	/** Grades a submitted text, feeds its errors into the weak-point pipeline, and advances progress. */
	SubmitWritingLibraryAnswerResponse submitAnswer(
			String userId, Long promptId, SubmitWritingLibraryAnswerRequest request);

	/**
	 * Generates "học thường" AI practice aimed at one library attempt's own mistakes, landing in the
	 * same {@code writing_practice_items} bank the learn flow uses.
	 */
	List<WritingPracticeItemDto> generatePracticeFromAttempt(String userId, Long attemptId);
}
