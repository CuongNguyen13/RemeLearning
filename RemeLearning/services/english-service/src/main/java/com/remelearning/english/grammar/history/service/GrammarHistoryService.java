package com.remelearning.english.grammar.history.service;

import com.remelearning.english.grammar.history.dto.GrammarHistoryEntryDto;

import java.util.List;

public interface GrammarHistoryService {

	/**
	 * Merges a learner's "học thường" (learn) attempt history and "Thư viện" (library) session
	 * history into one list, sorted newest-first by completion time.
	 */
	List<GrammarHistoryEntryDto> getMergedHistory(String userId);
}
