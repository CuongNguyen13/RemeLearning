package com.remelearning.english.speaking.library.service;

import com.remelearning.common.ai.pronunciation.PronunciationScore;
import com.remelearning.common.ai.pronunciation.PronunciationScoringClient;
import com.remelearning.common.exception.BusinessException;
import com.remelearning.common.storage.StorageClient;
import com.remelearning.english.speaking.library.domain.SpeakingLibraryAttempt;
import com.remelearning.english.speaking.library.domain.SpeakingLibrarySection;
import com.remelearning.english.speaking.library.domain.SpeakingLibrarySentence;
import com.remelearning.english.speaking.library.domain.SpeakingLibraryTopic;
import com.remelearning.english.speaking.library.domain.SpeakingTopicProgress;
import com.remelearning.english.speaking.library.domain.SpeakingTopicStatus;
import com.remelearning.english.speaking.library.dto.FinishSectionResponse;
import com.remelearning.english.speaking.library.dto.SentenceAttemptResultDto;
import com.remelearning.english.speaking.library.dto.SpeakingLibrarySectionDto;
import com.remelearning.english.speaking.library.dto.SpeakingLibraryTopicDto;
import com.remelearning.english.speaking.library.generator.LlmSpeakingLibraryGenerator;
import com.remelearning.english.speaking.library.mapper.SpeakingLibraryAttemptMapper;
import com.remelearning.english.speaking.library.mapper.SpeakingLibrarySectionMapper;
import com.remelearning.english.speaking.library.mapper.SpeakingLibrarySentenceMapper;
import com.remelearning.english.speaking.library.mapper.SpeakingLibraryTopicMapper;
import com.remelearning.english.speaking.library.mapper.SpeakingTopicProgressMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Fixed-topic speaking library: exposes topic progress (gating cloned from
 * {@code ListeningLibraryServiceImpl}'s LOCKED/UNLOCKED/IN_PROGRESS/PASSED state machine), starts/
 * resumes a Section (generating one via AI - a pool of sample sentences with IPA + sample audio -
 * when the topic has no section yet), scores one recorded sentence attempt at a time via the same
 * GOP scoring service {@code speaking.learn} uses, and only advances gating in the separate
 * {@link #finishSection} call once every sentence has passed.
 */
@Service
public class SpeakingLibraryServiceImpl implements SpeakingLibraryService {

	private static final double PASS_THRESHOLD = 0.7;
	private static final int FIRST_SEQUENCE_ORDER = 1;
	private static final String ATTEMPT_KEY = "speaking-library/attempts/%s/%s.wav";

	private final SpeakingLibraryTopicMapper topicMapper;
	private final SpeakingLibrarySectionMapper sectionMapper;
	private final SpeakingLibrarySentenceMapper sentenceMapper;
	private final SpeakingTopicProgressMapper progressMapper;
	private final SpeakingLibraryAttemptMapper attemptMapper;
	private final LlmSpeakingLibraryGenerator generator;
	private final PronunciationScoringClient pronunciationScoringClient;
	private final StorageClient storageClient;
	private final String ttsLang;

	public SpeakingLibraryServiceImpl(
			SpeakingLibraryTopicMapper topicMapper,
			SpeakingLibrarySectionMapper sectionMapper,
			SpeakingLibrarySentenceMapper sentenceMapper,
			SpeakingTopicProgressMapper progressMapper,
			SpeakingLibraryAttemptMapper attemptMapper,
			LlmSpeakingLibraryGenerator generator,
			PronunciationScoringClient pronunciationScoringClient,
			StorageClient storageClient,
			@Value("${speaking.tts.lang:en}") String ttsLang) {
		this.topicMapper = topicMapper;
		this.sectionMapper = sectionMapper;
		this.sentenceMapper = sentenceMapper;
		this.progressMapper = progressMapper;
		this.attemptMapper = attemptMapper;
		this.generator = generator;
		this.pronunciationScoringClient = pronunciationScoringClient;
		this.storageClient = storageClient;
		this.ttsLang = ttsLang;
	}

	// Bootstraps the very first topic to UNLOCKED for a new learner, then reports every catalog
	// topic with whatever progress row exists (LOCKED for any topic without one yet) - mirrors
	// ListeningLibraryServiceImpl.getTopics exactly.
	@Override
	@Transactional
	public List<SpeakingLibraryTopicDto> getTopics(String userId) {
		SpeakingLibraryTopic firstTopic = topicMapper.findBySequenceOrder(FIRST_SEQUENCE_ORDER);
		if (firstTopic != null) {
			progressMapper.bootstrapFirstTopic(userId, firstTopic.getId());
		}
		Map<Long, SpeakingTopicStatus> statusByTopicId = new HashMap<>();
		for (SpeakingTopicProgress progress : progressMapper.findByUserId(userId)) {
			statusByTopicId.put(progress.getTopicId(), progress.getStatus());
		}
		return topicMapper.findAll().stream()
				.map(t -> new SpeakingLibraryTopicDto(
						t.getId(), t.getName(), t.getLevel(),
						statusByTopicId.getOrDefault(t.getId(), SpeakingTopicStatus.LOCKED).name()))
				.toList();
	}

	// Starts a brand-new Section (generated via AI) the first time a topic is reached, or resumes
	// its most recent Section on any later call - then marks the topic IN_PROGRESS.
	@Override
	@Transactional
	public SpeakingLibrarySectionDto startOrResumeSection(String userId, Long topicId) {
		SpeakingLibraryTopic topic = requireTopic(topicId);
		requireUnlockedOrInProgress(userId, topicId);
		List<SpeakingLibrarySection> existing = sectionMapper.findByTopicId(topicId);
		SpeakingLibrarySection section = existing.isEmpty()
				? generator.generateSection(topic)
				: existing.get(existing.size() - 1);
		progressMapper.markInProgress(userId, topicId);

		List<SpeakingLibrarySentence> sentences = sentenceMapper.findBySectionId(section.getId());
		List<SpeakingLibrarySectionDto.SentenceView> sentenceViews = sentences.stream()
				.map(s -> new SpeakingLibrarySectionDto.SentenceView(
						s.getId(), s.getSentenceText(), s.getIpa(),
						// StorageClient.url() returns a directly-fetchable locator for the stored audio
						// object - null if no sample audio was generated for this sentence.
						s.getSampleAudioStorageKey() != null ? storageClient.url(s.getSampleAudioStorageKey()) : null))
				.toList();

		return new SpeakingLibrarySectionDto(section.getId(), sentenceViews);
	}

	// Mirrors ListeningLibraryServiceImpl.requireTopic: throws a 404-mapped BusinessException instead
	// of letting a null topic silently flow into the section generator.
	private SpeakingLibraryTopic requireTopic(Long topicId) {
		SpeakingLibraryTopic topic = topicMapper.findById(topicId);
		if (topic == null) {
			throw BusinessException.notFound("Speaking library topic not found: id=" + topicId);
		}
		return topic;
	}

	// Mirrors ListeningLibraryServiceImpl.requireUnlockedOrInProgress: only LOCKED is rejected (a
	// missing row counts as LOCKED); UNLOCKED/IN_PROGRESS/PASSED all pass through.
	private void requireUnlockedOrInProgress(String userId, Long topicId) {
		SpeakingTopicProgress progress = progressMapper.findByUserIdAndTopicId(userId, topicId);
		SpeakingTopicStatus status = progress == null ? SpeakingTopicStatus.LOCKED : progress.getStatus();
		if (status == SpeakingTopicStatus.LOCKED) {
			throw BusinessException.forbidden("Speaking topic is locked for this learner: topicId=" + topicId);
		}
	}

	// Scores one recorded sentence via the same GOP scoring service speaking.learn uses (uploads the
	// audio to storage first, then re-reads it for scoring - same pattern as SpeakingLearnServiceImpl
	// - and persists one attempt row). Does not touch topic progress; that only happens in
	// finishSection once every sentence has a passing attempt.
	@Override
	@Transactional
	public SentenceAttemptResultDto submitSentenceAttempt(String userId, Long sectionId, Long sentenceId, MultipartFile recordedAudio) {
		SpeakingLibrarySection section = sectionMapper.findById(sectionId);
		if (section == null) {
			throw BusinessException.notFound("Speaking library section not found: id=" + sectionId);
		}
		SpeakingLibrarySentence sentence = sentenceMapper.findById(sentenceId);
		if (sentence == null) {
			throw BusinessException.notFound("Speaking library sentence not found: id=" + sentenceId);
		}

		String attemptKey = ATTEMPT_KEY.formatted(userId, UUID.randomUUID());
		try (InputStream learnerAudio = recordedAudio.getInputStream()) {
			storageClient.write(attemptKey, learnerAudio, recordedAudio.getSize());
		} catch (IOException ex) {
			throw new IllegalStateException("Failed to read uploaded speaking library attempt audio", ex);
		}

		PronunciationScore score;
		try (InputStream scoringAudio = storageClient.read(attemptKey)) {
			score = pronunciationScoringClient.score(
					scoringAudio, recordedAudio.getOriginalFilename() == null ? "attempt.wav" : recordedAudio.getOriginalFilename(),
					sentence.getSentenceText(), ttsLang);
		} catch (IOException ex) {
			throw new IllegalStateException("Failed to read stored speaking library attempt audio for scoring", ex);
		}

		double wordScore = averageWordScore(score);
		double phonemeScore = averagePhonemeScore(score);
		boolean passed = phonemeScore >= PASS_THRESHOLD && wordScore >= PASS_THRESHOLD;

		SpeakingLibraryAttempt attempt = SpeakingLibraryAttempt.builder()
				.userId(userId)
				.sectionId(sectionId)
				.sentenceId(sentenceId)
				.phonemeScore(phonemeScore)
				.wordScore(wordScore)
				.recordedAudioStorageKey(attemptKey)
				.build();
		attemptMapper.insert(attempt);

		return new SentenceAttemptResultDto(sentenceId, phonemeScore, wordScore, passed, score.transcript());
	}

	// Reduces PronunciationScoringClient's per-word/per-phoneme breakdown down to the single
	// word-level and phoneme-level scores this library's per-sentence gating threshold checks
	// against - falls back to the overall score if the breakdown is empty.
	private double averageWordScore(PronunciationScore score) {
		if (score.words() == null || score.words().isEmpty()) {
			return score.overall();
		}
		return score.words().stream().mapToDouble(w -> w.score()).average().orElse(score.overall());
	}

	private double averagePhonemeScore(PronunciationScore score) {
		if (score.words() == null || score.words().isEmpty()) {
			return score.overall();
		}
		List<Double> phonemeScores = score.words().stream()
				.flatMap(w -> w.phonemes() == null ? List.<com.remelearning.common.ai.pronunciation.PhonemePronunciationScore>of().stream() : w.phonemes().stream())
				.map(p -> p.score())
				.toList();
		return phonemeScores.isEmpty() ? score.overall() : phonemeScores.stream().mapToDouble(Double::doubleValue).average().orElse(score.overall());
	}

	// Checks every sentence in the section for at least one attempt scoring above PASS_THRESHOLD on
	// both phoneme and word score; on full pass, marks the topic PASSED and unlocks the next topic by
	// sequence order - same markPassed/unlockIfLocked sequence as
	// ListeningLibraryServiceImpl.submitAnswers's pass branch.
	@Override
	@Transactional
	public FinishSectionResponse finishSection(String userId, Long sectionId) {
		SpeakingLibrarySection section = sectionMapper.findById(sectionId);
		if (section == null) {
			throw BusinessException.notFound("Speaking library section not found: id=" + sectionId);
		}
		List<SpeakingLibrarySentence> sentences = sentenceMapper.findBySectionId(sectionId);
		List<SpeakingLibraryAttempt> attempts = attemptMapper.findBySectionId(sectionId);

		Set<Long> passedSentenceIds = attempts.stream()
				.filter(a -> a.getPhonemeScore() != null && a.getPhonemeScore() >= PASS_THRESHOLD
						&& a.getWordScore() != null && a.getWordScore() >= PASS_THRESHOLD)
				.map(SpeakingLibraryAttempt::getSentenceId)
				.collect(Collectors.toSet());

		int total = sentences.size();
		long passedCount = sentences.stream().filter(s -> passedSentenceIds.contains(s.getId())).count();
		boolean passed = total > 0 && passedCount == total;

		Long nextTopicId = null;
		boolean nextTopicUnlocked = false;
		if (passed) {
			SpeakingLibraryTopic topic = topicMapper.findById(section.getTopicId());
			progressMapper.markPassed(userId, topic.getId());
			SpeakingLibraryTopic nextTopic = topicMapper.findBySequenceOrder(topic.getSequenceOrder() + 1);
			if (nextTopic != null) {
				progressMapper.unlockIfLocked(userId, nextTopic.getId());
				SpeakingTopicProgress nextProgress = progressMapper.findByUserIdAndTopicId(userId, nextTopic.getId());
				nextTopicUnlocked = nextProgress != null && nextProgress.getStatus() != SpeakingTopicStatus.LOCKED;
				nextTopicId = nextTopic.getId();
			}
		}

		return new FinishSectionResponse(total, (int) passedCount, passed, nextTopicId, nextTopicUnlocked);
	}

	@Override
	public List<SpeakingLibraryAttempt> getHistory(String userId) {
		return attemptMapper.findByUserId(userId);
	}
}
