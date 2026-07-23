package com.remelearning.english.listening.library.mapper;

import com.remelearning.english.listening.library.domain.ListeningLibraryQuestion;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ListeningLibraryQuestionMapper {

	/** Inserts one question; the generated id is written back into {@code question.id}. */
	void insert(ListeningLibraryQuestion question);

	List<ListeningLibraryQuestion> findBySectionId(@Param("sectionId") Long sectionId);
}
