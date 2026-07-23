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
}
