package com.remelearning.english.listening.weakpoint.mapper;

import com.remelearning.english.listening.weakpoint.domain.ListeningWeakPoint;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ListeningWeakPointMapper {

	/** Insert-or-refresh keyed by (userId, itemId): re-analysis of the same item updates its score. */
	void upsert(ListeningWeakPoint point);

	/** {@code sourceType} is optional (null = no filter); results ordered by forgetting_score desc. */
	List<ListeningWeakPoint> findByUserId(@Param("userId") String userId, @Param("sourceType") String sourceType);
}
