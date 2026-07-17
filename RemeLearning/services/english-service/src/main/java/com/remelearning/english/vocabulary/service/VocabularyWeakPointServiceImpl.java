package com.remelearning.english.vocabulary.service;

import com.remelearning.english.vocabulary.classifier.VocabularyClassifier;
import com.remelearning.english.vocabulary.domain.VocabularyType;
import com.remelearning.english.vocabulary.domain.VocabularyWeakPoint;
import com.remelearning.common.event.LearningGapAnalyzedEvent;
import com.remelearning.common.event.WeakPointPayload;
import com.remelearning.common.scoring.ScoreSource;
import com.remelearning.english.practice.scoring.WeakPointScoreUpdate;
import com.remelearning.english.vocabulary.mapper.VocabularyWeakPointMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class VocabularyWeakPointServiceImpl implements VocabularyWeakPointService {

	private static final String VOCABULARY_CATEGORY = "vocabulary";

	private final VocabularyWeakPointMapper mapper;
	private final VocabularyClassifier classifier;

	// learning.gap.analyzed carries all categories (grammar/vocabulary/pronunciation); only "vocabulary"
	// is ours, so every other weak point is skipped. Each surviving item is classified (word type /
	// phrase type) then upserted, keyed on (userId, itemId), so re-analysis updates the score in place.
	@Override
	@Transactional
	public void saveWeakPoints(LearningGapAnalyzedEvent event) {
		for (WeakPointPayload weakPoint : event.getWeakPoints()) {
			if (!VOCABULARY_CATEGORY.equalsIgnoreCase(weakPoint.getCategory())) {
				continue;
			}

			VocabularyType type = classifier.classify(weakPoint.getLabel());
			mapper.upsert(VocabularyWeakPoint.builder()
					.recordingId(event.getRecordingId())
					.userId(event.getUserId())
					.itemId(weakPoint.getItemId())
					.label(weakPoint.getLabel())
					.vocabularyType(type)
					.forgettingScore(weakPoint.getForgettingScore())
					.recommendation(weakPoint.getRecommendation())
					.scoreSource(ScoreSource.PYTHON_LEGACY)
					.build());
		}
	}

	@Override
	public List<VocabularyWeakPoint> getWeakPoints(String userId, VocabularyType type) {
		return mapper.findByUserId(userId, type == null ? null : type.name());
	}

	@Override
	public List<VocabularyWeakPoint> getTopWeakPoints(String userId, int limit) {
		return mapper.findTopByUserId(userId, limit);
	}

	// Same upsert path as saveWeakPoints, but sourced from the practice/redo flow's Java scoring
	// engine instead of ai-service's Kafka event - marked JAVA_ENGINE so the guarded upsert (see
	// this mapper's XML) keeps a stale PYTHON_LEGACY recompute from clobbering it afterwards.
	@Override
	@Transactional
	public void applyJavaComputedScore(WeakPointScoreUpdate update) {
		if (!VOCABULARY_CATEGORY.equalsIgnoreCase(update.getCategory())) {
			return;
		}

		VocabularyType type = classifier.classify(update.getLabel());
		mapper.upsert(VocabularyWeakPoint.builder()
				.recordingId(update.getRecordingId())
				.userId(update.getUserId())
				.itemId(update.getItemId())
				.label(update.getLabel())
				.vocabularyType(type)
				.forgettingScore(update.getWeakScore())
				.recommendation("Ôn lại từ vựng: " + update.getLabel() + ". Đặt câu mới với từ này và ôn lại theo lịch spaced-repetition.")
				.masteryLevel(update.getMasteryLevel())
				.nextReviewAt(update.getNextReviewAt())
				.scoreSource(ScoreSource.JAVA_ENGINE)
				.build());
	}
}
