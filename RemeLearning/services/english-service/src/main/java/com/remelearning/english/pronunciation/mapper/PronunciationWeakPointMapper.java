package com.remelearning.english.pronunciation.mapper;

import com.remelearning.english.pronunciation.domain.PronunciationWeakPoint;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface PronunciationWeakPointMapper {

	/** Insert-or-refresh keyed by (userId, itemId): re-analysis of the same item updates its score. */
	void upsert(PronunciationWeakPoint point);

	/** {@code pronunciationType} is optional (null = no filter); results ordered by forgetting_score desc. */
	List<PronunciationWeakPoint> findByUserId(
			@Param("userId") String userId, @Param("pronunciationType") String pronunciationType);
}
