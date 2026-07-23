package com.remelearning.english.grammar.library.mapper;

import com.remelearning.english.grammar.library.domain.GrammarLibraryQuestion;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface GrammarLibraryQuestionMapper {

	/** Inserts one pool question; the generated id is written back into {@code question.id}. */
	void insert(GrammarLibraryQuestion question);

	List<GrammarLibraryQuestion> findByTopicId(@Param("topicId") Long topicId);
}
