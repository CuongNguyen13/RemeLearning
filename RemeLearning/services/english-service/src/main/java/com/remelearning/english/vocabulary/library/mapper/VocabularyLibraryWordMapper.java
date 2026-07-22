package com.remelearning.english.vocabulary.library.mapper;

import com.remelearning.english.vocabulary.library.domain.VocabularyLibraryWord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface VocabularyLibraryWordMapper {

	/** Inserts one word; the generated id is written back into {@code word.id}. */
	void insert(VocabularyLibraryWord word);

	VocabularyLibraryWord findById(@Param("id") Long id);

	List<VocabularyLibraryWord> findByTopicId(@Param("topicId") Long topicId);

	int countByTopicId(@Param("topicId") Long topicId);

	/** Bare word strings already in a topic, so a top-up generation call can avoid repeating them. */
	List<String> findWordsByTopicId(@Param("topicId") Long topicId);

	/** Words in a topic this learner has not yet mastered (or never attempted), soonest-picked first, random order. */
	List<VocabularyLibraryWord> findNotYetMasteredByTopicId(
			@Param("topicId") Long topicId, @Param("userId") String userId, @Param("limit") int limit);

	/** Random words in a topic excluding the given ids - used both to fill out a section and to sample MCQ/MATCHING distractors. */
	List<VocabularyLibraryWord> findRandomByTopicIdExcluding(
			@Param("topicId") Long topicId, @Param("excludeIds") List<Long> excludeIds, @Param("limit") int limit);

	void updateAudioStorageKey(@Param("id") Long id, @Param("audioStorageKey") String audioStorageKey);
}
