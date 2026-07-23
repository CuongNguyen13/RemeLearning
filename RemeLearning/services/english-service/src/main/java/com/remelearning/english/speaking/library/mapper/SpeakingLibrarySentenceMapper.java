package com.remelearning.english.speaking.library.mapper;

import com.remelearning.english.speaking.library.domain.SpeakingLibrarySentence;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SpeakingLibrarySentenceMapper {

	List<SpeakingLibrarySentence> findBySectionId(@Param("sectionId") Long sectionId);

	SpeakingLibrarySentence findById(@Param("id") Long id);

	/** Inserts one sentence; the generated id is written back into {@code sentence.id}. */
	void insert(SpeakingLibrarySentence sentence);
}
