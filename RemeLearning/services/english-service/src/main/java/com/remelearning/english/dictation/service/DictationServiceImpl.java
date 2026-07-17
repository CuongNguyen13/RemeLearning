package com.remelearning.english.dictation.service;

import com.remelearning.common.ai.tts.TtsAudio;
import com.remelearning.common.ai.tts.TtsClient;
import com.remelearning.common.ai.tts.TtsRequest;
import com.remelearning.common.event.WeakPointPayload;
import com.remelearning.common.exception.BusinessException;
import com.remelearning.common.storage.StorageClient;
import com.remelearning.english.dictation.analyzer.DictationAnalysis;
import com.remelearning.english.dictation.analyzer.DictationAnalyzer;
import com.remelearning.english.dictation.domain.DictationAttempt;
import com.remelearning.english.dictation.domain.DictationClip;
import com.remelearning.english.dictation.domain.DictationMiss;
import com.remelearning.english.dictation.domain.DictationPracticeItem;
import com.remelearning.english.dictation.domain.MissWordCount;
import com.remelearning.english.dictation.dto.DictationAttemptRequest;
import com.remelearning.english.dictation.dto.DictationAttemptResultDto;
import com.remelearning.english.dictation.dto.DictationAudioResource;
import com.remelearning.english.dictation.dto.DictationClipDto;
import com.remelearning.english.dictation.dto.DictationFacetsDto;
import com.remelearning.english.dictation.dto.DictationHistoryEntryDto;
import com.remelearning.english.dictation.dto.DictationPracticeItemDto;
import com.remelearning.english.dictation.dto.StartDictationSessionRequest;
import com.remelearning.english.dictation.dto.WordDiffDto;
import com.remelearning.english.dictation.dto.WordDiffTag;
import com.remelearning.english.dictation.kafka.DictationGapEventPublisher;
import com.remelearning.english.dictation.mapper.DictationMapper;
import com.remelearning.english.dictation.scoring.DictationScoreResult;
import com.remelearning.english.dictation.scoring.DictationScorer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Orchestrates dictation: browsing the fixed audio library, grading attempts against clip scripts
 * with the pure {@link DictationScorer}, recording misses, producing immediate AI feedback
 * ({@link DictationAnalyzer}) and publishing those misses into the recommendation pipeline
 * ({@link DictationGapEventPublisher}), plus the "Luyện nghe với AI" section (Supertonic-voiced
 * practice sentences stored via {@link StorageClient}). Constructor injection throughout so it can
 * be built directly with mocks in unit tests.
 */
@Slf4j
@Service
public class DictationServiceImpl implements DictationService {

	private static final String CLIP_AUDIO_URL = "/api/v1/dictation/clips/%d/audio";
	private static final String PRACTICE_AUDIO_URL = "/api/v1/dictation/ai-practice/items/%d/audio";
	private static final String GENERATED_KEY = "generated/%s/%d.wav";
	private static final String WEAK_POINT_CATEGORY = "vocabulary";
	private static final String WEAK_POINT_ITEM_PREFIX = "dictation:";
	private static final int DEFAULT_LIST_LIMIT = 50;

	private final DictationMapper dictationMapper;
	private final DictationAnalyzer dictationAnalyzer;
	private final DictationGapEventPublisher gapEventPublisher;
	private final TtsClient ttsClient;
	private final StorageClient storageClient;
	private final String ttsVoice;
	private final String ttsLang;
	private final int missWindow;

	public DictationServiceImpl(
			DictationMapper dictationMapper,
			DictationAnalyzer dictationAnalyzer,
			DictationGapEventPublisher gapEventPublisher,
			TtsClient ttsClient,
			StorageClient storageClient,
			@Value("${dictation.tts.voice:F1}") String ttsVoice,
			@Value("${dictation.tts.lang:en}") String ttsLang,
			@Value("${dictation.ai-practice.miss-window:8}") int missWindow) {
		this.dictationMapper = dictationMapper;
		this.dictationAnalyzer = dictationAnalyzer;
		this.gapEventPublisher = gapEventPublisher;
		this.ttsClient = ttsClient;
		this.storageClient = storageClient;
		this.ttsVoice = ttsVoice;
		this.ttsLang = ttsLang;
		this.missWindow = missWindow;
	}

	// Reads the four distinct facet value lists for the UI filters.
	@Override
	public DictationFacetsDto getFacets() {
		return DictationFacetsDto.builder()
				.skills(dictationMapper.findDistinctSkills())
				.levels(dictationMapper.findDistinctLevels())
				.topics(dictationMapper.findDistinctTopics())
				.examTypes(dictationMapper.findDistinctExamTypes())
				.build();
	}

	@Override
	public List<DictationClipDto> listClips(String skill, String level, String topic, String examType, int limit) {
		int effectiveLimit = limit <= 0 ? DEFAULT_LIST_LIMIT : limit;
		return dictationMapper.findClipsByFacets(skill, level, topic, examType, effectiveLimit).stream()
				.map(this::toClipDto)
				.toList();
	}

	// Picks a random batch of clips matching the requested facets, so repeated sessions vary.
	@Override
	public List<DictationClipDto> startSession(String userId, StartDictationSessionRequest request) {
		return dictationMapper.findRandomClipsByFacets(
						request.getSkill(), request.getLevel(), request.getTopic(), request.getExamType(), request.getCount()).stream()
				.map(this::toClipDto)
				.toList();
	}

	@Override
	public DictationAudioResource loadClipAudio(Long clipId) {
		DictationClip clip = dictationMapper.findClipById(clipId);
		if (clip == null) {
			throw BusinessException.notFound("Dictation clip not found: id=" + clipId);
		}
		return toAudioResource(clip.getStorageKey(), clip.getCode());
	}

	// Grades the attempt against the correct reference text (library clip or AI-practice sentence),
	// records the per-word misses, then runs the shared immediate-AI + recommendation-feed flow.
	@Override
	@Transactional
	public DictationAttemptResultDto submitAttempt(DictationAttemptRequest request) {
		ReferenceTarget target = resolveReference(request);

		DictationScoreResult score = DictationScorer.score(target.referenceText(), request.getUserTranscript());

		DictationAttempt attempt = DictationAttempt.builder()
				.clipId(request.getClipId())
				.practiceItemId(request.getPracticeItemId())
				.userId(request.getUserId())
				.userTranscript(request.getUserTranscript())
				.accuracy(score.getAccuracy())
				.wer(score.getWer())
				.build();
		dictationMapper.insertAttempt(attempt);

		List<DictationMiss> misses = extractMisses(score.getDiff(), attempt.getId(), request.getUserId(), request.getClipId());
		if (!misses.isEmpty()) {
			dictationMapper.insertMisses(misses);
		}

		List<String> missedWords = distinctMissedWords(misses);
		DictationAnalysis analysis = dictationAnalyzer.analyzeAttempt(target.referenceText(), missedWords);
		persistPracticeSentences(request.getUserId(), analysis.getPracticeSentences());
		publishWeakPoints(target.recordingId(), request.getUserId(), missedWords, analysis.getSuggestions());

		return DictationAttemptResultDto.builder()
				.referenceText(target.referenceText())
				.accuracy(score.getAccuracy())
				.wer(score.getWer())
				.diff(score.getDiff())
				.aiSuggestions(analysis.getSuggestions())
				.practiceSentences(analysis.getPracticeSentences())
				.build();
	}

	@Override
	public List<DictationHistoryEntryDto> getHistory(String userId) {
		return dictationMapper.findHistoryByUserId(userId).stream()
				.map(row -> DictationHistoryEntryDto.builder()
						.attemptId(row.getAttemptId())
						.clipId(row.getClipId())
						.title(row.getTitle())
						.skill(row.getSkill())
						.level(row.getLevel())
						.examType(row.getExamType())
						.accuracy(row.getAccuracy())
						.wer(row.getWer())
						.attemptedAt(row.getCreatedAt())
						.build())
				.toList();
	}

	@Override
	public List<DictationPracticeItemDto> getAiPractice(String userId) {
		return dictationMapper.findPracticeItemsByUserId(userId).stream()
				.map(this::toPracticeItemDto)
				.toList();
	}

	// Synthesizes audio for any practice items that don't have it yet; if there are none, first
	// generates fresh practice sentences from the learner's most-missed words. A single item's
	// TTS/storage failure is logged and skipped so the rest still get produced.
	@Override
	@Transactional
	public List<DictationPracticeItemDto> generateAiPractice(String userId) {
		List<DictationPracticeItem> pending = dictationMapper.findPracticeItemsWithoutAudio(userId);
		if (pending.isEmpty()) {
			pending = generateFreshPracticeItems(userId);
		}

		for (DictationPracticeItem item : pending) {
			try {
				TtsAudio audio = ttsClient.synthesize(TtsRequest.builder()
						.text(item.getSentenceText()).languageCode(ttsLang).voice(ttsVoice).build());
				String key = GENERATED_KEY.formatted(userId, item.getId());
				storageClient.write(key, new ByteArrayInputStream(audio.getAudioBytes()), audio.getAudioBytes().length);
				dictationMapper.updatePracticeItemStorageKey(item.getId(), key);
			} catch (RuntimeException ex) {
				log.warn("Failed to synthesize AI-practice audio for item {}, skipping", item.getId(), ex);
			}
		}
		return getAiPractice(userId);
	}

	@Override
	public DictationAudioResource loadPracticeAudio(Long practiceItemId) {
		DictationPracticeItem item = dictationMapper.findPracticeItemById(practiceItemId);
		if (item == null || item.getStorageKey() == null) {
			throw BusinessException.notFound("Dictation practice audio not ready: id=" + practiceItemId);
		}
		return toAudioResource(item.getStorageKey(), "practice-" + practiceItemId);
	}

	// --- helpers ---

	// The reference text + an event recordingId, resolved from whichever id the attempt carries.
	private ReferenceTarget resolveReference(DictationAttemptRequest request) {
		if (request.getClipId() != null) {
			DictationClip clip = dictationMapper.findClipById(request.getClipId());
			if (clip == null) {
				throw BusinessException.notFound("Dictation clip not found: id=" + request.getClipId());
			}
			return new ReferenceTarget(clip.getScriptText(), "dictation-clip-" + clip.getId());
		}
		if (request.getPracticeItemId() != null) {
			DictationPracticeItem item = dictationMapper.findPracticeItemById(request.getPracticeItemId());
			if (item == null) {
				throw BusinessException.notFound("Dictation practice item not found: id=" + request.getPracticeItemId());
			}
			return new ReferenceTarget(item.getSentenceText(), "dictation-practice-" + item.getId());
		}
		throw BusinessException.badRequest("An attempt must reference either clipId or practiceItemId");
	}

	// Turns the WER diff's wrong slots (missing/substituted, which have an expected word) into miss rows.
	private List<DictationMiss> extractMisses(List<WordDiffDto> diff, Long attemptId, String userId, Long clipId) {
		List<DictationMiss> misses = new ArrayList<>();
		for (WordDiffDto slot : diff) {
			if (slot.getTag() == WordDiffTag.MISSING || slot.getTag() == WordDiffTag.SUBSTITUTED) {
				misses.add(DictationMiss.builder()
						.attemptId(attemptId)
						.userId(userId)
						.clipId(clipId)
						.expectedWord(slot.getExpectedWord())
						.actualWord(slot.getActualWord())
						.tag(slot.getTag().name())
						.build());
			}
		}
		return misses;
	}

	// Distinct lower-cased expected words from the misses, preserving first-seen order.
	private List<String> distinctMissedWords(List<DictationMiss> misses) {
		Set<String> words = new LinkedHashSet<>();
		for (DictationMiss miss : misses) {
			words.add(miss.getExpectedWord().toLowerCase());
		}
		return new ArrayList<>(words);
	}

	// Persists the AI-suggested practice sentences (no audio yet - synthesized later on demand).
	private void persistPracticeSentences(String userId, List<String> sentences) {
		for (String sentence : sentences) {
			dictationMapper.insertPracticeItem(DictationPracticeItem.builder()
					.userId(userId).sentenceText(sentence).source("attempt").build());
		}
	}

	// Publishes each missed word as a vocabulary weak point onto learning.gap.analyzed; the
	// forgetting score saturates with the learner's running miss count for that word.
	private void publishWeakPoints(String recordingId, String userId, List<String> missedWords, List<String> suggestions) {
		String recommendation = suggestions.isEmpty() ? null : suggestions.get(0);
		List<WeakPointPayload> weakPoints = new ArrayList<>();
		for (String word : missedWords) {
			int missCount = dictationMapper.countMissesForWord(userId, word);
			WeakPointPayload payload = new WeakPointPayload();
			payload.setItemId(WEAK_POINT_ITEM_PREFIX + word);
			payload.setCategory(WEAK_POINT_CATEGORY);
			payload.setLabel(word);
			payload.setForgettingScore((double) missCount / (missCount + 2.0));
			payload.setRecommendation(recommendation);
			weakPoints.add(payload);
		}
		gapEventPublisher.publish(recordingId, userId, weakPoints);
	}

	// Generates and persists fresh practice sentences from the learner's most-missed words.
	private List<DictationPracticeItem> generateFreshPracticeItems(String userId) {
		List<String> words = dictationMapper.findTopMissedWords(userId, missWindow).stream()
				.map(MissWordCount::getWord)
				.toList();
		List<DictationPracticeItem> created = new ArrayList<>();
		for (String sentence : dictationAnalyzer.generatePracticeSentences(words)) {
			DictationPracticeItem item = DictationPracticeItem.builder()
					.userId(userId).sentenceText(sentence).source("ai-practice").build();
			dictationMapper.insertPracticeItem(item);
			created.add(item);
		}
		return created;
	}

	private DictationClipDto toClipDto(DictationClip clip) {
		return DictationClipDto.builder()
				.clipId(clip.getId())
				.code(clip.getCode())
				.title(clip.getTitle())
				.skill(clip.getSkill())
				.level(clip.getLevel())
				.topic(clip.getTopic())
				.examType(clip.getExamType())
				.audioUrl(CLIP_AUDIO_URL.formatted(clip.getId()))
				.build();
	}

	private DictationPracticeItemDto toPracticeItemDto(DictationPracticeItem item) {
		return DictationPracticeItemDto.builder()
				.practiceItemId(item.getId())
				.audioUrl(item.getStorageKey() == null ? null : PRACTICE_AUDIO_URL.formatted(item.getId()))
				.build();
	}

	// Opens the stored object and infers a content type from the key's extension (mp3 vs wav).
	private DictationAudioResource toAudioResource(String storageKey, String baseName) {
		boolean isMp3 = storageKey.toLowerCase().endsWith(".mp3");
		String contentType = isMp3 ? "audio/mpeg" : "audio/wav";
		String extension = isMp3 ? ".mp3" : ".wav";
		return new DictationAudioResource(
				storageClient.read(storageKey), storageClient.size(storageKey), contentType, baseName + extension);
	}

	private record ReferenceTarget(String referenceText, String recordingId) {
	}
}
