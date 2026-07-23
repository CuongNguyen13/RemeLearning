package com.remelearning.english.speaking.library.mapper;

import com.remelearning.english.speaking.library.domain.SpeakingLibrarySection;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SpeakingLibrarySectionMapper {

	List<SpeakingLibrarySection> findByTopicId(@Param("topicId") Long topicId);

	SpeakingLibrarySection findById(@Param("id") Long id);

	/** Inserts one section; the generated id is written back into {@code section.id}. */
	void insert(SpeakingLibrarySection section);
}
