package com.remelearning.english.speaking.history.service;

import com.remelearning.english.speaking.history.dto.SpeakingHistoryEntryDto;

import java.util.List;

public interface SpeakingHistoryService {

	/**
	 * Merges a learner's "học thường" (learn) attempt history and "Thư viện" (library) sentence
	 * attempt history into one list, sorted newest-first by completion time.
	 */
	List<SpeakingHistoryEntryDto> getMergedHistory(String userId);
}
