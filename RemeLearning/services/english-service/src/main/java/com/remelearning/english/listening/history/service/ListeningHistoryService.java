package com.remelearning.english.listening.history.service;

import com.remelearning.english.listening.history.dto.ListeningHistoryEntryDto;

import java.util.List;

public interface ListeningHistoryService {

	/**
	 * Merges a learner's "học thường" (learn) attempt history and "Thư viện" (library) section
	 * attempt history into one list, sorted newest-first by completion time.
	 */
	List<ListeningHistoryEntryDto> getMergedHistory(String userId);
}
