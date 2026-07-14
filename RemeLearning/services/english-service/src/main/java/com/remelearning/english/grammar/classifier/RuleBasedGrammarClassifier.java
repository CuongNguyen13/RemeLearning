package com.remelearning.english.grammar.classifier;

import com.remelearning.english.grammar.domain.GrammarType;
import org.springframework.stereotype.Component;

/** Heuristic classifier: keyword matching against the label text, no LLM cost. */
@Component
public class RuleBasedGrammarClassifier implements GrammarClassifier {

	@Override
	public GrammarType classify(String label) {
		String text = label.toLowerCase();
		if (text.contains("agreement")) {
			return GrammarType.SUBJECT_VERB_AGREEMENT;
		}
		if (text.contains("tense")) {
			return GrammarType.TENSE;
		}
		if (text.contains("article")) {
			return GrammarType.ARTICLE;
		}
		if (text.contains("preposition")) {
			return GrammarType.PREPOSITION;
		}
		if (text.contains("order")) {
			return GrammarType.WORD_ORDER;
		}
		if (text.contains("plural")) {
			return GrammarType.PLURAL;
		}
		if (text.contains("punctuation")) {
			return GrammarType.PUNCTUATION;
		}
		return GrammarType.OTHER;
	}
}
