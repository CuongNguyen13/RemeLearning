package com.remelearning.english.grammar.mapper;

import com.remelearning.english.grammar.domain.GrammarWeakPoint;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface GrammarWeakPointMapper {

	/** Insert-or-refresh keyed by (userId, itemId): re-analysis of the same item updates its score. */
	void upsert(GrammarWeakPoint point);

	/** {@code grammarType} is optional (null = no filter); results ordered by forgetting_score desc. */
	List<GrammarWeakPoint> findByUserId(@Param("userId") String userId, @Param("grammarType") String grammarType);
}
