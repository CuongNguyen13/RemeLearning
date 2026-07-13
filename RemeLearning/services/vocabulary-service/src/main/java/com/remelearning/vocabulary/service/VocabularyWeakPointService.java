package com.remelearning.vocabulary.service;

import com.remelearning.vocabulary.classifier.VocabularyClassifier;
import com.remelearning.vocabulary.domain.VocabularyType;
import com.remelearning.vocabulary.domain.VocabularyWeakPoint;
import com.remelearning.vocabulary.event.LearningGapAnalyzedEvent;
import com.remelearning.vocabulary.event.WeakPointPayload;
import com.remelearning.vocabulary.mapper.VocabularyWeakPointMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class VocabularyWeakPointService {

	private static final String VOCABULARY_CATEGORY = "vocabulary";

	private final VocabularyWeakPointMapper mapper;
	private final VocabularyClassifier classifier;

	/** learning.gap.analyzed carries all categories (grammar/vocabulary/pronunciation); only "vocabulary" is ours. */
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
					.build());
		}
	}

	public List<VocabularyWeakPoint> getWeakPoints(String userId, VocabularyType type) {
		return mapper.findByUserId(userId, type == null ? null : type.name());
	}
}
