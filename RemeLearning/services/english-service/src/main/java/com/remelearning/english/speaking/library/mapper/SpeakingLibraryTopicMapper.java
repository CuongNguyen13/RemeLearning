package com.remelearning.english.speaking.library.mapper;

import com.remelearning.english.speaking.library.domain.SpeakingLibraryTopic;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SpeakingLibraryTopicMapper {

	List<SpeakingLibraryTopic> findAll();

	SpeakingLibraryTopic findById(@Param("id") Long id);

	/** Null if there is no topic at that sequence position (e.g. past the last topic). */
	SpeakingLibraryTopic findBySequenceOrder(@Param("sequenceOrder") int sequenceOrder);
}
