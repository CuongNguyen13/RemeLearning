package com.remelearning.english.speaking.service;

import com.remelearning.english.speaking.dto.GenerateSpeakingPracticeRequest;
import com.remelearning.english.speaking.dto.SpeakingAttemptDetailDto;
import com.remelearning.english.speaking.dto.SpeakingAttemptHistoryEntryDto;
import com.remelearning.english.speaking.dto.SpeakingAttemptResultDto;
import com.remelearning.english.speaking.dto.SpeakingAudioResource;
import com.remelearning.english.speaking.dto.SpeakingPracticeItemDto;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface SpeakingLearnService {

	SpeakingPracticeItemDto generate(String userId, GenerateSpeakingPracticeRequest request);

	SpeakingPracticeItemDto getItem(Long itemId);

	List<SpeakingPracticeItemDto> listItems(String userId);

	SpeakingAudioResource loadSampleAudio(Long itemId);

	SpeakingAttemptResultDto submit(String userId, Long practiceItemId, MultipartFile audio);

	List<SpeakingAttemptHistoryEntryDto> getHistory(String userId);

	SpeakingAttemptDetailDto getAttemptDetail(String userId, Long attemptId);
}
