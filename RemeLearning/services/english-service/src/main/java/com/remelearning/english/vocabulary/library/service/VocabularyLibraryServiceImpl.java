package com.remelearning.english.vocabulary.library.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.remelearning.common.ai.tts.TtsAudio;
import com.remelearning.common.ai.tts.TtsClient;
import com.remelearning.common.ai.tts.TtsRequest;
import com.remelearning.common.exception.BusinessException;
import com.remelearning.common.storage.StorageClient;
import com.remelearning.common.constants.LearningCategories;
import com.remelearning.english.listening.scoring.OpenAnswerGrade;
import com.remelearning.english.listening.scoring.OpenAnswerGrader;
import com.remelearning.english.practice.dto.PracticeAttemptRequest;
import com.remelearning.english.practice.dto.PracticeRedoRequest;
import com.remelearning.english.practice.service.PracticeService;
import com.remelearning.english.vocabulary.domain.VocabularyType;
import com.remelearning.english.vocabulary.library.domain.SectionQueueEntry;
import com.remelearning.english.vocabulary.library.domain.TopicMasterySummaryRow;
import com.remelearning.english.vocabulary.library.domain.VocabularyLibraryWord;
import com.remelearning.english.vocabulary.library.domain.VocabularySectionAttempt;
import com.remelearning.english.vocabulary.library.domain.SectionStatus;
import com.remelearning.english.vocabulary.library.domain.VocabularyTopic;
import com.remelearning.english.vocabulary.library.dto.SectionAnswerResultDto;
import com.remelearning.english.vocabulary.library.dto.SectionCardDto;
import com.remelearning.english.vocabulary.library.dto.SectionHistoryEntryDto;
import com.remelearning.english.vocabulary.library.dto.SectionProgressDto;
import com.remelearning.english.vocabulary.library.dto.StartSectionRequest;
import com.remelearning.english.vocabulary.library.dto.SubmitSectionAnswerRequest;
import com.remelearning.english.vocabulary.library.dto.TopicSummaryDto;
import com.remelearning.english.vocabulary.library.dto.VocabularyAudioResource;
import com.remelearning.english.vocabulary.library.generator.GeneratedLibraryWord;
import com.remelearning.english.vocabulary.library.generator.LibraryWordGenerator;
import com.remelearning.english.vocabulary.library.mapper.VocabularyLibraryWordMapper;
import com.remelearning.english.vocabulary.library.mapper.VocabularySectionMapper;
import com.remelearning.english.vocabulary.library.mapper.VocabularyTopicMapper;
import com.remelearning.english.vocabulary.library.scoring.SectionCardBuilder;
import com.remelearning.english.vocabulary.library.session.SectionQueue;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Orchestrates the topic library (listing + on-demand top-up) and Section practice (start/answer/
 * finish/history). See the design spec (docs/superpowers/specs/2026-07-22-vocabulary-library-
 * sections-design.md) for the full rationale; method-level comments below cover the non-obvious
 * parts only.
 */
@Service
public class VocabularyLibraryServiceImpl implements VocabularyLibraryService {

	private static final int DEFAULT_SECTION_SIZE = 10;
	private static final int MIN_SECTION_SIZE = 5;
	private static final int MAX_SECTION_SIZE = 20;
	private static final int OPTIONS_COUNT = 4;
	private static final int TOP_UP_BATCH_SIZE = 15;
	private static final String ITEM_ID_PREFIX = "vocab:";
	private static final String AUDIO_URL = "/api/v1/learn/vocabulary/library/words/%d/audio";
	private static final String GENERATED_AUDIO_KEY = "vocab-library/%d/%d.wav";

	private final VocabularyTopicMapper topicMapper;
	private final VocabularyLibraryWordMapper libraryWordMapper;
	private final VocabularySectionMapper sectionMapper;
	private final LibraryWordGenerator libraryWordGenerator;
	private final TtsClient ttsClient;
	private final StorageClient storageClient;
	private final PracticeService practiceService;
	private final OpenAnswerGrader openAnswerGrader;
	private final ObjectMapper objectMapper;

	public VocabularyLibraryServiceImpl(VocabularyTopicMapper topicMapper, VocabularyLibraryWordMapper libraryWordMapper,
			VocabularySectionMapper sectionMapper, LibraryWordGenerator libraryWordGenerator, TtsClient ttsClient,
			StorageClient storageClient, PracticeService practiceService, OpenAnswerGrader openAnswerGrader,
			ObjectMapper objectMapper) {
		this.topicMapper = topicMapper;
		this.libraryWordMapper = libraryWordMapper;
		this.sectionMapper = sectionMapper;
		this.libraryWordGenerator = libraryWordGenerator;
		this.ttsClient = ttsClient;
		this.storageClient = storageClient;
		this.practiceService = practiceService;
		this.openAnswerGrader = openAnswerGrader;
		this.objectMapper = objectMapper;
	}

	@Override
	public List<TopicSummaryDto> listTopics(String userId) {
		Map<Long, TopicMasterySummaryRow> summaryByTopicId = new HashMap<>();
		for (TopicMasterySummaryRow row : topicMapper.findMasterySummaryByUserId(userId)) {
			summaryByTopicId.put(row.getTopicId(), row);
		}
		return topicMapper.findAll().stream().map(topic -> {
			TopicMasterySummaryRow summary = summaryByTopicId.get(topic.getId());
			return TopicSummaryDto.builder()
					.topicId(topic.getId()).code(topic.getCode()).name(topic.getName())
					.description(topic.getDescription()).level(topic.getLevel())
					.wordCount(summary == null ? 0 : summary.getWordCount())
					.masteredCount(summary == null ? 0 : summary.getMasteredCount())
					.build();
		}).toList();
	}

	// Picks sectionSize words for the topic (not-yet-mastered first, then random fill), tops the
	// topic up via the LLM generator first if it doesn't have enough words at all, and persists a
	// fresh IN_PROGRESS attempt with a freshly-shuffled Leitner-lite queue.
	@Override
	@Transactional
	public SectionCardDto startSection(String userId, Long topicId, StartSectionRequest request) {
		requireTopic(topicId);
		int sectionSize = clampSectionSize(request == null ? null : request.getSectionSize());
		ensureTopicHasEnoughWords(topicId, sectionSize);

		List<VocabularyLibraryWord> notYetMastered = libraryWordMapper.findNotYetMasteredByTopicId(topicId, userId, sectionSize);
		List<Long> wordIds = new ArrayList<>(notYetMastered.stream().map(VocabularyLibraryWord::getId).toList());
		if (wordIds.size() < sectionSize) {
			libraryWordMapper.findRandomByTopicIdExcluding(topicId, wordIds, sectionSize - wordIds.size())
					.forEach(word -> wordIds.add(word.getId()));
		}

		List<SectionQueueEntry> queue = SectionQueue.initial(wordIds);
		VocabularySectionAttempt attempt = VocabularySectionAttempt.builder()
				.userId(userId).topicId(topicId).status(SectionStatus.IN_PROGRESS)
				.sectionSize(wordIds.size()).libraryWordIdsJson(writeJson(wordIds))
				.queueStateJson(writeJson(queue)).correctCount(0).totalAnswers(0)
				.build();
		sectionMapper.insertAttempt(attempt);

		return buildCard(attempt, queue);
	}

	@Override
	@Transactional
	public SectionAnswerResultDto submitAnswer(Long sectionId, SubmitSectionAnswerRequest request) {
		VocabularySectionAttempt attempt = requireInProgressAttempt(sectionId);
		List<SectionQueueEntry> queue = readQueue(attempt.getQueueStateJson());
		SectionQueueEntry entry = SectionQueue.current(queue);
		VocabularyLibraryWord word = requireWord(entry.getLibraryWordId());

		if (!entry.isIntroShown()) {
			List<SectionQueueEntry> updated = SectionQueue.acknowledgeIntro(queue);
			persistQueue(attempt, updated);
			return SectionAnswerResultDto.builder().correct(true).completed(false)
					.nextCard(buildCard(attempt, updated)).progress(progressFor(updated, attempt.getSectionSize())).build();
		}

		var type = entry.getPendingExerciseType();
		String correctAnswer = correctAnswerFor(word, type);
		double score = scoreAnswer(type, word, request.getSubmittedAnswer());
		boolean correct = score >= com.remelearning.english.vocabulary.library.scoring.SectionAnswerScoring.CORRECT_THRESHOLD;

		sectionMapper.insertAnswer(com.remelearning.english.vocabulary.library.domain.VocabularySectionAnswer.builder()
				.sectionAttemptId(sectionId).libraryWordId(word.getId()).exerciseType(type)
				.submittedAnswer(request.getSubmittedAnswer()).score(score).correct(correct).build());

		List<SectionQueueEntry> updated = SectionQueue.applyResult(queue, correct);
		int correctCount = attempt.getCorrectCount() + (correct ? 1 : 0);
		int totalAnswers = attempt.getTotalAnswers() + 1;

		if (SectionQueue.isComplete(updated)) {
			persistQueue(attempt, updated, correctCount, totalAnswers);
			return completeSection(attempt, correct, correctAnswer, score);
		}

		persistQueue(attempt, updated, correctCount, totalAnswers);
		return SectionAnswerResultDto.builder()
				.correct(correct).correctAnswer(correctAnswer).score(score).completed(false)
				.nextCard(buildCard(attempt, updated)).progress(progressFor(updated, attempt.getSectionSize())).build();
	}

	// Explicit early-quit: marks the attempt ABANDONED (not COMPLETED) but still feeds whatever
	// answers were persisted so far into the weak-point pipeline - partial progress still counts.
	@Override
	@Transactional
	public SectionAnswerResultDto finishSection(Long sectionId) {
		VocabularySectionAttempt attempt = requireInProgressAttempt(sectionId);
		sectionMapper.completeAttempt(sectionId, SectionStatus.ABANDONED.name());
		feedWeakPoints(attempt.getUserId(), attempt.getId());
		List<SectionQueueEntry> queue = readQueue(attempt.getQueueStateJson());
		return SectionAnswerResultDto.builder().completed(true).nextCard(null)
				.progress(progressFor(queue, attempt.getSectionSize())).build();
	}

	@Override
	public List<SectionHistoryEntryDto> getSectionHistory(String userId) {
		throw new UnsupportedOperationException("Implemented in Task 11");
	}

	@Override
	public VocabularyAudioResource loadWordAudio(Long wordId) {
		throw new UnsupportedOperationException("Implemented in Task 11");
	}

	// --- helpers (shared by later tasks too) ---

	private int clampSectionSize(Integer requested) {
		int size = requested == null ? DEFAULT_SECTION_SIZE : requested;
		return Math.max(MIN_SECTION_SIZE, Math.min(MAX_SECTION_SIZE, size));
	}

	// Generates + persists a fresh batch of words (with synthesized audio) via the LLM generator,
	// only when the topic doesn't even have `minCount` words in total yet - a learner who has
	// simply mastered everything already in a well-stocked topic does NOT trigger this (that case
	// is handled by falling back to random-fill in startSection instead of forcing new content).
	private void ensureTopicHasEnoughWords(Long topicId, int minCount) {
		if (libraryWordMapper.countByTopicId(topicId) >= minCount) {
			return;
		}
		VocabularyTopic topic = requireTopic(topicId);
		List<String> existingWords = libraryWordMapper.findWordsByTopicId(topicId);
		List<GeneratedLibraryWord> generated = libraryWordGenerator.generate(topic.getName(), existingWords, TOP_UP_BATCH_SIZE);
		for (GeneratedLibraryWord g : generated) {
			VocabularyLibraryWord word = VocabularyLibraryWord.builder()
					.topicId(topicId).word(g.word()).wordType(parseWordType(g.wordType()))
					.meaningVi(g.meaningVi()).exampleEn(g.exampleEn())
					.build();
			libraryWordMapper.insert(word);
			synthesizeAndStoreAudio(word);
		}
	}

	// Synthesizes the word's pronunciation once at creation time (never at Section-runtime) and
	// stores it via the shared StorageClient, mirroring ListeningLearnServiceImpl.generate.
	private void synthesizeAndStoreAudio(VocabularyLibraryWord word) {
		TtsAudio audio = ttsClient.synthesize(TtsRequest.builder().text(word.getWord()).languageCode("en").build());
		String key = GENERATED_AUDIO_KEY.formatted(word.getTopicId(), word.getId());
		storageClient.write(key, new ByteArrayInputStream(audio.getAudioBytes()), audio.getAudioBytes().length);
		libraryWordMapper.updateAudioStorageKey(word.getId(), key);
		word.setAudioStorageKey(key);
	}

	private VocabularyType parseWordType(String raw) {
		if (raw == null) {
			return VocabularyType.OTHER;
		}
		try {
			return VocabularyType.valueOf(raw.trim().toUpperCase());
		} catch (IllegalArgumentException ex) {
			return VocabularyType.OTHER;
		}
	}

	// Builds the DTO for whatever is currently at the front of the queue - an INTRO flashcard the
	// first time a word appears, otherwise a QUIZ of its (frozen-per-occurrence) exercise type.
	private SectionCardDto buildCard(VocabularySectionAttempt attempt, List<SectionQueueEntry> queue) {
		SectionQueueEntry entry = SectionQueue.current(queue);
		VocabularyLibraryWord word = requireWord(entry.getLibraryWordId());
		SectionProgressDto progress = progressFor(queue, attempt.getSectionSize());
		String audioUrl = word.getAudioStorageKey() == null ? null : AUDIO_URL.formatted(word.getId());

		if (!entry.isIntroShown()) {
			return SectionCardBuilder.buildIntro(attempt.getId(), word, audioUrl, progress);
		}
		if (entry.getPendingExerciseType() == null) {
			entry.setPendingExerciseType(randomExerciseType());
			persistQueue(attempt, queue);
		}
		List<VocabularyLibraryWord> distractors =
				libraryWordMapper.findRandomByTopicIdExcluding(word.getTopicId(), List.of(word.getId()), OPTIONS_COUNT - 1);
		return SectionCardBuilder.buildQuiz(attempt.getId(), word, entry.getPendingExerciseType(), distractors, audioUrl, progress);
	}

	private com.remelearning.english.vocabulary.library.domain.SectionExerciseType randomExerciseType() {
		var types = com.remelearning.english.vocabulary.library.domain.SectionExerciseType.values();
		return types[java.util.concurrent.ThreadLocalRandom.current().nextInt(types.length)];
	}

	private SectionProgressDto progressFor(List<SectionQueueEntry> queue, int totalWords) {
		return SectionProgressDto.builder()
				.totalWords(totalWords).wordsRemaining(queue.size()).wordsMastered(totalWords - queue.size()).build();
	}

	private void persistQueue(VocabularySectionAttempt attempt, List<SectionQueueEntry> queue) {
		persistQueue(attempt, queue, attempt.getCorrectCount(), attempt.getTotalAnswers());
	}

	private void persistQueue(VocabularySectionAttempt attempt, List<SectionQueueEntry> queue, int correctCount, int totalAnswers) {
		String json = writeJson(queue);
		sectionMapper.updateAttemptQueueState(attempt.getId(), json, correctCount, totalAnswers);
		attempt.setQueueStateJson(json);
		attempt.setCorrectCount(correctCount);
		attempt.setTotalAnswers(totalAnswers);
	}

	// CLOZE/MCQ/LISTENING_DICTATION/TRANSLATE_VI_TO_EN are guessing the word itself; MATCHING/
	// TRANSLATE_EN_TO_VI are guessing the meaning.
	private String correctAnswerFor(VocabularyLibraryWord word, com.remelearning.english.vocabulary.library.domain.SectionExerciseType type) {
		return switch (type) {
			case CLOZE, MCQ, LISTENING_DICTATION, TRANSLATE_VI_TO_EN -> word.getWord();
			case MATCHING, TRANSLATE_EN_TO_VI -> word.getMeaningVi();
		};
	}

	// Every type but TRANSLATE_EN_TO_VI is graded by the pure SectionAnswerScoring dispatcher;
	// TRANSLATE_EN_TO_VI (free-text Vietnamese meaning) needs the LLM grader, reusing the same
	// OpenAnswerGrader the listening skill's OPEN questions already use.
	private double scoreAnswer(com.remelearning.english.vocabulary.library.domain.SectionExerciseType type,
			VocabularyLibraryWord word, String submitted) {
		if (type == com.remelearning.english.vocabulary.library.domain.SectionExerciseType.TRANSLATE_EN_TO_VI) {
			OpenAnswerGrade grade = openAnswerGrader.grade(word.getExampleEn(),
					"Nghĩa tiếng Việt của từ '" + word.getWord() + "' là gì?", word.getMeaningVi(), submitted);
			return grade.score();
		}
		return com.remelearning.english.vocabulary.library.scoring.SectionAnswerScoring.scoreClosed(
				type, correctAnswerFor(word, type), submitted);
	}

	private VocabularySectionAttempt requireInProgressAttempt(Long sectionId) {
		VocabularySectionAttempt attempt = sectionMapper.findAttemptById(sectionId);
		if (attempt == null) {
			throw BusinessException.notFound("Vocabulary section attempt not found: id=" + sectionId);
		}
		if (attempt.getStatus() != SectionStatus.IN_PROGRESS) {
			throw BusinessException.conflict("Vocabulary section attempt is not in progress: id=" + sectionId);
		}
		return attempt;
	}

	// Marks the attempt COMPLETED (all words mastered) and feeds every persisted answer for the
	// section into the weak-point pipeline before returning the final result.
	private SectionAnswerResultDto completeSection(VocabularySectionAttempt attempt, boolean lastCorrect,
			String lastCorrectAnswer, double lastScore) {
		sectionMapper.completeAttempt(attempt.getId(), SectionStatus.COMPLETED.name());
		feedWeakPoints(attempt.getUserId(), attempt.getId());
		return SectionAnswerResultDto.builder()
				.correct(lastCorrect).correctAnswer(lastCorrectAnswer).score(lastScore).completed(true).nextCard(null)
				.progress(SectionProgressDto.builder().totalWords(attempt.getSectionSize())
						.wordsMastered(attempt.getSectionSize()).wordsRemaining(0).build())
				.build();
	}

	// Feeds EVERY persisted answer for this section (including repeats of the same word) into the
	// existing weak-point/spaced-repetition pipeline in one batch - deliberately not deduped by word
	// (see this task's plan note): PracticeServiceImpl.redo detects a repeated itemId within the
	// same attempts list to set recurredInBatch, which is exactly the recurrence signal this
	// feature's in-session repetition is designed to produce. A no-answer early finish (empty list)
	// is a no-op, matching VocabLearnServiceImpl's feedWeakPoints.
	private void feedWeakPoints(String userId, Long sectionAttemptId) {
		List<com.remelearning.english.vocabulary.library.domain.VocabularySectionAnswer> answers =
				sectionMapper.findAnswersByAttemptId(sectionAttemptId);
		if (answers.isEmpty()) {
			return;
		}
		List<PracticeAttemptRequest> attempts = new ArrayList<>();
		for (com.remelearning.english.vocabulary.library.domain.VocabularySectionAnswer answer : answers) {
			VocabularyLibraryWord word = requireWord(answer.getLibraryWordId());
			PracticeAttemptRequest attempt = new PracticeAttemptRequest();
			attempt.setItemId(ITEM_ID_PREFIX + word.getWord().toLowerCase());
			attempt.setCategory(LearningCategories.VOCABULARY);
			attempt.setLabel(word.getWord());
			attempt.setCorrect(answer.isCorrect());
			attempts.add(attempt);
		}
		PracticeRedoRequest request = new PracticeRedoRequest();
		request.setUserId(userId);
		request.setAttempts(attempts);
		practiceService.redo(request);
	}

	private VocabularyTopic requireTopic(Long topicId) {
		VocabularyTopic topic = topicMapper.findById(topicId);
		if (topic == null) {
			throw BusinessException.notFound("Vocabulary topic not found: id=" + topicId);
		}
		return topic;
	}

	private VocabularyLibraryWord requireWord(Long wordId) {
		VocabularyLibraryWord word = libraryWordMapper.findById(wordId);
		if (word == null) {
			throw new IllegalStateException("Vocabulary library word referenced by a section queue not found: id=" + wordId);
		}
		return word;
	}

	private List<SectionQueueEntry> readQueue(String json) {
		try {
			return new ArrayList<>(objectMapper.readValue(json, new TypeReference<List<SectionQueueEntry>>() { }));
		} catch (JsonProcessingException ex) {
			throw new IllegalStateException("Failed to deserialize section queue state", ex);
		}
	}

	private String writeJson(Object value) {
		try {
			return objectMapper.writeValueAsString(value);
		} catch (JsonProcessingException ex) {
			throw new IllegalStateException("Failed to serialize section content", ex);
		}
	}
}
