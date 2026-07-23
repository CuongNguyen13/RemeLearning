package com.remelearning.english.grammar.library.mapper;

import com.remelearning.english.grammar.library.domain.GrammarLibraryContent;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface GrammarLibraryContentMapper {

	/** Null if the topic's theory page has not been AI-generated yet. */
	GrammarLibraryContent findByTopicId(@Param("topicId") Long topicId);

	/** Inserts one topic's theory page; the generated id is written back into {@code content.id}. */
	void insert(GrammarLibraryContent content);
}
