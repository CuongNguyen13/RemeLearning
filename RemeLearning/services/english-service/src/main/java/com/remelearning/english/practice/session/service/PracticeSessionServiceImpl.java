package com.remelearning.english.practice.session.service;

import com.remelearning.common.exception.BusinessException;
import com.remelearning.english.grammar.learn.dto.GenerateGrammarPracticeRequest;
import com.remelearning.english.grammar.learn.dto.GrammarPracticeItemDto;
import com.remelearning.english.grammar.learn.service.GrammarLearnService;
import com.remelearning.english.grammar.service.GrammarWeakPointService;
import com.remelearning.english.listening.dto.GenerateListeningPracticeRequest;
import com.remelearning.english.listening.dto.ListeningPracticeItemDto;
import com.remelearning.english.listening.service.ListeningLearnService;
import com.remelearning.english.listening.weakpoint.domain.ListeningWeakPoint;
import com.remelearning.english.listening.weakpoint.service.ListeningWeakPointService;
import com.remelearning.english.practice.session.domain.PracticeExerciseStatus;
import com.remelearning.english.practice.session.domain.PracticeSession;
import com.remelearning.english.practice.session.domain.PracticeSessionExercise;
import com.remelearning.english.practice.session.domain.PracticeSessionStatus;
import com.remelearning.english.practice.session.dto.PracticeSessionDto;
import com.remelearning.english.practice.session.dto.PracticeSessionExerciseDto;
import com.remelearning.english.practice.session.mapper.PracticeSessionMapper;
import com.remelearning.english.pronunciation.service.PronunciationWeakPointService;
import com.remelearning.english.speaking.dto.GenerateSpeakingPracticeRequest;
import com.remelearning.english.speaking.dto.SpeakingPracticeItemDto;
import com.remelearning.english.speaking.service.SpeakingLearnService;
import com.remelearning.english.vocabulary.learn.dto.GenerateVocabPracticeRequest;
import com.remelearning.english.vocabulary.learn.dto.VocabPracticeItemDto;
import com.remelearning.english.vocabulary.learn.service.VocabLearnService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Default orchestrator. It ranks the four skill categories by the learner's highest weak-point score,
 * assigns session slots round-robin (highest categories first, cycling when fewer categories have weak
 * points than slots, and spreading across all four on cold-start), then delegates each slot to the
 * owning domain's existing {@code generate}. Nothing here re-implements exercise generation or scoring.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PracticeSessionServiceImpl implements PracticeSessionService {

	static final String CATEGORY_VOCABULARY = "vocabulary";
	static final String CATEGORY_GRAMMAR = "grammar";
	static final String CATEGORY_LISTENING = "listening";
	static final String CATEGORY_SPEAKING = "speaking";
	static final String CATEGORY_WRITING = "writing";

	private static final int DEFAULT_EXERCISE_COUNT = 4;
	private static final int MIN_EXERCISE_COUNT = 1;
	private static final int MAX_EXERCISE_COUNT = 8;
	/** How many top weak-point labels to hand a domain generator as focus items. */
	private static final int FOCUS_LIMIT = 3;

	/** Cold-start rotation when the learner has no weak points anywhere yet - one exercise per skill. */
	private static final List<String> DEFAULT_SPREAD =
			List.of(CATEGORY_VOCABULARY, CATEGORY_GRAMMAR, CATEGORY_LISTENING, CATEGORY_SPEAKING,
					CATEGORY_WRITING);

	private final PracticeSessionMapper mapper;

	private final VocabLearnService vocabLearnService;
	private final GrammarLearnService grammarLearnService;
	private final ListeningLearnService listeningLearnService;
	private final SpeakingLearnService speakingLearnService;
	private final com.remelearning.english.writing.service.WritingLearnService writingLearnService;

	private final com.remelearning.english.vocabulary.service.VocabularyWeakPointService vocabularyWeakPointService;
	private final GrammarWeakPointService grammarWeakPointService;
	private final PronunciationWeakPointService pronunciationWeakPointService;
	private final ListeningWeakPointService listeningWeakPointService;

	// Builds a session: rank categories by top weak-point score, assign slots, generate one AI exercise
	// per slot via the owning domain, then persist the session header and its slots. Sequential on
	// purpose - each domain generate is @Transactional and listening/speaking synthesize TTS audio.
	@Override
	@Transactional
	public PracticeSessionDto startSession(String userId, Integer exerciseCount, String examType) {
		int slots = clampCount(exerciseCount);
		// Normalized once so every slot in the session is generated for the same, canonical exam style.
		String normalizedExamType = com.remelearning.common.constants.ExamTypes.normalize(examType);

		// Rank the categories that actually have weak points (highest score first); fall back to an
		// even spread across all four skills when the learner has no weak points yet (cold start).
		List<CategoryFocus> focuses = collectFocuses(userId);
		List<String> rotation = focuses.stream()
				.sorted(Comparator.comparingDouble(CategoryFocus::topScore).reversed())
				.map(CategoryFocus::category)
				.toList();
		if (rotation.isEmpty()) {
			rotation = DEFAULT_SPREAD;
		}

		// Persist the session header first so slots can reference its generated id.
		PracticeSession session = PracticeSession.builder()
				.userId(userId)
				.status(PracticeSessionStatus.IN_PROGRESS)
				.totalExercises(slots)
				.build();
		mapper.insertSession(session);

		// One slot per exercise, cycling the ranked rotation so top categories are hit first / most.
		List<PracticeSessionExercise> exercises = new ArrayList<>();
		for (int order = 1; order <= slots; order++) {
			String category = rotation.get((order - 1) % rotation.size());
			List<String> focusItems = focusItemsFor(category, focuses);
			GeneratedSlot generated = generateSlot(userId, category, focusItems, normalizedExamType);

			PracticeSessionExercise exercise = PracticeSessionExercise.builder()
					.sessionId(session.getId())
					.exerciseOrder(order)
					.category(category)
					.practiceItemId(generated.practiceItemId())
					.topic(generated.topic())
					.status(PracticeExerciseStatus.PENDING)
					.build();
			mapper.insertExercise(exercise);
			exercises.add(exercise);
		}

		return toDto(session, exercises);
	}

	@Override
	public PracticeSessionDto getSession(Long sessionId) {
		PracticeSession session = requireSession(sessionId);
		return toDto(session, mapper.findExercisesBySessionId(sessionId));
	}

	// Resume support: return the learner's newest still-open session, or null so the client shows the
	// plain start screen instead.
	@Override
	public PracticeSessionDto getLatestInProgress(String userId) {
		PracticeSession session = mapper.findLatestInProgressByUserId(userId);
		if (session == null) {
			return null;
		}
		return toDto(session, mapper.findExercisesBySessionId(session.getId()));
	}

	// Marks one slot done with its score; when no slot is left pending the session is completed.
	@Override
	@Transactional
	public PracticeSessionDto completeExercise(Long sessionId, int exerciseOrder, Double score) {
		PracticeSession session = requireSession(sessionId);
		mapper.markExerciseDone(sessionId, exerciseOrder, score);

		if (mapper.countPendingBySessionId(sessionId) == 0) {
			mapper.completeSession(sessionId);
		}

		PracticeSession refreshed = mapper.findSessionById(sessionId);
		return toDto(refreshed, mapper.findExercisesBySessionId(sessionId));
	}

	// --- helpers -------------------------------------------------------------------------------

	// Clamp the requested exercise count into a sane range, defaulting to 4 when unspecified.
	private int clampCount(Integer requested) {
		int count = requested == null ? DEFAULT_EXERCISE_COUNT : requested;
		return Math.max(MIN_EXERCISE_COUNT, Math.min(MAX_EXERCISE_COUNT, count));
	}

	// Reads each category's top weak points into a ranking record. Speaking is driven by pronunciation
	// weak points; listening is ranked by its own weak points but always generated with empty focus
	// (the listening generator self-falls-back to recently-missed keywords). Categories with no weak
	// points are omitted so they don't dilute the ranking (cold start handles the all-empty case).
	private List<CategoryFocus> collectFocuses(String userId) {
		List<CategoryFocus> focuses = new ArrayList<>();

		List<com.remelearning.english.vocabulary.domain.VocabularyWeakPoint> vocab =
				vocabularyWeakPointService.getTopWeakPoints(userId, FOCUS_LIMIT);
		addIfPresent(focuses, CATEGORY_VOCABULARY, vocab,
				com.remelearning.english.vocabulary.domain.VocabularyWeakPoint::getForgettingScore,
				com.remelearning.english.vocabulary.domain.VocabularyWeakPoint::getLabel);

		List<com.remelearning.english.grammar.domain.GrammarWeakPoint> grammar =
				grammarWeakPointService.getTopWeakPoints(userId, FOCUS_LIMIT);
		addIfPresent(focuses, CATEGORY_GRAMMAR, grammar,
				com.remelearning.english.grammar.domain.GrammarWeakPoint::getForgettingScore,
				com.remelearning.english.grammar.domain.GrammarWeakPoint::getLabel);

		List<com.remelearning.english.pronunciation.domain.PronunciationWeakPoint> pronunciation =
				pronunciationWeakPointService.getTopWeakPoints(userId, FOCUS_LIMIT);
		addIfPresent(focuses, CATEGORY_SPEAKING, pronunciation,
				com.remelearning.english.pronunciation.domain.PronunciationWeakPoint::getForgettingScore,
				com.remelearning.english.pronunciation.domain.PronunciationWeakPoint::getLabel);

		// Listening weak points come back already ordered by forgetting_score desc from the mapper.
		List<ListeningWeakPoint> listening = listeningWeakPointService.getWeakPoints(userId, null).stream()
				.limit(FOCUS_LIMIT).toList();
		addIfPresent(focuses, CATEGORY_LISTENING, listening,
				ListeningWeakPoint::getForgettingScore, ListeningWeakPoint::getLabel);

		// Writing has no weak-point table of its own by design (its mistakes are stored as grammar/
		// vocabulary ones - see the writing package), so it can't be ranked off one. It borrows both
		// lists: ranked by whichever of the two is more urgent, and given both sets of labels as focus,
		// since a writing task is the one exercise that drills grammar and vocabulary simultaneously.
		if (!grammar.isEmpty() || !vocab.isEmpty()) {
			double writingScore = Math.max(
					grammar.isEmpty() ? 0.0 : grammar.get(0).getForgettingScore(),
					vocab.isEmpty() ? 0.0 : vocab.get(0).getForgettingScore());
			List<String> writingLabels = new ArrayList<>();
			grammar.forEach(weakPoint -> writingLabels.add(weakPoint.getLabel()));
			vocab.forEach(weakPoint -> writingLabels.add(weakPoint.getLabel()));
			focuses.add(new CategoryFocus(CATEGORY_WRITING, writingScore, writingLabels));
		}

		return focuses;
	}

	// Only categories with at least one weak point participate in the ranked rotation. The first element
	// (highest forgetting_score) supplies the ranking score; every element's label becomes a focus item.
	private <T> void addIfPresent(
			List<CategoryFocus> focuses,
			String category,
			List<T> weakPoints,
			java.util.function.ToDoubleFunction<T> scoreFn,
			java.util.function.Function<T, String> labelFn) {
		if (weakPoints.isEmpty()) {
			return;
		}
		double topScore = scoreFn.applyAsDouble(weakPoints.get(0));
		List<String> labels = weakPoints.stream().map(labelFn).toList();
		focuses.add(new CategoryFocus(category, topScore, labels));
	}

	// Focus items handed to the domain generator: the category's top weak-point labels, except
	// listening which always self-falls-back (empty focus), per the agreed design.
	private List<String> focusItemsFor(String category, List<CategoryFocus> focuses) {
		if (CATEGORY_LISTENING.equals(category)) {
			return List.of();
		}
		return focuses.stream()
				.filter(f -> f.category().equals(category))
				.findFirst()
				.map(CategoryFocus::labels)
				.orElse(List.of());
	}

	// Dispatches to the owning domain's existing generate() and normalizes the result to (itemId, topic).
	private GeneratedSlot generateSlot(
			String userId, String category, List<String> focusItems, String examType) {
		switch (category) {
			case CATEGORY_VOCABULARY -> {
				GenerateVocabPracticeRequest request = new GenerateVocabPracticeRequest();
				request.setFocusItems(focusItems);
				request.setExamType(examType);
				VocabPracticeItemDto item = vocabLearnService.generate(userId, request);
				return new GeneratedSlot(item.getPracticeItemId(), item.getTopic());
			}
			case CATEGORY_GRAMMAR -> {
				GenerateGrammarPracticeRequest request = new GenerateGrammarPracticeRequest();
				request.setFocusItems(focusItems);
				request.setExamType(examType);
				GrammarPracticeItemDto item = grammarLearnService.generate(userId, request);
				return new GeneratedSlot(item.getPracticeItemId(), item.getTopic());
			}
			case CATEGORY_LISTENING -> {
				GenerateListeningPracticeRequest request = new GenerateListeningPracticeRequest();
				request.setFocusItems(focusItems);
				request.setExamType(examType);
				ListeningPracticeItemDto item = listeningLearnService.generate(userId, request);
				return new GeneratedSlot(item.getPracticeItemId(), item.getTopic());
			}
			case CATEGORY_SPEAKING -> {
				GenerateSpeakingPracticeRequest request = new GenerateSpeakingPracticeRequest();
				request.setFocusItems(focusItems);
				request.setExamType(examType);
				SpeakingPracticeItemDto item = speakingLearnService.generate(userId, request);
				return new GeneratedSlot(item.getPracticeItemId(), item.getTopic());
			}
			case CATEGORY_WRITING -> {
				com.remelearning.english.writing.dto.GenerateWritingPracticeRequest request =
						new com.remelearning.english.writing.dto.GenerateWritingPracticeRequest();
				// Rotates the three writing modes so a multi-exercise session doesn't serve the same mode
				// twice; taskType is required by the writing generator, unlike the other domains' facets.
				request.setTaskType(randomWritingTaskType());
				request.setFocusItems(focusItems);
				request.setExamType(examType);
				com.remelearning.english.writing.dto.WritingPracticeItemDto item =
						writingLearnService.generate(userId, request);
				return new GeneratedSlot(item.getPracticeItemId(), item.getTopic());
			}
			default -> throw new IllegalStateException("Unknown practice category: " + category);
		}
	}

	// One of the three writing modes at random (write from a brief / translate either direction), so a
	// session's writing slot isn't always the same kind of task.
	private com.remelearning.english.writing.domain.WritingTaskType randomWritingTaskType() {
		var types = com.remelearning.english.writing.domain.WritingTaskType.values();
		return types[java.util.concurrent.ThreadLocalRandom.current().nextInt(types.length)];
	}

	// Loads a session or fails with not-found.
	private PracticeSession requireSession(Long sessionId) {
		PracticeSession session = mapper.findSessionById(sessionId);
		if (session == null) {
			throw BusinessException.notFound("Practice session not found: id=" + sessionId);
		}
		return session;
	}

	// Maps the persisted session + slots into the client-facing DTO.
	private PracticeSessionDto toDto(PracticeSession session, List<PracticeSessionExercise> exercises) {
		List<PracticeSessionExerciseDto> exerciseDtos = exercises.stream()
				.map(e -> PracticeSessionExerciseDto.builder()
						.order(e.getExerciseOrder())
						.category(e.getCategory())
						.practiceItemId(e.getPracticeItemId())
						.topic(e.getTopic())
						.status(e.getStatus() == null ? null : e.getStatus().name())
						.score(e.getScore())
						.build())
				.toList();

		return PracticeSessionDto.builder()
				.sessionId(session.getId())
				.status(session.getStatus() == null ? null : session.getStatus().name())
				.totalExercises(session.getTotalExercises())
				.exercises(exerciseDtos)
				.createdAt(session.getCreatedAt())
				.completedAt(session.getCompletedAt())
				.build();
	}

	/** A category's ranking score plus the top weak-point labels used as generator focus items. */
	private record CategoryFocus(String category, double topScore, List<String> labels) {
	}

	/** The persisted-item reference a domain generate() produced for one slot. */
	private record GeneratedSlot(Long practiceItemId, String topic) {
	}
}
