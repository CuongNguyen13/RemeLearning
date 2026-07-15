package com.remelearning.recommendation.mapper;

import com.remelearning.recommendation.domain.Recommendation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface RecommendationMapper {

	/** Insert-or-refresh keyed by (userId, itemId): re-analysis of the same item updates its score. */
	void upsert(Recommendation recommendation);

	/** {@code category} is optional (null = no filter); results ordered by forgetting_score desc. */
	List<Recommendation> findByUserId(@Param("userId") String userId, @Param("category") String category);
}
