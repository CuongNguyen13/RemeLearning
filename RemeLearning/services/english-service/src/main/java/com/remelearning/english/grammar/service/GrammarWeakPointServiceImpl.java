package com.remelearning.english.grammar.service;

import com.remelearning.english.grammar.classifier.GrammarClassifier;
import com.remelearning.english.grammar.domain.GrammarType;
import com.remelearning.english.grammar.domain.GrammarWeakPoint;
import com.remelearning.common.event.LearningGapAnalyzedEvent;
import com.remelearning.common.event.WeakPointPayload;
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
					.build());
		}
	}

	@Override
	public List<GrammarWeakPoint> getWeakPoints(String userId, GrammarType type) {
		return mapper.findByUserId(userId, type == null ? null : type.name());
	}
}
