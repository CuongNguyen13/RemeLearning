package com.remelearning.english.grammar.library.service;

import com.remelearning.english.grammar.library.dto.FinishGrammarLibrarySessionResponse;
import com.remelearning.english.grammar.library.dto.GrammarLibraryAnswerResultDto;
import com.remelearning.english.grammar.library.dto.GrammarLibraryContentDto;
import com.remelearning.english.grammar.library.dto.GrammarLibraryHistoryEntryDto;
import com.remelearning.english.grammar.library.dto.GrammarLibraryTopicDto;
import com.remelearning.english.grammar.library.dto.StartGrammarSessionResponse;
import com.remelearning.english.grammar.library.dto.SubmitGrammarLibraryAnswerRequest;
import com.remelearning.english.grammar.learn.dto.GrammarPracticeItemDto;

import java.util.List;

public interface GrammarLibraryService {

	/** All 60 catalog topics with this learner's own progression status, bootstrapping the first topic to UNLOCKED if needed. */
	List<GrammarLibraryTopicDto> listTopics(String userId);

	/** The topic's theory page + question pool, generating it via AI on first read only. */
	GrammarLibraryContentDto getTopicContent(Long topicId);

	/** Starts a fresh INITIAL session for the topic; the topic must be UNLOCKED or IN_PROGRESS. */
	StartGrammarSessionResponse startSession(String userId, Long topicId);

	/** Grades one submitted answer within an in-progress session. */
	GrammarLibraryAnswerResultDto submitAnswer(Long sessionId, SubmitGrammarLibraryAnswerRequest request);

	/** Finishes a session: PASSED + next-topic unlock when all correct, otherwise a new RETRY session for the missed questions. */
	FinishGrammarLibrarySessionResponse finishSession(Long sessionId);

	/** A learner's completed sessions for one topic, newest first. */
	List<GrammarLibraryHistoryEntryDto> getHistory(String userId, Long topicId);

	/**
	 * Generates one new AI practice set targeting a past session's missed questions (the "Luyện tập
	 * với AI" action from a library session), persisting it into the same
	 * {@code grammar_practice_items} bank the "học thường" learn flow uses - there is only one
	 * AI-practice destination per domain, regardless of which flow the mistake came from. Returns the
	 * learner's refreshed practice-set list (same DTO shape as the learn flow's own listing). Throws
	 * not-found if the session doesn't exist or belongs to someone else.
	 */
	List<GrammarPracticeItemDto> generatePracticeFromSession(String userId, Long sessionId);
}
