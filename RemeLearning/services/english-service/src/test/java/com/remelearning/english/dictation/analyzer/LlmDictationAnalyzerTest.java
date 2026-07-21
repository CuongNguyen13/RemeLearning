package com.remelearning.english.dictation.analyzer;

import com.remelearning.common.ai.LlmClient;
import com.remelearning.common.ai.LlmResponse;
import com.remelearning.english.dictation.dto.WordDiffDto;
import com.remelearning.english.dictation.dto.WordDiffTag;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClientException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LlmDictationAnalyzerTest {

	private final LlmClient llmClient = mock(LlmClient.class);
	private final LlmDictationAnalyzer analyzer = new LlmDictationAnalyzer(llmClient);

	private static final List<WordDiffDto> DIFF = List.of(
			WordDiffDto.builder().tag(WordDiffTag.MISSING).expectedWord("reluctant").actualWord(null).build());

	@Test
	void parsesFullAnalysisSchema() {
		when(llmClient.complete(any())).thenReturn(LlmResponse.builder().content("""
				{"errorTable": [{"original": "reluctant", "transcribed": "", "category": "LEXICON", "note": "từ lạ"}],
				 "rootCauses": [{"category": "LEXICON", "summary": "Từ vựng còn lạ.", "examples": ["reluctant"]}],
				 "actionAdvice": ["Ôn lại từ 'reluctant'."],
				 "practiceSentences": ["She was reluctant to leave."]}""").build());

		DictationAnalysis analysis = analyzer.analyzeAttempt("She was reluctant to admit it.", "She was to admit it.", DIFF);

		assertThat(analysis.getErrorTable()).hasSize(1);
		assertThat(analysis.getErrorTable().get(0).getCategory()).isEqualTo(DictationErrorCategory.LEXICON);
		assertThat(analysis.getRootCauses().get(0).getSummary()).isEqualTo("Từ vựng còn lạ.");
		assertThat(analysis.getActionAdvice()).containsExactly("Ôn lại từ 'reluctant'.");
		assertThat(analysis.getPracticeSentences()).containsExactly("She was reluctant to leave.");
	}

	@Test
	void stripsMarkdownCodeFencesBeforeParsing() {
		when(llmClient.complete(any())).thenReturn(LlmResponse.builder().content(
				"```json\n{\"errorTable\":[],\"rootCauses\":[],\"actionAdvice\":[\"Nghe lại.\"],\"practiceSentences\":[]}\n```")
				.build());

		DictationAnalysis analysis = analyzer.analyzeAttempt("ref", "typed", DIFF);

		assertThat(analysis.getActionAdvice()).containsExactly("Nghe lại.");
	}

	@Test
	void unrecognizedCategoryDefaultsToLexicon() {
		when(llmClient.complete(any())).thenReturn(LlmResponse.builder().content("""
				{"errorTable": [{"original": "reluctant", "transcribed": "", "category": "SLANG", "note": ""}],
				 "rootCauses": [], "actionAdvice": ["tip"], "practiceSentences": []}""").build());

		DictationAnalysis analysis = analyzer.analyzeAttempt("ref", "typed", DIFF);

		assertThat(analysis.getErrorTable().get(0).getCategory()).isEqualTo(DictationErrorCategory.LEXICON);
	}

	@Test
	void fallsBackToRuleBasedHeuristicWhenLlmCallFails() {
		when(llmClient.complete(any())).thenThrow(new RestClientException("ai-service unreachable"));

		DictationAnalysis analysis = analyzer.analyzeAttempt("She was reluctant to admit it.", "She was to admit it.", DIFF);

		assertThat(analysis.getErrorTable()).hasSize(1);
		assertThat(analysis.getActionAdvice()).isNotEmpty();
	}

	@Test
	void fallsBackToRuleBasedHeuristicWhenJsonIsUnparseable() {
		when(llmClient.complete(any())).thenReturn(LlmResponse.builder().content("not json").build());

		DictationAnalysis analysis = analyzer.analyzeAttempt("She was reluctant to admit it.", "She was to admit it.", DIFF);

		assertThat(analysis.getErrorTable()).hasSize(1);
	}

	@Test
	void generatePracticeSentencesParsesJsonArray() {
		when(llmClient.complete(any())).thenReturn(LlmResponse.builder()
				.content("[\"She was reluctant to leave.\"]").build());

		List<String> sentences = analyzer.generatePracticeSentences(List.of("reluctant"));

		assertThat(sentences).containsExactly("She was reluctant to leave.");
	}

	@Test
	void generatePracticeSentencesFallsBackToTemplatesOnFailure() {
		when(llmClient.complete(any())).thenThrow(new RestClientException("ai-service unreachable"));

		List<String> sentences = analyzer.generatePracticeSentences(List.of("reluctant"));

		assertThat(sentences).hasSize(1);
	}
}
