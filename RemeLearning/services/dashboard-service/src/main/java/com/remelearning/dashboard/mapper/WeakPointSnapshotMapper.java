package com.remelearning.dashboard.mapper;

import com.remelearning.dashboard.domain.WeakPointSnapshot;
import com.remelearning.dashboard.dto.CategoryProgress;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface WeakPointSnapshotMapper {

	/** Insert-or-refresh keyed by (userId, itemId): re-analysis of the same item updates its score in place. */
	void upsert(WeakPointSnapshot snapshot);

	/** Per-category count/avg forgetting score/last-updated, computed at read time via GROUP BY (not a running counter). */
	List<CategoryProgress> selectProgressSummary(@Param("userId") String userId);
}
