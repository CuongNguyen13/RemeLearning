package com.remelearning.english.listening.library.mapper;

import com.remelearning.english.listening.library.domain.ListeningLibraryTopic;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ListeningLibraryTopicMapper {

	List<ListeningLibraryTopic> findAll();

	ListeningLibraryTopic findById(@Param("id") Long id);

	/** Null if there is no topic at that sequence position (e.g. past the last topic). */
	ListeningLibraryTopic findBySequenceOrder(@Param("sequenceOrder") int sequenceOrder);
}
