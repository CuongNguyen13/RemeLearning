package com.remelearning.english.listening.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.remelearning.common.constants.LearningCategories;
import com.remelearning.common.exception.BusinessException;
import com.remelearning.common.storage.AudioContentTypes;
import com.remelearning.common.storage.StorageClient;
import com.remelearning.english.learn.common.DialogueAudioSynthesizer;
import com.remelearning.english.learn.common.DialogueLine;
import com.remelearning.english.learn.common.DialogueText;
import com.remelearning.english.learn.common.DialogueTextRenderer;
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
import com.remelearning.english.listening.generator.ListeningMistakeAnalyzer;
import com.remelearning.english.listening.generator.ListeningPracticeGenerator;
import com.remelearning.english.listening.generator.ListeningSessionRequest;
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
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Orchestrates the listening "learn" skill: generating a whole AI session of passages (one Gemini
 * call for 5-10 distinct transcripts+questions), grading a submitted attempt (MCQ/KEYWORD via the
 * pure {@link ListeningQuestionScoring}, OPEN via {@link OpenAnswerGrader}), and feeding each graded
 * question back into the existing spaced-repetition/weak-point pipeline via
 * {@link PracticeService#redo} - same reuse pattern as {@code VocabLearnServiceImpl}/
 * {@code GrammarLearnServiceImpl}, category {@code "listening"}.
 *
 * <p>Supertonic audio (via {@link DialogueAudioSynthesizer}) is synthesized lazily, on the first
 * request for a passage's audio rather than while generating it - a session's worth of eager TTS
 * would put minutes of work into one generate request, most of it for passages never opened.
 */
@Service
public class ListeningLearnServiceImpl implements ListeningLearnService {

	private static final int DEFAULT_FOCUS_LIMIT = 8;
	// One "Tạo bài luyện" builds a whole session of this many passages (inclusive bounds, drawn per
	// call) instead of a single one, so a learner sits down to a set of varied passages rather than
	// pressing generate over and over - the same 5-10 chain length the Thư viện uses per topic.
	private static final int MIN_SESSION_PASSAGES = 5;
	private static final int MAX_SESSION_PASSAGES = 10;
	// How many of the learner's most recent passage topics are fed to the generator as "do not reuse".
	private static final int AVOID_TOPICS_LIMIT = 12;
	private static final String ITEM_ID_PREFIX = "listening:";
	// Must match bff-service's public route (LearnerController#getListeningAudio), not english-service's
	// own internal controller route - this URL is returned straight to the FE client, which only ever
	// talks to bff-service.
	private static final String AUDIO_URL = "/api/v1/learners/%s/learn/listening/items/%d/audio";
	private static final String GENERATED_KEY = "listening/%s/%d.opus";

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

	// Generates a whole session of passages+questions in one LLM call and persists them; audio is
	// deliberately NOT synthesized here (see loadAudio), so a 5-10 passage session still costs one
	// round trip rather than dozens of TTS calls.
	@Override
	@Transactional
	public List<ListeningPracticeItemDto> generate(String userId, GenerateListeningPracticeRequest request) {
		List<ListeningPracticeItem> pastItems = listeningMapper.findItemsByUserId(userId);
		return generateSession(userId, resolveTargetKeywords(pastItems, request.getFocusItems()),
				avoidTopics(pastItems), request.getLevel(), request.getExamType(), request.getTranslationLang());
	}

	@Override
	public ListeningPracticeItemDto getItem(Long itemId) {
		ListeningPracticeItem item = requireItem(itemId);
		return toItemDto(item, readQuestions(item.getQuestionsJson()));
	}

	@Override
	public List<ListeningPracticeItemDto> listItems(String userId) {
		return listeningMapper.findPendingItemsByUserId(userId).stream()
				.map(item -> toItemDto(item, readQuestions(item.getQuestionsJson())))
				.toList();
	}

	// Streams the item's audio, synthesizing it on this first request if the session it belongs to
	// hasn't had it built yet - see synthesizeAndStoreAudio for why generation no longer does it.
	@Override
	@Transactional
	public ListeningAudioResource loadAudio(Long itemId) {
		ListeningPracticeItem item = requireItem(itemId);
		String storageKey = item.getStorageKey() != null ? item.getStorageKey() : synthesizeAndStoreAudio(item);
		return new ListeningAudioResource(
				storageClient.read(storageKey), storageClient.size(storageKey),
				AudioContentTypes.contentType(storageKey), "listening-" + itemId + AudioContentTypes.extension(storageKey));
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
					.type(question.getType())
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

	// Shared generate-and-persist step: builds one session of ListeningPracticeItems from whatever
	// target keywords/level/exam type the caller resolved (top weak keywords, explicit focus items,
	// or a past attempt's/library section's misses), inserts them into the same
	// listening_practice_items bank generate() uses, and returns the learner's refreshed pending
	// practice-set list - mirrors GrammarLearnServiceImpl.generatePracticeForRules.
	@Override
	@Transactional
	public List<ListeningPracticeItemDto> generatePracticeForKeywords(String userId, List<String> targetKeywords, String level, String examType) {
		generateSession(userId, targetKeywords, avoidTopics(listeningMapper.findItemsByUserId(userId)), level, examType, null);
		return listItems(userId);
	}

	// Generates AI practice targeted at one past attempt's mistakes: verifies the attempt belongs
	// to this learner, diffs its persisted resultsJson via the pure ListeningMistakeAnalyzer to
	// find every missed question's topic text, then reuses the exact same generate-and-persist
	// pipeline generate() uses (generatePracticeForKeywords) so the regenerated content lands in
	// the same bank as a normal "học thường" set.
	@Override
	@Transactional
	public List<ListeningPracticeItemDto> generatePracticeFromAttempt(String userId, Long attemptId) {
		ListeningAttemptDetailRow attempt = listeningMapper.findAttemptDetailByIdAndUserId(attemptId, userId);
		if (attempt == null) {
			throw BusinessException.notFound("Listening practice attempt not found: id=" + attemptId);
		}
		List<String> missedTopics = ListeningMistakeAnalyzer.extractMissedTopics(attempt.getResultsJson(), attempt.getTopic());
		return generatePracticeForKeywords(userId, missedTopics, attempt.getLevel(), attempt.getExamType());
	}

	// --- helpers ---

	// Actual generation+persistence work shared by generate() and generatePracticeForKeywords():
	// draws this session's passage count, asks the AI generator for that many distinct passages in
	// one call, and inserts each one. No TTS here - see synthesizeAndStoreAudio.
	private List<ListeningPracticeItemDto> generateSession(
			String userId, List<String> targetKeywords, List<String> avoidTopics,
			String level, String examType, String translationLang) {
		int passageCount = ThreadLocalRandom.current().nextInt(MIN_SESSION_PASSAGES, MAX_SESSION_PASSAGES + 1);
		List<GeneratedListeningPractice> generated = generator.generate(new ListeningSessionRequest(
				targetKeywords, level, examType, translationLang, avoidTopics, passageCount));

		List<ListeningPracticeItemDto> items = new ArrayList<>();
		for (GeneratedListeningPractice passage : generated) {
			items.add(persistPassage(userId, passage, level, examType));
		}
		return items;
	}

	// Persists one generated passage: the transcript/translation are rendered from its lines without
	// touching TTS, and the lines themselves are stored so the audio can be synthesized on first play.
	private ListeningPracticeItemDto persistPassage(
			String userId, GeneratedListeningPractice generated, String level, String examType) {
		DialogueText text = DialogueTextRenderer.render(generated.lines());
		ListeningPracticeItem item = ListeningPracticeItem.builder()
				.userId(userId)
				.level(level)
				.examType(examType)
				.topic(generated.topic())
				.transcript(text.transcriptText())
				.translation(text.translationText())
				.linesJson(writeJson(generated.lines()))
				.questionsJson(writeJson(generated.questions()))
				.build();
		listeningMapper.insertItem(item);
		return toItemDto(item, generated.questions());
	}

	// Synthesizes and stores an item's audio the first time it is played, recording the key so this
	// only ever runs once per item. Deferred out of generation because one generation call now
	// creates 5-10 passages, and synthesizing all of them up front (one TTS call per line plus a
	// transcode per passage) would make that single request take minutes - for passages the learner
	// may never open. Legacy rows predating lazy synthesis carry no lines to rebuild from, so a
	// missing audio there stays the 404 it always was.
	private String synthesizeAndStoreAudio(ListeningPracticeItem item) {
		if (item.getLinesJson() == null) {
			throw BusinessException.notFound("Listening practice audio not ready: id=" + item.getId());
		}
		SynthesizedDialogue synthesized = audioSynthesizer.synthesize(readLines(item.getLinesJson()), ttsLang);
		String key = GENERATED_KEY.formatted(item.getUserId(), item.getId());
		storageClient.write(key, new ByteArrayInputStream(synthesized.audioBytes()), synthesized.audioBytes().length);
		listeningMapper.updateItemStorageKey(item.getId(), key);
		return key;
	}

	// Explicit focusItems win; otherwise falls back to the learner's own recently-missed keywords
	// across their past listening attempts' wrong KEYWORD questions (this domain has no dedicated
	// weak-point table - see the V14 migration note); an empty result lets the generator pick its
	// own topics (brand-new learner with no history yet). Shuffled before truncating: taking the
	// first DEFAULT_FOCUS_LIMIT distinct keywords returned the identical set on every call, which is
	// what made consecutive sessions come back with the same passage.
	private List<String> resolveTargetKeywords(List<ListeningPracticeItem> pastItems, List<String> focusItems) {
		if (focusItems != null && !focusItems.isEmpty()) {
			return focusItems;
		}
		List<String> keywords = new ArrayList<>(pastItems.stream()
				.flatMap(item -> readQuestions(item.getQuestionsJson()).stream())
				.filter(question -> question.getType() == ListeningQuestionType.KEYWORD)
				.map(ListeningQuestionItem::getAnswer)
				.filter(Objects::nonNull)
				.distinct()
				.toList());
		Collections.shuffle(keywords);
		return keywords.stream().limit(DEFAULT_FOCUS_LIMIT).toList();
	}

	// The learner's most recent passage topics, handed to the generator as "do not reuse these" so a
	// new session can't repeat what they already listened to.
	private List<String> avoidTopics(List<ListeningPracticeItem> pastItems) {
		return pastItems.stream()
				.map(ListeningPracticeItem::getTopic)
				.filter(Objects::nonNull)
				.distinct()
				.limit(AVOID_TOPICS_LIMIT)
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
		// The audio URL is advertised as soon as the passage's lines exist, not only once the audio has
		// been synthesized: hitting that URL is exactly what triggers synthesis (see loadAudio).
		boolean audioAvailable = item.getStorageKey() != null || item.getLinesJson() != null;
		return ListeningPracticeItemDto.builder()
				.practiceItemId(item.getId())
				.audioUrl(audioAvailable ? AUDIO_URL.formatted(item.getUserId(), item.getId()) : null)
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

	private List<DialogueLine> readLines(String json) {
		try {
			return objectMapper.readValue(json, new TypeReference<List<DialogueLine>>() { });
		} catch (JsonProcessingException ex) {
			throw new IllegalStateException("Failed to deserialize listening passage lines", ex);
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
