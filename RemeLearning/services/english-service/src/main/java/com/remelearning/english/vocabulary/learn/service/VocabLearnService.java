package com.remelearning.english.vocabulary.learn.service;

import com.remelearning.english.vocabulary.learn.dto.GenerateVocabPracticeRequest;
import com.remelearning.english.vocabulary.learn.dto.SubmitVocabAttemptRequest;
import com.remelearning.english.vocabulary.learn.dto.VocabAttemptDetailDto;
import com.remelearning.english.vocabulary.learn.dto.VocabAttemptHistoryEntryDto;
import com.remelearning.english.vocabulary.learn.dto.VocabAttemptResultDto;
import com.remelearning.english.vocabulary.learn.dto.VocabPracticeItemDto;

import java.util.List;

public interface VocabLearnService {

	VocabPracticeItemDto generate(String userId, GenerateVocabPracticeRequest request);

	VocabPracticeItemDto getItem(Long itemId);

	List<VocabPracticeItemDto> listItems(String userId);

	VocabAttemptResultDto submit(SubmitVocabAttemptRequest request);

	List<VocabAttemptHistoryEntryDto> getHistory(String userId);

	VocabAttemptDetailDto getAttemptDetail(String userId, Long attemptId);
}
