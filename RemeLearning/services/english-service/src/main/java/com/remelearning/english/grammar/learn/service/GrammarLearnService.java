package com.remelearning.english.grammar.learn.service;

import com.remelearning.english.grammar.learn.dto.GenerateGrammarPracticeRequest;
import com.remelearning.english.grammar.learn.dto.SubmitGrammarAttemptRequest;
import com.remelearning.english.grammar.learn.dto.GrammarAttemptDetailDto;
import com.remelearning.english.grammar.learn.dto.GrammarAttemptHistoryEntryDto;
import com.remelearning.english.grammar.learn.dto.GrammarAttemptResultDto;
import com.remelearning.english.grammar.learn.dto.GrammarPracticeItemDto;

import java.util.List;

public interface GrammarLearnService {

	GrammarPracticeItemDto generate(String userId, GenerateGrammarPracticeRequest request);

	GrammarPracticeItemDto getItem(Long itemId);

	List<GrammarPracticeItemDto> listItems(String userId);

	GrammarAttemptResultDto submit(SubmitGrammarAttemptRequest request);

	List<GrammarAttemptHistoryEntryDto> getHistory(String userId);

	GrammarAttemptDetailDto getAttemptDetail(String userId, Long attemptId);

	/**
	 * Generates one new AI practice set targeting the given grammar rules and persists it into the
	 * shared {@code grammar_practice_items} bank, returning the learner's refreshed practice-set
	 * list - the common persistence step both {@link #generatePracticeFromAttempt} and the Grammar
	 * Library's own "generate from session" flow delegate to, so both surfaces feed the same bank.
	 */
	List<GrammarPracticeItemDto> generatePracticeForRules(String userId, List<String> targetRules, String level, String examType);

	/**
	 * Generates one new AI practice set targeting one specific past attempt's missed rules (the
	 * "Luyện tập với AI" action from a history row), persists it into the shared practice-item bank,
	 * and returns the learner's refreshed practice-set list. Throws not-found if the attempt doesn't
	 * exist or belongs to someone else.
	 */
	List<GrammarPracticeItemDto> generatePracticeFromAttempt(String userId, Long attemptId);
}
