package com.remelearning.english.speaking.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.remelearning.common.ai.pronunciation.PhonemePronunciationScore;
import com.remelearning.common.ai.pronunciation.PronunciationScore;
import com.remelearning.common.ai.pronunciation.PronunciationScoringClient;
import com.remelearning.common.ai.pronunciation.WordPronunciationScore;
import com.remelearning.common.ai.tts.TtsAudio;
import com.remelearning.common.ai.tts.TtsClient;
import com.remelearning.common.ai.tts.TtsRequest;
import com.remelearning.common.constants.LearningCategories;
import com.remelearning.common.exception.BusinessException;
import com.remelearning.common.storage.StorageClient;
import com.remelearning.english.practice.dto.PracticeAttemptRequest;
import com.remelearning.english.practice.dto.PracticeRedoRequest;
import com.remelearning.english.practice.service.PracticeService;
import com.remelearning.english.pronunciation.domain.PronunciationWeakPoint;
import com.remelearning.english.pronunciation.service.PronunciationWeakPointService;
import com.remelearning.english.speaking.domain.SpeakingAttempt;
import com.remelearning.english.speaking.domain.SpeakingAttemptDetailRow;
import com.remelearning.english.speaking.domain.SpeakingPracticeItem;
import com.remelearning.english.speaking.dto.GenerateSpeakingPracticeRequest;
import com.remelearning.english.speaking.dto.PhonemeScoreDto;
import com.remelearning.english.speaking.dto.SpeakingAttemptDetailDto;
import com.remelearning.english.speaking.dto.SpeakingAttemptHistoryEntryDto;
import com.remelearning.english.speaking.dto.SpeakingAttemptResultDto;
import com.remelearning.english.speaking.dto.SpeakingAudioResource;
import com.remelearning.english.speaking.dto.SpeakingPracticeItemDto;
import com.remelearning.english.speaking.dto.WordScoreDto;
import com.remelearning.english.speaking.generator.GeneratedSpeakingPractice;
import com.remelearning.english.speaking.generator.SpeakingPracticeGenerator;
import com.remelearning.english.speaking.mapper.SpeakingMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Orchestrates the speaking "learn" skill: generating an AI sentence/passage with a Supertonic
 * sample recording, scoring a learner's recorded attempt via ai-service's wav2vec2 GOP model
 * ({@link PronunciationScoringClient}), and feeding each scored word back into the existing
 * spaced-repetition/weak-point pipeline via {@link PracticeService#redo} - same reuse pattern as
 * {@code VocabLearnServiceImpl}/{@code GrammarLearnServiceImpl}/{@code ListeningLearnServiceImpl},
 * category {@code "pronunciation"} (reusing the domain that already exists from ai-service's
 * original forgetting-pattern pipeline, unlike listening's brand-new category).
 */
@Slf4j
@Service
public class SpeakingLearnServiceImpl implements SpeakingLearnService {

	private static final int DEFAULT_FOCUS_LIMIT = 8;
	private static final String ITEM_ID_PREFIX = "pronunciation:";
	// Must match bff-service's public route (LearnerController#getSpeakingSampleAudio), not
	// english-service's own internal controller route - this URL is returned straight to the FE
	// client, which only ever talks to bff-service.
	private static final String SAMPLE_AUDIO_URL = "/api/v1/learners/%s/learn/speaking/items/%d/sample-audio";
	private static final String SAMPLE_KEY = "speaking/sample/%s/%d.wav";
	private static final String ATTEMPT_KEY = "speaking/attempts/%s/%s.wav";
	/** A word's GOP score at/above this is treated as "correct" for the weak-point feed. */
	private static final double CORRECT_THRESHOLD = 0.6;

	private final SpeakingMapper speakingMapper;
	private final SpeakingPracticeGenerator generator;
	private final PronunciationWeakPointService pronunciationWeakPointService;
	private final PronunciationScoringClient pronunciationScoringClient;
	private final TtsClient ttsClient;
	private final StorageClient storageClient;
	private final PracticeService practiceService;
	private final ObjectMapper objectMapper;
	private final String ttsVoice;
	private final String ttsLang;

	public SpeakingLearnServiceImpl(
			SpeakingMapper speakingMapper,
			SpeakingPracticeGenerator generator,
			PronunciationWeakPointService pronunciationWeakPointService,
			PronunciationScoringClient pronunciationScoringClient,
			TtsClient ttsClient,
			StorageClient storageClient,
			PracticeService practiceService,
			ObjectMapper objectMapper,
			@Value("${speaking.tts.voice:F1}") String ttsVoice,
			@Value("${speaking.tts.lang:en}") String ttsLang) {
		this.speakingMapper = speakingMapper;
		this.generator = generator;
		this.pronunciationWeakPointService = pronunciationWeakPointService;
		this.pronunciationScoringClient = pronunciationScoringClient;
		this.ttsClient = ttsClient;
		this.storageClient = storageClient;
		this.practiceService = practiceService;
		this.objectMapper = objectMapper;
		this.ttsVoice = ttsVoice;
		this.ttsLang = ttsLang;
	}

	@Override
	@Transactional
	public SpeakingPracticeItemDto generate(String userId, GenerateSpeakingPracticeRequest request) {
		List<String> targetWords = resolveTargetWords(userId, request.getFocusItems());
		GeneratedSpeakingPractice generated = generator.generate(targetWords, request.getLevel(), request.getExamType());

		SpeakingPracticeItem item = SpeakingPracticeItem.builder()
				.userId(userId)
				.level(request.getLevel())
				.examType(request.getExamType())
				.topic(generated.topic())
				.targetText(generated.targetText())
				.translation(generated.translation())
				.build();
		speakingMapper.insertItem(item);

		TtsAudio sample = ttsClient.synthesize(TtsRequest.builder()
				.text(generated.targetText()).languageCode(ttsLang).voice(ttsVoice).build());
		String key = SAMPLE_KEY.formatted(userId, item.getId());
		storageClient.write(key, new ByteArrayInputStream(sample.getAudioBytes()), sample.getAudioBytes().length);
		speakingMapper.updateItemStorageKey(item.getId(), key);
		item.setStorageKey(key);

		return toItemDto(item);
	}

	@Override
	public SpeakingPracticeItemDto getItem(Long itemId) {
		return toItemDto(requireItem(itemId));
	}

	@Override
	public List<SpeakingPracticeItemDto> listItems(String userId) {
		return speakingMapper.findItemsByUserId(userId).stream().map(this::toItemDto).toList();
	}

	@Override
	public SpeakingAudioResource loadSampleAudio(Long itemId) {
		SpeakingPracticeItem item = requireItem(itemId);
		if (item.getStorageKey() == null) {
			throw BusinessException.notFound("Speaking practice sample audio not ready: id=" + itemId);
		}
		return new SpeakingAudioResource(
				storageClient.read(item.getStorageKey()), storageClient.size(item.getStorageKey()), "audio/wav", "speaking-sample-" + itemId + ".wav");
	}

	@Override
	@Transactional
	public SpeakingAttemptResultDto submit(String userId, Long practiceItemId, MultipartFile audio) {
		SpeakingPracticeItem item = requireItem(practiceItemId);

		String attemptKey = ATTEMPT_KEY.formatted(userId, UUID.randomUUID());
		try (InputStream learnerAudio = audio.getInputStream()) {
			storageClient.write(attemptKey, learnerAudio, audio.getSize());
		} catch (IOException ex) {
			throw new IllegalStateException("Failed to read uploaded speaking attempt audio", ex);
		}

		PronunciationScore score;
		try (InputStream scoringAudio = storageClient.read(attemptKey)) {
			score = pronunciationScoringClient.score(
					scoringAudio, audio.getOriginalFilename() == null ? "attempt.wav" : audio.getOriginalFilename(),
					item.getTargetText(), ttsLang);
		} catch (IOException ex) {
			throw new IllegalStateException("Failed to read stored speaking attempt audio for scoring", ex);
		}

		List<WordScoreDto> words = score.words().stream()
				.map(this::toWordScoreDto)
				.toList();

		SpeakingAttempt attempt = SpeakingAttempt.builder()
				.practiceItemId(item.getId())
				.userId(userId)
				.audioStorageKey(attemptKey)
				.overallScore(score.overall())
				.wordScoresJson(writeJson(words))
				.transcript(score.transcript())
				.weakPhonemesJson(writeJson(score.weakPhonemes()))
				.build();
		speakingMapper.insertAttempt(attempt);

		feedWeakPoints(userId, words);

		return SpeakingAttemptResultDto.builder()
				.overall(score.overall())
				.words(words)
				.transcript(score.transcript())
				.weakPhonemes(score.weakPhonemes())
				.actionAdvice(buildActionAdvice(words))
				.build();
	}

	@Override
	public List<SpeakingAttemptHistoryEntryDto> getHistory(String userId) {
		return speakingMapper.findHistoryByUserId(userId).stream()
				.map(row -> SpeakingAttemptHistoryEntryDto.builder()
						.attemptId(row.getAttemptId())
						.practiceItemId(row.getPracticeItemId())
						.level(row.getLevel())
						.examType(row.getExamType())
						.topic(row.getTopic())
						.overallScore(row.getOverallScore())
						.attemptedAt(row.getCreatedAt())
						.build())
				.toList();
	}

	@Override
	public SpeakingAttemptDetailDto getAttemptDetail(String userId, Long attemptId) {
		SpeakingAttemptDetailRow row = speakingMapper.findAttemptDetailByIdAndUserId(attemptId, userId);
		if (row == null) {
			throw BusinessException.notFound("Speaking practice attempt not found: id=" + attemptId);
		}
		return SpeakingAttemptDetailDto.builder()
				.attemptId(row.getAttemptId())
				.level(row.getLevel())
				.examType(row.getExamType())
				.topic(row.getTopic())
				.targetText(row.getTargetText())
				.overallScore(row.getOverallScore())
				.words(readWordScores(row.getWordScoresJson()))
				.transcript(row.getTranscript())
				.weakPhonemes(readStrings(row.getWeakPhonemesJson()))
				.attemptedAt(row.getCreatedAt())
				.build();
	}

	// --- helpers ---

	private List<String> resolveTargetWords(String userId, List<String> focusItems) {
		if (focusItems != null && !focusItems.isEmpty()) {
			return focusItems;
		}
		return pronunciationWeakPointService.getWeakPoints(userId, null).stream()
				.sorted(Comparator.comparingDouble(PronunciationWeakPoint::getForgettingScore).reversed())
				.limit(DEFAULT_FOCUS_LIMIT)
				.map(PronunciationWeakPoint::getLabel)
				.toList();
	}

	// Scores each word directly against the existing practice/redo pipeline - see
	// VocabLearnServiceImpl.feedWeakPoints for the full rationale. Treated as binary
	// (score >= CORRECT_THRESHOLD) rather than routed through the continuous BKT variant, the same
	// simplification ListeningLearnServiceImpl applies to its KEYWORD questions - consistent with
	// the rest of this session's "learn" skills rather than introducing a second scoring path.
	private void feedWeakPoints(String userId, List<WordScoreDto> words) {
		List<PracticeAttemptRequest> attempts = new ArrayList<>();
		Set<String> seenWords = new LinkedHashSet<>();
		for (WordScoreDto word : words) {
			if (word.getWord() == null || word.getWord().isBlank() || !seenWords.add(word.getWord().toLowerCase())) {
				continue;
			}
			PracticeAttemptRequest attempt = new PracticeAttemptRequest();
			attempt.setItemId(ITEM_ID_PREFIX + word.getWord().toLowerCase());
			attempt.setCategory(LearningCategories.PRONUNCIATION);
			attempt.setLabel(word.getWord());
			attempt.setCorrect(word.getScore() >= CORRECT_THRESHOLD);
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

	private List<String> buildActionAdvice(List<WordScoreDto> words) {
		Set<String> advice = new LinkedHashSet<>();
		for (WordScoreDto word : words) {
			if (word.getScore() >= CORRECT_THRESHOLD) {
				continue;
			}
			advice.add("Luyện lại từ '%s' - nghe mẫu và lặp lại nhiều lần.".formatted(word.getWord()));
		}
		return new ArrayList<>(advice);
	}

	private WordScoreDto toWordScoreDto(WordPronunciationScore word) {
		return WordScoreDto.builder()
				.word(word.word())
				.score(word.score())
				.phonemes(word.phonemes().stream()
						.map(this::toPhonemeScoreDto)
						.toList())
				.build();
	}

	private PhonemeScoreDto toPhonemeScoreDto(PhonemePronunciationScore phoneme) {
		return PhonemeScoreDto.builder().ipa(phoneme.ipa()).score(phoneme.score()).build();
	}

	private SpeakingPracticeItemDto toItemDto(SpeakingPracticeItem item) {
		return SpeakingPracticeItemDto.builder()
				.practiceItemId(item.getId())
				.sampleAudioUrl(item.getStorageKey() == null ? null : SAMPLE_AUDIO_URL.formatted(item.getUserId(), item.getId()))
				.level(item.getLevel())
				.examType(item.getExamType())
				.topic(item.getTopic())
				.targetText(item.getTargetText())
				.translation(item.getTranslation())
				.createdAt(item.getCreatedAt())
				.build();
	}

	private SpeakingPracticeItem requireItem(Long itemId) {
		SpeakingPracticeItem item = speakingMapper.findItemById(itemId);
		if (item == null) {
			throw BusinessException.notFound("Speaking practice item not found: id=" + itemId);
		}
		return item;
	}

	private List<WordScoreDto> readWordScores(String json) {
		try {
			return objectMapper.readValue(json, new TypeReference<List<WordScoreDto>>() { });
		} catch (JsonProcessingException ex) {
			throw new IllegalStateException("Failed to deserialize speaking word scores", ex);
		}
	}

	private List<String> readStrings(String json) {
		try {
			return objectMapper.readValue(json, new TypeReference<List<String>>() { });
		} catch (JsonProcessingException ex) {
			throw new IllegalStateException("Failed to deserialize speaking weak phonemes", ex);
		}
	}

	private String writeJson(Object value) {
		try {
			return objectMapper.writeValueAsString(value);
		} catch (JsonProcessingException ex) {
			throw new IllegalStateException("Failed to serialize speaking practice content", ex);
		}
	}
}
