package com.remelearning.english.grammar.library.mapper;

import com.remelearning.english.grammar.library.domain.GrammarLibraryTopic;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface GrammarLibraryTopicMapper {

	List<GrammarLibraryTopic> findAll();

	GrammarLibraryTopic findById(@Param("id") Long id);

	/** Null if there is no topic at that sequence position (e.g. past the last topic). */
	GrammarLibraryTopic findBySequenceOrder(@Param("sequenceOrder") int sequenceOrder);
}
