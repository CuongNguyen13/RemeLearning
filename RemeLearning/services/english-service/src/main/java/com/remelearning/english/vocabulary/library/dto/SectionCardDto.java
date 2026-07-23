package com.remelearning.english.vocabulary.library.dto;

import com.remelearning.english.vocabulary.library.domain.SectionCardKind;
import com.remelearning.english.vocabulary.library.domain.SectionExerciseType;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * One card in a Section's run - either an unscored {@code INTRO} flashcard (word/meaning/example/
 * audio all shown) or a graded {@code QUIZ} card (prompt/options only; word/meaning/audio are set
 * only when doing so cannot leak the answer - see {@code SectionCardBuilder}).
 */
@Getter
@Builder
public class SectionCardDto {
	private Long sectionId;
	private SectionCardKind cardKind;
	private Long libraryWordId;
	private String word;
	private String ipa;
	private String meaningVi;
	private String exampleEn;
	private String audioUrl;
	private SectionExerciseType exerciseType;
	private String prompt;
	private List<String> options;
	private SectionProgressDto progress;
}
