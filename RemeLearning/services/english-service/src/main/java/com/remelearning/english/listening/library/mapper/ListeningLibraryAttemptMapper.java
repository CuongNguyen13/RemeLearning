package com.remelearning.english.listening.library.mapper;

import com.remelearning.english.listening.library.domain.ListeningLibraryAttempt;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ListeningLibraryAttemptMapper {

	/** Inserts one completed attempt; the generated id is written back into {@code attempt.id}. */
	void insert(ListeningLibraryAttempt attempt);

	List<ListeningLibraryAttempt> findByUserId(@Param("userId") String userId);
}
