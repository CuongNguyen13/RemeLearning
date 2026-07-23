package com.remelearning.english.grammar.library.service;

import com.remelearning.english.grammar.library.dto.FinishGrammarLibrarySessionResponse;
import com.remelearning.english.grammar.library.dto.GrammarLibraryAnswerResultDto;
import com.remelearning.english.grammar.library.dto.GrammarLibraryContentDto;
import com.remelearning.english.grammar.library.dto.GrammarLibraryHistoryEntryDto;
import com.remelearning.english.grammar.library.dto.GrammarLibraryTopicDto;
import com.remelearning.english.grammar.library.dto.StartGrammarSessionResponse;
import com.remelearning.english.grammar.library.dto.SubmitGrammarLibraryAnswerRequest;

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
}
