package com.remelearning.english.grammar.service;

import com.remelearning.english.grammar.classifier.GrammarClassifier;
import com.remelearning.english.grammar.domain.GrammarType;
import com.remelearning.english.grammar.domain.GrammarWeakPoint;
import com.remelearning.common.event.LearningGapAnalyzedEvent;
import com.remelearning.common.event.WeakPointPayload;
import com.remelearning.common.scoring.ScoreSource;
import com.remelearning.english.practice.scoring.WeakPointScoreUpdate;
import com.remelearning.english.grammar.mapper.GrammarWeakPointMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class GrammarWeakPointServiceImpl implements GrammarWeakPointService {

	private static final String GRAMMAR_CATEGORY = "grammar";

	private final GrammarWeakPointMapper mapper;
	private final GrammarClassifier classifier;

	// learning.gap.analyzed carries all categories (grammar/vocabulary/pronunciation); only "grammar"
	// is ours, so every other weak point is skipped. Each surviving item is classified (which rule
	// it violates) then upserted, keyed on (userId, itemId), so re-analysis updates the score in place.
	@Override
	@Transactional
	public void saveWeakPoints(LearningGapAnalyzedEvent event) {
		for (WeakPointPayload weakPoint : event.getWeakPoints()) {
			if (!GRAMMAR_CATEGORY.equalsIgnoreCase(weakPoint.getCategory())) {
				continue;
			}

			GrammarType type = classifier.classify(weakPoint.getLabel());
			mapper.upsert(GrammarWeakPoint.builder()
					.recordingId(event.getRecordingId())
					.userId(event.getUserId())
					.itemId(weakPoint.getItemId())
					.label(weakPoint.getLabel())
					.grammarType(type)
					.forgettingScore(weakPoint.getForgettingScore())
					.recommendation(weakPoint.getRecommendation())
					.scoreSource(ScoreSource.PYTHON_LEGACY)
					.build());
		}
	}

	@Override
	public List<GrammarWeakPoint> getWeakPoints(String userId, GrammarType type) {
		return mapper.findByUserId(userId, type == null ? null : type.name());
	}

	@Override
	public List<GrammarWeakPoint> getTopWeakPoints(String userId, int limit) {
		return mapper.findTopByUserId(userId, limit);
	}

	// Same upsert path as saveWeakPoints, but sourced from the practice/redo flow's Java scoring
	// engine instead of ai-service's Kafka event - marked JAVA_ENGINE so the guarded upsert (see
	// this mapper's XML) keeps a stale PYTHON_LEGACY recompute from clobbering it afterwards.
	@Override
	@Transactional
	public void applyJavaComputedScore(WeakPointScoreUpdate update) {
		if (!GRAMMAR_CATEGORY.equalsIgnoreCase(update.getCategory())) {
			return;
		}

		GrammarType type = classifier.classify(update.getLabel());
		mapper.upsert(GrammarWeakPoint.builder()
				.recordingId(update.getRecordingId())
				.userId(update.getUserId())
				.itemId(update.getItemId())
				.label(update.getLabel())
				.grammarType(type)
				.forgettingScore(update.getWeakScore())
				.recommendation("Ôn lại quy tắc ngữ pháp: " + update.getLabel() + ". Làm 5-10 bài tập áp dụng và thử dùng nó trong câu nói của bạn.")
				.masteryLevel(update.getMasteryLevel())
				.nextReviewAt(update.getNextReviewAt())
				.scoreSource(ScoreSource.JAVA_ENGINE)
				.build());
	}
}
