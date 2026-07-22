package com.remelearning.english.vocabulary.library.service;

import com.remelearning.english.vocabulary.library.dto.SectionAnswerResultDto;
import com.remelearning.english.vocabulary.library.dto.SectionCardDto;
import com.remelearning.english.vocabulary.library.dto.SectionHistoryEntryDto;
import com.remelearning.english.vocabulary.library.dto.StartSectionRequest;
import com.remelearning.english.vocabulary.library.dto.SubmitSectionAnswerRequest;
import com.remelearning.english.vocabulary.library.dto.TopicSummaryDto;
import com.remelearning.english.vocabulary.library.dto.VocabularyAudioResource;

import java.util.List;

public interface VocabularyLibraryService {

	List<TopicSummaryDto> listTopics(String userId);

	/** Starts a new Section for a topic, topping up its word bank first if needed; returns the first card. */
	SectionCardDto startSection(String userId, Long topicId, StartSectionRequest request);

	/** Grades the current card's answer (or acknowledges an INTRO) and returns the next card, or a completed result. */
	SectionAnswerResultDto submitAnswer(Long sectionId, SubmitSectionAnswerRequest request);

	/** Ends an in-progress Section early, feeding whatever was answered into the weak-point pipeline. */
	SectionAnswerResultDto finishSection(Long sectionId);

	List<SectionHistoryEntryDto> getSectionHistory(String userId);

	VocabularyAudioResource loadWordAudio(Long wordId);
}
