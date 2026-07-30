package com.remelearning.english.practice.session.service;

import com.remelearning.english.grammar.domain.GrammarWeakPoint;
import com.remelearning.english.grammar.learn.dto.GenerateGrammarPracticeRequest;
import com.remelearning.english.grammar.learn.dto.GrammarPracticeItemDto;
import com.remelearning.english.grammar.learn.service.GrammarLearnService;
import com.remelearning.english.grammar.service.GrammarWeakPointService;
import com.remelearning.english.listening.dto.GenerateListeningPracticeRequest;
import com.remelearning.english.listening.dto.ListeningPracticeItemDto;
import com.remelearning.english.listening.service.ListeningLearnService;
import com.remelearning.english.listening.weakpoint.domain.ListeningWeakPoint;
import com.remelearning.english.listening.weakpoint.service.ListeningWeakPointService;
import com.remelearning.english.practice.session.domain.PracticeSession;
import com.remelearning.english.practice.session.domain.PracticeSessionExercise;
import com.remelearning.english.practice.session.domain.PracticeSessionStatus;
import com.remelearning.english.practice.session.dto.PracticeSessionDto;
import com.remelearning.english.practice.session.mapper.PracticeSessionMapper;
import com.remelearning.english.pronunciation.domain.PronunciationWeakPoint;
import com.remelearning.english.pronunciation.service.PronunciationWeakPointService;
import com.remelearning.english.speaking.dto.GenerateSpeakingPracticeRequest;
import com.remelearning.english.speaking.dto.SpeakingPracticeItemDto;
import com.remelearning.english.speaking.service.SpeakingLearnService;
import com.remelearning.english.vocabulary.domain.VocabularyWeakPoint;
import com.remelearning.english.vocabulary.learn.dto.GenerateVocabPracticeRequest;
import com.remelearning.english.vocabulary.learn.dto.VocabPracticeItemDto;
import com.remelearning.english.vocabulary.learn.service.VocabLearnService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PracticeSessionServiceImplTest {

	private final PracticeSessionMapper mapper = mock(PracticeSessionMapper.class);
	private final VocabLearnService vocabLearnService = mock(VocabLearnService.class);
	private final GrammarLearnService grammarLearnService = mock(GrammarLearnService.class);
	private final ListeningLearnService listeningLearnService = mock(ListeningLearnService.class);
	private final SpeakingLearnService speakingLearnService = mock(SpeakingLearnService.class);
	private final com.remelearning.english.vocabulary.service.VocabularyWeakPointService vocabularyWeakPointService =
			mock(com.remelearning.english.vocabulary.service.VocabularyWeakPointService.class);
	private final GrammarWeakPointService grammarWeakPointService = mock(GrammarWeakPointService.class);
	private final PronunciationWeakPointService pronunciationWeakPointService = mock(PronunciationWeakPointService.class);
	private final ListeningWeakPointService listeningWeakPointService = mock(ListeningWeakPointService.class);
	private final com.remelearning.english.writing.service.WritingLearnService writingLearnService =
			mock(com.remelearning.english.writing.service.WritingLearnService.class);

	private final PracticeSessionServiceImpl service = new PracticeSessionServiceImpl(
			mapper, vocabLearnService, grammarLearnService, listeningLearnService, speakingLearnService,
			writingLearnService, vocabularyWeakPointService, grammarWeakPointService,
			pronunciationWeakPointService, listeningWeakPointService);

	// Every generate() returns a usable item DTO so slots persist without NPEs.
	private void stubAllGenerators() {
		when(vocabLearnService.generate(any(), any()))
				.thenReturn(VocabPracticeItemDto.builder().practiceItemId(10L).topic("Vocab topic").build());
		when(grammarLearnService.generate(any(), any()))
				.thenReturn(GrammarPracticeItemDto.builder().practiceItemId(20L).topic("Grammar topic").build());
		when(listeningLearnService.generate(any(), any()))
				.thenReturn(ListeningPracticeItemDto.builder().practiceItemId(30L).topic("Listening topic").build());
		when(speakingLearnService.generate(any(), any()))
				.thenReturn(SpeakingPracticeItemDto.builder().practiceItemId(40L).topic("Speaking topic").build());
		when(writingLearnService.generate(any(), any()))
				.thenReturn(com.remelearning.english.writing.dto.WritingPracticeItemDto.builder()
						.practiceItemId(50L).topic("Writing topic").build());
	}

	private void noWeakPoints() {
		when(vocabularyWeakPointService.getTopWeakPoints(any(), anyInt())).thenReturn(List.of());
		when(grammarWeakPointService.getTopWeakPoints(any(), anyInt())).thenReturn(List.of());
		when(pronunciationWeakPointService.getTopWeakPoints(any(), anyInt())).thenReturn(List.of());
		when(listeningWeakPointService.getWeakPoints(any(), any())).thenReturn(List.of());
	}

	@Test
	void ranksCategoriesByHighestWeakPointScoreAndRotatesToFillSlots() {
		// Only vocabulary (0.9) and grammar (0.5) have weak points -> rotation [vocab, grammar]
		// round-robins to fill 4 slots: vocab, grammar, vocab, grammar.
		when(vocabularyWeakPointService.getTopWeakPoints(eq("user-1"), anyInt()))
				.thenReturn(List.of(VocabularyWeakPoint.builder().label("reluctant").forgettingScore(0.9).build()));
		when(grammarWeakPointService.getTopWeakPoints(eq("user-1"), anyInt()))
				.thenReturn(List.of(GrammarWeakPoint.builder().label("past tense").forgettingScore(0.5).build()));
		when(pronunciationWeakPointService.getTopWeakPoints(eq("user-1"), anyInt())).thenReturn(List.of());
		when(listeningWeakPointService.getWeakPoints(eq("user-1"), any())).thenReturn(List.of());
		stubAllGenerators();

		PracticeSessionDto dto = service.startSession("user-1", 4, null);

		assertThat(dto.getTotalExercises()).isEqualTo(4);
		// Writing joins the rotation because grammar/vocabulary have weak points - it has no weak-point
		// table of its own and borrows theirs, ranked by whichever is more urgent (vocabulary's 0.9 here,
		// tying with vocabulary itself and landing after it since collectFocuses adds it last).
		assertThat(dto.getExercises()).extracting("category")
				.containsExactly("vocabulary", "writing", "grammar", "vocabulary");
		verify(vocabLearnService, times(2)).generate(eq("user-1"), any());
		verify(grammarLearnService, times(1)).generate(eq("user-1"), any());
		verify(writingLearnService, times(1)).generate(eq("user-1"), any());
		verify(listeningLearnService, never()).generate(any(), any());
		verify(speakingLearnService, never()).generate(any(), any());
	}

	@Test
	void passesTopWeakPointLabelsAsFocusItemsToDomainGenerator() {
		when(vocabularyWeakPointService.getTopWeakPoints(eq("user-1"), anyInt()))
				.thenReturn(List.of(VocabularyWeakPoint.builder().label("reluctant").forgettingScore(0.9).build()));
		when(grammarWeakPointService.getTopWeakPoints(any(), anyInt())).thenReturn(List.of());
		when(pronunciationWeakPointService.getTopWeakPoints(any(), anyInt())).thenReturn(List.of());
		when(listeningWeakPointService.getWeakPoints(any(), any())).thenReturn(List.of());
		stubAllGenerators();

		service.startSession("user-1", 1, null);

		ArgumentCaptor<GenerateVocabPracticeRequest> captor = ArgumentCaptor.forClass(GenerateVocabPracticeRequest.class);
		verify(vocabLearnService).generate(eq("user-1"), captor.capture());
		assertThat(captor.getValue().getFocusItems()).containsExactly("reluctant");
	}

	@Test
	void coldStartSpreadsOneExerciseAcrossAllFiveSkills() {
		noWeakPoints();
		stubAllGenerators();

		PracticeSessionDto dto = service.startSession("user-1", 5, null);

		assertThat(dto.getExercises()).extracting("category")
				.containsExactly("vocabulary", "grammar", "listening", "speaking", "writing");
		verify(vocabLearnService, times(1)).generate(any(), any());
		verify(grammarLearnService, times(1)).generate(any(), any());
		verify(listeningLearnService, times(1)).generate(any(), any());
		verify(speakingLearnService, times(1)).generate(any(), any());
		verify(writingLearnService, times(1)).generate(any(), any());
	}

	@Test
	void listeningSlotIsGeneratedWithEmptyFocusItemsToSelfFallback() {
		// Listening has the highest score but must still be generated with empty focus items.
		when(listeningWeakPointService.getWeakPoints(eq("user-1"), any()))
				.thenReturn(List.of(ListeningWeakPoint.builder().label("weather").forgettingScore(0.95).build()));
		when(vocabularyWeakPointService.getTopWeakPoints(any(), anyInt())).thenReturn(List.of());
		when(grammarWeakPointService.getTopWeakPoints(any(), anyInt())).thenReturn(List.of());
		when(pronunciationWeakPointService.getTopWeakPoints(any(), anyInt())).thenReturn(List.of());
		stubAllGenerators();

		service.startSession("user-1", 1, null);

		ArgumentCaptor<GenerateListeningPracticeRequest> captor =
				ArgumentCaptor.forClass(GenerateListeningPracticeRequest.class);
		verify(listeningLearnService).generate(eq("user-1"), captor.capture());
		assertThat(captor.getValue().getFocusItems()).isEmpty();
	}

	@Test
	void speakingSlotUsesPronunciationWeakPointLabelsAsFocus() {
		when(pronunciationWeakPointService.getTopWeakPoints(eq("user-1"), anyInt()))
				.thenReturn(List.of(PronunciationWeakPoint.builder().label("th sound").forgettingScore(0.8).build()));
		when(vocabularyWeakPointService.getTopWeakPoints(any(), anyInt())).thenReturn(List.of());
		when(grammarWeakPointService.getTopWeakPoints(any(), anyInt())).thenReturn(List.of());
		when(listeningWeakPointService.getWeakPoints(any(), any())).thenReturn(List.of());
		stubAllGenerators();

		service.startSession("user-1", 1, null);

		ArgumentCaptor<GenerateSpeakingPracticeRequest> captor =
				ArgumentCaptor.forClass(GenerateSpeakingPracticeRequest.class);
		verify(speakingLearnService).generate(eq("user-1"), captor.capture());
		assertThat(captor.getValue().getFocusItems()).containsExactly("th sound");
	}

	@Test
	void writingSlotBorrowsBothGrammarAndVocabularyLabelsAndPicksAWritingMode() {
		// Writing is the one exercise that drills grammar and vocabulary at the same time, so it gets
		// both label sets rather than one domain's.
		when(vocabularyWeakPointService.getTopWeakPoints(eq("user-1"), anyInt()))
				.thenReturn(List.of(VocabularyWeakPoint.builder().label("reluctant").forgettingScore(0.4).build()));
		when(grammarWeakPointService.getTopWeakPoints(eq("user-1"), anyInt()))
				.thenReturn(List.of(GrammarWeakPoint.builder().label("past perfect").forgettingScore(0.99).build()));
		when(pronunciationWeakPointService.getTopWeakPoints(any(), anyInt())).thenReturn(List.of());
		when(listeningWeakPointService.getWeakPoints(any(), any())).thenReturn(List.of());
		stubAllGenerators();

		// grammar 0.99 ranks first, writing ties it at 0.99 (max of the two) and comes next.
		service.startSession("user-1", 2, null);

		ArgumentCaptor<com.remelearning.english.writing.dto.GenerateWritingPracticeRequest> captor =
				ArgumentCaptor.forClass(com.remelearning.english.writing.dto.GenerateWritingPracticeRequest.class);
		verify(writingLearnService).generate(eq("user-1"), captor.capture());
		assertThat(captor.getValue().getFocusItems()).containsExactly("past perfect", "reluctant");
		// taskType is mandatory for the writing generator (unlike the other domains' optional facets).
		assertThat(captor.getValue().getTaskType()).isNotNull();
	}

	@Test
	void theChosenExamStyleReachesEveryDomainGeneratorNormalized() {
		noWeakPoints();
		stubAllGenerators();

		service.startSession("user-1", 5, "ielts");

		ArgumentCaptor<GenerateVocabPracticeRequest> vocabCaptor =
				ArgumentCaptor.forClass(GenerateVocabPracticeRequest.class);
		verify(vocabLearnService).generate(any(), vocabCaptor.capture());
		// Normalized once per session, so "ielts" and "IELTS" can't produce two different stored values.
		assertThat(vocabCaptor.getValue().getExamType()).isEqualTo("IELTS");

		ArgumentCaptor<com.remelearning.english.writing.dto.GenerateWritingPracticeRequest> writingCaptor =
				ArgumentCaptor.forClass(com.remelearning.english.writing.dto.GenerateWritingPracticeRequest.class);
		verify(writingLearnService).generate(any(), writingCaptor.capture());
		assertThat(writingCaptor.getValue().getExamType()).isEqualTo("IELTS");
	}

	@Test
	void anUnspecifiedExamStyleStaysNullRatherThanBecomingAString() {
		noWeakPoints();
		stubAllGenerators();

		service.startSession("user-1", 1, "   ");

		ArgumentCaptor<GenerateVocabPracticeRequest> captor =
				ArgumentCaptor.forClass(GenerateVocabPracticeRequest.class);
		verify(vocabLearnService).generate(any(), captor.capture());
		assertThat(captor.getValue().getExamType()).isNull();
	}

	@Test
	void persistsSessionHeaderAndOneExercisePerSlot() {
		noWeakPoints();
		stubAllGenerators();

		service.startSession("user-1", 4, null);

		ArgumentCaptor<PracticeSession> sessionCaptor = ArgumentCaptor.forClass(PracticeSession.class);
		verify(mapper).insertSession(sessionCaptor.capture());
		assertThat(sessionCaptor.getValue().getStatus()).isEqualTo(PracticeSessionStatus.IN_PROGRESS);
		assertThat(sessionCaptor.getValue().getTotalExercises()).isEqualTo(4);
		verify(mapper, times(4)).insertExercise(any(PracticeSessionExercise.class));
	}

	@Test
	void completeExerciseMarksSlotDoneAndClosesSessionWhenNoneRemainPending() {
		when(mapper.findSessionById(1L)).thenReturn(PracticeSession.builder().id(1L).userId("user-1").build());
		when(mapper.findExercisesBySessionId(1L)).thenReturn(List.of());
		when(mapper.countPendingBySessionId(1L)).thenReturn(0);

		service.completeExercise(1L, 4, 88.0);

		verify(mapper).markExerciseDone(1L, 4, 88.0);
		verify(mapper).completeSession(1L);
	}

	@Test
	void completeExerciseKeepsSessionOpenWhileSlotsRemainPending() {
		when(mapper.findSessionById(1L)).thenReturn(PracticeSession.builder().id(1L).userId("user-1").build());
		when(mapper.findExercisesBySessionId(1L)).thenReturn(List.of());
		when(mapper.countPendingBySessionId(1L)).thenReturn(2);

		service.completeExercise(1L, 1, 75.0);

		verify(mapper).markExerciseDone(1L, 1, 75.0);
		verify(mapper, never()).completeSession(any());
	}

	@Test
	void getLatestInProgressReturnsNullWhenNoOpenSession() {
		when(mapper.findLatestInProgressByUserId("user-1")).thenReturn(null);

		assertThat(service.getLatestInProgress("user-1")).isNull();
	}
}
