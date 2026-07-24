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

	/**
	 * Shared generate-and-persist step: builds one {@link SpeakingPracticeItemDto} targeting the
	 * given words/sounds/level/exam type (same {@code speaking_practice_items} bank {@link #generate}
	 * uses) and returns the learner's refreshed practice-set list. Used both by
	 * {@link #generatePracticeFromAttempt} and by the Speaking Library flow
	 * ({@code SpeakingLibraryService#generatePracticeFromSection}).
	 */
	List<SpeakingPracticeItemDto> generatePracticeForKeywords(String userId, List<String> targetWords, String level, String examType);

	/** Generates AI practice targeted at one past learn-flow attempt's weak phonemes. */
	List<SpeakingPracticeItemDto> generatePracticeFromAttempt(String userId, Long attemptId);
}
