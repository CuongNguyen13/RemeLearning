package com.remelearning.english.vocabulary.library.scoring;

import com.remelearning.english.vocabulary.library.domain.SectionCardKind;
import com.remelearning.english.vocabulary.library.domain.SectionExerciseType;
import com.remelearning.english.vocabulary.library.domain.VocabularyLibraryWord;
import com.remelearning.english.vocabulary.library.dto.SectionCardDto;
import com.remelearning.english.vocabulary.library.dto.SectionProgressDto;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * Builds the client-facing {@link SectionCardDto} for one queue entry - either the unscored INTRO
 * flashcard or one of the six QUIZ shapes. Deliberately never sets a field on the QUIZ path that
 * would hand the learner the answer (see the design spec's leak table): CLOZE/MCQ/LISTENING_
 * DICTATION/TRANSLATE_VI_TO_EN never get {@code audioUrl}, and no QUIZ card ever gets {@code word}/
 * {@code meaningVi} except where that value already *is* the visible prompt.
 */
public final class SectionCardBuilder {

	private SectionCardBuilder() {
	}

	/** The unscored first-exposure flashcard: everything about the word is shown. */
	public static SectionCardDto buildIntro(Long sectionId, VocabularyLibraryWord word, String audioUrl, SectionProgressDto progress) {
		return SectionCardDto.builder()
				.sectionId(sectionId).cardKind(SectionCardKind.INTRO).libraryWordId(word.getId())
				.word(word.getWord()).ipa(word.getIpa()).meaningVi(word.getMeaningVi()).exampleEn(word.getExampleEn())
				.audioUrl(audioUrl).progress(progress).build();
	}

	/** One graded QUIZ card of the given type; {@code distractorWords} must have at least 3 entries for MCQ/MATCHING. */
	public static SectionCardDto buildQuiz(Long sectionId, VocabularyLibraryWord word, SectionExerciseType type,
			List<VocabularyLibraryWord> distractorWords, String audioUrl, SectionProgressDto progress) {
		SectionCardDto.SectionCardDtoBuilder builder = SectionCardDto.builder()
				.sectionId(sectionId).cardKind(SectionCardKind.QUIZ).libraryWordId(word.getId())
				.exerciseType(type).progress(progress);

		switch (type) {
			case CLOZE -> builder.prompt(blank(word));
			case MCQ -> builder.prompt(blank(word))
					.options(shuffledOptions(word.getWord(), distractorWords, VocabularyLibraryWord::getWord));
			case MATCHING -> builder.prompt(word.getWord())
					.options(shuffledOptions(word.getMeaningVi(), distractorWords, VocabularyLibraryWord::getMeaningVi))
					.audioUrl(audioUrl);
			case LISTENING_DICTATION -> builder.prompt("Nghe và gõ lại từ bạn nghe được.").audioUrl(audioUrl);
			case TRANSLATE_EN_TO_VI -> builder.prompt(word.getWord()).audioUrl(audioUrl);
			case TRANSLATE_VI_TO_EN -> builder.prompt(word.getMeaningVi());
		}
		return builder.build();
	}

	// Case-insensitive whole-word replace of the target word with a blank, for CLOZE/MCQ prompts.
	private static String blank(VocabularyLibraryWord word) {
		return word.getExampleEn().replaceAll("(?i)\\b" + Pattern.quote(word.getWord()) + "\\b", "____");
	}

	// Combines the correct value with the sibling words' corresponding field, then shuffles.
	private static List<String> shuffledOptions(String correct, List<VocabularyLibraryWord> distractorWords,
			Function<VocabularyLibraryWord, String> field) {
		List<String> options = new ArrayList<>(distractorWords.stream().map(field).toList());
		options.add(correct);
		Collections.shuffle(options);
		return options;
	}
}
