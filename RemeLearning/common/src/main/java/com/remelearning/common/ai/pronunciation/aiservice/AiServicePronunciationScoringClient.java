package com.remelearning.common.ai.pronunciation.aiservice;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.remelearning.common.ai.pronunciation.PhonemePronunciationScore;
import com.remelearning.common.ai.pronunciation.PronunciationScore;
import com.remelearning.common.ai.pronunciation.PronunciationScoringClient;
import com.remelearning.common.ai.pronunciation.WordPronunciationScore;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.web.client.RestClient;

import java.io.InputStream;
import java.util.List;

/**
 * {@link PronunciationScoringClient} backed by the Python ai-service's
 * {@code POST /api/v1/pronunciation/score} (g2p_en + wav2vec2-lv-60-espeak-cv-ft GOP scoring).
 * Registered by {@link AiServicePronunciationScoringClientConfig}.
 */
public class AiServicePronunciationScoringClient implements PronunciationScoringClient {

	private static final String SCORE_PATH = "/api/v1/pronunciation/score";

	private final RestClient restClient;

	public AiServicePronunciationScoringClient(RestClient restClient) {
		this.restClient = restClient;
	}

	@Override
	public PronunciationScore score(InputStream audio, String audioFilename, String expectedText, String languageCode) {
		MultipartBodyBuilder builder = new MultipartBodyBuilder();
		builder.part("audio", new InputStreamResource(audio)).filename(audioFilename);
		builder.part("expected_text", expectedText);
		builder.part("language_code", languageCode == null ? "en" : languageCode);

		Response response = restClient.post()
				.uri(SCORE_PATH)
				.contentType(MediaType.MULTIPART_FORM_DATA)
				.body(builder.build())
				.retrieve()
				.body(Response.class);

		if (response == null) {
			throw new IllegalStateException("ai-service returned no pronunciation score");
		}
		List<WordPronunciationScore> words = response.words == null ? List.of() : response.words.stream()
				.map(w -> new WordPronunciationScore(w.word, w.score,
						w.phonemes == null ? List.of() : w.phonemes.stream()
								.map(p -> new PhonemePronunciationScore(p.ipa, p.score)).toList()))
				.toList();
		return new PronunciationScore(response.overall, words, response.transcript,
				response.weakPhonemes == null ? List.of() : response.weakPhonemes);
	}

	// Raw JSON shape ai-service's PronunciationScoreResponse returns.
	private record Response(double overall, List<WordScore> words, String transcript,
			@JsonProperty("weak_phonemes") List<String> weakPhonemes) {
	}

	private record WordScore(String word, double score, List<PhonemeScore> phonemes) {
	}

	private record PhonemeScore(String ipa, double score) {
	}
}
