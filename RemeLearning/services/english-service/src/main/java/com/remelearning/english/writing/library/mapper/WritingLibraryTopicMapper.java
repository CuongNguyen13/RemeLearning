package com.remelearning.english.writing.library.mapper;

import com.remelearning.english.writing.library.domain.WritingLibraryTopic;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface WritingLibraryTopicMapper {

	WritingLibraryTopic findById(@Param("topicId") Long topicId);

	/** Every topic on one axis, in that axis's own sequence order. */
	List<WritingLibraryTopic> findByTaxonomy(@Param("taxonomy") String taxonomy);

	/**
	 * Looks up by position WITHIN one axis - sequence_order is only unique per taxonomy, so the axis
	 * must be part of the key or unlocking would jump between axes.
	 */
	WritingLibraryTopic findByTaxonomyAndSequenceOrder(
			@Param("taxonomy") String taxonomy, @Param("sequenceOrder") int sequenceOrder);
}
