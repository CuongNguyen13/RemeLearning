package com.remelearning.english.listening.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.remelearning.common.constants.LearningCategories;
import com.remelearning.common.exception.BusinessException;
import com.remelearning.common.storage.StorageClient;
import com.remelearning.english.learn.common.DialogueAudioSynthesizer;
import com.remelearning.english.learn.common.SynthesizedDialogue;
import com.remelearning.english.listening.domain.ListeningAttempt;
import com.remelearning.english.listening.domain.ListeningAttemptDetailRow;
import com.remelearning.english.listening.domain.ListeningAttemptHistoryRow;
import com.remelearning.english.listening.domain.ListeningPracticeItem;
import com.remelearning.english.listening.domain.ListeningQuestionItem;
import com.remelearning.english.listening.domain.ListeningQuestionType;
import com.remelearning.english.listening.dto.GenerateListeningPracticeRequest;
import com.remelearning.english.listening.dto.ListeningAttemptDetailDto;
import com.remelearning.english.listening.dto.ListeningAttemptHistoryEntryDto;
import com.remelearning.english.listening.dto.ListeningAttemptQuestionResultDto;
import com.remelearning.english.listening.dto.ListeningAttemptResultDto;
import com.remelearning.english.listening.dto.ListeningAudioResource;
import com.remelearning.english.listening.dto.ListeningPracticeItemDto;
import com.remelearning.english.listening.dto.ListeningQuestionDto;
import com.remelearning.english.listening.dto.SubmitListeningAttemptRequest;
import com.remelearning.english.listening.generator.GeneratedListeningPractice;
import com.remelearning.english.listening.generator.ListeningPracticeGenerator;
import com.remelearning.english.listening.mapper.ListeningMapper;
import com.remelearning.english.listening.scoring.ListeningQuestionScoring;
import com.remelearning.english.listening.scoring.OpenAnswerGrade;
import com.remelearning.english.listening.scoring.OpenAnswerGrader;
import com.remelearning.english.practice.dto.PracticeAttemptRequest;
import com.remelearning.english.practice.dto.PracticeRedoRequest;
import com.remelearning.english.practice.service.PracticeService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Orchestrates the listening "learn" skill: generating an AI passage (Gemini transcript+questions,
 * Supertonic audio via {@link DialogueAudioSynthesizer}), grading a submitted attempt (MCQ/
 * KEYWORD via the pure {@link ListeningQuestionScoring}, OPEN via {@link OpenAnswerGrader}), and
 * feeding each graded question back into the existing spaced-repetition/weak-point pipeline via
 * {@link PracticeService#redo} - same reuse pattern as {@code VocabLearnServiceImpl}/
 * {@code GrammarLearnServiceImpl}, category {@code "listening"}.
 */
@Service
public class ListeningLearnServiceImpl implements ListeningLearnService {

	private static final int DEFAULT_FOCUS_LIMIT = 8;
	private static final String ITEM_ID_PREFIX = "listening:";
	// Must match bff-service's public route (LearnerController#getListeningAudio), not english-service's
	// own internal controller route - this URL is returned straight to the FE client, which only ever
	// talks to bff-service.
	private static final String AUDIO_URL = "/api/v1/learners/%s/learn/listening/items/%d/audio";
	private static final String GENERATED_KEY = "listening/%s/%d.wav";

	private final ListeningMapper listeningMapper;
	private final ListeningPracticeGenerator generator;
	private final DialogueAudioSynthesizer audioSynthesizer;
	private final OpenAnswerGrader openAnswerGrader;
	private final StorageClient storageClient;
	private final PracticeService practiceService;
	private final ObjectMapper objectMapper;
	private final String ttsLang;

	public ListeningLearnServiceImpl(
			ListeningMapper listeningMapper,
			ListeningPracticeGenerator generator,
			DialogueAudioSynthesizer audioSynthesizer,
			OpenAnswerGrader openAnswerGrader,
			StorageClient storageClient,
			PracticeService practiceService,
			ObjectMapper objectMapper,
			@Value("${listening.tts.lang:en}") String ttsLang) {
		this.listeningMapper = listeningMapper;
		this.generator = generator;
		this.audioSynthesizer = audioSynthesizer;
		this.openAnswerGrader = openAnswerGrader;
		this.storageClient = storageClient;
		this.practiceService = practiceService;
		this.objectMapper = objectMapper;
		this.ttsLang = ttsLang;
	}

	// Generates the passage+questions, synthesizes its audio synchronously (matching dictation's
	// own AI-practice generation - no async job queue in this codebase), and persists both the
	// item and its storage key in one call.
	@Override
	@Transactional
	public ListeningPracticeItemDto generate(String userId, GenerateListeningPracticeRequest request) {
		List<String> targetKeywords = resolveTargetKeywords(userId, request.getFocusItems());
		GeneratedListeningPractice generated = generator.generate(
				targetKeywords, request.getLevel(), request.getExamType(), request.getTranslationLang());
		SynthesizedDialogue synthesized = audioSynthesizer.synthesize(generated.lines(), ttsLang);

		ListeningPracticeItem item = ListeningPracticeItem.builder()
				.userId(userId)
				.level(request.getLevel())
				.examType(request.getExamType())
				.topic(generated.topic())
				.transcript(synthesized.transcriptText())
				.translation(synthesized.translationText())
				.questionsJson(writeJson(generated.questions()))
				.build();
		listeningMapper.insertItem(item);

		String key = GENERATED_KEY.formatted(userId, item.getId());
		storageClient.write(key, new ByteArrayInputStream(synthesized.audioBytes()), synthesized.audioBytes().length);
		listeningMapper.updateItemStorageKey(item.getId(), key);
		item.setStorageKey(key);

		return toItemDto(item, generated.questions());
	}

	@Override
	public ListeningPracticeItemDto getItem(Long itemId) {
		ListeningPracticeItem item = requireItem(itemId);
		return toItemDto(item, readQuestions(item.getQuestionsJson()));
	}

	@Override
	public List<ListeningPracticeItemDto> listItems(String userId) {
		return listeningMapper.findItemsByUserId(userId).stream()
				.map(item -> toItemDto(item, readQuestions(item.getQuestionsJson())))
				.toList();
	}

	@Override
	public ListeningAudioResource loadAudio(Long itemId) {
		ListeningPracticeItem item = requireItem(itemId);
		if (item.getStorageKey() == null) {
			throw BusinessException.notFound("Listening practice audio not ready: id=" + itemId);
		}
		return new ListeningAudioResource(
				storageClient.read(item.getStorageKey()), storageClient.size(item.getStorageKey()), "audio/wav", "listening-" + itemId + ".wav");
	}

	@Override
	@Transactional
	public ListeningAttemptResultDto submit(SubmitListeningAttemptRequest request) {
		ListeningPracticeItem item = requireItem(request.getPracticeItemId());
		List<ListeningQuestionItem> questions = readQuestions(item.getQuestionsJson());
		List<String> answers = request.getAnswers();

		List<ListeningAttemptQuestionResultDto> results = new ArrayList<>();
		double totalScore = 0.0;
		for (int i = 0; i < questions.size(); i++) {
			ListeningQuestionItem question = questions.get(i);
			String submitted = i < answers.size() ? answers.get(i) : null;

			double subScore;
			String explanation;
			if (question.getType() == ListeningQuestionType.OPEN) {
				OpenAnswerGrade grade = openAnswerGrader.grade(item.getTranscript(), question.getPrompt(), question.getAnswer(), submitted);
				subScore = grade.score();
				explanation = grade.feedback();
			} else {
				subScore = ListeningQuestionScoring.scoreClosed(question, submitted);
				explanation = question.getExplanation();
			}
			boolean correct = subScore >= ListeningQuestionScoring.CORRECT_THRESHOLD;
			totalScore += subScore;

			results.add(ListeningAttemptQuestionResultDto.builder()
					.index(i)
					.prompt(question.getPrompt())
					.yourAnswer(submitted)
					.correctAnswer(question.getAnswer())
					.correct(correct)
					.subScore(subScore)
					.explanation(explanation)
					.build());
		}

		double accuracy = questions.isEmpty() ? 0.0 : totalScore / questions.size();

		ListeningAttempt attempt = ListeningAttempt.builder()
				.practiceItemId(item.getId())
				.userId(request.getUserId())
				.answersJson(writeJson(answers))
				.resultsJson(writeJson(results))
				.score(accuracy)
				.build();
		listeningMapper.insertAttempt(attempt);

		feedWeakPoints(request.getUserId(), questions, results);

		return ListeningAttemptResultDto.builder()
				.accuracy(accuracy)
				.results(results)
				.transcript(item.getTranscript())
				.translation(item.getTranslation())
				.actionAdvice(buildActionAdvice(questions, results))
				.build();
	}

	@Override
	public List<ListeningAttemptHistoryEntryDto> getHistory(String userId) {
		return listeningMapper.findHistoryByUserId(userId).stream()
				.map(row -> ListeningAttemptHistoryEntryDto.builder()
						.attemptId(row.getAttemptId())
						.practiceItemId(row.getPracticeItemId())
						.level(row.getLevel())
						.examType(row.getExamType())
						.topic(row.getTopic())
						.score(row.getScore())
						.attemptedAt(row.getCreatedAt())
						.build())
				.toList();
	}

	@Override
	public ListeningAttemptDetailDto getAttemptDetail(String userId, Long attemptId) {
		ListeningAttemptDetailRow row = listeningMapper.findAttemptDetailByIdAndUserId(attemptId, userId);
		if (row == null) {
			throw BusinessException.notFound("Listening practice attempt not found: id=" + attemptId);
		}
		return ListeningAttemptDetailDto.builder()
				.attemptId(row.getAttemptId())
				.level(row.getLevel())
				.examType(row.getExamType())
				.topic(row.getTopic())
				.accuracy(row.getScore())
				.results(readResults(row.getResultsJson()))
				.transcript(row.getTranscript())
				.translation(row.getTranslation())
				.attemptedAt(row.getCreatedAt())
				.build();
	}

	// --- helpers ---

	// Explicit focusItems win; otherwise falls back to the learner's own recently-missed keywords
	// across their past listening attempts' wrong KEYWORD questions (this domain has no dedicated
	// weak-point table - see the V14 migration note); an empty result lets the generator pick its
	// own topic (brand-new learner with no history yet).
	private List<String> resolveTargetKeywords(String userId, List<String> focusItems) {
		if (focusItems != null && !focusItems.isEmpty()) {
			return focusItems;
		}
		return listeningMapper.findItemsByUserId(userId).stream()
				.flatMap(item -> readQuestions(item.getQuestionsJson()).stream())
				.filter(question -> question.getType() == ListeningQuestionType.KEYWORD)
				.map(ListeningQuestionItem::getAnswer)
				.distinct()
				.limit(DEFAULT_FOCUS_LIMIT)
				.toList();
	}

	private void feedWeakPoints(String userId, List<ListeningQuestionItem> questions, List<ListeningAttemptQuestionResultDto> results) {
		List<PracticeAttemptRequest> attempts = new ArrayList<>();
		Set<String> seenLabels = new LinkedHashSet<>();
		for (int i = 0; i < questions.size(); i++) {
			ListeningQuestionItem question = questions.get(i);
			String label = question.getType() == ListeningQuestionType.KEYWORD ? question.getAnswer() : question.getSkill();
			if (label == null || !seenLabels.add(label.toLowerCase())) {
				continue;
			}
			PracticeAttemptRequest attempt = new PracticeAttemptRequest();
			attempt.setItemId(ITEM_ID_PREFIX + label.toLowerCase());
			attempt.setCategory(LearningCategories.LISTENING);
			attempt.setLabel(label);
			attempt.setCorrect(results.get(i).isCorrect());
			attempts.add(attempt);
		}
		if (attempts.isEmpty()) {
			return;
		}
		PracticeRedoRequest request = new PracticeRedoRequest();
		request.setUserId(userId);
		request.setAttempts(attempts);
		practiceService.redo(request);
	}

	private List<String> buildActionAdvice(List<ListeningQuestionItem> questions, List<ListeningAttemptQuestionResultDto> results) {
		Set<String> advice = new LinkedHashSet<>();
		for (int i = 0; i < questions.size(); i++) {
			if (results.get(i).isCorrect()) {
				continue;
			}
			ListeningQuestionItem question = questions.get(i);
			advice.add(question.getType() == ListeningQuestionType.KEYWORD
					? "Nghe lại và ôn từ khóa '%s'.".formatted(question.getAnswer())
					: "Ôn lại kỹ năng '%s' khi nghe (VD: %s).".formatted(question.getSkill(), question.getPrompt()));
		}
		return new ArrayList<>(advice);
	}

	private ListeningPracticeItemDto toItemDto(ListeningPracticeItem item, List<ListeningQuestionItem> questions) {
		List<ListeningQuestionDto> questionDtos = new ArrayList<>();
		for (int i = 0; i < questions.size(); i++) {
			ListeningQuestionItem question = questions.get(i);
			// OPEN questions are LLM-graded server-side, so their model answer must not leak to the
			// client; MCQ/KEYWORD are graded locally on the client, so they carry the answer.
			String answer = question.getType() == ListeningQuestionType.OPEN ? null : question.getAnswer();
			questionDtos.add(ListeningQuestionDto.builder()
					.index(i)
					.prompt(question.getPrompt())
					.type(question.getType())
					.options(question.getOptions())
					.answer(answer)
					.explanation(question.getExplanation())
					.build());
		}
		return ListeningPracticeItemDto.builder()
				.practiceItemId(item.getId())
				.audioUrl(item.getStorageKey() == null ? null : AUDIO_URL.formatted(item.getUserId(), item.getId()))
				.level(item.getLevel())
				.examType(item.getExamType())
				.topic(item.getTopic())
				.questions(questionDtos)
				.createdAt(item.getCreatedAt())
				.build();
	}

	private ListeningPracticeItem requireItem(Long itemId) {
		ListeningPracticeItem item = listeningMapper.findItemById(itemId);
		if (item == null) {
			throw BusinessException.notFound("Listening practice item not found: id=" + itemId);
		}
		return item;
	}

	private List<ListeningQuestionItem> readQuestions(String json) {
		try {
			return objectMapper.readValue(json, new TypeReference<List<ListeningQuestionItem>>() { });
		} catch (JsonProcessingException ex) {
			throw new IllegalStateException("Failed to deserialize listening questions", ex);
		}
	}

	private List<ListeningAttemptQuestionResultDto> readResults(String json) {
		try {
			return objectMapper.readValue(json, new TypeReference<List<ListeningAttemptQuestionResultDto>>() { });
		} catch (JsonProcessingException ex) {
			throw new IllegalStateException("Failed to deserialize listening attempt results", ex);
		}
	}

	private String writeJson(Object value) {
		try {
			return objectMapper.writeValueAsString(value);
		} catch (JsonProcessingException ex) {
			throw new IllegalStateException("Failed to serialize listening practice content", ex);
		}
	}
}
