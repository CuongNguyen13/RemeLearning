package com.remelearning.english.vocabulary.mapper;

import com.remelearning.english.vocabulary.domain.VocabularyWeakPoint;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface VocabularyWeakPointMapper {

	/** Insert-or-refresh keyed by (userId, itemId): re-analysis of the same item updates its score. */
	void upsert(VocabularyWeakPoint point);

	/** {@code vocabularyType} is optional (null = no filter); results ordered by forgetting_score desc. */
	List<VocabularyWeakPoint> findByUserId(@Param("userId") String userId, @Param("vocabularyType") String vocabularyType);
}
