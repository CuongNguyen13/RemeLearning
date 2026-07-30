package com.remelearning.english.writing.library.mapper;

import com.remelearning.english.writing.library.domain.WritingLibraryPrompt;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface WritingLibraryPromptMapper {

	/** Inserts one prompt; the generated id is written back into {@code prompt.id}. */
	void insert(WritingLibraryPrompt prompt);

	WritingLibraryPrompt findById(@Param("promptId") Long promptId);

	/** A topic's prompt chain, oldest-first (the order the learner works through it). */
	List<WritingLibraryPrompt> findByTopicId(@Param("topicId") Long topicId);
}
