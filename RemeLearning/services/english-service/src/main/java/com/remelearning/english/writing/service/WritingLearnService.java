package com.remelearning.english.writing.service;

import com.remelearning.english.writing.domain.WritingSuggestion;
import com.remelearning.english.writing.domain.WritingTaskType;
import com.remelearning.english.writing.dto.GenerateWritingPracticeRequest;
import com.remelearning.english.writing.dto.SubmitWritingAttemptRequest;
import com.remelearning.english.writing.dto.SuggestNextSentenceRequest;
import com.remelearning.english.writing.dto.WritingAttemptDetailDto;
import com.remelearning.english.writing.dto.WritingAttemptHistoryEntryDto;
import com.remelearning.english.writing.dto.WritingAttemptResultDto;
import com.remelearning.english.writing.dto.WritingPracticeItemDto;

import java.util.List;

/**
 * Orchestrates the writing/translation skill: generating a prompt aimed at the learner's own weak
 * grammar/vocabulary points, hinting at the next sentence while they write, grading the finished
 * text, and feeding each labelled mistake back into the existing weak-point/spaced-repetition
 * pipeline. Controllers depend on this interface, not the implementation.
 */
public interface WritingLearnService {

	/** Generates one prompt for the requested task type, targeting {@code focusItems} or the learner's weak points. */
	WritingPracticeItemDto generate(String userId, GenerateWritingPracticeRequest request);

	/** One prompt, without its reference answer. */
	WritingPracticeItemDto getItem(Long itemId);

	/** A learner's generated prompts, newest first. */
	List<WritingPracticeItemDto> listItems(String userId);

	/** 2-3 hints for what to write next; empty when the AI is unavailable. */
	List<WritingSuggestion> suggest(SuggestNextSentenceRequest request);

	/** Grades a submission, records it, and pushes its labelled errors into the weak-point pipeline. */
	WritingAttemptResultDto submit(SubmitWritingAttemptRequest request);

	/** A learner's past attempts, newest first. */
	List<WritingAttemptHistoryEntryDto> getHistory(String userId);

	/** Full detail for one of the learner's own past attempts. */
	WritingAttemptDetailDto getAttemptDetail(String userId, Long attemptId);

	/**
	 * Generates a fresh prompt aimed at one past attempt's mistakes, reusing that attempt's task
	 * type/level/exam style, and returns the learner's refreshed prompt list.
	 */
	List<WritingPracticeItemDto> generatePracticeFromAttempt(String userId, Long attemptId);

	/**
	 * Shared generate-and-persist step used by {@link #generate} and the retry action, exposed so the
	 * library sub-package can target a topic's labels through the same pipeline.
	 */
	List<WritingPracticeItemDto> generatePracticeForLabels(
			String userId, WritingTaskType taskType, List<String> targetLabels, String level, String examType);
}
