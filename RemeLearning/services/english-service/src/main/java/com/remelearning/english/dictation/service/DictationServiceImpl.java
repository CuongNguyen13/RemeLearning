package com.remelearning.english.dictation.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.remelearning.common.ai.align.SentenceAlignmentClient;
import com.remelearning.common.ai.align.SentenceTiming;
import com.remelearning.common.ai.tts.TtsAudio;
import com.remelearning.common.ai.tts.TtsClient;
import com.remelearning.common.ai.tts.TtsRequest;
import com.remelearning.common.event.WeakPointPayload;
import com.remelearning.common.exception.BusinessException;
import com.remelearning.common.storage.StorageClient;
import java.util.Random;
import com.remelearning.english.dictation.analyzer.DialogueGenerationResult;
import com.remelearning.english.dictation.analyzer.DictationAnalysis;
import com.remelearning.english.dictation.analyzer.DictationAnalyzer;
import com.remelearning.english.dictation.analyzer.DictationDialogueGenerator;
import com.remelearning.english.dictation.analyzer.DictationDialogueLine;
import com.remelearning.english.dictation.analyzer.DictationSentenceTranslator;
import com.remelearning.english.dictation.audio.WavAudioMerger;
import com.remelearning.english.dictation.domain.DictationAttempt;
import com.remelearning.english.dictation.domain.DictationAttemptDetailRow;
import com.remelearning.english.dictation.domain.DictationClip;
import com.remelearning.english.dictation.domain.DictationClipSentence;
import com.remelearning.english.dictation.domain.DictationMiss;
import com.remelearning.english.dictation.domain.DictationPracticeItem;
import com.remelearning.english.dictation.domain.FolderCount;
import com.remelearning.english.dictation.domain.MissWordCount;
import com.remelearning.english.dictation.dto.DictationAttemptDetailDto;
import com.remelearning.english.dictation.dto.DictationAttemptRequest;
import com.remelearning.english.dictation.dto.DictationAttemptResultDto;
import com.remelearning.english.dictation.dto.DictationAudioResource;
import com.remelearning.english.dictation.dto.DictationClipDetailDto;
import com.remelearning.english.dictation.dto.DictationClipDto;
import com.remelearning.english.dictation.dto.DictationFacetsDto;
import com.remelearning.english.dictation.dto.DictationFolderDto;
import com.remelearning.english.dictation.dto.DictationHistoryEntryDto;
import com.remelearning.english.dictation.dto.DictationLessonSummaryDto;
import com.remelearning.english.dictation.dto.DictationMistakeDto;
import com.remelearning.english.dictation.dto.DictationPracticeItemDetailDto;
import com.remelearning.english.dictation.dto.DictationPracticeItemDto;
import com.remelearning.english.dictation.dto.DictationPracticeType;
import com.remelearning.english.dictation.dto.DictationSentenceDto;
import com.remelearning.english.dictation.dto.DictationSentenceMistakeRequest;
import com.remelearning.english.dictation.dto.GenerateAiPracticeRequest;
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
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
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
	private static final String RANDOM_FACET = "RANDOM";
	private static final int DEFAULT_LIST_LIMIT = 50;
	// The ten Supertonic preset voices (ai-service's SUPPORTED_VOICES) - randomly assigned one per
	// distinct speaker in a generated AI-practice dialogue.
	private static final List<String> VOICE_POOL = List.of("F1", "F2", "F3", "F4", "F5", "M1", "M2", "M3", "M4", "M5");
	// CEFR levels a learner can pick (or "RANDOM" among) when creating an AI-practice passage.
	private static final List<String> LEVEL_POOL = List.of("A1", "A2", "B1", "B2", "C1");
	// Used only if the library has no exam-type facets yet to randomize among.
	private static final List<String> EXAM_TYPE_FALLBACK = List.of("TOEIC", "IELTS", "TOEFL", "General");
	// Shared Random instance for facet resolution (level/exam-type facet randomization).
	private static final Random RANDOM = new Random();

	private final DictationMapper dictationMapper;
	private final DictationAnalyzer dictationAnalyzer;
	private final DictationDialogueGenerator dialogueGenerator;
	private final DictationSentenceTranslator sentenceTranslator;
	private final DictationGapEventPublisher gapEventPublisher;
	private final TtsClient ttsClient;
	private final StorageClient storageClient;
	private final SentenceAlignmentClient sentenceAlignmentClient;
	private final ObjectMapper objectMapper;
	private final String ttsVoice;
	private final String ttsLang;
	private final int missWindow;
	private final int minListensForHint;

	public DictationServiceImpl(
			DictationMapper dictationMapper,
			DictationAnalyzer dictationAnalyzer,
			DictationDialogueGenerator dialogueGenerator,
			DictationSentenceTranslator sentenceTranslator,
			DictationGapEventPublisher gapEventPublisher,
			TtsClient ttsClient,
			StorageClient storageClient,
			SentenceAlignmentClient sentenceAlignmentClient,
			ObjectMapper objectMapper,
			@Value("${dictation.tts.voice:F1}") String ttsVoice,
			@Value("${dictation.tts.lang:en}") String ttsLang,
			@Value("${dictation.ai-practice.miss-window:8}") int missWindow,
			@Value("${dictation.hint.min-listens:3}") int minListensForHint) {
		this.dictationMapper = dictationMapper;
		this.dictationAnalyzer = dictationAnalyzer;
		this.dialogueGenerator = dialogueGenerator;
		this.sentenceTranslator = sentenceTranslator;
		this.gapEventPublisher = gapEventPublisher;
		this.ttsClient = ttsClient;
		this.storageClient = storageClient;
		this.sentenceAlignmentClient = sentenceAlignmentClient;
		this.objectMapper = objectMapper;
		this.ttsVoice = ttsVoice;
		this.ttsLang = ttsLang;
		this.missWindow = missWindow;
		this.minListensForHint = minListensForHint;
	}

	// Reads the four distinct facet value lists for the UI filters, plus the configured hint threshold.
	@Override
	public DictationFacetsDto getFacets() {
		return DictationFacetsDto.builder()
				.skills(dictationMapper.findDistinctSkills())
				.levels(dictationMapper.findDistinctLevels())
				.topics(dictationMapper.findDistinctTopics())
				.examTypes(dictationMapper.findDistinctExamTypes())
				.minListensForHint(minListensForHint)
				.build();
	}

	// Distinct clip folders with their lesson counts, for the folder-browse listing.
	@Override
	public List<DictationFolderDto> listFolders() {
		return dictationMapper.findDistinctFolders().stream()
				.map(this::toFolderDto)
				.toList();
	}

	// Light-weight lesson listing for one folder (no script/sentences - those load on demand per clip).
	@Override
	public List<DictationLessonSummaryDto> listFolderLessons(String folder) {
		return dictationMapper.findClipsByFolder(folder).stream()
				.map(this::toLessonSummaryDto)
				.toList();
	}

	// Full clip detail (script + ordered sentences) for the sentence-mode practice screen. Any
	// sentence still missing startMs/endMs is lazily AI-aligned against the clip's own audio before
	// the response is built (see ensureSentencesAligned) - a failed alignment attempt just leaves
	// those sentences null rather than failing the whole request.
	@Override
	@Transactional
	public DictationClipDetailDto getClipDetail(Long clipId, String translationLang) {
		DictationClip clip = dictationMapper.findClipById(clipId);
		if (clip == null) {
			throw BusinessException.notFound("Dictation clip not found: id=" + clipId);
		}
		List<DictationClipSentence> clipSentences = dictationMapper.findSentencesByClipId(clipId);
		ensureSentencesAligned(clip, clipSentences);
		ensureSentencesTranslated(clip, clipSentences, translationLang);

		List<DictationSentenceDto> sentences = clipSentences.stream()
				.map(this::toSentenceDto)
				.toList();
		return DictationClipDetailDto.builder()
				.clipId(clip.getId())
				.code(clip.getCode())
				.title(clip.getTitle())
				.audioUrl(CLIP_AUDIO_URL.formatted(clip.getId()))
				.scriptText(clip.getScriptText())
				.sentences(sentences)
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
	// records the per-word misses (both from the final transcript's diff and from any sentence-mode
	// retries the learner needed before getting each sentence right), then runs the shared
	// immediate-AI + recommendation-feed flow.
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
		misses.addAll(extractSentenceMistakes(request.getSentenceMistakes(), attempt.getId(), request.getUserId(), request.getClipId()));
		if (!misses.isEmpty()) {
			dictationMapper.insertMisses(misses);
		}

		List<String> missedWords = distinctMissedWords(misses);
		DictationAnalysis analysis = dictationAnalyzer.analyzeAttempt(target.referenceText(), missedWords);
		persistPracticeSentences(request.getUserId(), analysis.getPracticeSentences());
		dictationMapper.updateAttemptAiSuggestions(attempt.getId(), serializeSuggestions(analysis.getSuggestions()));
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
						.attemptCount(row.getAttemptCount())
						.practiceType(row.getClipId() != null ? DictationPracticeType.LIBRARY : DictationPracticeType.AI_PRACTICE)
						.build())
				.toList();
	}

	@Override
	public DictationAttemptDetailDto getAttemptDetail(String userId, Long attemptId) {
		DictationAttemptDetailRow row = dictationMapper.findAttemptDetailByIdAndUserId(attemptId, userId);
		if (row == null) {
			throw BusinessException.notFound("Dictation attempt not found: id=" + attemptId);
		}
		List<DictationMistakeDto> mistakes = dictationMapper.findMissesByAttemptId(attemptId).stream()
				.map(miss -> DictationMistakeDto.builder()
						.expectedWord(miss.getExpectedWord())
						.actualWord(miss.getActualWord())
						.tag(WordDiffTag.valueOf(miss.getTag()))
						.build())
				.toList();
		return DictationAttemptDetailDto.builder()
				.attemptId(row.getAttemptId())
				.title(row.getTitle())
				.skill(row.getSkill())
				.level(row.getLevel())
				.examType(row.getExamType())
				.referenceText(row.getReferenceText())
				.userTranscript(row.getUserTranscript())
				.accuracy(row.getAccuracy())
				.wer(row.getWer())
				.mistakes(mistakes)
				.aiSuggestions(deserializeSuggestions(row.getAiSuggestions()))
				.attemptedAt(row.getCreatedAt())
				.build();
	}

	@Override
	public List<DictationPracticeItemDto> getAiPractice(String userId) {
		return dictationMapper.findPracticeItemsByUserId(userId).stream()
				.map(this::toPracticeItemDto)
				.toList();
	}

	// Full detail for one AI-practice item, split into sentences the same way a library clip's script
	// is, so the sentence-mode runner can drive both sections identically. The passage's audio is one
	// merged file with no per-sentence timing, so every sentence's startMs/endMs stays null - the
	// client falls back to its own word-count-share estimate, exactly as it does for a library clip
	// whose AI alignment hasn't run yet. The stored translation (generated once, at creation time) is
	// split the same way and zipped in by index, so it stays 1:1 with sentenceText's own line order.
	@Override
	public DictationPracticeItemDetailDto getAiPracticeDetail(Long practiceItemId) {
		DictationPracticeItem item = dictationMapper.findPracticeItemById(practiceItemId);
		if (item == null) {
			throw BusinessException.notFound("AI-practice item not found: id=" + practiceItemId);
		}
		List<DictationSentenceDto> sentences = splitIntoSentences(item.getSentenceText(), item.getTranslationText());
		return DictationPracticeItemDetailDto.builder()
				.practiceItemId(item.getId())
				.audioUrl(item.getStorageKey() == null ? null : PRACTICE_AUDIO_URL.formatted(item.getId()))
				.scriptText(item.getSentenceText())
				.level(item.getLevel())
				.examType(item.getExamType())
				.topic(item.getTopic())
				.sentences(sentences)
				.build();
	}

	// Builds one AI-generated listening-practice passage: (1) reads whichever practice items are
	// still without audio (or, if none, the learner's recent top-missed words) as the target
	// words/phrases to practice; (2) resolves any "RANDOM" level/examType facet to one concrete value;
	// (3) sends them to the configured LLM (Gemini) to write one passage - a monologue or a
	// multi-speaker dialogue - reusing them naturally, with a topic label and an optional per-line
	// translation; (4) assigns a random Supertonic voice per distinct speaker; (5) synthesizes each
	// line via the TTS AI service and merges the clips into one continuous audio file, replacing
	// whatever pending items existed with this single new one. A failure anywhere in
	// generation/synthesis is logged and swallowed, leaving prior pending items untouched so the next
	// call can retry.
	@Override
	@Transactional
	public List<DictationPracticeItemDto> generateAiPractice(String userId, GenerateAiPracticeRequest request) {
		List<DictationPracticeItem> pending = dictationMapper.findPracticeItemsWithoutAudio(userId);
		List<String> targetPhrases = pending.isEmpty()
				? dictationMapper.findTopMissedWords(userId, missWindow).stream().map(MissWordCount::getWord).toList()
				: pending.stream().map(DictationPracticeItem::getSentenceText).toList();

		String level = resolveLevel(request.getLevel());
		String examType = resolveExamType(request.getExamType());

		try {
			DialogueGenerationResult dialogue = dialogueGenerator.generateDialogue(targetPhrases, level, examType, request.getTranslationLang());
			synthesizeDialoguePracticeItem(userId, dialogue, level, examType);
			if (!pending.isEmpty()) {
				dictationMapper.deletePracticeItemsWithoutAudio(userId);
			}
		} catch (RuntimeException ex) {
			log.warn("Failed to generate AI-practice dialogue for user {}, leaving pending items untouched", userId, ex);
		}
		return getAiPractice(userId);
	}

	// Resolves a level facet: a concrete value passes through unchanged, "RANDOM" picks one from the
	// fixed CEFR pool, and null/blank means no preference (the generator falls back to its own default).
	private String resolveLevel(String requested) {
		if (RANDOM_FACET.equalsIgnoreCase(requested)) {
			return LEVEL_POOL.get(RANDOM.nextInt(LEVEL_POOL.size()));
		}
		return requested;
	}

	// Resolves an exam-type facet the same way, randomizing among the library's own distinct exam
	// types (falling back to a small constant list if the library has none yet).
	private String resolveExamType(String requested) {
		if (RANDOM_FACET.equalsIgnoreCase(requested)) {
			List<String> pool = dictationMapper.findDistinctExamTypes();
			List<String> effectivePool = pool.isEmpty() ? EXAM_TYPE_FALLBACK : pool;
			return effectivePool.get(RANDOM.nextInt(effectivePool.size()));
		}
		return requested;
	}

	// Generates AI-practice content targeted at one specific past attempt's mistakes (the "Luyện
	// tập với AI" action from a history row): one dialogue/short-passage item via the same generator
	// Path A uses (previously this used a separate rule-based/LLM sentence-by-sentence analyzer,
	// producing many single-sentence items instead of one cohesive passage), synthesizes its audio,
	// and returns the learner's refreshed AI-practice list. No level/examType selector exists for this
	// entry point, so both are left null (the generator's own default applies). Throws not-found if
	// the attempt doesn't exist or belongs to someone else, the same ownership check getAttemptDetail uses.
	@Override
	@Transactional
	public List<DictationPracticeItemDto> generateAiPracticeFromAttempt(String userId, Long attemptId, String translationLang) {
		if (dictationMapper.findAttemptDetailByIdAndUserId(attemptId, userId) == null) {
			throw BusinessException.notFound("Dictation attempt not found: id=" + attemptId);
		}
		List<String> missedWords = dictationMapper.findMissesByAttemptId(attemptId).stream()
				.map(miss -> miss.getExpectedWord().toLowerCase())
				.distinct()
				.toList();
		DialogueGenerationResult dialogue = dialogueGenerator.generateDialogue(missedWords, null, null, translationLang);
		synthesizeDialoguePracticeItem(userId, dialogue, null, null);
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

	// Lazily AI-aligns any sentence still missing startMs/endMs against the clip's own audio: reads
	// the audio via StorageClient, sends it plus the ordered sentence texts to SentenceAlignmentClient
	// (ai-service Whisper word-timestamps + sequential matching), persists whichever sentences came
	// back with a timing, and updates the in-memory list so the response reflects them immediately.
	// Skipped entirely when nothing is missing or the clip has no audio yet; any failure (ai-service
	// unreachable, bad audio) is logged and swallowed so clip detail still loads without timestamps.
	private void ensureSentencesAligned(DictationClip clip, List<DictationClipSentence> sentences) {
		boolean anyMissing = sentences.stream().anyMatch(s -> s.getStartMs() == null || s.getEndMs() == null);
		if (!anyMissing || sentences.isEmpty() || clip.getStorageKey() == null || clip.getStorageKey().isBlank()) {
			return;
		}

		try (InputStream audio = storageClient.read(clip.getStorageKey())) {
			List<String> texts = sentences.stream().map(DictationClipSentence::getText).toList();
			List<SentenceTiming> timings = sentenceAlignmentClient.align(audio, clip.getStorageKey(), texts);

			for (int i = 0; i < sentences.size(); i++) {
				SentenceTiming timing = timings.get(i);
				if (timing.startMs() == null || timing.endMs() == null) {
					continue;
				}
				DictationClipSentence sentence = sentences.get(i);
				dictationMapper.updateSentenceTimestamps(clip.getId(), sentence.getSeq(), timing.startMs(), timing.endMs());
				sentence.setStartMs(timing.startMs());
				sentence.setEndMs(timing.endMs());
			}
		} catch (Exception ex) {
			log.warn("Failed to AI-align sentence timestamps for clip {}, returning without them", clip.getId(), ex);
		}
	}

	// Lazily translates any sentence still missing a translation, when the requested language isn't
	// English (the content's own language). Mirrors ensureSentencesAligned's lazy-fill shape: one
	// batched LLM call for the whole clip, persisted per-sentence, with the in-memory list updated so
	// the response reflects them immediately. Skipped entirely when no language was requested, it's
	// English, or nothing is missing.
	private void ensureSentencesTranslated(DictationClip clip, List<DictationClipSentence> sentences, String translationLang) {
		if (translationLang == null || "en".equalsIgnoreCase(translationLang) || sentences.isEmpty()) {
			return;
		}
		boolean anyMissing = sentences.stream().anyMatch(s -> s.getTranslation() == null);
		if (!anyMissing) {
			return;
		}

		List<String> texts = sentences.stream().map(DictationClipSentence::getText).toList();
		List<String> translations = sentenceTranslator.translate(texts, translationLang);
		for (int i = 0; i < sentences.size(); i++) {
			String translation = translations.get(i);
			if (translation == null) {
				continue;
			}
			DictationClipSentence sentence = sentences.get(i);
			dictationMapper.updateSentenceTranslation(clip.getId(), sentence.getSeq(), translation);
			sentence.setTranslation(translation);
		}
	}

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

	// Scores each sentence-mode retry (expected sentence vs. what the learner actually typed on that
	// failed check) independently and turns the resulting word-level slots into miss rows, the same
	// shape a normal wrong answer produces. This is how a learner's earlier stumbles still reach the
	// AI/weak-point pipeline even though sentence-mode forces the final transcript to be fully correct.
	private List<DictationMiss> extractSentenceMistakes(
			List<DictationSentenceMistakeRequest> sentenceMistakes, Long attemptId, String userId, Long clipId) {
		if (sentenceMistakes == null || sentenceMistakes.isEmpty()) {
			return new ArrayList<>();
		}
		List<DictationMiss> misses = new ArrayList<>();
		for (DictationSentenceMistakeRequest mistake : sentenceMistakes) {
			DictationScoreResult sentenceScore = DictationScorer.score(mistake.getExpectedText(), mistake.getAttemptedText());
			misses.addAll(extractMisses(sentenceScore.getDiff(), attemptId, userId, clipId));
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

	// Synthesizes each dialogue line with its assigned speaker voice, merges the resulting WAV
	// clips into one continuous audio file, and persists the whole passage (rendered as
	// "Speaker: line" per turn, or plain text for a single-speaker monologue) as one new practice
	// item, along with its resolved level/examType/topic and (if generated) translation. The TTS
	// audio for each line is synthesized from the EXACT SAME text that gets persisted as the
	// graded/displayed sentence (including any "Speaker: " prefix) - previously the audio spoke only
	// the bare line while the graded text carried the prefix, so multi-speaker audio never said the
	// name the learner was graded against; using one shared `lineText` for both fixes that. Any
	// TTS/storage failure propagates so the caller can leave prior pending items intact.
	private void synthesizeDialoguePracticeItem(String userId, DialogueGenerationResult dialogue, String level, String examType) {
		List<DictationDialogueLine> lines = dialogue.lines();
		Map<String, String> speakerVoices = assignVoicesToSpeakers(lines);
		boolean multiSpeaker = speakerVoices.size() > 1;
		List<byte[]> clips = new ArrayList<>();
		StringBuilder passageText = new StringBuilder();
		StringBuilder translationText = new StringBuilder();
		boolean anyTranslation = false;
		for (DictationDialogueLine line : lines) {
			String lineText = multiSpeaker ? line.speaker() + ": " + line.text() : line.text();
			TtsAudio audio = ttsClient.synthesize(TtsRequest.builder()
					.text(lineText).languageCode(ttsLang).voice(speakerVoices.get(line.speaker())).build());
			clips.add(audio.getAudioBytes());
			if (!passageText.isEmpty()) {
				passageText.append('\n');
				translationText.append('\n');
			}
			passageText.append(lineText);
			if (line.translation() != null) {
				anyTranslation = true;
				translationText.append(multiSpeaker ? line.speaker() + ": " + line.translation() : line.translation());
			}
		}

		DictationPracticeItem item = DictationPracticeItem.builder()
				.userId(userId).sentenceText(passageText.toString()).source("ai-practice")
				.level(level).examType(examType).topic(dialogue.topic())
				.translationText(anyTranslation ? translationText.toString() : null)
				.build();
		dictationMapper.insertPracticeItem(item);

		byte[] mergedAudio = WavAudioMerger.merge(clips);
		String key = GENERATED_KEY.formatted(userId, item.getId());
		storageClient.write(key, new ByteArrayInputStream(mergedAudio), mergedAudio.length);
		dictationMapper.updatePracticeItemStorageKey(item.getId(), key);
	}

	// Picks one random Supertonic voice preset (VOICE_POOL) per distinct speaker in the dialogue,
	// without repeats until the ten-voice pool is exhausted (then wraps around); a single-speaker
	// monologue simply gets one random voice.
	private Map<String, String> assignVoicesToSpeakers(List<DictationDialogueLine> dialogue) {
		List<String> shuffledPool = new ArrayList<>(VOICE_POOL);
		Collections.shuffle(shuffledPool);
		Map<String, String> speakerVoices = new LinkedHashMap<>();
		for (DictationDialogueLine line : dialogue) {
			speakerVoices.computeIfAbsent(line.speaker(), speaker -> shuffledPool.get(speakerVoices.size() % shuffledPool.size()));
		}
		return speakerVoices;
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

	private DictationFolderDto toFolderDto(FolderCount folderCount) {
		return DictationFolderDto.builder()
				.folderId(folderCount.getFolder())
				.name(folderCount.getFolder())
				.lessonCount(folderCount.getCount())
				.build();
	}

	private DictationLessonSummaryDto toLessonSummaryDto(DictationClip clip) {
		return DictationLessonSummaryDto.builder()
				.clipId(clip.getId())
				.code(clip.getCode())
				.title(clip.getTitle())
				.audioUrl(CLIP_AUDIO_URL.formatted(clip.getId()))
				.build();
	}

	private DictationSentenceDto toSentenceDto(DictationClipSentence sentence) {
		return DictationSentenceDto.builder()
				.index(sentence.getSeq())
				.text(sentence.getText())
				.startMs(sentence.getStartMs())
				.endMs(sentence.getEndMs())
				.translation(sentence.getTranslation())
				.build();
	}

	private DictationPracticeItemDto toPracticeItemDto(DictationPracticeItem item) {
		return DictationPracticeItemDto.builder()
				.practiceItemId(item.getId())
				.audioUrl(item.getStorageKey() == null ? null : PRACTICE_AUDIO_URL.formatted(item.getId()))
				.level(item.getLevel())
				.examType(item.getExamType())
				.topic(item.getTopic())
				.build();
	}

	// Splits an AI-practice passage into per-sentence chunks for the runner: a multi-speaker dialogue
	// is already one line per turn (see synthesizeDialoguePracticeItem), so a newline split preserves
	// that structure; a single-speaker monologue has no newlines, so this falls back to splitting on
	// sentence-ending punctuation instead. translationText (if present) is split the exact same way and
	// zipped in by index - if its line count doesn't match (defensive; shouldn't happen since both are
	// built from the same loop at generation time), translations are left null rather than misaligned.
	private List<DictationSentenceDto> splitIntoSentences(String passageText, String translationText) {
		List<String> rawSentences = splitPassage(passageText);
		List<String> rawTranslations = translationText == null ? List.of() : splitPassage(translationText);
		boolean translationsAlign = rawTranslations.size() == rawSentences.size();

		List<DictationSentenceDto> sentences = new ArrayList<>();
		for (int i = 0; i < rawSentences.size(); i++) {
			sentences.add(DictationSentenceDto.builder()
					.index(i)
					.text(rawSentences.get(i))
					.startMs(null)
					.endMs(null)
					.translation(translationsAlign ? rawTranslations.get(i) : null)
					.build());
		}
		return sentences;
	}

	// Shared newline-or-punctuation splitting logic used for both the passage and its translation.
	private List<String> splitPassage(String text) {
		List<String> lines = Arrays.stream(text.split("\\r?\\n"))
				.map(String::trim)
				.filter(line -> !line.isEmpty())
				.toList();
		return lines.size() > 1
				? lines
				: Arrays.stream(text.split("(?<=[.!?])\\s+"))
						.map(String::trim)
						.filter(sentence -> !sentence.isEmpty())
						.toList();
	}

	// Opens the stored object and infers a content type from the key's extension (mp3 vs wav).
	private DictationAudioResource toAudioResource(String storageKey, String baseName) {
		boolean isMp3 = storageKey.toLowerCase().endsWith(".mp3");
		String contentType = isMp3 ? "audio/mpeg" : "audio/wav";
		String extension = isMp3 ? ".mp3" : ".wav";
		return new DictationAudioResource(
				storageClient.read(storageKey), storageClient.size(storageKey), contentType, baseName + extension);
	}

	// Serializes the AI suggestions to a JSON array string for the ai_suggestions column.
	private String serializeSuggestions(List<String> suggestions) {
		try {
			return objectMapper.writeValueAsString(suggestions);
		} catch (JsonProcessingException ex) {
			throw new IllegalStateException("Failed to serialize AI suggestions", ex);
		}
	}

	// Deserializes the ai_suggestions column back into a list; empty (not an error) for null/blank,
	// which covers attempts submitted before this column existed.
	private List<String> deserializeSuggestions(String aiSuggestionsJson) {
		if (aiSuggestionsJson == null || aiSuggestionsJson.isBlank()) {
			return List.of();
		}
		try {
			return objectMapper.readValue(aiSuggestionsJson, new TypeReference<List<String>>() { });
		} catch (JsonProcessingException ex) {
			log.warn("Failed to deserialize AI suggestions, returning empty list", ex);
			return List.of();
		}
	}

	private record ReferenceTarget(String referenceText, String recordingId) {
	}
}
