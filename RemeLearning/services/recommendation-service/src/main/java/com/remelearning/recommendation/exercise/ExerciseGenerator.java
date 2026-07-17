package com.remelearning.recommendation.exercise;

import java.util.List;

/**
 * Generates concrete, actionable practice exercises for a weak point. Deliberately
 * category-agnostic: unlike english-service's per-domain classifiers, one implementation of this
 * interface serves every category (vocabulary, grammar, pronunciation, and any future category)
 * since recommendation-service never filters by category.
 */
public interface ExerciseGenerator {

	/**
	 * Never returns {@code null} and never throws - implementations must degrade to a safe default
	 * (e.g. a generic template) rather than propagate a failure, since a flaky call here can't be
	 * allowed to break weak-point ingestion.
	 *
	 * @param category      the weak point's skill category (e.g. "grammar", "vocabulary", "pronunciation")
	 * @param label         the specific weak point label (e.g. "past perfect tense")
	 * @param forgettingScore how urgent this weak point is (higher = more urgent)
	 * @return a short list of concrete exercises the learner can do to practice this weak point
	 */
	List<String> generate(String category, String label, double forgettingScore);
}
