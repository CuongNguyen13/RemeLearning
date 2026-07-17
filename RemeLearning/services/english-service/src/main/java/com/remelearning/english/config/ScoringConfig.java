package com.remelearning.english.config;

import com.remelearning.common.scoring.BktParams;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

/**
 * Per-category Bayesian Knowledge Tracing parameters for the practice/redo scoring engine.
 * {@code pGuess} in particular shouldn't be one-size-fits-all: guessing a correct multiple-choice
 * grammar/vocabulary answer is a real phenomenon BKT was designed to model, but you can't "guess"
 * a correct pronunciation the same way, so pronunciation gets a much lower pGuess than the other
 * two categories. Plain constants for now rather than externalized YAML - if these need tuning
 * per environment later, that's the point to add config properties, not before.
 */
@Configuration
public class ScoringConfig {

	private static final BktParams VOCABULARY_PARAMS = BktParams.DEFAULT;
	private static final BktParams GRAMMAR_PARAMS = BktParams.DEFAULT;
	private static final BktParams PRONUNCIATION_PARAMS = BktParams.builder()
			.pInit(0.3)
			.pTransit(0.1)
			.pSlip(0.1)
			.pGuess(0.05)
			.build();

	/** Exposes the per-category BKT parameter map for injection into the scoring orchestrator. */
	@Bean
	public Map<String, BktParams> bktParamsByCategory() {
		return Map.of(
				"vocabulary", VOCABULARY_PARAMS,
				"grammar", GRAMMAR_PARAMS,
				"pronunciation", PRONUNCIATION_PARAMS);
	}
}
