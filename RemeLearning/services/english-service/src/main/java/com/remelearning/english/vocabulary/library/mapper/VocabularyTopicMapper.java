package com.remelearning.english.vocabulary.library.mapper;

import com.remelearning.english.vocabulary.library.domain.TopicMasterySummaryRow;
import com.remelearning.english.vocabulary.library.domain.VocabularyTopic;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface VocabularyTopicMapper {

	List<VocabularyTopic> findAll();

	VocabularyTopic findById(@Param("id") Long id);

	/** Word count and mastered-count (mastery_level >= 0.7 in vocabulary_weak_points) per topic, for this learner. */
	List<TopicMasterySummaryRow> findMasterySummaryByUserId(@Param("userId") String userId);
}
