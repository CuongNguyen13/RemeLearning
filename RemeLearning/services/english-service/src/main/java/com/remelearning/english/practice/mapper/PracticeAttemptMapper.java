package com.remelearning.english.practice.mapper;

import com.remelearning.english.practice.domain.PracticeAttempt;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PracticeAttemptMapper {

	/** Audit-log insert only; never read back by the scoring pipeline. */
	void insert(PracticeAttempt attempt);
}
