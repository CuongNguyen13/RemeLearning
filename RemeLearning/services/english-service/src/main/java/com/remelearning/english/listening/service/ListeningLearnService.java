package com.remelearning.english.listening.service;

import com.remelearning.english.listening.dto.GenerateListeningPracticeRequest;
import com.remelearning.english.listening.dto.ListeningAttemptDetailDto;
import com.remelearning.english.listening.dto.ListeningAttemptHistoryEntryDto;
import com.remelearning.english.listening.dto.ListeningAttemptResultDto;
import com.remelearning.english.listening.dto.ListeningAudioResource;
import com.remelearning.english.listening.dto.ListeningPracticeItemDto;
import com.remelearning.english.listening.dto.SubmitListeningAttemptRequest;

import java.util.List;

public interface ListeningLearnService {

	ListeningPracticeItemDto generate(String userId, GenerateListeningPracticeRequest request);

	ListeningPracticeItemDto getItem(Long itemId);

	List<ListeningPracticeItemDto> listItems(String userId);

	ListeningAudioResource loadAudio(Long itemId);

	ListeningAttemptResultDto submit(SubmitListeningAttemptRequest request);

	List<ListeningAttemptHistoryEntryDto> getHistory(String userId);

	ListeningAttemptDetailDto getAttemptDetail(String userId, Long attemptId);

	/**
	 * Shared generate-and-persist step: builds one {@link ListeningPracticeItemDto} targeting the
	 * given keywords/level/exam type (same {@code listening_practice_items} bank {@link #generate}
	 * uses) and returns the learner's refreshed practice-set list. Used both by
	 * {@link #generatePracticeFromAttempt} and by the Listening Library flow
	 * ({@code ListeningLibraryService#generatePracticeFromSection}).
	 */
	List<ListeningPracticeItemDto> generatePracticeForKeywords(String userId, List<String> targetKeywords, String level, String examType);

	/** Generates AI practice targeted at one past learn-flow attempt's missed questions. */
	List<ListeningPracticeItemDto> generatePracticeFromAttempt(String userId, Long attemptId);
}
