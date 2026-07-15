package com.remelearning.english.pronunciation.service;

import com.remelearning.english.pronunciation.classifier.PronunciationClassifier;
import com.remelearning.english.pronunciation.domain.PronunciationType;
import com.remelearning.english.pronunciation.domain.PronunciationWeakPoint;
import com.remelearning.common.event.LearningGapAnalyzedEvent;
import com.remelearning.common.event.WeakPointPayload;
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
					.build());
		}
	}

	@Override
	public List<PronunciationWeakPoint> getWeakPoints(String userId, PronunciationType type) {
		return mapper.findByUserId(userId, type == null ? null : type.name());
	}
}
