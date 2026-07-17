package com.remelearning.recommendation.exercise;

import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Default {@link ExerciseGenerator}: static, per-category templates, no LLM cost. Active unless
 * {@code recommendation.exercise-generator.mode=llm} (see {@link LlmExerciseGenerator}).
 */
@Component
@ConditionalOnProperty(prefix = "recommendation.exercise-generator", name = "mode", havingValue = "rule-based", matchIfMissing = true)
public class RuleBasedExerciseGenerator implements ExerciseGenerator {

	// Ignores forgettingScore - the static templates don't vary by urgency, only by category/label.
	@Override
	public List<String> generate(String category, String label, double forgettingScore) {
		return ExerciseTemplates.defaultsFor(category, label);
	}
}
