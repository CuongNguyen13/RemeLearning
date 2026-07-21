# AI Dictation Practice Enhancements Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix the AI-practice audio/answer-text mismatch bug, add a topic label + level/exam-type selection (with per-field random) to AI-practice generation, unify the two generation code paths onto one dialogue generator, persist/display the resolved level/examType/topic, and add a per-sentence translation to the user's UI language for both AI-practice and the Library.

**Architecture:** Backend changes live entirely in `english-service` (source of truth) and are thinly proxied by `bff-service`, following the existing pattern where `bff-service` DTOs/clients/controller methods mirror `english-service`'s 1:1. Frontend changes add one new creation dialog, extend existing types/hooks, and extend the shared `SentenceDictationRunner` to show a translation hint. No new services, no schema outside two `ALTER TABLE`s.

**Tech Stack:** Spring Boot 4 / Java 21 / MyBatis (english-service, bff-service), Flyway migration, React 19 / TypeScript / TanStack Query / react-i18next (RemeLearning_FE).

## Global Constraints

- Comment non-trivial Java/TS code blocks with a short "what this does" comment (project convention, not just WHY).
- Java unit tests: plain JUnit 5 + AssertJ + `Mockito.mock(...)` — no `@Mock`/`@ExtendWith(MockitoExtension.class)`, no Spring context. Follow `DictationServiceImplTest`'s existing style exactly.
- Every REST endpoint/DTO change must be reflected in the same commit in: the service's own `openapi.yaml`, `docs/API.md`, `docs/flow/english-service-data-flow.md`, `docs/sequence/English_service/` (+ its `overview.md`), the service's own `README.md`, and `D:\Personal Project\RemeLearning_BA\Business.md`.
- Run `cd RemeLearning && ./mvnw -pl services/english-service -am test -Dsurefire.failIfNoSpecifiedTests=false` (english-service tasks) or the same with `services/bff-service` after each backend task.
- Level pool for "random" resolution: `A1, A2, B1, B2, C1` (per spec, no C2). Exam-type "random" resolves from `dictationMapper.findDistinctExamTypes()`, falling back to `TOEIC, IELTS, TOEFL, General` if that list is empty.
- Translation is generated/shown only when the target language is `"vi"` (content is always English; translating English→English is a no-op) — this skip rule is enforced at every call site, not centralized.

---

### Task 1: Schema + domain + DTO fields (english-service)

**Files:**
- Create: `RemeLearning/services/english-service/src/main/resources/db/migration/V11__ai_practice_taxonomy_and_translation.sql`
- Modify: `RemeLearning/services/english-service/src/main/java/com/remelearning/english/dictation/domain/DictationPracticeItem.java`
- Modify: `RemeLearning/services/english-service/src/main/java/com/remelearning/english/dictation/domain/DictationClipSentence.java`
- Modify: `RemeLearning/services/english-service/src/main/java/com/remelearning/english/dictation/dto/DictationSentenceDto.java`
- Modify: `RemeLearning/services/english-service/src/main/java/com/remelearning/english/dictation/dto/DictationPracticeItemDto.java`
- Modify: `RemeLearning/services/english-service/src/main/java/com/remelearning/english/dictation/dto/DictationPracticeItemDetailDto.java`
- Modify: `RemeLearning/services/english-service/src/main/java/com/remelearning/english/dictation/mapper/DictationMapper.java`
- Modify: `RemeLearning/services/english-service/src/main/resources/mapper/dictation/DictationMapper.xml`

**Interfaces:**
- Produces: `DictationPracticeItem` gains `String level`, `String examType`, `String topic`, `String translationText` fields (Lombok `@Data @Builder`). `DictationClipSentence` gains `String translation`. `DictationSentenceDto` gains `String translation`. `DictationPracticeItemDto`/`DictationPracticeItemDetailDto` gain `String level`, `String examType`, `String topic`. `DictationMapper` gains `void updateSentenceTranslation(Long clipId, int seq, String translation)`.

- [ ] **Step 1: Write the migration**

```sql
-- V11__ai_practice_taxonomy_and_translation.sql
-- Adds the level/examType/topic taxonomy (mirroring dictation_clips) plus a translation column to
-- AI-generated practice passages, and a per-sentence translation column to the library's sentences.
ALTER TABLE dictation_practice_items
    ADD COLUMN level VARCHAR(10),
    ADD COLUMN exam_type VARCHAR(40),
    ADD COLUMN topic VARCHAR(200),
    ADD COLUMN translation_text TEXT;

ALTER TABLE dictation_clip_sentences
    ADD COLUMN translation TEXT;
```

- [ ] **Step 2: Update `DictationPracticeItem`**

```java
package com.remelearning.english.dictation.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * A Gemini-suggested practice sentence/dialogue for the "Luyện nghe với AI" section, generated from
 * a learner's recurring dictation misses. {@code storageKey} is null until Supertonic TTS synthesizes
 * its audio. {@code level}/{@code examType}/{@code topic} mirror {@link DictationClip}'s taxonomy and
 * are null for single-sentence items (no generation-time facet selection); {@code translationText} is
 * the passage translated to the learner's UI language, newline-joined in the same line order as
 * {@code sentenceText}, or null when no translation was requested/generated.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DictationPracticeItem {
	private Long id;
	private String userId;
	private String sentenceText;
	private String source;
	private String storageKey;
	private String level;
	private String examType;
	private String topic;
	private String translationText;
	private Instant createdAt;
}
```

- [ ] **Step 3: Update `DictationClipSentence`**

```java
package com.remelearning.english.dictation.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * One sentence of a {@link DictationClip}'s script, ordered by {@code seq} within the clip.
 * {@code startMs}/{@code endMs} are null until the separate AI-alignment step (STT via ai-service)
 * has located this sentence in the audio timeline. {@code translation} is null until lazily
 * translated to the learner's UI language (see {@code DictationServiceImpl#getClipDetail}).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DictationClipSentence {
	private Long id;
	private Long clipId;
	private int seq;
	private String text;
	private Integer startMs;
	private Integer endMs;
	private String translation;
	private Instant createdAt;
}
```

- [ ] **Step 4: Update `DictationSentenceDto`, `DictationPracticeItemDto`, `DictationPracticeItemDetailDto`**

```java
// DictationSentenceDto.java
package com.remelearning.english.dictation.dto;

import lombok.Builder;
import lombok.Getter;

/** One sentence of a clip's script, with its AI-aligned audio timestamps and translation if available. */
@Getter
@Builder
public class DictationSentenceDto {
	private int index;
	private String text;
	private Integer startMs; // null if the clip hasn't been AI-aligned yet
	private Integer endMs;   // null if the clip hasn't been AI-aligned yet
	private String translation; // null if no translation was requested/generated
}
```

```java
// DictationPracticeItemDto.java
package com.remelearning.english.dictation.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * One AI-practice item for the "Luyện nghe với AI" section. {@code audioUrl} is null until the
 * Supertonic audio has been synthesized; the learner dictates the (hidden) sentence by ear.
 * {@code level}/{@code examType}/{@code topic} are null for items generated without an explicit
 * facet selection (e.g. from a history attempt's mistakes).
 */
@Getter
@Builder
public class DictationPracticeItemDto {
	private Long practiceItemId;
	private String audioUrl;
	private String level;
	private String examType;
	private String topic;
}
```

```java
// DictationPracticeItemDetailDto.java
package com.remelearning.english.dictation.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * Full detail for one AI-practice item, returned only when the learner opens it to practice
 * sentence-by-sentence (GET /api/v1/dictation/ai-practice/items/{practiceItemId}/detail) - mirrors
 * {@link DictationClipDetailDto} so the same sentence-mode runner works for both the library and the
 * "Luyện nghe với AI" section. {@code sentences} never carry AI-aligned timestamps (the passage's
 * audio is one merged file with no per-sentence timing), so the client falls back to its own
 * word-count-share estimate the same way it does for a library clip not yet aligned.
 */
@Getter
@Builder
public class DictationPracticeItemDetailDto {
	private Long practiceItemId;
	private String audioUrl;
	private String scriptText;
	private String level;
	private String examType;
	private String topic;
	private List<DictationSentenceDto> sentences;
}
```

- [ ] **Step 5: Add the new mapper method + update existing SQL**

Add to `DictationMapper.java` (right after `updateSentenceTimestamps`):

```java
	/** Sets one sentence's translation once it's been lazily generated (see getClipDetail). */
	void updateSentenceTranslation(@Param("clipId") Long clipId, @Param("seq") int seq, @Param("translation") String translation);
```

Update `DictationMapper.xml`:

```xml
    <update id="updateSentenceTranslation">
        UPDATE dictation_clip_sentences
        SET translation = #{translation}
        WHERE clip_id = #{clipId} AND seq = #{seq}
    </update>
```

Replace `findSentencesByClipId`:

```xml
    <select id="findSentencesByClipId" resultType="com.remelearning.english.dictation.domain.DictationClipSentence">
        SELECT id, clip_id, seq, text, start_ms, end_ms, translation, created_at
        FROM dictation_clip_sentences
        WHERE clip_id = #{clipId}
        ORDER BY seq
    </select>
```

Replace `insertPracticeItem`, `findPracticeItemById`, `findPracticeItemsByUserId`, `findPracticeItemsWithoutAudio`:

```xml
    <insert id="insertPracticeItem" parameterType="com.remelearning.english.dictation.domain.DictationPracticeItem"
            useGeneratedKeys="true" keyProperty="id">
        INSERT INTO dictation_practice_items
            (user_id, sentence_text, source, storage_key, level, exam_type, topic, translation_text, created_at)
        VALUES
            (#{userId}, #{sentenceText}, #{source}, #{storageKey}, #{level}, #{examType}, #{topic}, #{translationText}, now())
    </insert>

    <select id="findPracticeItemById" resultType="com.remelearning.english.dictation.domain.DictationPracticeItem">
        SELECT id, user_id, sentence_text, source, storage_key, level, exam_type, topic, translation_text, created_at
        FROM dictation_practice_items
        WHERE id = #{practiceItemId}
    </select>

    <select id="findPracticeItemsByUserId" resultType="com.remelearning.english.dictation.domain.DictationPracticeItem">
        SELECT id, user_id, sentence_text, source, storage_key, level, exam_type, topic, translation_text, created_at
        FROM dictation_practice_items
        WHERE user_id = #{userId}
        ORDER BY created_at DESC
    </select>

    <select id="findPracticeItemsWithoutAudio" resultType="com.remelearning.english.dictation.domain.DictationPracticeItem">
        SELECT id, user_id, sentence_text, source, storage_key, level, exam_type, topic, translation_text, created_at
        FROM dictation_practice_items
        WHERE user_id = #{userId} AND storage_key IS NULL
        ORDER BY created_at DESC
    </select>
```

- [ ] **Step 6: Compile + run existing tests (no behavior changed yet, this is pure plumbing)**

Run: `cd RemeLearning && ./mvnw -pl services/english-service -am test -Dsurefire.failIfNoSpecifiedTests=false`
Expected: BUILD SUCCESS, all existing tests still pass (builder-based construction of these classes is unaffected by added fields).

- [ ] **Step 7: Commit**

```bash
git add RemeLearning/services/english-service/src/main/resources/db/migration/V11__ai_practice_taxonomy_and_translation.sql RemeLearning/services/english-service/src/main/java/com/remelearning/english/dictation/domain/DictationPracticeItem.java RemeLearning/services/english-service/src/main/java/com/remelearning/english/dictation/domain/DictationClipSentence.java RemeLearning/services/english-service/src/main/java/com/remelearning/english/dictation/dto/DictationSentenceDto.java RemeLearning/services/english-service/src/main/java/com/remelearning/english/dictation/dto/DictationPracticeItemDto.java RemeLearning/services/english-service/src/main/java/com/remelearning/english/dictation/dto/DictationPracticeItemDetailDto.java RemeLearning/services/english-service/src/main/java/com/remelearning/english/dictation/mapper/DictationMapper.java RemeLearning/services/english-service/src/main/resources/mapper/dictation/DictationMapper.xml
git commit -m "feat(dictation): add level/examType/topic/translation columns and DTO fields"
```

---

### Task 2: Dialogue generator contract — topic + level/examType/translation

**Files:**
- Modify: `RemeLearning/services/english-service/src/main/java/com/remelearning/english/dictation/analyzer/DictationDialogueLine.java`
- Create: `RemeLearning/services/english-service/src/main/java/com/remelearning/english/dictation/analyzer/DialogueGenerationResult.java`
- Modify: `RemeLearning/services/english-service/src/main/java/com/remelearning/english/dictation/analyzer/DictationDialogueGenerator.java`
- Modify: `RemeLearning/services/english-service/src/main/java/com/remelearning/english/dictation/analyzer/LlmDictationDialogueGenerator.java`
- Modify: `RemeLearning/services/english-service/src/test/java/com/remelearning/english/dictation/analyzer/LlmDictationDialogueGeneratorTest.java`

**Interfaces:**
- Consumes: `DictationAnalysisTemplates.practiceSentencesFor(List<String>)` (unchanged, package-private, existing).
- Produces: `DictationDialogueLine(String speaker, String text, String translation)`. `DialogueGenerationResult(String topic, List<DictationDialogueLine> lines)`. `DictationDialogueGenerator.generateDialogue(List<String> targetPhrases, String level, String examType, String translationLang)` returning `DialogueGenerationResult` — consumed by Task 4 (`DictationServiceImpl`).

- [ ] **Step 1: Write the failing tests (full replacement of the test file)**

```java
package com.remelearning.english.dictation.analyzer;

import com.remelearning.common.ai.LlmClient;
import com.remelearning.common.ai.LlmResponse;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClientException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LlmDictationDialogueGeneratorTest {

	private final LlmClient llmClient = mock(LlmClient.class);
	private final LlmDictationDialogueGenerator generator = new LlmDictationDialogueGenerator(llmClient);

	@Test
	void parsesTopicAndSpeakerTaggedLinesFromJsonObject() {
		when(llmClient.complete(any())).thenReturn(LlmResponse.builder()
				.content("""
						{"topic": "Returning a faulty product", "lines": [
						  {"speaker": "Alex", "text": "Did you see that reluctant look?"},
						  {"speaker": "Sam", "text": "Yes, he seemed hesitant."}
						]}""")
				.build());

		DialogueGenerationResult result = generator.generateDialogue(List.of("reluctant", "hesitant"), null, null, null);

		assertThat(result.topic()).isEqualTo("Returning a faulty product");
		assertThat(result.lines()).containsExactly(
				new DictationDialogueLine("Alex", "Did you see that reluctant look?", null),
				new DictationDialogueLine("Sam", "Yes, he seemed hesitant.", null));
	}

	@Test
	void parsesPerLineTranslationWhenTranslationLangRequested() {
		when(llmClient.complete(any())).thenReturn(LlmResponse.builder()
				.content("""
						{"topic": "Ordering coffee", "lines": [
						  {"speaker": "Narrator", "text": "Listen carefully.", "translation": "Hãy lắng nghe cẩn thận."}
						]}""")
				.build());

		DialogueGenerationResult result = generator.generateDialogue(List.of("careful"), "B1", "TOEIC", "vi");

		assertThat(result.lines()).containsExactly(
				new DictationDialogueLine("Narrator", "Listen carefully.", "Hãy lắng nghe cẩn thận."));
	}

	@Test
	void stripsMarkdownCodeFencesBeforeParsing() {
		when(llmClient.complete(any())).thenReturn(LlmResponse.builder()
				.content("```json\n{\"topic\": \"Study tips\", \"lines\": [{\"speaker\": \"Narrator\", \"text\": \"Listen carefully.\"}]}\n```")
				.build());

		DialogueGenerationResult result = generator.generateDialogue(List.of("careful"), null, null, null);

		assertThat(result.topic()).isEqualTo("Study tips");
		assertThat(result.lines()).containsExactly(new DictationDialogueLine("Narrator", "Listen carefully.", null));
	}

	@Test
	void fallsBackToTemplatedLinesWithNullTopicWhenLlmCallFails() {
		when(llmClient.complete(any())).thenThrow(new RestClientException("ai-service unreachable"));

		DialogueGenerationResult result = generator.generateDialogue(List.of("reluctant"), null, null, null);

		assertThat(result.topic()).isNull();
		assertThat(result.lines()).containsExactly(
				new DictationDialogueLine("Narrator", "Listen carefully and write the word \"reluctant\".", null));
	}

	@Test
	void fallsBackToTemplatedLinesWhenLlmReturnsNoUsableLines() {
		when(llmClient.complete(any())).thenReturn(LlmResponse.builder().content("{\"topic\": \"x\", \"lines\": []}").build());

		DialogueGenerationResult result = generator.generateDialogue(List.of("reluctant"), null, null, null);

		assertThat(result.topic()).isNull();
		assertThat(result.lines()).containsExactly(
				new DictationDialogueLine("Narrator", "Listen carefully and write the word \"reluctant\".", null));
	}
}
```

- [ ] **Step 2: Run to verify it fails to compile (old classes don't have this shape yet)**

Run: `cd RemeLearning && ./mvnw -pl services/english-service -am test -Dtest=LlmDictationDialogueGeneratorTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: COMPILATION ERROR (`DictationDialogueLine` has no 3-arg constructor, `generateDialogue` has no 4-arg overload, `DialogueGenerationResult` doesn't exist).

- [ ] **Step 3: Update `DictationDialogueLine`**

```java
package com.remelearning.english.dictation.analyzer;

/**
 * One line/turn within a generated listening-practice passage, spoken by {@code speaker}.
 * {@code translation} is null unless a translation language was requested at generation time.
 */
public record DictationDialogueLine(String speaker, String text, String translation) {
}
```

- [ ] **Step 4: Create `DialogueGenerationResult`**

```java
package com.remelearning.english.dictation.analyzer;

import java.util.List;

/** One generated listening-practice passage: its topic label (may be null on fallback) plus its lines. */
public record DialogueGenerationResult(String topic, List<DictationDialogueLine> lines) {
}
```

- [ ] **Step 5: Update the `DictationDialogueGenerator` interface**

```java
package com.remelearning.english.dictation.analyzer;

import java.util.List;

/**
 * Generates one listening-practice passage - a monologue or a multi-speaker dialogue - that
 * naturally reuses a learner's hard-to-hear words/phrases, for the "Luyện nghe với AI" section.
 * Callers depend on this interface, not the implementation, so the generation provider can change
 * without touching them.
 */
public interface DictationDialogueGenerator {

	/**
	 * Never returns a result with null/empty lines and never throws - implementations must degrade to
	 * one templated line per phrase (see {@link DictationAnalysisTemplates}) with a null topic on any
	 * LLM/parse failure.
	 *
	 * @param targetPhrases the words/phrases to practice; may be empty for a generic passage
	 * @param level CEFR target (e.g. "B1"); null lets the implementation pick a sensible default
	 * @param examType exam style to frame the passage around (e.g. "TOEIC"); null for no framing
	 * @param translationLang UI language to also translate each line into; null or "en" (the content's
	 *                        own language) means no translation is requested
	 */
	DialogueGenerationResult generateDialogue(List<String> targetPhrases, String level, String examType, String translationLang);
}
```

- [ ] **Step 6: Rewrite `LlmDictationDialogueGenerator`**

```java
package com.remelearning.english.dictation.analyzer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.remelearning.common.ai.LlmClient;
import com.remelearning.common.ai.LlmRequest;
import com.remelearning.common.ai.LlmResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;

import java.util.ArrayList;
import java.util.List;

/**
 * {@link DictationDialogueGenerator} backed by whichever {@link LlmClient} is configured (Gemini
 * today) - always active, unlike {@link DictationAnalyzer}'s rule-based/llm toggle, since a
 * templated one-word-per-line fallback would defeat the point of this feature. Falls back to that
 * template anyway if the LLM call or its JSON parsing fails, honoring the interface's never-throw
 * contract.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LlmDictationDialogueGenerator implements DictationDialogueGenerator {

	private static final String BASE_INSTRUCTIONS = """
            You are an English listening-practice content generator for a dictation app. Given a list of words/phrases a learner has trouble hearing, write a short dialogue between 2-3 named speakers that naturally reuses ALL of the provided words/phrases.

            Strict Constraints:

            Structure: It MUST be a dialogue containing AT LEAST 10 sentences in total.

            Difficulty: The vocabulary and grammar must align with the CEFR %s level.
            %s
            Thematic Unity: The conversation MUST strictly revolve around ONE single, coherent topic or scenario - name that topic/scenario concisely.

            Length: Keep the total word count concise (around 120-150 words) while fulfilling the 10-sentence minimum.
            %s
            Respond with STRICTLY a raw JSON object (no markdown fences, no code blocks, no commentary) of the shape {"topic": "short topic label", "lines": [{"speaker": "SpeakerName", "text": "..."%s}, ...]}. Use a distinct name per person, allocating one array element per conversational turn or line.""";

	private static final ObjectMapper MAPPER = new ObjectMapper();
	private static final String DEFAULT_SPEAKER = "Narrator";
	private static final String DEFAULT_LEVEL = "B2";

	private final LlmClient llmClient;

	// Asks the LLM for the passage as a JSON object {topic, lines: [{speaker, text, translation?}]};
	// any failure or empty parse falls back to one templated line per target phrase (with a null
	// topic) so generation never breaks.
	@Override
	public DialogueGenerationResult generateDialogue(
			List<String> targetPhrases, String level, String examType, String translationLang) {
		List<String> phrases = targetPhrases == null ? List.of() : targetPhrases;
		boolean wantsTranslation = translationLang != null && !"en".equalsIgnoreCase(translationLang);
		LlmRequest request = LlmRequest.builder()
				.systemPrompt(buildSystemPrompt(level, examType, wantsTranslation, translationLang))
				.userPrompt("Words/phrases to practice: "
						+ (phrases.isEmpty() ? "(none - pick any everyday topic)" : String.join(", ", phrases)))
				.temperature(0.7)
				.maxOutputTokens(wantsTranslation ? 700 : 400)
				.build();
		try {
			LlmResponse response = llmClient.complete(request);
			DialogueGenerationResult result = readDialogueObject(MAPPER.readTree(stripCodeFences(response.getContent())));
			if (result.lines().isEmpty()) {
				throw new IllegalStateException("LLM returned no dialogue lines");
			}
			return result;
		} catch (JsonProcessingException | IllegalStateException | RestClientException ex) {
			log.warn("LLM dialogue generation failed for {} target phrases, falling back to templates", phrases.size(), ex);
			return new DialogueGenerationResult(null, fallbackDialogue(phrases));
		}
	}

	// Composes the constraint block: CEFR level (defaults to B2 when unspecified), an optional
	// exam-style framing line, and an optional translation instruction/schema field.
	private String buildSystemPrompt(String level, String examType, boolean wantsTranslation, String translationLang) {
		String examLine = examType == null || examType.isBlank()
				? ""
				: "\nExam framing: Write the dialogue in a style suited to preparing for the " + examType + " exam.\n";
		String translationLine = wantsTranslation
				? "\nTranslation: Also translate every line into " + languageName(translationLang) + ".\n"
				: "";
		String translationField = wantsTranslation ? ", \"translation\": \"...\"" : "";
		return BASE_INSTRUCTIONS.formatted(
				level == null || level.isBlank() ? DEFAULT_LEVEL : level, examLine, translationLine, translationField);
	}

	private String languageName(String code) {
		return "vi".equalsIgnoreCase(code) ? "Vietnamese" : code;
	}

	// Reads {"topic": "...", "lines": [{speaker, text, translation?}]} into a result, skipping any
	// line with blank text.
	private DialogueGenerationResult readDialogueObject(JsonNode root) {
		String topic = root.path("topic").asText("").trim();
		List<DictationDialogueLine> lines = new ArrayList<>();
		for (JsonNode node : root.path("lines")) {
			String text = node.path("text").asText("").trim();
			if (!text.isBlank()) {
				String speaker = node.path("speaker").asText(DEFAULT_SPEAKER).trim();
				String translation = node.path("translation").asText("").trim();
				lines.add(new DictationDialogueLine(
						speaker.isBlank() ? DEFAULT_SPEAKER : speaker, text, translation.isBlank() ? null : translation));
			}
		}
		return new DialogueGenerationResult(topic.isBlank() ? null : topic, lines);
	}

	// Degrades to one single-speaker templated line per target phrase, mirroring DictationAnalysisTemplates.
	private List<DictationDialogueLine> fallbackDialogue(List<String> targetPhrases) {
		return DictationAnalysisTemplates.practiceSentencesFor(targetPhrases).stream()
				.map(sentence -> new DictationDialogueLine(DEFAULT_SPEAKER, sentence, null))
				.toList();
	}

	// Gemini occasionally wraps JSON in a ```json ... ``` fence despite being asked not to; strip it.
	private static String stripCodeFences(String content) {
		String trimmed = content.trim();
		if (trimmed.startsWith("```")) {
			trimmed = trimmed.substring(trimmed.indexOf('\n') + 1);
			int lastFence = trimmed.lastIndexOf("```");
			if (lastFence >= 0) {
				trimmed = trimmed.substring(0, lastFence);
			}
		}
		return trimmed.trim();
	}
}
```

- [ ] **Step 7: Run tests to verify they pass**

Run: `cd RemeLearning && ./mvnw -pl services/english-service -am test -Dtest=LlmDictationDialogueGeneratorTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS, all 5 tests green.

- [ ] **Step 8: Commit**

```bash
git add RemeLearning/services/english-service/src/main/java/com/remelearning/english/dictation/analyzer/DictationDialogueLine.java RemeLearning/services/english-service/src/main/java/com/remelearning/english/dictation/analyzer/DialogueGenerationResult.java RemeLearning/services/english-service/src/main/java/com/remelearning/english/dictation/analyzer/DictationDialogueGenerator.java RemeLearning/services/english-service/src/main/java/com/remelearning/english/dictation/analyzer/LlmDictationDialogueGenerator.java RemeLearning/services/english-service/src/test/java/com/remelearning/english/dictation/analyzer/LlmDictationDialogueGeneratorTest.java
git commit -m "feat(dictation): generate a topic label + optional per-line translation alongside the dialogue"
```

---

### Task 3: Library sentence translator (new component)

**Files:**
- Create: `RemeLearning/services/english-service/src/main/java/com/remelearning/english/dictation/analyzer/DictationSentenceTranslator.java`
- Create: `RemeLearning/services/english-service/src/main/java/com/remelearning/english/dictation/analyzer/LlmDictationSentenceTranslator.java`
- Create: `RemeLearning/services/english-service/src/test/java/com/remelearning/english/dictation/analyzer/LlmDictationSentenceTranslatorTest.java`

**Interfaces:**
- Produces: `DictationSentenceTranslator.translate(List<String> sentences, String targetLang)` returning `List<String>` (nullable elements, always same size as input, never throws) — consumed by Task 4's `DictationServiceImpl.getClipDetail`.

- [ ] **Step 1: Write the failing test**

```java
package com.remelearning.english.dictation.analyzer;

import com.remelearning.common.ai.LlmClient;
import com.remelearning.common.ai.LlmResponse;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClientException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LlmDictationSentenceTranslatorTest {

	private final LlmClient llmClient = mock(LlmClient.class);
	private final LlmDictationSentenceTranslator translator = new LlmDictationSentenceTranslator(llmClient);

	@Test
	void translatesEachSentenceInOrder() {
		when(llmClient.complete(any())).thenReturn(LlmResponse.builder()
				.content("[\"Xin chào.\", \"Bạn khỏe không?\"]")
				.build());

		List<String> translations = translator.translate(List.of("Hello.", "How are you?"), "vi");

		assertThat(translations).containsExactly("Xin chào.", "Bạn khỏe không?");
	}

	@Test
	void stripsMarkdownCodeFencesBeforeParsing() {
		when(llmClient.complete(any())).thenReturn(LlmResponse.builder()
				.content("```json\n[\"Xin chào.\"]\n```")
				.build());

		List<String> translations = translator.translate(List.of("Hello."), "vi");

		assertThat(translations).containsExactly("Xin chào.");
	}

	@Test
	void returnsAllNullsSameSizeWhenLlmCallFails() {
		when(llmClient.complete(any())).thenThrow(new RestClientException("ai-service unreachable"));

		List<String> translations = translator.translate(List.of("Hello.", "Bye."), "vi");

		assertThat(translations).containsExactly(new String[] { null, null });
	}

	@Test
	void returnsAllNullsSameSizeWhenLlmReturnsMismatchedCount() {
		when(llmClient.complete(any())).thenReturn(LlmResponse.builder().content("[\"Xin chào.\"]").build());

		List<String> translations = translator.translate(List.of("Hello.", "Bye."), "vi");

		assertThat(translations).containsExactly(new String[] { null, null });
	}

	@Test
	void returnsEmptyListForEmptyInput() {
		List<String> translations = translator.translate(List.of(), "vi");

		assertThat(translations).isEmpty();
	}
}
```

- [ ] **Step 2: Run to verify it fails to compile**

Run: `cd RemeLearning && ./mvnw -pl services/english-service -am test -Dtest=LlmDictationSentenceTranslatorTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: COMPILATION ERROR (`LlmDictationSentenceTranslator` doesn't exist yet).

- [ ] **Step 3: Write `DictationSentenceTranslator`**

```java
package com.remelearning.english.dictation.analyzer;

import java.util.List;

/**
 * Translates a library clip's sentences into a target UI language, for the lazy per-sentence
 * translation shown alongside the dictation hint. Callers depend on this interface, not the
 * implementation, so the translation provider can change without touching them.
 */
public interface DictationSentenceTranslator {

	/**
	 * Never throws - on any failure (LLM error, unparsable/mismatched response), returns a list the
	 * same size as {@code sentences} filled with nulls, so callers can always zip the result 1:1
	 * against their input and simply skip whichever entries came back null.
	 */
	List<String> translate(List<String> sentences, String targetLang);
}
```

- [ ] **Step 4: Write `LlmDictationSentenceTranslator`**

```java
package com.remelearning.english.dictation.analyzer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.remelearning.common.ai.LlmClient;
import com.remelearning.common.ai.LlmRequest;
import com.remelearning.common.ai.LlmResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * {@link DictationSentenceTranslator} backed by whichever {@link LlmClient} is configured. One
 * batched call per clip (not one call per sentence) to keep this cheap; any failure or count
 * mismatch degrades to an all-null result the same size as the input, honoring the interface's
 * never-throw contract.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LlmDictationSentenceTranslator implements DictationSentenceTranslator {

	private static final String SYSTEM_PROMPT = """
            You are a translation engine for an English-listening dictation app. Translate each of the
            given English sentences into %s, preserving order and count exactly - one output string per
            input sentence, in the same order.

            Respond with STRICTLY a raw JSON array of strings (no markdown fences, no commentary).""";

	private static final ObjectMapper MAPPER = new ObjectMapper();

	private final LlmClient llmClient;

	@Override
	public List<String> translate(List<String> sentences, String targetLang) {
		if (sentences == null || sentences.isEmpty()) {
			return List.of();
		}
		LlmRequest request = LlmRequest.builder()
				.systemPrompt(SYSTEM_PROMPT.formatted(languageName(targetLang)))
				.userPrompt(String.join("\n", sentences))
				.temperature(0.2)
				.maxOutputTokens(600)
				.build();
		try {
			LlmResponse response = llmClient.complete(request);
			List<String> translations = readStringArray(MAPPER.readTree(stripCodeFences(response.getContent())));
			if (translations.size() != sentences.size()) {
				throw new IllegalStateException(
						"Translation count %d did not match sentence count %d".formatted(translations.size(), sentences.size()));
			}
			return translations;
		} catch (JsonProcessingException | IllegalStateException | RestClientException ex) {
			log.warn("Sentence translation to {} failed for {} sentences, returning no translations", targetLang, sentences.size(), ex);
			return Collections.nCopies(sentences.size(), null);
		}
	}

	private String languageName(String code) {
		return "vi".equalsIgnoreCase(code) ? "Vietnamese" : code;
	}

	private List<String> readStringArray(JsonNode root) {
		List<String> values = new ArrayList<>();
		if (root.isArray()) {
			for (JsonNode node : root) {
				values.add(node.asText());
			}
		}
		return values;
	}

	private static String stripCodeFences(String content) {
		String trimmed = content.trim();
		if (trimmed.startsWith("```")) {
			trimmed = trimmed.substring(trimmed.indexOf('\n') + 1);
			int lastFence = trimmed.lastIndexOf("```");
			if (lastFence >= 0) {
				trimmed = trimmed.substring(0, lastFence);
			}
		}
		return trimmed.trim();
	}
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `cd RemeLearning && ./mvnw -pl services/english-service -am test -Dtest=LlmDictationSentenceTranslatorTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS, all 5 tests green.

- [ ] **Step 6: Commit**

```bash
git add RemeLearning/services/english-service/src/main/java/com/remelearning/english/dictation/analyzer/DictationSentenceTranslator.java RemeLearning/services/english-service/src/main/java/com/remelearning/english/dictation/analyzer/LlmDictationSentenceTranslator.java RemeLearning/services/english-service/src/test/java/com/remelearning/english/dictation/analyzer/LlmDictationSentenceTranslatorTest.java
git commit -m "feat(dictation): add an LLM-backed library sentence translator"
```

---

### Task 4: `GenerateAiPracticeRequest` DTO + service/controller signatures (english-service)

**Files:**
- Create: `RemeLearning/services/english-service/src/main/java/com/remelearning/english/dictation/dto/GenerateAiPracticeRequest.java`
- Modify: `RemeLearning/services/english-service/src/main/java/com/remelearning/english/dictation/service/DictationService.java`
- Modify: `RemeLearning/services/english-service/src/main/java/com/remelearning/english/dictation/controller/DictationController.java`

**Interfaces:**
- Produces: `GenerateAiPracticeRequest` with `String level`, `String examType`, `String translationLang` (plain getters/setters, no validation needed - all optional). `DictationService.generateAiPractice(String userId, GenerateAiPracticeRequest request)`. `DictationService.generateAiPracticeFromAttempt(String userId, Long attemptId, String translationLang)`. `DictationService.getClipDetail(Long clipId, String translationLang)`.

- [ ] **Step 1: Write `GenerateAiPracticeRequest`**

```java
package com.remelearning.english.dictation.dto;

import lombok.Data;

/**
 * Facets for generating one AI-practice passage. Each of {@code level}/{@code examType} may be a
 * concrete value (e.g. "B1", "TOEIC"), the literal {@code "RANDOM"} (resolved server-side to one
 * concrete value so the caller always learns what was picked via the returned item's own fields), or
 * null/blank (no preference - the LLM picks freely, matching the pre-existing default behavior).
 * {@code translationLang} is the learner's current UI language ("en"/"vi"); a translation is only
 * generated when it's not "en" (the content's own language).
 */
@Data
public class GenerateAiPracticeRequest {
	private String level;
	private String examType;
	private String translationLang;
}
```

- [ ] **Step 2: Update `DictationService` interface**

Replace the three affected method signatures/docs (leave every other method unchanged):

```java
	/** The learner's AI-practice items (audio URL present once synthesized). */
	List<DictationPracticeItemDto> getAiPractice(String userId);

	/**
	 * Ensures the learner has AI-practice clips with audio: synthesizes any pending items (or first
	 * generates new ones from their most-missed words), honoring the request's level/examType facets
	 * (each may be a concrete value, "RANDOM", or null for no preference) and translation language,
	 * then returns the full list.
	 */
	List<DictationPracticeItemDto> generateAiPractice(String userId, GenerateAiPracticeRequest request);

	/**
	 * Generates AI-practice sentences targeted at one specific past attempt's mistakes (the "Luyện
	 * tập với AI" action from a history row), synthesizes their audio, then returns the learner's
	 * refreshed AI-practice list; throws {@code BusinessException.notFound} if the attempt doesn't
	 * exist or belongs to a different user. {@code translationLang} is the learner's current UI
	 * language; a translation is only generated when it's not "en".
	 */
	List<DictationPracticeItemDto> generateAiPracticeFromAttempt(String userId, Long attemptId, String translationLang);
```

```java
	/**
	 * Full detail for one clip - script + split sentences - shown once the learner opens it to
	 * practice. {@code translationLang} is the learner's current UI language; a per-sentence
	 * translation is lazily generated (and cached) only when it's not "en".
	 */
	DictationClipDetailDto getClipDetail(Long clipId, String translationLang);
```

(Remove the old no-arg `getClipDetail(Long clipId)` and the old no-request `generateAiPractice(String userId)`/`generateAiPracticeFromAttempt(String userId, Long attemptId)` declarations they replace.)

- [ ] **Step 3: Update `DictationController`**

```java
	@Operation(summary = "Full detail for one clip - script + split sentences - for sentence-by-sentence practice, with an optional per-sentence translation")
	@GetMapping("/clips/{clipId}")
	public ApiResponse<DictationClipDetailDto> getClipDetail(
			@PathVariable Long clipId, @RequestParam(required = false) String translationLang) {
		return ApiResponse.ok(dictationService.getClipDetail(clipId, translationLang));
	}
```

```java
	@Operation(summary = "Generate one AI-practice listening passage (Gemini monologue/dialogue, one random Supertonic voice per speaker, merged into one audio file) from the learner's still-unsynthesized items or most-missed words, honoring the requested level/exam-type facets (concrete value, \"RANDOM\", or omitted) and translation language")
	@PostMapping("/ai-practice/{userId}/generate")
	public ApiResponse<List<DictationPracticeItemDto>> generateAiPractice(
			@PathVariable String userId, @RequestBody(required = false) GenerateAiPracticeRequest request) {
		return ApiResponse.ok(dictationService.generateAiPractice(userId, request == null ? new GenerateAiPracticeRequest() : request));
	}
```

```java
	@Operation(summary = "Generate AI-practice clips targeted at one specific past attempt's mistakes (the \"Luyện tập với AI\" action from a history row)")
	@PostMapping("/history/{userId}/{attemptId}/ai-practice")
	public ApiResponse<List<DictationPracticeItemDto>> generateAiPracticeFromAttempt(
			@PathVariable String userId, @PathVariable Long attemptId,
			@RequestParam(required = false) String translationLang) {
		return ApiResponse.ok(dictationService.generateAiPracticeFromAttempt(userId, attemptId, translationLang));
	}
```

Add the import `com.remelearning.english.dictation.dto.GenerateAiPracticeRequest` alongside the other DTO imports.

- [ ] **Step 4: Compile (implementation still has old signatures — expected to fail until Task 5)**

Run: `cd RemeLearning && ./mvnw -pl services/english-service -am compile`
Expected: COMPILATION ERROR in `DictationServiceImpl` (doesn't yet implement the new interface methods) - this is expected; Task 5 fixes it. Do not commit yet.

- [ ] **Step 5: Commit once Task 5's `DictationServiceImpl` changes compile together**

(Committed together with Task 5 — see Task 5 Step 8, which stages both this task's and Task 5's files in one commit, since they're two halves of one non-compiling-in-between change.)

---

### Task 5: `DictationServiceImpl` — bug fix, unification, random resolution, translation wiring

**Files:**
- Modify: `RemeLearning/services/english-service/src/main/java/com/remelearning/english/dictation/service/DictationServiceImpl.java`
- Modify: `RemeLearning/services/english-service/src/test/java/com/remelearning/english/dictation/service/DictationServiceImplTest.java`

**Interfaces:**
- Consumes: `DictationDialogueGenerator.generateDialogue(List<String>, String, String, String)` → `DialogueGenerationResult` (Task 2). `DictationSentenceTranslator.translate(List<String>, String)` (Task 3). `GenerateAiPracticeRequest` (Task 4). New mapper columns/method from Task 1.
- Produces: implements the Task 4 interface signatures; constructor gains one new parameter `DictationSentenceTranslator sentenceTranslator` (added right after `dialogueGenerator` to keep related AI-generation collaborators adjacent).

- [ ] **Step 1: Update failing/changed tests in `DictationServiceImplTest`**

Update the constructor wiring in `setUp()`:

```java
	private final DictationDialogueGenerator dialogueGenerator = mock(DictationDialogueGenerator.class);
	private final DictationSentenceTranslator sentenceTranslator = mock(DictationSentenceTranslator.class);
	private final DictationGapEventPublisher gapEventPublisher = mock(DictationGapEventPublisher.class);
	...
	@BeforeEach
	void setUp() {
		service = new DictationServiceImpl(dictationMapper, dictationAnalyzer, dialogueGenerator, sentenceTranslator,
				gapEventPublisher, ttsClient, storageClient, sentenceAlignmentClient, objectMapper, "F1", "en", 8, 3);
	}
```

Add the import `com.remelearning.english.dictation.analyzer.DictationSentenceTranslator` and `com.remelearning.english.dictation.dto.GenerateAiPracticeRequest`.

Replace `generateAiPracticeGeneratesDialogueFromPendingItemsAndReplacesThem`:

```java
	@Test
	void generateAiPracticeGeneratesDialogueFromPendingItemsAndReplacesThem() {
		when(dictationMapper.findPracticeItemsWithoutAudio("user-1")).thenReturn(List.of(
				DictationPracticeItem.builder().id(11L).userId("user-1").sentenceText("Listen and write.").build()));
		when(dialogueGenerator.generateDialogue(eq(List.of("Listen and write.")), isNull(), isNull(), isNull()))
				.thenReturn(new DialogueGenerationResult("Study habits",
						List.of(new DictationDialogueLine("Narrator", "Listen and write it down.", null))));
		simulateGeneratedPracticeItemId(21L);
		when(ttsClient.synthesize(any(TtsRequest.class))).thenReturn(
				TtsAudio.builder().audioBytes(new byte[] { 1, 2, 3 }).mimeType("audio/wav").sampleRate(44100).build());
		when(dictationMapper.findPracticeItemsByUserId("user-1")).thenReturn(List.of(
				DictationPracticeItem.builder().id(21L).userId("user-1").sentenceText("Listen and write it down.")
						.topic("Study habits").storageKey("generated/user-1/21.wav").build()));

		List<DictationPracticeItemDto> items = service.generateAiPractice("user-1", new GenerateAiPracticeRequest());

		ArgumentCaptor<DictationPracticeItem> itemCaptor = ArgumentCaptor.forClass(DictationPracticeItem.class);
		verify(dictationMapper).insertPracticeItem(itemCaptor.capture());
		assertThat(itemCaptor.getValue().getTopic()).isEqualTo("Study habits");
		verify(storageClient).write(eq("generated/user-1/21.wav"), any(), eq(3L));
		verify(dictationMapper).updatePracticeItemStorageKey(21L, "generated/user-1/21.wav");
		verify(dictationMapper).deletePracticeItemsWithoutAudio("user-1");
		assertThat(items).hasSize(1);
		assertThat(items.get(0).getAudioUrl()).isEqualTo("/api/v1/dictation/ai-practice/items/21/audio");
		assertThat(items.get(0).getTopic()).isEqualTo("Study habits");
	}
```

Replace `generateAiPracticeUsesTopMissedWordsWhenNonePending`:

```java
	@Test
	void generateAiPracticeUsesTopMissedWordsWhenNonePending() {
		when(dictationMapper.findPracticeItemsWithoutAudio("user-1")).thenReturn(List.of());
		when(dictationMapper.findTopMissedWords("user-1", 8)).thenReturn(List.of(
				MissWordCount.builder().word("reluctant").missCount(3).build()));
		when(dialogueGenerator.generateDialogue(eq(List.of("reluctant")), isNull(), isNull(), isNull()))
				.thenReturn(new DialogueGenerationResult(null,
						List.of(new DictationDialogueLine("Narrator", "She was reluctant to leave.", null))));
		simulateGeneratedPracticeItemId(21L);
		when(ttsClient.synthesize(any(TtsRequest.class))).thenReturn(
				TtsAudio.builder().audioBytes(new byte[] { 9 }).mimeType("audio/wav").build());
		when(dictationMapper.findPracticeItemsByUserId("user-1")).thenReturn(List.of());

		service.generateAiPractice("user-1", new GenerateAiPracticeRequest());

		verify(dictationMapper).insertPracticeItem(any(DictationPracticeItem.class));
		verify(storageClient).write(eq("generated/user-1/21.wav"), any(), eq(1L));
		verify(dictationMapper, org.mockito.Mockito.never()).deletePracticeItemsWithoutAudio(anyString());
	}
```

Replace `generateAiPracticeAssignsDistinctVoicesPerSpeakerAndMergesAudio`'s stubs and add a new bug-fix assertion (the whole test, updated):

```java
	@Test
	void generateAiPracticeAssignsDistinctVoicesPerSpeakerAndMergesAudioWithSpeakerNameSpoken() throws java.io.IOException {
		when(dictationMapper.findPracticeItemsWithoutAudio("user-1")).thenReturn(List.of());
		when(dictationMapper.findTopMissedWords("user-1", 8)).thenReturn(List.of());
		when(dialogueGenerator.generateDialogue(eq(List.of()), isNull(), isNull(), isNull())).thenReturn(
				new DialogueGenerationResult(null, List.of(
						new DictationDialogueLine("Alex", "Did you see that?", null),
						new DictationDialogueLine("Sam", "Yes, I did.", null))));
		simulateGeneratedPracticeItemId(40L);
		when(ttsClient.synthesize(any(TtsRequest.class)))
				.thenReturn(TtsAudio.builder().audioBytes(buildWav(new byte[] { 1, 2 })).mimeType("audio/wav").build())
				.thenReturn(TtsAudio.builder().audioBytes(buildWav(new byte[] { 3, 4, 5 })).mimeType("audio/wav").build());
		when(dictationMapper.findPracticeItemsByUserId("user-1")).thenReturn(List.of());

		service.generateAiPractice("user-1", new GenerateAiPracticeRequest());

		ArgumentCaptor<TtsRequest> requestCaptor = ArgumentCaptor.forClass(TtsRequest.class);
		verify(ttsClient, org.mockito.Mockito.times(2)).synthesize(requestCaptor.capture());
		List<String> voicesUsed = requestCaptor.getAllValues().stream().map(TtsRequest::getVoice).distinct().toList();
		assertThat(voicesUsed).hasSize(2);
		assertThat(voicesUsed).allMatch(voice -> VOICE_POOL.contains(voice));
		// Bug fix: the TTS audio must speak the exact same "Speaker: text" the learner is graded
		// against, not just the bare line - otherwise the audio never says the name the answer key expects.
		List<String> spokenTexts = requestCaptor.getAllValues().stream().map(TtsRequest::getText).toList();
		assertThat(spokenTexts).containsExactly("Alex: Did you see that?", "Sam: Yes, I did.");

		ArgumentCaptor<InputStream> audioCaptor = ArgumentCaptor.forClass(InputStream.class);
		verify(storageClient).write(eq("generated/user-1/40.wav"), audioCaptor.capture(), eq(49L));
		assertThat(audioCaptor.getValue().readAllBytes()).hasSize(49);

		ArgumentCaptor<DictationPracticeItem> itemCaptor = ArgumentCaptor.forClass(DictationPracticeItem.class);
		verify(dictationMapper).insertPracticeItem(itemCaptor.capture());
		assertThat(itemCaptor.getValue().getSentenceText()).isEqualTo("Alex: Did you see that?\nSam: Yes, I did.");
	}
```

Add a new test asserting "RANDOM" resolution:

```java
	@Test
	void generateAiPracticeResolvesRandomLevelFromTheFixedPoolAndPersistsIt() {
		when(dictationMapper.findPracticeItemsWithoutAudio("user-1")).thenReturn(List.of());
		when(dictationMapper.findTopMissedWords("user-1", 8)).thenReturn(List.of());
		when(dialogueGenerator.generateDialogue(eq(List.of()), any(), isNull(), isNull())).thenReturn(
				new DialogueGenerationResult("Weekend plans", List.of(new DictationDialogueLine("Narrator", "Let's go.", null))));
		simulateGeneratedPracticeItemId(50L);
		when(ttsClient.synthesize(any(TtsRequest.class))).thenReturn(
				TtsAudio.builder().audioBytes(new byte[] { 1 }).mimeType("audio/wav").build());
		when(dictationMapper.findPracticeItemsByUserId("user-1")).thenReturn(List.of());

		GenerateAiPracticeRequest request = new GenerateAiPracticeRequest();
		request.setLevel("RANDOM");
		service.generateAiPractice("user-1", request);

		ArgumentCaptor<String> levelCaptor = ArgumentCaptor.forClass(String.class);
		verify(dialogueGenerator).generateDialogue(eq(List.of()), levelCaptor.capture(), isNull(), isNull());
		assertThat(levelCaptor.getValue()).isIn("A1", "A2", "B1", "B2", "C1");

		ArgumentCaptor<DictationPracticeItem> itemCaptor = ArgumentCaptor.forClass(DictationPracticeItem.class);
		verify(dictationMapper).insertPracticeItem(itemCaptor.capture());
		assertThat(itemCaptor.getValue().getLevel()).isEqualTo(levelCaptor.getValue());
	}

	@Test
	void generateAiPracticeResolvesRandomExamTypeFromFacetsFallingBackWhenEmpty() {
		when(dictationMapper.findPracticeItemsWithoutAudio("user-1")).thenReturn(List.of());
		when(dictationMapper.findTopMissedWords("user-1", 8)).thenReturn(List.of());
		when(dictationMapper.findDistinctExamTypes()).thenReturn(List.of());
		when(dialogueGenerator.generateDialogue(eq(List.of()), isNull(), any(), isNull())).thenReturn(
				new DialogueGenerationResult(null, List.of(new DictationDialogueLine("Narrator", "Let's go.", null))));
		simulateGeneratedPracticeItemId(51L);
		when(ttsClient.synthesize(any(TtsRequest.class))).thenReturn(
				TtsAudio.builder().audioBytes(new byte[] { 1 }).mimeType("audio/wav").build());
		when(dictationMapper.findPracticeItemsByUserId("user-1")).thenReturn(List.of());

		GenerateAiPracticeRequest request = new GenerateAiPracticeRequest();
		request.setExamType("RANDOM");
		service.generateAiPractice("user-1", request);

		ArgumentCaptor<String> examTypeCaptor = ArgumentCaptor.forClass(String.class);
		verify(dialogueGenerator).generateDialogue(eq(List.of()), isNull(), examTypeCaptor.capture(), isNull());
		assertThat(examTypeCaptor.getValue()).isIn("TOEIC", "IELTS", "TOEFL", "General");
	}
```

Replace `generateAiPracticeFromAttemptUsesThatAttemptsMissesAndSynthesizesAudio` (now goes through the dialogue generator, not `dictationAnalyzer.generatePracticeSentences`):

```java
	@Test
	void generateAiPracticeFromAttemptGeneratesADialogueFromThatAttemptsMissesAndSynthesizesAudio() {
		when(dictationMapper.findAttemptDetailByIdAndUserId(500L, "user-1")).thenReturn(
				DictationAttemptDetailRow.builder().attemptId(500L).clipId(42L).build());
		when(dictationMapper.findMissesByAttemptId(500L)).thenReturn(List.of(
				DictationMiss.builder().attemptId(500L).userId("user-1").expectedWord("Reluctant").build(),
				DictationMiss.builder().attemptId(500L).userId("user-1").expectedWord("reluctant").build()));
		when(dialogueGenerator.generateDialogue(eq(List.of("reluctant")), isNull(), isNull(), isNull()))
				.thenReturn(new DialogueGenerationResult("Making excuses",
						List.of(new DictationDialogueLine("Narrator", "She was reluctant to leave.", null))));
		simulateGeneratedPracticeItemId(31L);
		when(ttsClient.synthesize(any(TtsRequest.class))).thenReturn(
				TtsAudio.builder().audioBytes(new byte[] { 4, 5 }).mimeType("audio/wav").build());
		when(dictationMapper.findPracticeItemsByUserId("user-1")).thenReturn(List.of());

		service.generateAiPracticeFromAttempt("user-1", 500L, null);

		// Duplicate/differently-cased misses collapse to one distinct word before generation.
		verify(dialogueGenerator).generateDialogue(List.of("reluctant"), null, null, null);
		verify(dictationAnalyzer, org.mockito.Mockito.never()).generatePracticeSentences(anyList());
		verify(dictationMapper).insertPracticeItem(any(DictationPracticeItem.class));
		verify(storageClient).write(eq("generated/user-1/31.wav"), any(), eq(2L));
	}
```

Update `getAiPracticeDetailSplitsMultiSpeakerDialogueByLine`'s two sibling tests to also cover translation zipping — add:

```java
	@Test
	void getAiPracticeDetailZipsStoredTranslationLinesWithSentences() {
		when(dictationMapper.findPracticeItemById(12L)).thenReturn(
				DictationPracticeItem.builder().id(12L)
						.sentenceText("A: Hi there.\nB: Hello, how are you?")
						.translationText("A: Chào bạn.\nB: Xin chào, bạn khỏe không?")
						.level("B1").examType("TOEIC").topic("Greetings")
						.build());

		DictationPracticeItemDetailDto detail = service.getAiPracticeDetail(12L);

		assertThat(detail.getLevel()).isEqualTo("B1");
		assertThat(detail.getExamType()).isEqualTo("TOEIC");
		assertThat(detail.getTopic()).isEqualTo("Greetings");
		assertThat(detail.getSentences().get(0).getTranslation()).isEqualTo("A: Chào bạn.");
		assertThat(detail.getSentences().get(1).getTranslation()).isEqualTo("B: Xin chào, bạn khỏe không?");
	}

	@Test
	void getAiPracticeDetailLeavesTranslationNullWhenNoneStored() {
		when(dictationMapper.findPracticeItemById(13L)).thenReturn(
				DictationPracticeItem.builder().id(13L).sentenceText("Hi there.").build());

		DictationPracticeItemDetailDto detail = service.getAiPracticeDetail(13L);

		assertThat(detail.getSentences().get(0).getTranslation()).isNull();
	}
```

Update `getClipDetailReturnsScriptAndOrderedSentences` and friends to pass the new `translationLang` param (pass `null` — behavior unchanged when not requesting translation):

```java
		DictationClipDetailDto detail = service.getClipDetail(5L, null);
```
(apply this same `, null` argument addition to every existing `service.getClipDetail(...)` call in the file: `getClipDetailAlignsMissingTimestampsAndPersistsThem`, `getClipDetailSkipsAlignmentWhenAllSentencesAlreadyTimestamped`, `getClipDetailSkipsAlignmentWhenClipHasNoStorageKey`, `getClipDetailDegradesGracefullyWhenAlignmentFails`, `getClipDetailThrowsNotFoundForUnknownClip`.)

Add two new tests for the lazy-translate behavior:

```java
	@Test
	void getClipDetailLazilyTranslatesMissingSentencesWhenVietnameseRequested() {
		when(dictationMapper.findClipById(5L)).thenReturn(DictationClip.builder().id(5L).code("c-1").build());
		when(dictationMapper.findSentencesByClipId(5L)).thenReturn(List.of(
				DictationClipSentence.builder().clipId(5L).seq(1).text("Hi.").startMs(0).endMs(500).build(),
				DictationClipSentence.builder().clipId(5L).seq(2).text("Bye.").startMs(500).endMs(900).build()));
		when(sentenceTranslator.translate(List.of("Hi.", "Bye."), "vi")).thenReturn(List.of("Chào.", "Tạm biệt."));

		DictationClipDetailDto detail = service.getClipDetail(5L, "vi");

		assertThat(detail.getSentences().get(0).getTranslation()).isEqualTo("Chào.");
		assertThat(detail.getSentences().get(1).getTranslation()).isEqualTo("Tạm biệt.");
		verify(dictationMapper).updateSentenceTranslation(5L, 1, "Chào.");
		verify(dictationMapper).updateSentenceTranslation(5L, 2, "Tạm biệt.");
	}

	@Test
	void getClipDetailSkipsTranslationWhenLanguageIsEnglish() {
		when(dictationMapper.findClipById(5L)).thenReturn(DictationClip.builder().id(5L).code("c-1").build());
		when(dictationMapper.findSentencesByClipId(5L)).thenReturn(List.of(
				DictationClipSentence.builder().clipId(5L).seq(1).text("Hi.").startMs(0).endMs(500).build()));

		service.getClipDetail(5L, "en");

		verifyNoInteractions(sentenceTranslator);
	}

	@Test
	void getClipDetailSkipsTranslationWhenAlreadyPresent() {
		when(dictationMapper.findClipById(5L)).thenReturn(DictationClip.builder().id(5L).code("c-1").build());
		when(dictationMapper.findSentencesByClipId(5L)).thenReturn(List.of(
				DictationClipSentence.builder().clipId(5L).seq(1).text("Hi.").startMs(0).endMs(500)
						.translation("Chào.").build()));

		DictationClipDetailDto detail = service.getClipDetail(5L, "vi");

		assertThat(detail.getSentences().get(0).getTranslation()).isEqualTo("Chào.");
		verifyNoInteractions(sentenceTranslator);
	}
```

Add the needed static imports if not already present: `import static org.mockito.ArgumentMatchers.isNull;`.

- [ ] **Step 2: Run to verify the whole file fails to compile against the old `DictationServiceImpl`**

Run: `cd RemeLearning && ./mvnw -pl services/english-service -am test -Dtest=DictationServiceImplTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: COMPILATION ERROR - constructor arity, missing methods/fields.

- [ ] **Step 3: Update `DictationServiceImpl`'s fields, constructor, and constants**

```java
	private static final String CLIP_AUDIO_URL = "/api/v1/dictation/clips/%d/audio";
	private static final String PRACTICE_AUDIO_URL = "/api/v1/dictation/ai-practice/items/%d/audio";
	private static final String GENERATED_KEY = "generated/%s/%d.wav";
	private static final String WEAK_POINT_CATEGORY = "vocabulary";
	private static final String WEAK_POINT_ITEM_PREFIX = "dictation:";
	private static final String RANDOM_FACET = "RANDOM";
	private static final int DEFAULT_LIST_LIMIT = 50;
	// The ten Supertonic preset voices (ai-service's SUPPORTED_VOICES) - randomly assigned one per
	// distinct speaker in a generated AI-practice dialogue.
	private static final List<String> VOICE_POOL = List.of("F1", "F2", "F3", "F4", "F5", "M1", "M2", "M3", "M4", "M5");
	// CEFR levels a learner can pick (or "RANDOM" among) when creating an AI-practice passage.
	private static final List<String> LEVEL_POOL = List.of("A1", "A2", "B1", "B2", "C1");
	// Used only if the library has no exam-type facets yet to randomize among.
	private static final List<String> EXAM_TYPE_FALLBACK = List.of("TOEIC", "IELTS", "TOEFL", "General");

	private final DictationMapper dictationMapper;
	private final DictationAnalyzer dictationAnalyzer;
	private final DictationDialogueGenerator dialogueGenerator;
	private final DictationSentenceTranslator sentenceTranslator;
	private final DictationGapEventPublisher gapEventPublisher;
	private final TtsClient ttsClient;
	private final StorageClient storageClient;
	private final SentenceAlignmentClient sentenceAlignmentClient;
	private final ObjectMapper objectMapper;
	private final String ttsVoice;
	private final String ttsLang;
	private final int missWindow;
	private final int minListensForHint;

	public DictationServiceImpl(
			DictationMapper dictationMapper,
			DictationAnalyzer dictationAnalyzer,
			DictationDialogueGenerator dialogueGenerator,
			DictationSentenceTranslator sentenceTranslator,
			DictationGapEventPublisher gapEventPublisher,
			TtsClient ttsClient,
			StorageClient storageClient,
			SentenceAlignmentClient sentenceAlignmentClient,
			ObjectMapper objectMapper,
			@Value("${dictation.tts.voice:F1}") String ttsVoice,
			@Value("${dictation.tts.lang:en}") String ttsLang,
			@Value("${dictation.ai-practice.miss-window:8}") int missWindow,
			@Value("${dictation.hint.min-listens:3}") int minListensForHint) {
		this.dictationMapper = dictationMapper;
		this.dictationAnalyzer = dictationAnalyzer;
		this.dialogueGenerator = dialogueGenerator;
		this.sentenceTranslator = sentenceTranslator;
		this.gapEventPublisher = gapEventPublisher;
		this.ttsClient = ttsClient;
		this.storageClient = storageClient;
		this.sentenceAlignmentClient = sentenceAlignmentClient;
		this.objectMapper = objectMapper;
		this.ttsVoice = ttsVoice;
		this.ttsLang = ttsLang;
		this.missWindow = missWindow;
		this.minListensForHint = minListensForHint;
	}
```

Add imports: `com.remelearning.english.dictation.analyzer.DialogueGenerationResult`, `com.remelearning.english.dictation.analyzer.DictationSentenceTranslator`, `com.remelearning.english.dictation.dto.GenerateAiPracticeRequest`, `java.util.Random` (or reuse existing `Collections`/`ArrayList` imports already present).

- [ ] **Step 4: Rewrite `getClipDetail` to add translation lazy-fill**

```java
	@Override
	@Transactional
	public DictationClipDetailDto getClipDetail(Long clipId, String translationLang) {
		DictationClip clip = dictationMapper.findClipById(clipId);
		if (clip == null) {
			throw BusinessException.notFound("Dictation clip not found: id=" + clipId);
		}
		List<DictationClipSentence> clipSentences = dictationMapper.findSentencesByClipId(clipId);
		ensureSentencesAligned(clip, clipSentences);
		ensureSentencesTranslated(clip, clipSentences, translationLang);

		List<DictationSentenceDto> sentences = clipSentences.stream()
				.map(this::toSentenceDto)
				.toList();
		return DictationClipDetailDto.builder()
				.clipId(clip.getId())
				.code(clip.getCode())
				.title(clip.getTitle())
				.audioUrl(CLIP_AUDIO_URL.formatted(clip.getId()))
				.scriptText(clip.getScriptText())
				.sentences(sentences)
				.build();
	}
```

- [ ] **Step 5: Add `ensureSentencesTranslated` helper (next to `ensureSentencesAligned`)**

```java
	// Lazily translates any sentence still missing a translation, when the requested language isn't
	// English (the content's own language). Mirrors ensureSentencesAligned's lazy-fill shape: one
	// batched LLM call for the whole clip, persisted per-sentence, with the in-memory list updated so
	// the response reflects them immediately. Skipped entirely when no language was requested, it's
	// English, or nothing is missing.
	private void ensureSentencesTranslated(DictationClip clip, List<DictationClipSentence> sentences, String translationLang) {
		if (translationLang == null || "en".equalsIgnoreCase(translationLang) || sentences.isEmpty()) {
			return;
		}
		boolean anyMissing = sentences.stream().anyMatch(s -> s.getTranslation() == null);
		if (!anyMissing) {
			return;
		}

		List<String> texts = sentences.stream().map(DictationClipSentence::getText).toList();
		List<String> translations = sentenceTranslator.translate(texts, translationLang);
		for (int i = 0; i < sentences.size(); i++) {
			String translation = translations.get(i);
			if (translation == null) {
				continue;
			}
			DictationClipSentence sentence = sentences.get(i);
			dictationMapper.updateSentenceTranslation(clip.getId(), sentence.getSeq(), translation);
			sentence.setTranslation(translation);
		}
	}
```

- [ ] **Step 6: Rewrite `getAiPracticeDetail`, `generateAiPractice`, `generateAiPracticeFromAttempt`, `synthesizeDialoguePracticeItem`, `toPracticeItemDto`, `toSentenceDto`, `splitIntoSentences`**

```java
	// Full detail for one AI-practice item, split into sentences the same way a library clip's script
	// is, so the sentence-mode runner can drive both sections identically. The passage's audio is one
	// merged file with no per-sentence timing, so every sentence's startMs/endMs stays null - the
	// client falls back to its own word-count-share estimate, exactly as it does for a library clip
	// whose AI alignment hasn't run yet. The stored translation (generated once, at creation time) is
	// split the same way and zipped in by index, so it stays 1:1 with sentenceText's own line order.
	@Override
	public DictationPracticeItemDetailDto getAiPracticeDetail(Long practiceItemId) {
		DictationPracticeItem item = dictationMapper.findPracticeItemById(practiceItemId);
		if (item == null) {
			throw BusinessException.notFound("AI-practice item not found: id=" + practiceItemId);
		}
		List<DictationSentenceDto> sentences = splitIntoSentences(item.getSentenceText(), item.getTranslationText());
		return DictationPracticeItemDetailDto.builder()
				.practiceItemId(item.getId())
				.audioUrl(item.getStorageKey() == null ? null : PRACTICE_AUDIO_URL.formatted(item.getId()))
				.scriptText(item.getSentenceText())
				.level(item.getLevel())
				.examType(item.getExamType())
				.topic(item.getTopic())
				.sentences(sentences)
				.build();
	}

	// Builds one AI-generated listening-practice passage: (1) reads whichever practice items are
	// still without audio (or, if none, the learner's recent top-missed words) as the target
	// words/phrases to practice; (2) resolves any "RANDOM" level/examType facet to one concrete value;
	// (3) sends them to the configured LLM (Gemini) to write one passage - a monologue or a
	// multi-speaker dialogue - reusing them naturally, with a topic label and an optional per-line
	// translation; (4) assigns a random Supertonic voice per distinct speaker; (5) synthesizes each
	// line via the TTS AI service and merges the clips into one continuous audio file, replacing
	// whatever pending items existed with this single new one. A failure anywhere in
	// generation/synthesis is logged and swallowed, leaving prior pending items untouched so the next
	// call can retry.
	@Override
	@Transactional
	public List<DictationPracticeItemDto> generateAiPractice(String userId, GenerateAiPracticeRequest request) {
		List<DictationPracticeItem> pending = dictationMapper.findPracticeItemsWithoutAudio(userId);
		List<String> targetPhrases = pending.isEmpty()
				? dictationMapper.findTopMissedWords(userId, missWindow).stream().map(MissWordCount::getWord).toList()
				: pending.stream().map(DictationPracticeItem::getSentenceText).toList();

		String level = resolveLevel(request.getLevel());
		String examType = resolveExamType(request.getExamType());

		try {
			DialogueGenerationResult dialogue = dialogueGenerator.generateDialogue(targetPhrases, level, examType, request.getTranslationLang());
			synthesizeDialoguePracticeItem(userId, dialogue, level, examType);
			if (!pending.isEmpty()) {
				dictationMapper.deletePracticeItemsWithoutAudio(userId);
			}
		} catch (RuntimeException ex) {
			log.warn("Failed to generate AI-practice dialogue for user {}, leaving pending items untouched", userId, ex);
		}
		return getAiPractice(userId);
	}

	// Resolves a level facet: a concrete value passes through unchanged, "RANDOM" picks one from the
	// fixed CEFR pool, and null/blank means no preference (the generator falls back to its own default).
	private String resolveLevel(String requested) {
		if (RANDOM_FACET.equalsIgnoreCase(requested)) {
			return LEVEL_POOL.get(new java.util.Random().nextInt(LEVEL_POOL.size()));
		}
		return requested;
	}

	// Resolves an exam-type facet the same way, randomizing among the library's own distinct exam
	// types (falling back to a small constant list if the library has none yet).
	private String resolveExamType(String requested) {
		if (RANDOM_FACET.equalsIgnoreCase(requested)) {
			List<String> pool = dictationMapper.findDistinctExamTypes();
			List<String> effectivePool = pool.isEmpty() ? EXAM_TYPE_FALLBACK : pool;
			return effectivePool.get(new java.util.Random().nextInt(effectivePool.size()));
		}
		return requested;
	}

	// Generates AI-practice content targeted at one specific past attempt's mistakes (the "Luyện
	// tập với AI" action from a history row): one dialogue/short-passage item via the same generator
	// Path A uses (previously this used a separate rule-based/LLM sentence-by-sentence analyzer,
	// producing many single-sentence items instead of one cohesive passage), synthesizes its audio,
	// and returns the learner's refreshed AI-practice list. No level/examType selector exists for this
	// entry point, so both are left null (the generator's own default applies). Throws not-found if
	// the attempt doesn't exist or belongs to someone else, the same ownership check getAttemptDetail uses.
	@Override
	@Transactional
	public List<DictationPracticeItemDto> generateAiPracticeFromAttempt(String userId, Long attemptId, String translationLang) {
		if (dictationMapper.findAttemptDetailByIdAndUserId(attemptId, userId) == null) {
			throw BusinessException.notFound("Dictation attempt not found: id=" + attemptId);
		}
		List<String> missedWords = dictationMapper.findMissesByAttemptId(attemptId).stream()
				.map(miss -> miss.getExpectedWord().toLowerCase())
				.distinct()
				.toList();
		DialogueGenerationResult dialogue = dialogueGenerator.generateDialogue(missedWords, null, null, translationLang);
		synthesizeDialoguePracticeItem(userId, dialogue, null, null);
		return getAiPractice(userId);
	}
```

Delete `createPracticeItems` and `synthesizeAudio` only if nothing else calls them — check first: `synthesizeAudio` is unused after this change (its only caller was `generateAiPracticeFromAttempt`); `createPracticeItems` is likewise now unused. Remove both dead private methods.

Rewrite `synthesizeDialoguePracticeItem` (the bug fix + new persisted fields):

```java
	// Synthesizes each dialogue line with its assigned speaker voice, merges the resulting WAV
	// clips into one continuous audio file, and persists the whole passage (rendered as
	// "Speaker: line" per turn, or plain text for a single-speaker monologue) as one new practice
	// item, along with its resolved level/examType/topic and (if generated) translation. The TTS
	// audio for each line is synthesized from the EXACT SAME text that gets persisted as the
	// graded/displayed sentence (including any "Speaker: " prefix) - previously the audio spoke only
	// the bare line while the graded text carried the prefix, so multi-speaker audio never said the
	// name the learner was graded against; using one shared `lineText` for both fixes that. Any
	// TTS/storage failure propagates so the caller can leave prior pending items intact.
	private void synthesizeDialoguePracticeItem(String userId, DialogueGenerationResult dialogue, String level, String examType) {
		List<DictationDialogueLine> lines = dialogue.lines();
		Map<String, String> speakerVoices = assignVoicesToSpeakers(lines);
		boolean multiSpeaker = speakerVoices.size() > 1;
		List<byte[]> clips = new ArrayList<>();
		StringBuilder passageText = new StringBuilder();
		StringBuilder translationText = new StringBuilder();
		boolean anyTranslation = false;
		for (DictationDialogueLine line : lines) {
			String lineText = multiSpeaker ? line.speaker() + ": " + line.text() : line.text();
			TtsAudio audio = ttsClient.synthesize(TtsRequest.builder()
					.text(lineText).languageCode(ttsLang).voice(speakerVoices.get(line.speaker())).build());
			clips.add(audio.getAudioBytes());
			if (!passageText.isEmpty()) {
				passageText.append('\n');
				translationText.append('\n');
			}
			passageText.append(lineText);
			if (line.translation() != null) {
				anyTranslation = true;
				translationText.append(multiSpeaker ? line.speaker() + ": " + line.translation() : line.translation());
			}
		}

		DictationPracticeItem item = DictationPracticeItem.builder()
				.userId(userId).sentenceText(passageText.toString()).source("ai-practice")
				.level(level).examType(examType).topic(dialogue.topic())
				.translationText(anyTranslation ? translationText.toString() : null)
				.build();
		dictationMapper.insertPracticeItem(item);

		byte[] mergedAudio = WavAudioMerger.merge(clips);
		String key = GENERATED_KEY.formatted(userId, item.getId());
		storageClient.write(key, new ByteArrayInputStream(mergedAudio), mergedAudio.length);
		dictationMapper.updatePracticeItemStorageKey(item.getId(), key);
	}
```

`assignVoicesToSpeakers` now takes `List<DictationDialogueLine>` the same as before (unchanged body - already parameterized that way, just now called with `lines` extracted from the result).

Update `toPracticeItemDto` and `toSentenceDto`:

```java
	private DictationPracticeItemDto toPracticeItemDto(DictationPracticeItem item) {
		return DictationPracticeItemDto.builder()
				.practiceItemId(item.getId())
				.audioUrl(item.getStorageKey() == null ? null : PRACTICE_AUDIO_URL.formatted(item.getId()))
				.level(item.getLevel())
				.examType(item.getExamType())
				.topic(item.getTopic())
				.build();
	}
```

```java
	private DictationSentenceDto toSentenceDto(DictationClipSentence sentence) {
		return DictationSentenceDto.builder()
				.index(sentence.getSeq())
				.text(sentence.getText())
				.startMs(sentence.getStartMs())
				.endMs(sentence.getEndMs())
				.translation(sentence.getTranslation())
				.build();
	}
```

Update `splitIntoSentences` to accept and zip in an optional translation text:

```java
	// Splits an AI-practice passage into per-sentence chunks for the runner: a multi-speaker dialogue
	// is already one line per turn (see synthesizeDialoguePracticeItem), so a newline split preserves
	// that structure; a single-speaker monologue has no newlines, so this falls back to splitting on
	// sentence-ending punctuation instead. translationText (if present) is split the exact same way and
	// zipped in by index - if its line count doesn't match (defensive; shouldn't happen since both are
	// built from the same loop at generation time), translations are left null rather than misaligned.
	private List<DictationSentenceDto> splitIntoSentences(String passageText, String translationText) {
		List<String> rawSentences = splitPassage(passageText);
		List<String> rawTranslations = translationText == null ? List.of() : splitPassage(translationText);
		boolean translationsAlign = rawTranslations.size() == rawSentences.size();

		List<DictationSentenceDto> sentences = new ArrayList<>();
		for (int i = 0; i < rawSentences.size(); i++) {
			sentences.add(DictationSentenceDto.builder()
					.index(i)
					.text(rawSentences.get(i))
					.startMs(null)
					.endMs(null)
					.translation(translationsAlign ? rawTranslations.get(i) : null)
					.build());
		}
		return sentences;
	}

	// Shared newline-or-punctuation splitting logic used for both the passage and its translation.
	private List<String> splitPassage(String text) {
		List<String> lines = Arrays.stream(text.split("\\r?\\n"))
				.map(String::trim)
				.filter(line -> !line.isEmpty())
				.toList();
		return lines.size() > 1
				? lines
				: Arrays.stream(text.split("(?<=[.!?])\\s+"))
						.map(String::trim)
						.filter(sentence -> !sentence.isEmpty())
						.toList();
	}
```

- [ ] **Step 7: Run tests to verify they pass**

Run: `cd RemeLearning && ./mvnw -pl services/english-service -am test -Dtest=DictationServiceImplTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS, all tests green (old + new).

Run: `cd RemeLearning && ./mvnw -pl services/english-service -am test -Dsurefire.failIfNoSpecifiedTests=false`
Expected: BUILD SUCCESS, entire english-service test suite green.

- [ ] **Step 8: Commit (both Task 4 and Task 5's files, since the interface change only compiles once its implementation exists)**

```bash
git add RemeLearning/services/english-service/src/main/java/com/remelearning/english/dictation/dto/GenerateAiPracticeRequest.java RemeLearning/services/english-service/src/main/java/com/remelearning/english/dictation/service/DictationService.java RemeLearning/services/english-service/src/main/java/com/remelearning/english/dictation/controller/DictationController.java RemeLearning/services/english-service/src/main/java/com/remelearning/english/dictation/service/DictationServiceImpl.java RemeLearning/services/english-service/src/test/java/com/remelearning/english/dictation/service/DictationServiceImplTest.java
git commit -m "fix(dictation): match AI-practice audio to its graded text; add level/examType/topic/translation"
```

---

### Task 6: bff-service — DTOs, client, controller

**Files:**
- Modify: `RemeLearning/services/bff-service/src/main/java/com/remelearning/bff/dto/DictationSentenceDto.java`
- Modify: `RemeLearning/services/bff-service/src/main/java/com/remelearning/bff/dto/DictationPracticeItemDto.java`
- Modify: `RemeLearning/services/bff-service/src/main/java/com/remelearning/bff/dto/DictationPracticeItemDetailDto.java`
- Create: `RemeLearning/services/bff-service/src/main/java/com/remelearning/bff/dto/GenerateAiPracticeRequestDto.java`
- Modify: `RemeLearning/services/bff-service/src/main/java/com/remelearning/bff/client/EnglishServiceClient.java`
- Modify: `RemeLearning/services/bff-service/src/main/java/com/remelearning/bff/controller/LearnerController.java`

**Interfaces:**
- Consumes: english-service's new request/response shapes from Tasks 1/4/5 (proxied verbatim, same field names).
- Produces: `POST /api/v1/learners/{userId}/dictation/ai-practice/generate` now accepts a JSON body; `GET /api/v1/learners/{userId}/dictation/clips/{clipId}` and `POST /api/v1/learners/{userId}/dictation/history/{attemptId}/ai-practice` gain a `translationLang` query param.

- [ ] **Step 1: Update the three mirrored DTOs**

```java
// DictationSentenceDto.java
package com.remelearning.bff.dto;

import lombok.Data;

/** One sentence of a clip's script, with its AI-aligned audio timestamps and translation if available. */
@Data
public class DictationSentenceDto {
	private int index;
	private String text;
	private Integer startMs;
	private Integer endMs;
	private String translation;
}
```

```java
// DictationPracticeItemDto.java
package com.remelearning.bff.dto;

import lombok.Data;

/** One AI-practice item (Supertonic-voiced), proxied from english-service. */
@Data
public class DictationPracticeItemDto {
	private Long practiceItemId;
	private String audioUrl;
	private String level;
	private String examType;
	private String topic;
}
```

```java
// DictationPracticeItemDetailDto.java
package com.remelearning.bff.dto;

import lombok.Data;

import java.util.List;

/** Full detail for one AI-practice item - passage text split into sentences - proxied from english-service. */
@Data
public class DictationPracticeItemDetailDto {
	private Long practiceItemId;
	private String audioUrl;
	private String scriptText;
	private String level;
	private String examType;
	private String topic;
	private List<DictationSentenceDto> sentences;
}
```

- [ ] **Step 2: Create `GenerateAiPracticeRequestDto`**

```java
package com.remelearning.bff.dto;

import lombok.Data;

/** Facets for generating one AI-practice passage; proxied verbatim to english-service. */
@Data
public class GenerateAiPracticeRequestDto {
	private String level;
	private String examType;
	private String translationLang;
}
```

- [ ] **Step 3: Update `EnglishServiceClient`**

Replace `generateAiPractice`:

```java
	/** Triggers (re)generation of a learner's AI-practice audio in english-service, honoring the requested level/examType facets and translation language. */
	public Mono<List<DictationPracticeItemDto>> generateAiPractice(String userId, GenerateAiPracticeRequestDto request) {
		return englishServiceClient.post()
				.uri("/api/v1/dictation/ai-practice/{userId}/generate", userId)
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(request)
				.retrieve()
				.bodyToMono(new ParameterizedTypeReference<ApiResponse<List<DictationPracticeItemDto>>>() {})
				.map(ApiResponse::getData)
				.doOnError(ex -> log.error("Failed to generate AI-practice for userId={}", userId, ex));
	}
```

Replace `generateAiPracticeFromAttempt`:

```java
	/** Triggers AI-practice generation targeted at one specific past attempt's mistakes (the "Luyện tập với AI" history action). */
	public Mono<List<DictationPracticeItemDto>> generateAiPracticeFromAttempt(String userId, Long attemptId, String translationLang) {
		return englishServiceClient.post()
				.uri(uriBuilder -> uriBuilder.path("/api/v1/dictation/history/{userId}/{attemptId}/ai-practice")
						.queryParamIfPresent("translationLang", java.util.Optional.ofNullable(translationLang))
						.build(userId, attemptId))
				.retrieve()
				.bodyToMono(new ParameterizedTypeReference<ApiResponse<List<DictationPracticeItemDto>>>() {})
				.map(ApiResponse::getData)
				.doOnError(ex -> log.error("Failed to generate AI-practice from attempt for userId={}, attemptId={}", userId, attemptId, ex));
	}
```

Replace `getDictationClip`:

```java
	/** Fetches full detail (script + sentences, optionally translated) for one dictation clip, for sentence-mode practice. */
	public Mono<DictationClipDetailDto> getDictationClip(Long clipId, String translationLang) {
		return englishServiceClient.get()
				.uri(uriBuilder -> uriBuilder.path("/api/v1/dictation/clips/{clipId}")
						.queryParamIfPresent("translationLang", java.util.Optional.ofNullable(translationLang))
						.build(clipId))
				.retrieve()
				.bodyToMono(new ParameterizedTypeReference<ApiResponse<DictationClipDetailDto>>() {})
				.map(ApiResponse::getData)
				.doOnError(ex -> log.error("Failed to fetch dictation clip detail for clipId={}", clipId, ex));
	}
```

Add the import `com.remelearning.bff.dto.GenerateAiPracticeRequestDto` alongside the other dto imports.

- [ ] **Step 4: Update `LearnerController`**

Replace `generateAiPractice`:

```java
	@Operation(summary = "Generate AI-practice audio honoring the requested level/exam-type facets (concrete value, \"RANDOM\", or omitted) and translation language; thin proxy to english-service")
	@PostMapping("/{userId}/dictation/ai-practice/generate")
	public Mono<ApiResponse<List<DictationPracticeItemDto>>> generateAiPractice(
			@PathVariable String userId, @RequestBody(required = false) GenerateAiPracticeRequestDto request) {
		return englishServiceClient.generateAiPractice(userId, request == null ? new GenerateAiPracticeRequestDto() : request)
				.map(ApiResponse::ok);
	}
```

Replace `generateAiPracticeFromAttempt`:

```java
	@Operation(summary = "Generate AI-practice audio targeted at one specific past attempt's mistakes (the \"Luyện tập với AI\" history action); thin proxy to english-service")
	@PostMapping("/{userId}/dictation/history/{attemptId}/ai-practice")
	public Mono<ApiResponse<List<DictationPracticeItemDto>>> generateAiPracticeFromAttempt(
			@PathVariable String userId, @PathVariable Long attemptId,
			@RequestParam(required = false) String translationLang) {
		return englishServiceClient.generateAiPracticeFromAttempt(userId, attemptId, translationLang).map(ApiResponse::ok);
	}
```

Replace `getDictationClip`:

```java
	@Operation(summary = "Full detail for one dictation clip - script + split sentences, optionally translated - for sentence-by-sentence practice; thin proxy to english-service")
	@GetMapping("/{userId}/dictation/clips/{clipId}")
	public Mono<ApiResponse<DictationClipDetailDto>> getDictationClip(
			@PathVariable String userId, @PathVariable Long clipId,
			@RequestParam(required = false) String translationLang) {
		return englishServiceClient.getDictationClip(clipId, translationLang).map(ApiResponse::ok);
	}
```

Add the import `com.remelearning.bff.dto.GenerateAiPracticeRequestDto` alongside the other dto imports.

- [ ] **Step 5: Compile + run bff-service's existing tests**

Run: `cd RemeLearning && ./mvnw -pl services/bff-service -am test -Dsurefire.failIfNoSpecifiedTests=false`
Expected: BUILD SUCCESS, all existing tests still pass (no bff-service test currently covers these three methods directly based on the existing `LearnerOverviewServiceTest` pattern, so no test file needs updating here — this is proxy/plumbing code).

- [ ] **Step 6: Commit**

```bash
git add RemeLearning/services/bff-service/src/main/java/com/remelearning/bff/dto/DictationSentenceDto.java RemeLearning/services/bff-service/src/main/java/com/remelearning/bff/dto/DictationPracticeItemDto.java RemeLearning/services/bff-service/src/main/java/com/remelearning/bff/dto/DictationPracticeItemDetailDto.java RemeLearning/services/bff-service/src/main/java/com/remelearning/bff/dto/GenerateAiPracticeRequestDto.java RemeLearning/services/bff-service/src/main/java/com/remelearning/bff/client/EnglishServiceClient.java RemeLearning/services/bff-service/src/main/java/com/remelearning/bff/controller/LearnerController.java
git commit -m "feat(dictation): proxy level/examType/topic/translation facets and fields through bff-service"
```

---

### Task 7: Documentation updates (openapi.yaml, docs/API.md, docs/flow, docs/sequence, READMEs, Business.md)

**Files:**
- Modify: `RemeLearning/services/english-service/openapi.yaml` (paths ~265-291, 418-525, 908-926, 991-1000+)
- Modify: `RemeLearning/services/bff-service/openapi.yaml` (paths ~364-443, schemas ~666-688, 779-797, 862-870+)
- Modify: `docs/API.md` (dictation section 308-474, bff proxy mirror 768-803, summary tables ~1200-1255)
- Modify: `docs/flow/english-service-data-flow.md` (`DictationFlow` subgraph ~72-92, `AiGen` node ~90, data-shape table rows ~244-248)
- Modify: `docs/sequence/English_service/dictation-practice.md` (section 2 lines 124-174 = dialogue generation/synthesis; section 2b lines 176-215 = the from-attempt path being unified; section 2c lines 217-241 = detail endpoint)
- Modify: `docs/sequence/English_service/overview.md` (references at lines 7, 34, 182-184, 249-250)
- Modify: `RemeLearning/services/english-service/README.md` (dictation section 23-50, `LlmDictationDialogueGenerator` mention line 31)
- Modify: `RemeLearning/services/bff-service/README.md` (proxy-listing line 26)
- Modify: `D:\Personal Project\RemeLearning_BA\Business.md` (section 11, lines 248-450; most relevant existing subsection `### 11.7`, lines 428-450)

**Interfaces:**
- Consumes: the final endpoint/DTO shapes from Tasks 1–6 (this task is pure documentation, no code).

- [ ] **Step 1: Update `RemeLearning/services/english-service/openapi.yaml`**

- `GET /dictation/clips/{clipId}` (~265-291): add `translationLang` as an optional query parameter.
- `POST /dictation/ai-practice/{userId}/generate` (~467-488): add a request body schema `GenerateAiPracticeRequest` (`level`, `examType`, `translationLang`, all optional strings).
- `POST /dictation/history/{userId}/{attemptId}/ai-practice` (~418-447): add a `translationLang` optional query parameter.
- Schema `DictationPracticeItem` (~908-913): add `level`, `examType`, `topic` (optional strings).
- Schema `DictationPracticeItemDetail` (~914-926): add `level`, `examType`, `topic` (optional strings) and add `translation` (optional string) to its nested sentence schema.
- Wherever `DictationSentenceDto`'s schema is defined (check near `DictationClipDetail`, ~778-792): add `translation` (optional string).

- [ ] **Step 2: Mirror the same schema/path changes in `RemeLearning/services/bff-service/openapi.yaml`** — `POST /learners/{userId}/dictation/ai-practice/generate` (~397-410), `POST .../history/{attemptId}/ai-practice` (~364-380), `GET /learners/{userId}/dictation/clips/{clipId}` (find its path block near the `DictationClip`/`DictationClipDetail` schemas at ~666-688), schemas `DictationPracticeItem` (~779-784) and `DictationPracticeItemDetail` (~785-797).

- [ ] **Step 3: Update `docs/API.md`** — in the dictation section (308-474): the "ai-practice generate" entry (434-448) gains the request body shape; the "history-attempt ai-practice" entry (461-471) gains the `translationLang` query param; the "clip detail" entry (356) gains `translationLang`. Mirror the same in the bff proxy section (768-803, ai-practice lines 796-803). No mục lục/summary-table structural changes needed (no new endpoints added, only existing ones extended) — the summary table rows at 1239-1242 (english-service) and the bff equivalents at 1200-1214 need their one-line descriptions updated to mention the new facets.

- [ ] **Step 4: Update `docs/flow/english-service-data-flow.md`** — extend the `AiGen` node (~line 90, inside the `DictationFlow` subgraph ~72-92) to show: level/examType resolution (including "RANDOM"), the topic field, and the parallel translation text alongside the passage text. Update the data-shape table rows for `dictation_practice_items` (~244) and `DictationPracticeItemDetailDto` (~245) to list the new columns/fields.

- [ ] **Step 5: Update `docs/sequence/English_service/dictation-practice.md` + `overview.md`** — section 2 (124-174, the main generate flow) gains the level/examType resolution step and the topic/translation fields in the LLM call; section 2b (176-215, the from-attempt path) is rewritten to show it now calls the same `LlmDictationDialogueGenerator` as section 2 instead of `dictationAnalyzer.generatePracticeSentences` (no longer a separate unmerged path — consider merging 2b into 2 with a note on the one difference: no facet selection); section 2c (217-241, the detail endpoint) gains the translation zip step. Update `overview.md`'s summary references (182-184, 249-250) to match.

- [ ] **Step 6: Update `RemeLearning/services/english-service/README.md`** — in the dictation section (23-50), document the new `GenerateAiPracticeRequest` body shape (`level`/`examType`/`translationLang`) and the `translationLang` query parameter on the clip-detail endpoint, and mention the two new domain concepts (topic label, translation) near the existing `LlmDictationDialogueGenerator` mention (line 31).

- [ ] **Step 7: Update `RemeLearning/services/bff-service/README.md`** — expand the one-line dictation proxy mention (line 26) to note it now also proxies the level/examType/translationLang facets.

- [ ] **Step 8: Update `D:\Personal Project\RemeLearning_BA\Business.md`** — extend section 11 (248-450), adding a new subsection after `### 11.7` (428-450) describing in plain Vietnamese business terms: (a) the audio/answer-key bug and its fix, (b) the new topic label, (c) the new level/exam-type-with-random selection when creating an AI-practice passage, (d) the unified single-passage generation from history (no longer many single-sentence items), (e) the new translation shown alongside the dictation hint for both AI-practice and the Library.

- [ ] **Step 9: Commit**

```bash
git add RemeLearning/services/english-service/openapi.yaml RemeLearning/services/bff-service/openapi.yaml docs/API.md docs/flow/english-service-data-flow.md docs/sequence/English_service RemeLearning/services/english-service/README.md RemeLearning/services/bff-service/README.md
git commit -m "docs(dictation): document level/examType/topic/translation facets and the audio/answer-key fix"
```

Commit `Business.md` separately since it lives in a different repo/folder (`RemeLearning_BA`, not gitignored-in-this-repo but genuinely a different git repository — confirm with `git -C "D:\Personal Project\RemeLearning_BA" status` before committing there).

---

### Task 8: Frontend types + API client

**Files:**
- Modify: `RemeLearning_FE/src/types/api.ts`
- Modify: `RemeLearning_FE/src/api/learners.ts`

**Interfaces:**
- Produces: `DictationSentence.translation`, `DictationClip`-adjacent `DictationPracticeItem.level/examType/topic`, `DictationPracticeItemDetail.level/examType/topic`, `GenerateAiPracticeRequest` type, updated `getDictationClip`/`generateAiPractice`/`generateAiPracticeFromAttempt` signatures — consumed by Task 9 (hooks).

- [ ] **Step 1: Update `types/api.ts`**

```ts
/** One sentence of a clip's script, with optional AI-aligned audio timestamps and translation. */
export interface DictationSentence {
  index: number
  text: string
  startMs: number | null
  endMs: number | null
  translation: string | null
}
```

```ts
/** One AI-practice item (Supertonic-voiced); audioUrl null until synthesized. level/examType/topic are
 * null for items generated without an explicit facet selection (e.g. from a history attempt). */
export interface DictationPracticeItem {
  practiceItemId: number
  audioUrl: string | null
  level: string | null
  examType: string | null
  topic: string | null
}
```

```ts
/** Full detail for one AI-practice item - passage text split into sentences for sentence-by-sentence
 * practice, mirroring DictationClipDetail. Sentences never carry AI-aligned timestamps (the passage's
 * audio is one merged file with no per-sentence timing), so SentenceDictationRunner falls back to its
 * own word-count-share estimate the same way it does for an unaligned library clip. */
export interface DictationPracticeItemDetail {
  practiceItemId: number
  audioUrl: string | null
  scriptText: string
  level: string | null
  examType: string | null
  topic: string | null
  sentences: DictationSentence[]
}

/** Facets for generating one AI-practice passage. Each of level/examType may be a concrete value
 * (e.g. "B1", "TOEIC"), the literal "RANDOM" (resolved server-side), or omitted (no preference). */
export interface GenerateAiPracticeRequest {
  level?: string
  examType?: string
  translationLang?: string
}
```

- [ ] **Step 2: Update `api/learners.ts`**

Replace `getDictationClip`:

```ts
// GET /api/v1/learners/{userId}/dictation/clips/{clipId} - full clip detail (script + sentences),
// optionally with a per-sentence translation to translationLang (only "vi" ever returns one - the
// content is always English, so translating to "en" would be a no-op and is skipped server-side).
export async function getDictationClip(
  userId: string,
  clipId: number,
  translationLang?: string
): Promise<DictationClipDetail> {
  const { data } = await apiClient.get<ApiResponse<DictationClipDetail>>(
    `/learners/${userId}/dictation/clips/${clipId}`,
    { params: translationLang ? { translationLang } : undefined }
  )
  return unwrap(data)
}
```

Replace `generateAiPracticeFromAttempt`:

```ts
// POST /api/v1/learners/{userId}/dictation/history/{attemptId}/ai-practice - generate one AI-practice
// dialogue/passage targeted at one specific past attempt's mistakes.
export async function generateAiPracticeFromAttempt(
  userId: string,
  attemptId: number,
  translationLang?: string
): Promise<DictationPracticeItem[]> {
  const { data } = await apiClient.post<ApiResponse<DictationPracticeItem[]>>(
    `/learners/${userId}/dictation/history/${attemptId}/ai-practice`,
    undefined,
    { params: translationLang ? { translationLang } : undefined }
  )
  return unwrap(data)
}
```

Replace `generateAiPractice`:

```ts
// POST /api/v1/learners/{userId}/dictation/ai-practice/generate - synthesize one new AI-practice
// passage honoring the requested level/examType facets (concrete value, "RANDOM", or omitted) and
// translation language.
export async function generateAiPractice(
  userId: string,
  request: GenerateAiPracticeRequest
): Promise<DictationPracticeItem[]> {
  const { data } = await apiClient.post<ApiResponse<DictationPracticeItem[]>>(
    `/learners/${userId}/dictation/ai-practice/generate`,
    request
  )
  return unwrap(data)
}
```

Add `GenerateAiPracticeRequest` to the `import type { ... } from "@/types/api"` block.

- [ ] **Step 3: Type-check**

Run: `cd RemeLearning_FE && npx tsc --noEmit`
Expected: errors only in files not yet updated by later tasks (`hooks.ts`, `DictationPage.tsx`, `DictationAiPracticePage.tsx`, `DictationLessonPage.tsx`) calling `generateAiPractice()`/`generateAiPracticeFromAttempt(userId, attemptId)`/`getDictationClip(userId, clipId)` with the old (now-mismatched) arities - expected until Tasks 9-10 fix those call sites. No errors should appear in `types/api.ts` or `api/learners.ts` themselves.

- [ ] **Step 4: Commit**

```bash
git add RemeLearning_FE/src/types/api.ts RemeLearning_FE/src/api/learners.ts
git commit -m "feat(dictation): add level/examType/topic/translation types and updated API client signatures"
```

---

### Task 9: Frontend hooks

**Files:**
- Modify: `RemeLearning_FE/src/features/dictation/hooks.ts`

**Interfaces:**
- Consumes: Task 8's updated `api/learners.ts` signatures.
- Produces: `useDictationClip(userId, clipId, translationLang?)`, `useGenerateAiPractice(userId)` returning a mutation whose `mutate`/`mutateAsync` now takes a `GenerateAiPracticeRequest`, `useGenerateAiPracticeFromAttempt(userId)` returning a mutation taking `{ attemptId, translationLang? }`.

- [ ] **Step 1: Update `useDictationClip`**

```ts
export function useDictationClip(userId: string, clipId: number | null, translationLang?: string) {
  return useQuery({
    queryKey: ["learner", userId, "dictation", "clips", clipId, translationLang],
    queryFn: () => getDictationClip(userId, clipId as number, translationLang),
    enabled: !!userId && !!clipId,
  })
}
```

- [ ] **Step 2: Update `useGenerateAiPractice`**

```ts
export function useGenerateAiPractice(userId: string) {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (request: GenerateAiPracticeRequest) => generateAiPractice(userId, request),
    onSuccess: (items) => {
      queryClient.setQueryData(["learner", userId, "dictation", "ai-practice"], items)
    },
  })
}
```

- [ ] **Step 3: Update `useGenerateAiPracticeFromAttempt`**

```ts
export function useGenerateAiPracticeFromAttempt(userId: string) {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: ({ attemptId, translationLang }: { attemptId: number; translationLang?: string }) =>
      generateAiPracticeFromAttempt(userId, attemptId, translationLang),
    onSuccess: (items) => {
      queryClient.setQueryData(["learner", userId, "dictation", "ai-practice"], items)
    },
  })
}
```

Add `GenerateAiPracticeRequest` to the `import type { ... } from "@/types/api"` block.

- [ ] **Step 4: Type-check**

Run: `cd RemeLearning_FE && npx tsc --noEmit`
Expected: errors only in `DictationPage.tsx` (still calling `generateFromAttempt.mutate(attemptId, ...)` with the old shape) - fixed in Task 11.

- [ ] **Step 5: Commit**

```bash
git add RemeLearning_FE/src/features/dictation/hooks.ts
git commit -m "feat(dictation): thread level/examType/translationLang facets through the dictation hooks"
```

---

### Task 10: `GenerateAiPracticeDialog` component (new)

**Files:**
- Create: `RemeLearning_FE/src/features/dictation/GenerateAiPracticeDialog.tsx`
- Modify: `RemeLearning_FE/src/i18n/locales/vi.json`
- Modify: `RemeLearning_FE/src/i18n/locales/en.json`

**Interfaces:**
- Consumes: `useDictationFacets` (existing hook, for the `examTypes` list), `useGenerateAiPractice` (Task 9).
- Produces: `<GenerateAiPracticeDialog userId={string} trigger={ReactNode} />` — consumed by Task 11's `AiPracticeSection`.

- [ ] **Step 1: Add the new i18n keys**

In `vi.json`, inside the `"dictation"` object, right after `"aiGenerate"`/`"aiGenerating"` (~line 200-201):

```json
    "aiGenerateDialog": {
      "title": "Tạo bài luyện",
      "levelLabel": "Trình độ",
      "examTypeLabel": "Dạng đề",
      "random": "Ngẫu nhiên",
      "noPreference": "Không chọn (AI tự chọn)",
      "submit": "Tạo bài luyện"
    },
```

In `en.json`, at the same position:

```json
    "aiGenerateDialog": {
      "title": "Create practice",
      "levelLabel": "Level",
      "examTypeLabel": "Exam type",
      "random": "Random",
      "noPreference": "No preference (AI picks)",
      "submit": "Create practice"
    },
```

Also add, next to `"aiBadge"`/`"aiPracticeItemTitle"` in both files, a key for the topic-carrying badge label used in Task 11 — `vi.json`:

```json
    "aiTopicFallback": "Chủ đề chưa đặt tên",
```

`en.json`:

```json
    "aiTopicFallback": "Untitled topic",
```

- [ ] **Step 2: Write the component**

```tsx
import { Wand2 } from "lucide-react"
import { useState, type ReactNode } from "react"
import { useTranslation } from "react-i18next"
import { toast } from "sonner"
import { Button } from "@/components/ui/button"
import {
  Dialog,
  DialogClose,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog"
import { Field, FieldGroup, FieldLabel } from "@/components/ui/field"
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select"
import { useDictationFacets, useGenerateAiPractice } from "@/features/dictation/hooks"
import { ApiError } from "@/lib/http"

const LEVELS = ["A1", "A2", "B1", "B2", "C1"]
const RANDOM = "RANDOM"
const NONE = "NONE"

interface GenerateAiPracticeDialogProps {
  userId: string
  trigger: ReactNode
}

// Dialog shown before generating a new AI-practice passage: lets the learner optionally pick a CEFR
// level and/or exam type to frame it around, each independently defaulting to "no preference" or
// randomizable via its own "Random" option (resolved server-side, not here) - see
// GenerateAiPracticeRequest. Replaces the old one-click "Tạo bài luyện" button that always generated
// with no facets at all.
export function GenerateAiPracticeDialog({ userId, trigger }: GenerateAiPracticeDialogProps) {
  const { t, i18n } = useTranslation()
  const [open, setOpen] = useState(false)
  const [level, setLevel] = useState(NONE)
  const [examType, setExamType] = useState(NONE)
  const { data: facets } = useDictationFacets(userId)
  const generate = useGenerateAiPractice(userId)

  function handleOpenChange(next: boolean) {
    if (generate.isPending) return
    setOpen(next)
  }

  function handleSubmit() {
    generate.mutate(
      {
        level: level === NONE ? undefined : level,
        examType: examType === NONE ? undefined : examType,
        translationLang: i18n.resolvedLanguage,
      },
      {
        onSuccess: () => setOpen(false),
        onError: (error) =>
          toast.error(error instanceof ApiError ? error.message : t("dictation.aiError")),
      }
    )
  }

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogTrigger render={<span />}>{trigger}</DialogTrigger>
      <DialogContent showCloseButton={!generate.isPending}>
        <DialogHeader>
          <DialogTitle>{t("dictation.aiGenerateDialog.title")}</DialogTitle>
        </DialogHeader>

        <FieldGroup>
          <Field>
            <FieldLabel htmlFor="ai-practice-level">{t("dictation.aiGenerateDialog.levelLabel")}</FieldLabel>
            <Select value={level} onValueChange={setLevel} disabled={generate.isPending}>
              <SelectTrigger id="ai-practice-level" className="w-full">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value={NONE}>{t("dictation.aiGenerateDialog.noPreference")}</SelectItem>
                <SelectItem value={RANDOM}>{t("dictation.aiGenerateDialog.random")}</SelectItem>
                {LEVELS.map((lvl) => (
                  <SelectItem key={lvl} value={lvl}>
                    {lvl}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </Field>

          <Field>
            <FieldLabel htmlFor="ai-practice-exam-type">{t("dictation.aiGenerateDialog.examTypeLabel")}</FieldLabel>
            <Select value={examType} onValueChange={setExamType} disabled={generate.isPending}>
              <SelectTrigger id="ai-practice-exam-type" className="w-full">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value={NONE}>{t("dictation.aiGenerateDialog.noPreference")}</SelectItem>
                <SelectItem value={RANDOM}>{t("dictation.aiGenerateDialog.random")}</SelectItem>
                {(facets?.examTypes ?? []).map((type) => (
                  <SelectItem key={type} value={type}>
                    {type}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </Field>
        </FieldGroup>

        <DialogFooter>
          <DialogClose render={<Button type="button" variant="outline" disabled={generate.isPending} />}>
            {t("common.cancel")}
          </DialogClose>
          <Button onClick={handleSubmit} loading={generate.isPending}>
            <Wand2 /> {t("dictation.aiGenerateDialog.submit")}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
```

- [ ] **Step 4: Type-check**

Run: `cd RemeLearning_FE && npx tsc --noEmit`
Expected: no new errors introduced by this file (verify `Field`/`FieldGroup`/`FieldLabel` and `Button`'s `loading` prop match `UploadRecordingDialog.tsx`'s existing usage — if `common.cancel` key doesn't already exist, check `vi.json`/`en.json`'s top-level `"common"` block first; it already does, per `UploadRecordingDialog.tsx`'s use of `t("common.cancel")` and `t("common.upload")`).

- [ ] **Step 5: Commit**

```bash
git add RemeLearning_FE/src/features/dictation/GenerateAiPracticeDialog.tsx RemeLearning_FE/src/i18n/locales/vi.json RemeLearning_FE/src/i18n/locales/en.json
git commit -m "feat(dictation): add a level/exam-type/random creation dialog for AI practice"
```

---

### Task 11: Wire the dialog + badges into `DictationPage.tsx`, `DictationAiPracticePage.tsx`, `DictationLessonPage.tsx`

**Files:**
- Modify: `RemeLearning_FE/src/features/dictation/DictationPage.tsx`
- Modify: `RemeLearning_FE/src/features/dictation/DictationAiPracticePage.tsx`
- Modify: `RemeLearning_FE/src/features/dictation/DictationLessonPage.tsx`

**Interfaces:**
- Consumes: `GenerateAiPracticeDialog` (Task 10), updated hooks (Task 9).

- [ ] **Step 1: Replace `AiPracticeSection` in `DictationPage.tsx`**

Remove the `handleGenerate` function and the plain `<Button>` (lines 317-333), replacing the button with the dialog, and add level/examType/topic badges to each card:

```tsx
function AiPracticeSection({
  userId,
  onGoToLibrary,
}: {
  userId: string
  onGoToLibrary: () => void
}) {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const { data: items, isLoading } = useAiPractice(userId)

  const playable = (items ?? []).filter((item) => item.audioUrl)

  return (
    <div className="flex w-full max-w-2xl flex-col gap-5">
      <div className="flex items-start gap-3 rounded-2xl bg-muted/50 p-4">
        <Sparkles className="mt-0.5 size-5 shrink-0 text-accent-warm" />
        <p className="text-sm text-muted-foreground">{t("dictation.aiIntro")}</p>
      </div>

      <GenerateAiPracticeDialog
        userId={userId}
        trigger={
          <Button className="h-12 w-full">
            <Wand2 /> {t("dictation.aiGenerate")}
          </Button>
        }
      />

      {isLoading && (
        <div aria-busy="true" aria-live="polite" className="grid grid-cols-1 gap-3 sm:grid-cols-2">
          {Array.from({ length: 2 }).map((_, i) => (
            <Skeleton key={i} className="h-24 w-full rounded-2xl" />
          ))}
        </div>
      )}

      {!isLoading && playable.length === 0 && (
        <EmptyState
          icon={<Sparkles className="size-6" />}
          title={t("dictation.aiEmpty")}
          action={
            <Button variant="outline" className="h-12" onClick={onGoToLibrary}>
              <Headphones /> {t("dictation.goToLibrary")}
            </Button>
          }
        />
      )}

      {!isLoading && playable.length > 0 && (
        <RevealGroup className="grid grid-cols-1 gap-3 sm:grid-cols-2">
          {playable.map((item, index) => (
            <RevealItem key={item.practiceItemId}>
              <button
                type="button"
                onClick={() => navigate(`/dictation/ai-practice/${item.practiceItemId}`)}
                className="group flex w-full items-center gap-3 rounded-2xl bg-card p-5 text-left shadow-clay transition hover:shadow-clay-warm"
              >
                <div className="flex size-11 shrink-0 items-center justify-center rounded-xl bg-accent-warm/10">
                  <Sparkles className="size-5 text-accent-warm transition-transform duration-150 ease-out group-hover:scale-110 motion-reduce:transform-none" />
                </div>
                <div className="min-w-0 flex-1">
                  <p className="truncate font-medium">
                    {item.topic ?? t("dictation.aiPracticeItemTitle", { index: index + 1 })}
                  </p>
                  <p className="flex flex-wrap items-center gap-1 text-sm text-muted-foreground">
                    <span>{t("dictation.aiBadge")}</span>
                    {[item.level, item.examType].filter(Boolean).map((facet) => (
                      <Badge key={facet} variant="outline" className="text-[0.65rem]">
                        {facet}
                      </Badge>
                    ))}
                  </p>
                </div>
              </button>
            </RevealItem>
          ))}
        </RevealGroup>
      )}
    </div>
  )
}
```

Update the top-of-file imports: remove `useGenerateAiPractice` from the `@/features/dictation/hooks` import (no longer called directly in this file), remove the now-unused `toast`/`ApiError` imports if `AiPracticeSection` was their only user (check `HistorySection` below still uses `toast`/`ApiError` — it does, for `handlePracticeWithAi`'s `onError`, so keep those two imports), and add:

```tsx
import { GenerateAiPracticeDialog } from "@/features/dictation/GenerateAiPracticeDialog"
```

- [ ] **Step 2: Update `HistorySection`'s `handlePracticeWithAi` to pass a translation language**

```tsx
  function handlePracticeWithAi(attemptId: number) {
    generateFromAttempt.mutate(
      { attemptId, translationLang: i18n.resolvedLanguage },
      {
        onSuccess: () => onSwitchTab("ai"),
        onError: (error) =>
          toast.error(error instanceof ApiError ? error.message : t("dictation.aiError")),
      }
    )
  }
```

Destructure `i18n` alongside `t` at the top of `HistorySection`: `const { t, i18n } = useTranslation()`. Also update the disabled check further down, which compared `generateFromAttempt.variables` (previously a bare number) to `entry.attemptId`:

```tsx
                      disabled={generateFromAttempt.isPending && generateFromAttempt.variables?.attemptId === entry.attemptId}
```

- [ ] **Step 3: Update `DictationAiPracticePage.tsx`'s header to show the topic**

Replace the thin header block (lines 97-102) to show the item's topic when present, falling back to the existing back button alone:

```tsx
      <div className="flex items-center justify-between gap-4">
        <Button variant="ghost" className="h-9 w-fit" onClick={goToAiTab}>
          <ChevronLeft /> {t("dictation.lessons.back")}
        </Button>
        {item?.topic && <Badge variant="secondary">{item.topic}</Badge>}
      </div>
```

Add the import `import { Badge } from "@/components/ui/badge"`.

- [ ] **Step 4: Update `DictationLessonPage.tsx` and `DictationAiPracticePage.tsx` to pass `translationLang`**

In `DictationLessonPage.tsx`, update the `useDictationClip` call:

```tsx
  const { data: clip, isLoading: clipLoading, isError: clipError, refetch: refetchClip } =
    useDictationClip(userId, clipId, useTranslation().i18n.resolvedLanguage)
```

(Simpler: since `useTranslation()` is already called at the top for `t`, destructure `i18n` there instead of calling `useTranslation()` twice — update the existing `const { t } = useTranslation()` line to `const { t, i18n } = useTranslation()` and pass `i18n.resolvedLanguage` as the third arg.)

`DictationAiPracticePage.tsx` needs no such change — its translation was baked in at generation time (Task 5), not requested per-read.

- [ ] **Step 5: Type-check + manual smoke test**

Run: `cd RemeLearning_FE && npx tsc --noEmit`
Expected: no errors.

Run the app per the project's `run` skill (start bff-service + english-service + FE dev server) and manually verify: opening the AI tab shows the new dialog on click, generating with a level/examType selected produces a card showing those badges + a topic title, and the Library lesson page still loads (translation absent under English UI, present under Vietnamese UI once Task 12 renders it).

- [ ] **Step 6: Commit**

```bash
git add RemeLearning_FE/src/features/dictation/DictationPage.tsx RemeLearning_FE/src/features/dictation/DictationAiPracticePage.tsx RemeLearning_FE/src/features/dictation/DictationLessonPage.tsx
git commit -m "feat(dictation): wire the level/exam-type creation dialog and topic/level badges into the UI"
```

---

### Task 12: `SentenceDictationRunner` — show the translation hint

**Files:**
- Modify: `RemeLearning_FE/src/features/dictation/SentenceDictationRunner.tsx`
- Modify: `RemeLearning_FE/src/i18n/locales/vi.json`
- Modify: `RemeLearning_FE/src/i18n/locales/en.json`

**Interfaces:**
- Consumes: `DictationSentence.translation` (Task 8).

- [ ] **Step 1: Add the i18n key**

`vi.json`, next to `"hintHide"` (~line 240):

```json
    "translationLabel": "Bản dịch",
```

`en.json`, same position:

```json
    "translationLabel": "Translation",
```

- [ ] **Step 2: Render the translation under the existing hint reveal**

Replace the hint-reveal block (lines 413-423) to append the translation, when present, right after the sentence text:

```tsx
        {/* Hint reveal — slides down when toggled. Shows the answer text, plus its translation
            (if one was generated for this sentence) right underneath. */}
        {runner.hintRevealed && (
          <motion.div
            className="flex flex-col gap-1.5 rounded-2xl bg-muted/50 p-4 text-sm leading-relaxed text-muted-foreground"
            initial={{ opacity: 0, height: 0 }}
            animate={{ opacity: 1, height: "auto" }}
            transition={{ duration: reduceMotion ? 0 : 0.2, ease: EASE_OUT }}
          >
            <p>{currentSentence.text}</p>
            {currentSentence.translation && (
              <p className="border-t border-border/60 pt-1.5 text-xs italic">
                <span className="not-italic font-medium">{t("dictation.translationLabel")}: </span>
                {currentSentence.translation}
              </p>
            )}
          </motion.div>
        )}
```

- [ ] **Step 3: Type-check + manual smoke test**

Run: `cd RemeLearning_FE && npx tsc --noEmit`
Expected: no errors.

Manually verify (with the FE dev server pointed at the running bff-service + english-service): switching the app language to Vietnamese, opening a library lesson, listening enough times to unlock the hint, and revealing it shows both the English sentence and its Vietnamese translation; switching back to English shows no translation line (since `translation` is null when `translationLang="en"`).

- [ ] **Step 4: Commit**

```bash
git add RemeLearning_FE/src/features/dictation/SentenceDictationRunner.tsx RemeLearning_FE/src/i18n/locales/vi.json RemeLearning_FE/src/i18n/locales/en.json
git commit -m "feat(dictation): show the per-sentence translation alongside the dictation hint"
```

---

## Post-plan verification checklist

- [ ] `cd RemeLearning && ./mvnw -pl services/english-service -am test -Dsurefire.failIfNoSpecifiedTests=false` — green.
- [ ] `cd RemeLearning && ./mvnw -pl services/bff-service -am test -Dsurefire.failIfNoSpecifiedTests=false` — green.
- [ ] `cd RemeLearning_FE && npx tsc --noEmit` — no errors.
- [ ] Manual smoke test per Tasks 11-12's Step 5 (create AI practice with a chosen level/random exam type; verify the card shows a topic + badges; open it and confirm the audio speaks the same speaker-prefixed text the sentence input is graded against; open a Library lesson under Vietnamese UI and confirm the translation hint appears).
- [ ] `/code-standards` skill re-run over all touched Java files per `CLAUDE.md`.
