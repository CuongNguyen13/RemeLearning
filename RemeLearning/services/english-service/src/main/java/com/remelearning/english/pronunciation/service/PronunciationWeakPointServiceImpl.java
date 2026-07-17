package com.remelearning.english.pronunciation.service;

import com.remelearning.english.pronunciation.classifier.PronunciationClassifier;
import com.remelearning.english.pronunciation.domain.PronunciationType;
import com.remelearning.english.pronunciation.domain.PronunciationWeakPoint;
import com.remelearning.common.event.LearningGapAnalyzedEvent;
import com.remelearning.common.event.WeakPointPayload;
import com.remelearning.common.scoring.ScoreSource;
import com.remelearning.english.practice.scoring.WeakPointScoreUpdate;
import com.remelearning.english.pronunciation.mapper.PronunciationWeakPointMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PronunciationWeakPointServiceImpl implements PronunciationWeakPointService {

	private static final String PRONUNCIATION_CATEGORY = "pronunciation";

	private final PronunciationWeakPointMapper mapper;
	private final PronunciationClassifier classifier;

	// learning.gap.analyzed carries all categories (grammar/vocabulary/pronunciation); only
	// "pronunciation" is ours, so every other weak point is skipped. Each surviving item is
	// classified (which sound/aspect it targets) then upserted, keyed on (userId, itemId), so
	// re-analysis updates the score in place.
	@Override
	@Transactional
	public void saveWeakPoints(LearningGapAnalyzedEvent event) {
		for (WeakPointPayload weakPoint : event.getWeakPoints()) {
			if (!PRONUNCIATION_CATEGORY.equalsIgnoreCase(weakPoint.getCategory())) {
				continue;
			}

			PronunciationType type = classifier.classify(weakPoint.getLabel());
			mapper.upsert(PronunciationWeakPoint.builder()
					.recordingId(event.getRecordingId())
					.userId(event.getUserId())
					.itemId(weakPoint.getItemId())
					.label(weakPoint.getLabel())
					.pronunciationType(type)
					.forgettingScore(weakPoint.getForgettingScore())
					.recommendation(weakPoint.getRecommendation())
					.scoreSource(ScoreSource.PYTHON_LEGACY)
					.build());
		}
	}

	@Override
	public List<PronunciationWeakPoint> getWeakPoints(String userId, PronunciationType type) {
		return mapper.findByUserId(userId, type == null ? null : type.name());
	}

	// Same upsert path as saveWeakPoints, but sourced from the practice/redo flow's Java scoring
	// engine instead of ai-service's Kafka event - marked JAVA_ENGINE so the guarded upsert (see
	// this mapper's XML) keeps a stale PYTHON_LEGACY recompute from clobbering it afterwards.
	@Override
	@Transactional
	public void applyJavaComputedScore(WeakPointScoreUpdate update) {
		if (!PRONUNCIATION_CATEGORY.equalsIgnoreCase(update.getCategory())) {
			return;
		}

		PronunciationType type = classifier.classify(update.getLabel());
		mapper.upsert(PronunciationWeakPoint.builder()
				.recordingId(update.getRecordingId())
				.userId(update.getUserId())
				.itemId(update.getItemId())
				.label(update.getLabel())
				.pronunciationType(type)
				.forgettingScore(update.getWeakScore())
				.recommendation("Luyện phát âm: " + update.getLabel() + ". Nghe mẫu chuẩn, ghi âm lại bản thân và so sánh.")
				.masteryLevel(update.getMasteryLevel())
				.nextReviewAt(update.getNextReviewAt())
				.scoreSource(ScoreSource.JAVA_ENGINE)
				.build());
	}
}
