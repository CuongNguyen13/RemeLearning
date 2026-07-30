package com.remelearning.english.listening.weakpoint.service;

import com.remelearning.common.event.LearningGapAnalyzedEvent;
import com.remelearning.common.event.WeakPointPayload;
import com.remelearning.common.scoring.ScoreSource;
import com.remelearning.english.listening.weakpoint.domain.ListeningSourceType;
import com.remelearning.english.listening.weakpoint.domain.ListeningWeakPoint;
import com.remelearning.english.listening.weakpoint.mapper.ListeningWeakPointMapper;
import com.remelearning.english.practice.scoring.WeakPointScoreUpdate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ListeningWeakPointServiceImpl implements ListeningWeakPointService {

	private static final String LISTENING_CATEGORY = "listening";

	private final ListeningWeakPointMapper mapper;

	// learning.gap.analyzed carries all categories; only "listening" is ours. Today the only
	// producer of a "listening"-category weak point via this Kafka path is DictationServiceImpl's
	// dual-write (every dictation miss also becomes a listening weak point, in addition to its own
	// root-cause category) - there is no itemId convention that reliably distinguishes a dictation-
	// derived item from anything else, so sourceType is hard-coded to DICTATION here rather than
	// inferred, and revisited if a second Kafka-sourced producer of "listening" is ever added.
	@Override
	@Transactional
	public void saveWeakPoints(LearningGapAnalyzedEvent event) {
		for (WeakPointPayload weakPoint : event.getWeakPoints()) {
			if (!LISTENING_CATEGORY.equalsIgnoreCase(weakPoint.getCategory())) {
				continue;
			}

			mapper.upsert(ListeningWeakPoint.builder()
					.recordingId(event.getRecordingId())
					.userId(event.getUserId())
					.itemId(weakPoint.getItemId())
					.label(weakPoint.getLabel())
					.sourceType(ListeningSourceType.DICTATION)
					.forgettingScore(weakPoint.getForgettingScore())
					.recommendation(weakPoint.getRecommendation())
					.scoreSource(ScoreSource.PYTHON_LEGACY)
					.build());
		}
	}

	@Override
	public List<ListeningWeakPoint> getWeakPoints(String userId, ListeningSourceType sourceType) {
		return mapper.findByUserId(userId, sourceType == null ? null : sourceType.name());
	}

	// Same upsert path as saveWeakPoints, but sourced from the listening-comprehension practice/redo
	// flow's Java scoring engine instead of dictation's Kafka dual-write - the only caller of this
	// path today is the redo flow, so sourceType is hard-coded to COMPREHENSION, marked JAVA_ENGINE
	// so the guarded upsert (see this mapper's XML) keeps a stale PYTHON_LEGACY recompute from
	// clobbering it afterwards.
	@Override
	@Transactional
	public void applyJavaComputedScore(WeakPointScoreUpdate update) {
		if (!LISTENING_CATEGORY.equalsIgnoreCase(update.getCategory())) {
			return;
		}

		mapper.upsert(ListeningWeakPoint.builder()
				.recordingId(update.getRecordingId())
				.userId(update.getUserId())
				.itemId(update.getItemId())
				.label(update.getLabel())
				.sourceType(ListeningSourceType.COMPREHENSION)
				.forgettingScore(update.getWeakScore())
				.recommendation("Nghe lại đoạn audio liên quan đến \"" + update.getLabel()
						+ "\" và luyện tập thêm câu hỏi nghe hiểu tương tự.")
				.masteryLevel(update.getMasteryLevel())
				.nextReviewAt(update.getNextReviewAt())
				.scoreSource(ScoreSource.JAVA_ENGINE)
				.build());
	}
}
