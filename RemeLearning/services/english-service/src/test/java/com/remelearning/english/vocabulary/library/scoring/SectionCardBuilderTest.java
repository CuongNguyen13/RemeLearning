package com.remelearning.english.vocabulary.library.scoring;

import com.remelearning.english.vocabulary.domain.VocabularyType;
import com.remelearning.english.vocabulary.library.domain.SectionExerciseType;
import com.remelearning.english.vocabulary.library.domain.VocabularyLibraryWord;
import com.remelearning.english.vocabulary.library.dto.SectionCardDto;
import com.remelearning.english.vocabulary.library.dto.SectionProgressDto;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SectionCardBuilderTest {

	private final VocabularyLibraryWord word = VocabularyLibraryWord.builder()
			.id(1L).topicId(10L).word("reluctant").wordType(VocabularyType.ADJECTIVE).ipa("rɪˈlʌktənt")
			.meaningVi("miễn cưỡng").exampleEn("She was reluctant to admit it.").build();
	private final List<VocabularyLibraryWord> distractors = List.of(
			VocabularyLibraryWord.builder().id(2L).word("brief").meaningVi("ngắn gọn").build(),
			VocabularyLibraryWord.builder().id(3L).word("keen").meaningVi("hào hứng").build(),
			VocabularyLibraryWord.builder().id(4L).word("vague").meaningVi("mơ hồ").build());
	private final SectionProgressDto progress = SectionProgressDto.builder().totalWords(10).wordsMastered(3).wordsRemaining(7).build();

	@Test
	void buildIntroRevealsWordMeaningExampleAndAudioUnscored() {
		SectionCardDto card = SectionCardBuilder.buildIntro(100L, word, "/audio/1", progress);

		assertThat(card.getCardKind().name()).isEqualTo("INTRO");
		assertThat(card.getWord()).isEqualTo("reluctant");
		assertThat(card.getIpa()).isEqualTo("rɪˈlʌktənt");
		assertThat(card.getMeaningVi()).isEqualTo("miễn cưỡng");
		assertThat(card.getExampleEn()).isEqualTo("She was reluctant to admit it.");
		assertThat(card.getAudioUrl()).isEqualTo("/audio/1");
	}

	@Test
	void buildQuizClozeBlanksTheTargetWordAndNeverLeaksItOrAudio() {
		SectionCardDto card = SectionCardBuilder.buildQuiz(100L, word, SectionExerciseType.CLOZE, distractors, "/audio/1", progress);

		assertThat(card.getPrompt()).isEqualTo("She was ____ to admit it.");
		assertThat(card.getOptions()).isNull();
		assertThat(card.getWord()).isNull();
		assertThat(card.getMeaningVi()).isNull();
		assertThat(card.getAudioUrl()).isNull();
	}

	@Test
	void buildQuizMcqOffersFourShuffledWordOptionsIncludingTheCorrectOneWithNoAudio() {
		SectionCardDto card = SectionCardBuilder.buildQuiz(100L, word, SectionExerciseType.MCQ, distractors, "/audio/1", progress);

		assertThat(card.getOptions()).hasSize(4).contains("reluctant", "brief", "keen", "vague");
		assertThat(card.getAudioUrl()).isNull();
	}

	@Test
	void buildQuizMatchingPromptsWithTheWordItselfAndOffersMeaningOptionsWithAudioAllowed() {
		SectionCardDto card = SectionCardBuilder.buildQuiz(100L, word, SectionExerciseType.MATCHING, distractors, "/audio/1", progress);

		assertThat(card.getPrompt()).isEqualTo("reluctant");
		assertThat(card.getOptions()).hasSize(4).contains("miễn cưỡng", "ngắn gọn", "hào hứng", "mơ hồ");
		assertThat(card.getAudioUrl()).isEqualTo("/audio/1");
	}

	@Test
	void buildQuizListeningDictationHasNoOptionsAndAlwaysHasAudio() {
		SectionCardDto card = SectionCardBuilder.buildQuiz(100L, word, SectionExerciseType.LISTENING_DICTATION, distractors, "/audio/1", progress);

		assertThat(card.getOptions()).isNull();
		assertThat(card.getAudioUrl()).isEqualTo("/audio/1");
	}

	@Test
	void buildQuizTranslateEnToViShowsTheWordAsPromptAndAllowsAudioButHidesMeaning() {
		SectionCardDto card = SectionCardBuilder.buildQuiz(100L, word, SectionExerciseType.TRANSLATE_EN_TO_VI, distractors, "/audio/1", progress);

		assertThat(card.getPrompt()).isEqualTo("reluctant");
		assertThat(card.getMeaningVi()).isNull();
		assertThat(card.getAudioUrl()).isEqualTo("/audio/1");
	}

	@Test
	void buildQuizTranslateViToEnShowsTheMeaningAsPromptAndNeverPlaysAudio() {
		SectionCardDto card = SectionCardBuilder.buildQuiz(100L, word, SectionExerciseType.TRANSLATE_VI_TO_EN, distractors, "/audio/1", progress);

		assertThat(card.getPrompt()).isEqualTo("miễn cưỡng");
		assertThat(card.getAudioUrl()).isNull();
	}
}
