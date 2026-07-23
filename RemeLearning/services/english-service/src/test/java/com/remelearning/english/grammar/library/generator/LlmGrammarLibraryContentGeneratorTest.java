package com.remelearning.english.grammar.library.generator;

import com.remelearning.common.ai.LlmClient;
import com.remelearning.common.ai.LlmRequest;
import com.remelearning.common.ai.LlmResponse;
import com.remelearning.english.learn.common.AiContentClient;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LlmGrammarLibraryContentGeneratorTest {

	private final LlmClient llmClient = mock(LlmClient.class);
	private final LlmGrammarLibraryContentGenerator generator =
			new LlmGrammarLibraryContentGenerator(new AiContentClient(llmClient));

	// Mermaid rejects a node label like A[V(s/es)] outright (unquoted parentheses break its
	// flowchart grammar) - the LLM is asked to quote such labels itself, but generateTopicContent
	// must still auto-quote them as a safety net so the frontend actually renders a diagram instead
	// of silently falling back to plain text.
	@Test
	void generateTopicContent_quotesUnquotedSpecialCharsInMermaidNodeLabels() {
		String json = """
				{"explanationEn": "Usage: habits.", "explanationVi": "Cach dung: thoi quen.",
				 "illustrationText": "```mermaid\\ngraph LR\\n    A[S] --> B[V(s/es)] --> C[O]\\n    D[Verb + s/es]\\n```",
				 "examples": [{"en": "She works.", "vi": "Co ay lam viec."}],
				 "questions": [{"type": "MCQ", "prompt": "She ___ every day.", "options": ["work", "works"], "answer": "works", "explanationVi": "so it"}]}
				""";
		when(llmClient.complete(any(LlmRequest.class))).thenReturn(LlmResponse.builder().content(json).build());

		GeneratedGrammarTopicContent result = generator.generateTopicContent("Present Simple", "beginner");

		assertThat(result.illustrationText())
				.contains("B[\"V(s/es)\"]")
				.contains("D[\"Verb + s/es\"]")
				.doesNotContain("B[V(s/es)]")
				.doesNotContain("D[Verb + s/es]");
	}

	@Test
	void generateTopicContent_leavesPlainMarkdownTableUntouched() {
		String json = """
				{"explanationEn": "Usage: habits.", "explanationVi": "Cach dung: thoi quen.",
				 "illustrationText": "| Subject | Verb |\\n| --- | --- |\\n| He/She/It | works |",
				 "examples": [{"en": "She works.", "vi": "Co ay lam viec."}],
				 "questions": [{"type": "MCQ", "prompt": "She ___ every day.", "options": ["work", "works"], "answer": "works", "explanationVi": "so it"}]}
				""";
		when(llmClient.complete(any(LlmRequest.class))).thenReturn(LlmResponse.builder().content(json).build());

		GeneratedGrammarTopicContent result = generator.generateTopicContent("Present Simple", "beginner");

		assertThat(result.illustrationText()).isEqualTo("| Subject | Verb |\n| --- | --- |\n| He/She/It | works |");
	}
}
