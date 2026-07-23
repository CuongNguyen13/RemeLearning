package com.remelearning.english.listening.library.mapper;

import com.remelearning.english.listening.library.domain.ListeningLibrarySection;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ListeningLibrarySectionMapper {

	List<ListeningLibrarySection> findByTopicId(@Param("topicId") Long topicId);

	ListeningLibrarySection findById(@Param("id") Long id);

	/** Inserts one section; the generated id is written back into {@code section.id}. */
	void insert(ListeningLibrarySection section);
}
