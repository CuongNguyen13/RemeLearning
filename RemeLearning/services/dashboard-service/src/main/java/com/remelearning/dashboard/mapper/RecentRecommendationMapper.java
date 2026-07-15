package com.remelearning.dashboard.mapper;

import com.remelearning.dashboard.domain.RecommendationSnapshot;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface RecentRecommendationMapper {

	/** Insert-or-refresh keyed by (userId, itemId): re-recommending the same item refreshes it in place. */
	void upsert(RecommendationSnapshot snapshot);

	/** Most recent recommendations for a learner, newest first, capped at {@code limit}. */
	List<RecommendationSnapshot> findRecentByUserId(@Param("userId") String userId, @Param("limit") int limit);
}
