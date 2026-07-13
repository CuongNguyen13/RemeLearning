package com.remelearning.vocabulary.classifier;

import com.remelearning.vocabulary.domain.VocabularyType;
import org.springframework.stereotype.Component;

import java.util.Set;

/** Heuristic classifier: word count + suffix patterns, no LLM cost. */
@Component
public class RuleBasedVocabularyClassifier implements VocabularyClassifier {

	// Common phrasal-verb particles/prepositions ("give up", "look after", "wait for"...).
	private static final Set<String> PHRASAL_PARTICLES = Set.of(
			"up", "out", "off", "on", "in", "into", "away", "back", "down", "over", "through", "about", "along", "around",
			"after", "for", "with", "at", "to", "from", "of", "by");

	private static final Set<String> NOUN_SUFFIXES = Set.of(
			"tion", "sion", "ment", "ness", "ity", "ance", "ence", "ship", "hood", "dom", "er", "or");
	private static final Set<String> ADJECTIVE_SUFFIXES = Set.of(
			"ous", "ive", "able", "ible", "al", "ful", "less", "ic", "ish", "ant", "ent");

	@Override
	public VocabularyType classify(String label) {
		String term = extractTerm(label);
		String[] words = term.split("\\s+");
		return words.length >= 2 ? classifyPhrase(words) : classifyWord(words[0]);
	}

	/** Labels come as "category: term" (e.g. "word: reluctant") or a bare term. */
	private String extractTerm(String label) {
		int colonIndex = label.lastIndexOf(':');
		String term = colonIndex >= 0 ? label.substring(colonIndex + 1) : label;
		return term.trim().toLowerCase();
	}

	private VocabularyType classifyPhrase(String[] words) {
		if (words.length == 2 && PHRASAL_PARTICLES.contains(words[1])) {
			return VocabularyType.PHRASAL_VERB;
		}
		if (words.length <= 3) {
			return VocabularyType.COLLOCATION;
		}
		return VocabularyType.IDIOM;
	}

	private VocabularyType classifyWord(String word) {
		if (word.isEmpty()) {
			return VocabularyType.OTHER;
		}
		if (word.endsWith("ly")) {
			return VocabularyType.ADVERB;
		}
		if (endsWithAny(word, NOUN_SUFFIXES)) {
			return VocabularyType.NOUN;
		}
		if (endsWithAny(word, ADJECTIVE_SUFFIXES)) {
			return VocabularyType.ADJECTIVE;
		}
		if (word.endsWith("ing") || word.endsWith("ed")) {
			return VocabularyType.VERB;
		}
		return VocabularyType.OTHER;
	}

	private boolean endsWithAny(String word, Set<String> suffixes) {
		return suffixes.stream().anyMatch(word::endsWith);
	}
}
