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
}
