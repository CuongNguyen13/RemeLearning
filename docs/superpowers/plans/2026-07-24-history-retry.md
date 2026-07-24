# History Retry (AI/Library) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Bring dictation's "Làm lại" (reopen library content) + "Luyện tập với AI" (regenerate practice targeting a specific past attempt's mistakes) history-row actions to Grammar, Listening, and Speaking — for both their "học thường" (learn) and "Thư viện" (library) attempt sources, merged into one history list per domain.

**Architecture:** Two small schema additions (per-question answer persistence for Listening Library, weak-phoneme persistence for Speaking Library — Grammar already has enough per-attempt mistake detail in both its learn and library tables). Six new "generate practice from attempt" endpoints (one per domain × source), each mirroring `DictationServiceImpl.generateAiPracticeFromAttempt`'s shape: verify ownership → read that one attempt/session's mistake detail → call the domain's existing LLM generator with a mistake-focused prompt → persist a new practice item into the domain's existing "learn" practice-item bank → return the refreshed practice-item list. Three new merged-history endpoints (one per domain) that combine learn-attempt history + library-attempt history into one time-sorted list tagged by `source`. `bff-service` proxies all 9 new endpoints. FE adds the two retry buttons to each domain's history list, gated exactly like dictation's (`source === "LIBRARY"` gates the "Làm lại" button; "Luyện tập với AI" is unconditional).

**Tech Stack:** Spring Boot 4.1 / Java 21, MyBatis, Flyway, PostgreSQL (english-service); WebFlux (bff-service); React + TypeScript + react-query (RemeLearning_FE).

## Global Constraints

- Java tests: plain JUnit 5 + AssertJ + `Mockito.mock(...)` — no `@Mock`, no `@ExtendWith(MockitoExtension.class)`, no `@SpringBootTest`, no integration tests.
- Comment non-trivial code blocks/methods in both Java and TS (CLAUDE.md convention for this repo).
- Every new/changed REST endpoint requires: `openapi.yaml` (owning service), `docs/API.md`, a `docs/sequence/English_service/*.md` (or `Bff_service` for the bff proxy task) sequence diagram, `docs/flow/english-service-data-flow.md`, and the service's own `README.md` — all in the same commit as the code.
- `Business.md` (`D:\Personal Project\RemeLearning_BA\Business.md`, separate folder with no `.git` of its own) gets edited on disk, not committed there — report this rather than treating it as a failure.
- Flyway: pick the next unused `V__` version by checking `RemeLearning/services/english-service/src/main/resources/db/migration/`.
- `userId` is `String` throughout the listening/speaking library code from the prior plan (`2026-07-23-listening-speaking-library`) — match that type in every new method touching those packages. Grammar's existing code uses `String userId` too (confirm before writing).
- FE: match `src/api/learners.ts`'s real `apiClient.get/post` + `unwrap(data)` convention (confirmed real in the prior FE plan) — do not invent `apiGet`/`apiPost`.

---

## Task 1: Listening Library — persist per-question answers

**Files:**
- Create: `RemeLearning/services/english-service/src/main/resources/db/migration/V<next>__listening_library_answers.sql`
- Create: `RemeLearning/services/english-service/src/main/java/com/remelearning/english/listening/library/domain/ListeningLibraryAttemptAnswer.java`
- Create: `RemeLearning/services/english-service/src/main/java/com/remelearning/english/listening/library/mapper/ListeningLibraryAttemptAnswerMapper.java`
- Create: `RemeLearning/services/english-service/src/main/resources/mapper/listening/library/ListeningLibraryAttemptAnswerMapper.xml`
- Modify: `RemeLearning/services/english-service/src/main/java/com/remelearning/english/listening/library/service/ListeningLibraryServiceImpl.java`
- Modify: `RemeLearning/services/english-service/src/test/java/com/remelearning/english/listening/library/service/ListeningLibraryServiceImplTest.java`

**Interfaces:**
- Produces: `ListeningLibraryAttemptAnswerMapper.insert(ListeningLibraryAttemptAnswer a)`,
  `.findByAttemptId(Long attemptId): List<ListeningLibraryAttemptAnswer>` — consumed by
  Task 4's generator.

- [ ] **Step 1: Read `ListeningLibraryServiceImpl.submitAnswers` in full**

File: `RemeLearning/services/english-service/src/main/java/com/remelearning/english/listening/library/service/ListeningLibraryServiceImpl.java`.
Find the loop that computes `correctCount` by comparing `req.getAnswers()`
against `correctByQuestionId` — this is where per-question persistence gets
added, without changing the method's existing response shape.

- [ ] **Step 2: Write the migration**

```sql
-- V<next>__listening_library_answers.sql
-- Per-question answer detail for a listening library attempt, needed to
-- later regenerate AI practice targeting exactly what was missed.
CREATE TABLE listening_library_attempt_answers (
    id BIGSERIAL PRIMARY KEY,
    attempt_id BIGINT NOT NULL REFERENCES listening_library_attempts(id),
    question_id BIGINT NOT NULL REFERENCES listening_library_questions(id),
    selected_option VARCHAR(8) NOT NULL,
    correct_option VARCHAR(8) NOT NULL,
    is_correct BOOLEAN NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_listening_library_attempt_answers_attempt ON listening_library_attempt_answers(attempt_id);
```

- [ ] **Step 3: Write the domain class and mapper**

```java
package com.remelearning.english.listening.library.domain;

import lombok.Data;
import java.time.OffsetDateTime;

@Data
public class ListeningLibraryAttemptAnswer {
    private Long id;
    private Long attemptId;
    private Long questionId;
    private String selectedOption;
    private String correctOption;
    private Boolean isCorrect;
    private OffsetDateTime createdAt;
}
```

```java
package com.remelearning.english.listening.library.mapper;

import com.remelearning.english.listening.library.domain.ListeningLibraryAttemptAnswer;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface ListeningLibraryAttemptAnswerMapper {
    void insert(ListeningLibraryAttemptAnswer answer);
    List<ListeningLibraryAttemptAnswer> findByAttemptId(@Param("attemptId") Long attemptId);
}
```

Write the matching XML, same style as sibling mappers in
`RemeLearning/services/english-service/src/main/resources/mapper/listening/library/`
(read `ListeningLibraryAttemptMapper.xml` for the exact `insert`/`select`
shape to copy).

- [ ] **Step 4: Write the failing test, then wire persistence into `submitAnswers`**

Add a test asserting `attemptAnswerMapper.insert(...)` is called once per
submitted answer, with correct `isCorrect` values, alongside the existing
`submitAnswersComputesScoreMarksPassedAndUnlocksNextTopicAboveThreshold`
test (extend it or add a new one — read the existing test first to match
its mock-setup style). Then inject `ListeningLibraryAttemptAnswerMapper`
into the constructor and, inside the existing per-answer loop, add:

```java
ListeningLibraryAttemptAnswer answerRow = new ListeningLibraryAttemptAnswer();
answerRow.setAttemptId(attempt.getId()); // set after attemptMapper.insert(attempt) has assigned the id
answerRow.setQuestionId(answer.questionId());
answerRow.setSelectedOption(answer.selectedOption());
answerRow.setCorrectOption(correctByQuestionId.get(answer.questionId()));
answerRow.setIsCorrect(Objects.equals(correctByQuestionId.get(answer.questionId()), answer.selectedOption()));
attemptAnswerMapper.insert(answerRow);
```

Note: `attempt.getId()` is only populated after `attemptMapper.insert(attempt)`
runs (MyBatis `useGeneratedKeys`) — move this new per-answer persistence
loop to AFTER that insert call, not inside the original scoring loop (which
runs before the attempt is persisted). Restructure by first accumulating
`correctCount`/`score` in the existing loop, then inserting the attempt,
then looping over `req.getAnswers()` a second time to persist each answer
row now that `attemptId` is known — do not try to write the answer id before
the attempt exists.

- [ ] **Step 5: Run tests**

Run: `cd RemeLearning && ./mvnw -pl services/english-service -am test -Dtest=ListeningLibraryServiceImplTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add RemeLearning/services/english-service/src/main/resources/db/migration RemeLearning/services/english-service/src/main/java/com/remelearning/english/listening/library/domain/ListeningLibraryAttemptAnswer.java RemeLearning/services/english-service/src/main/java/com/remelearning/english/listening/library/mapper/ListeningLibraryAttemptAnswerMapper.java RemeLearning/services/english-service/src/main/resources/mapper/listening/library/ListeningLibraryAttemptAnswerMapper.xml RemeLearning/services/english-service/src/main/java/com/remelearning/english/listening/library/service/ListeningLibraryServiceImpl.java RemeLearning/services/english-service/src/test/java/com/remelearning/english/listening/library/service/ListeningLibraryServiceImplTest.java
git commit -m "feat(english-service): persist per-question answers for listening library attempts"
```

---

## Task 2: Speaking Library — persist weak phonemes per attempt

**Files:**
- Create: `RemeLearning/services/english-service/src/main/resources/db/migration/V<next+1>__speaking_library_weak_phonemes.sql`
- Modify: `RemeLearning/services/english-service/src/main/java/com/remelearning/english/speaking/library/domain/SpeakingLibraryAttempt.java`
- Modify: `RemeLearning/services/english-service/src/main/resources/mapper/speaking/library/SpeakingLibraryAttemptMapper.xml`
- Modify: `RemeLearning/services/english-service/src/main/java/com/remelearning/english/speaking/library/service/SpeakingLibraryServiceImpl.java`
- Modify: `RemeLearning/services/english-service/src/test/java/com/remelearning/english/speaking/library/service/SpeakingLibraryServiceImplTest.java`

**Interfaces:**
- Produces: `SpeakingLibraryAttempt.getWeakPhonemesJson(): String` (nullable) —
  consumed by Task 5's generator.

- [ ] **Step 1: Read `SpeakingAttemptDetailRow`/`SpeakingLearnServiceImpl`'s existing weak-phoneme analysis in full**

Path: `RemeLearning/services/english-service/src/main/java/com/remelearning/english/speaking/domain/SpeakingAttemptDetailRow.java`
and whichever service method populates `weakPhonemesJson` for the "học
thường" flow (`SpeakingLearnServiceImpl.submit` or similar) — find the exact
logic that decides which phonemes count as "weak" (a threshold on
`PhonemePronunciationScore.score()`, presumably) so Task 2 reuses the same
threshold/shape, not a new one.

- [ ] **Step 2: Write the migration**

```sql
-- V<next+1>__speaking_library_weak_phonemes.sql
ALTER TABLE speaking_library_attempts ADD COLUMN weak_phonemes_json TEXT;
```

- [ ] **Step 3: Add the field to `SpeakingLibraryAttempt` and the mapper XML**

Add `private String weakPhonemesJson;` (with Lombok `@Data`'s generated
getter/setter) to the domain class, and add `weak_phonemes_json` to the
`insert`/`select` column lists in `SpeakingLibraryAttemptMapper.xml` (read
the file first to find every place columns are listed).

- [ ] **Step 4: Write the failing test, then wire the analysis into `submitSentenceAttempt`**

Add a test asserting the persisted `SpeakingLibraryAttempt.weakPhonemesJson`
contains the expected phoneme list when the mocked
`PronunciationScoringClient` response includes phonemes below the reused
threshold (mirror the existing `submitSentenceAttempt` test's mock setup).
Then, in `SpeakingLibraryServiceImpl.submitSentenceAttempt`, after scoring,
compute the weak-phoneme list using the same logic identified in Step 1
(extract it into a small shared method if `SpeakingLearnServiceImpl`'s logic
isn't already a reusable static/utility — check first; if it's private and
not easily extracted, duplicate the specific filter expression only, not the
whole method, and comment why), serialize to JSON, and set it on the
`SpeakingLibraryAttempt` before calling `attemptMapper.insert(...)`.

- [ ] **Step 5: Run tests**

Run: `cd RemeLearning && ./mvnw -pl services/english-service -am test -Dtest=SpeakingLibraryServiceImplTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add RemeLearning/services/english-service/src/main/resources/db/migration RemeLearning/services/english-service/src/main/java/com/remelearning/english/speaking/library/domain/SpeakingLibraryAttempt.java RemeLearning/services/english-service/src/main/resources/mapper/speaking/library/SpeakingLibraryAttemptMapper.xml RemeLearning/services/english-service/src/main/java/com/remelearning/english/speaking/library/service/SpeakingLibraryServiceImpl.java RemeLearning/services/english-service/src/test/java/com/remelearning/english/speaking/library/service/SpeakingLibraryServiceImplTest.java
git commit -m "feat(english-service): persist weak phonemes for speaking library attempts"
```

---

## Task 3: Grammar — generate-from-attempt (learn + library) generator + endpoints

**Files:**
- Create: `RemeLearning/services/english-service/src/main/java/com/remelearning/english/grammar/learn/generator/GrammarMistakeAnalyzer.java`
- Modify: `RemeLearning/services/english-service/src/main/java/com/remelearning/english/grammar/learn/service/GrammarLearnServiceImpl.java` (add `generatePracticeFromAttempt`)
- Modify: `RemeLearning/services/english-service/src/main/java/com/remelearning/english/grammar/learn/service/GrammarLearnService.java` (interface)
- Modify: `RemeLearning/services/english-service/src/main/java/com/remelearning/english/grammar/learn/controller/GrammarLearnController.java`
- Modify: `RemeLearning/services/english-service/src/main/java/com/remelearning/english/grammar/library/service/GrammarLibraryServiceImpl.java` (add `generatePracticeFromSession`)
- Modify: `RemeLearning/services/english-service/src/main/java/com/remelearning/english/grammar/library/service/GrammarLibraryService.java` (interface)
- Modify: `RemeLearning/services/english-service/src/main/java/com/remelearning/english/grammar/library/controller/GrammarLibraryController.java`
- Test: `RemeLearning/services/english-service/src/test/java/com/remelearning/english/grammar/learn/generator/GrammarMistakeAnalyzerTest.java`
- Test: modify `GrammarLearnServiceImplTest.java` and `GrammarLibraryServiceImplTest.java`

**Interfaces:**
- Produces: `GrammarMistakeAnalyzer.extractMissedRules(String itemsJson, String answersJson): List<String>`
  (pure function: diffs generated items vs. submitted answers, returns the
  distinct rule/topic tags of every wrong answer) and an overload/sibling
  method for the library shape:
  `extractMissedRulesFromSession(String questionsJson, List<GrammarLibrarySessionAnswer> answers): List<String>`.
  `GrammarLearnService.generatePracticeFromAttempt(String userId, Long attemptId): List<GrammarPracticeItemDto>`.
  `GrammarLibraryService.generatePracticeFromSession(String userId, Long sessionId): List<GrammarPracticeItemDto>`
  (returns the same DTO shape as the learn flow's practice list, since the
  regenerated content lands in the same `grammar_practice_items` bank
  either way).

- [ ] **Step 1: Read `DictationServiceImpl.generateAiPracticeFromAttempt` in full**

This is the direct behavioral template: ownership check → read that
attempt's mistakes → call generator → persist new item → return refreshed
list.

- [ ] **Step 2: Read `GrammarAttemptDetailRow`/`GrammarPracticeAttempt` and `GrammarLibrarySessionAnswer`/`GrammarLibrarySessionQuestion` in full**

Confirm exact field names for `itemsJson`/`answersJson` (learn) and
`questionRef`/`submittedAnswer`/`correct` + the session's `questionsJson`
(library) — the brief's method signatures above are illustrative; use the
real field names/types found here.

- [ ] **Step 3: Read `LlmGrammarPracticeGenerator`/`GeneratedGrammarPractice` in full**

This is the existing "học thường" generator, called normally with a
level/topic/examType. Task 3 needs a **mistake-focused** variant: find
whether it already accepts a "target these specific rules" parameter, or
whether a new overload/prompt-building method is needed (most likely the
latter — add a method like
`generateTargetingRules(String level, List<String> ruleTags): GeneratedGrammarPractice`
to the existing generator class, reusing its LLM-calling infrastructure).

- [ ] **Step 4: Write `GrammarMistakeAnalyzer` with failing tests first**

```java
package com.remelearning.english.grammar.learn.generator;

// Pure diff logic: given a generated item set and the learner's submitted
// answers (or a library session's per-question answer log), returns the
// distinct rule/topic tags of every question the learner got wrong.
public final class GrammarMistakeAnalyzer {
    private GrammarMistakeAnalyzer() { }

    public static List<String> extractMissedRules(String itemsJson, String answersJson) {
        // parse itemsJson into List<GrammarQuestionItem> (reuse whatever
        // type GrammarAttemptDetailRow's existing deserialization uses),
        // parse answersJson the same way, zip by index, collect distinct
        // .getRuleTag() (or equivalent field name confirmed in Step 2) for
        // every index where submitted != correct.
    }

    public static List<String> extractMissedRulesFromSession(
            String questionsJson, List<GrammarLibrarySessionAnswer> answers) {
        // parse questionsJson into List<GrammarLibrarySessionQuestion>,
        // index by questionRef, collect distinct rule tags for every
        // answer where !answer.isCorrect().
    }
}
```

Write tests first covering: all-correct → empty list; some-wrong → correct
distinct rule tags; duplicate rule across multiple wrong questions →
deduplicated. Use real fixture JSON matching the actual serialization shape
confirmed in Step 2 (do not guess the JSON field names — read a real
persisted example via the domain class's Jackson annotations, or write the
fixture by first constructing the real DTOs and serializing them with the
same `ObjectMapper` this codebase uses, to guarantee shape correctness).

- [ ] **Step 5: Run the analyzer test**

Run: `cd RemeLearning && ./mvnw -pl services/english-service -am test -Dtest=GrammarMistakeAnalyzerTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS

- [ ] **Step 6: Add `generatePracticeFromAttempt` to `GrammarLearnServiceImpl`, with failing test first**

```java
@Override
@Transactional
public List<GrammarPracticeItemDto> generatePracticeFromAttempt(String userId, Long attemptId) {
    GrammarAttemptDetailRow attempt = attemptMapper.findDetailByIdAndUserId(attemptId, userId);
    if (attempt == null) {
        throw BusinessException.notFound("Grammar attempt not found: " + attemptId);
    }
    List<String> missedRules = GrammarMistakeAnalyzer.extractMissedRules(
            attempt.getItemsJson(), attempt.getAnswersJson());
    GeneratedGrammarPractice generated = generator.generateTargetingRules(attempt.getLevel(), missedRules);
    persistPracticeItem(userId, generated); // reuse whatever the existing "học thường" generate flow calls to insert into grammar_practice_items
    return getPracticeItems(userId); // reuse the existing list-all-practice-items method, confirm its real name in this class
}
```

Substitute the real method names for "find attempt by id+userId",
"persist a generated item", and "list practice items" — read
`GrammarLearnServiceImpl`'s existing `generate(...)` method in full first
and reuse its exact persistence call rather than inventing a new one.

Write the test first (mock the mapper/generator, assert ownership-check
throws on a null lookup, assert the generator is called with the correct
missed-rules list, assert the practice item is persisted and the refreshed
list is returned) — mirror `DictationServiceImplTest`'s equivalent test
structure if one exists, otherwise follow this file's own existing test
style.

- [ ] **Step 7: Add `generatePracticeFromSession` to `GrammarLibraryServiceImpl`, same TDD approach**

Mirrors Step 6, reading the session + its answers via
`GrammarLibrarySessionMapper`/`GrammarLibrarySessionAnswer`-equivalent
mapper (confirm real method name), calling
`GrammarMistakeAnalyzer.extractMissedRulesFromSession(...)`, then the same
`generator.generateTargetingRules(...)` + persist-into-`grammar_practice_items`
+ return-refreshed-list flow as Step 6 (the regenerated content always lands
in the "học thường" bank, regardless of which flow the mistake came from —
per the spec, there is only one AI-practice destination per domain).

- [ ] **Step 8: Add controller endpoints**

```java
// In GrammarLearnController:
@PostMapping("/attempts/{attemptId}/ai-practice")
public ApiResponse<List<GrammarPracticeItemDto>> generateFromAttempt(
        @PathVariable Long attemptId, @RequestParam String userId) {
    return ApiResponse.ok(service.generatePracticeFromAttempt(userId, attemptId));
}
```

```java
// In GrammarLibraryController:
@PostMapping("/sessions/{sessionId}/ai-practice")
public ApiResponse<List<GrammarPracticeItemDto>> generateFromSession(
        @PathVariable Long sessionId, @RequestParam String userId) {
    return ApiResponse.ok(service.generatePracticeFromSession(userId, sessionId));
}
```

Match whatever request-parameter convention (path/query/body for `userId`)
the sibling `GrammarLearnController`/`GrammarLibraryController` methods
already use — read them first, don't assume `@RequestParam`.

- [ ] **Step 9: Run all grammar tests**

Run: `cd RemeLearning && ./mvnw -pl services/english-service -am test -Dtest=GrammarMistakeAnalyzerTest,GrammarLearnServiceImplTest,GrammarLibraryServiceImplTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS

- [ ] **Step 10: Update docs**

`openapi.yaml` (2 new paths under grammar learn/library), `docs/API.md`, a
new/extended sequence diagram section in
`docs/sequence/English_service/grammar-learn.md` and `grammar-library.md`
(or one combined new file — your call, matching this repo's existing
per-endpoint-vs-per-file granularity), `docs/flow/english-service-data-flow.md`,
`english-service/README.md`.

- [ ] **Step 11: Commit**

```bash
git add RemeLearning/services/english-service/src/main/java/com/remelearning/english/grammar RemeLearning/services/english-service/src/test/java/com/remelearning/english/grammar RemeLearning/services/english-service/openapi.yaml RemeLearning/docs/API.md RemeLearning/docs/sequence/English_service RemeLearning/docs/flow/english-service-data-flow.md RemeLearning/services/english-service/README.md
git commit -m "feat(english-service): add grammar generate-from-attempt (learn + library)"
```

---

## Task 4: Listening — generate-from-attempt (learn + library)

**Files:** mirror Task 3's structure for the `listening` domain:
- Create: `RemeLearning/services/english-service/src/main/java/com/remelearning/english/listening/generator/ListeningMistakeAnalyzer.java`
- Modify: `ListeningLearnServiceImpl.java`/`ListeningLearnService.java`/`ListeningLearnController.java` (add `generatePracticeFromAttempt`)
- Modify: `.../listening/library/service/ListeningLibraryServiceImpl.java`/`ListeningLibraryService.java`/`.../listening/library/controller/ListeningLibraryController.java` (add `generatePracticeFromSection`)
- Test: `ListeningMistakeAnalyzerTest.java`, extend `ListeningLearnServiceImplTest.java` and `ListeningLibraryServiceImplTest.java`

**Interfaces:**
- Consumes: `ListeningAttemptDetailRow.resultsJson` (learn — already has
  per-question `prompt`/`yourAnswer`/`correctAnswer`/`correct`) and Task 1's
  new `ListeningLibraryAttemptAnswerMapper.findByAttemptId` (library).
- Produces: `ListeningMistakeAnalyzer.extractMissedTopics(String resultsJson): List<String>`
  and `.extractMissedQuestionIds(List<ListeningLibraryAttemptAnswer> answers): List<Long>`
  (library-side mistake extraction returns question ids, not rule tags,
  since listening questions don't carry a rule-tag concept the way grammar
  does — the generator instead re-reads the missed questions' text via
  `ListeningLibraryQuestionMapper` to build its prompt).
  `ListeningLearnService.generatePracticeFromAttempt(String userId, Long attemptId): List<ListeningPracticeItemDto>`.
  `ListeningLibraryService.generatePracticeFromSection(String userId, Long sectionId): List<ListeningPracticeItemDto>`.

- [ ] **Step 1: Read `ListeningAttemptDetailRow`/`ListeningAttemptQuestionResultDto` in full**

Confirm the exact field names in `resultsJson`'s deserialized shape.

- [ ] **Step 2: Read `LlmListeningPracticeGenerator` in full**

Find whether it needs a new "target these missed topics/keywords" prompt
variant, same as Task 3 Step 3's approach for grammar.

- [ ] **Step 3: Write `ListeningMistakeAnalyzer` with failing tests first**

Same TDD approach as Task 3 Step 4, adapted to listening's data shape:
`extractMissedTopics` parses `resultsJson`, filters `!correct`, collects
distinct `prompt`/topic-tag equivalents (confirm the real field name — if
there's no explicit "topic" field per question, use a summarized form of
the `prompt`/`explanation` text instead, and document that choice in a
comment). For the library side, `extractMissedQuestionIds` is a simpler
pure filter over `List<ListeningLibraryAttemptAnswer>` for `!isCorrect()`.

- [ ] **Step 4: Add `generatePracticeFromAttempt` to `ListeningLearnServiceImpl` (TDD, mirroring Task 3 Step 6) and `generatePracticeFromSection` to `ListeningLibraryServiceImpl` (TDD, mirroring Task 3 Step 7)**

For the library variant: read the missed question ids via
`attemptAnswerMapper.findByAttemptId(...)` (from the most recent attempt on
that section — confirm via `attemptMapper` how to find "the attempt for this
section" if multiple exist, e.g. most recent by `completedAt`), fetch the
actual question text via `ListeningLibraryQuestionMapper.findBySectionId(...)`
filtered to those ids, then call the generator with that specific missed
content as context.

- [ ] **Step 5: Add controller endpoints**

Mirror Task 3 Step 8's shape:
`POST /api/v1/learn/listening/attempts/{attemptId}/ai-practice` and
`POST /api/v1/learn/listening/library/{userId}/sections/{sectionId}/ai-practice`
(confirm the real base path prefix used by `ListeningLibraryController` —
some of this domain's routes are `/{userId}/...` prefixed rather than
`@RequestParam`, per Task 6's controller in the prior plan — match that
convention, not `@RequestParam`).

- [ ] **Step 6: Run all listening tests**

Run: `cd RemeLearning && ./mvnw -pl services/english-service -am test -Dtest=ListeningMistakeAnalyzerTest,ListeningLearnServiceImplTest,ListeningLibraryServiceImplTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS

- [ ] **Step 7: Update docs** (same 5 files/obligations as Task 3 Step 10, for the listening equivalents)

- [ ] **Step 8: Commit**

```bash
git add RemeLearning/services/english-service/src/main/java/com/remelearning/english/listening RemeLearning/services/english-service/src/test/java/com/remelearning/english/listening RemeLearning/services/english-service/openapi.yaml RemeLearning/docs/API.md RemeLearning/docs/sequence/English_service RemeLearning/docs/flow/english-service-data-flow.md RemeLearning/services/english-service/README.md
git commit -m "feat(english-service): add listening generate-from-attempt (learn + library)"
```

---

## Task 5: Speaking — generate-from-attempt (learn + library)

**Files:** mirror Task 4's structure for the `speaking` domain:
- Create: `RemeLearning/services/english-service/src/main/java/com/remelearning/english/speaking/generator/SpeakingMistakeAnalyzer.java`
- Modify: `SpeakingLearnServiceImpl.java`/`SpeakingLearnService.java`/`SpeakingLearnController.java` (add `generatePracticeFromAttempt`)
- Modify: `.../speaking/library/service/SpeakingLibraryServiceImpl.java`/`SpeakingLibraryService.java`/`.../speaking/library/controller/SpeakingLibraryController.java` (add `generatePracticeFromSection`)
- Test: `SpeakingMistakeAnalyzerTest.java`, extend `SpeakingLearnServiceImplTest.java` and `SpeakingLibraryServiceImplTest.java`

**Interfaces:**
- Consumes: `SpeakingAttemptDetailRow.weakPhonemesJson` (learn — already
  exists) and Task 2's new `SpeakingLibraryAttempt.weakPhonemesJson`
  (library).
- Produces: `SpeakingMistakeAnalyzer.extractWeakPhonemes(String weakPhonemesJson): List<String>`
  (shared pure parser used by both flows, since Task 2 deliberately reused
  the same JSON shape). `SpeakingLearnService.generatePracticeFromAttempt(String userId, Long attemptId): List<SpeakingPracticeItemDto>`.
  `SpeakingLibraryService.generatePracticeFromSection(String userId, Long sectionId): List<SpeakingPracticeItemDto>`.

- [ ] **Step 1: Read `SpeakingAttemptDetailRow.weakPhonemesJson`'s shape and `LlmSpeakingPracticeGenerator` in full**

Confirm the exact JSON shape Task 2 was told to reuse actually matches what
this generator expects as input — if Task 2's implementer serialized a
different shape, adapt `extractWeakPhonemes` to the real shape found here
(read Task 2's actual commit if unsure, don't assume the plan's Task 2 text
was followed literally).

- [ ] **Step 2: Write `SpeakingMistakeAnalyzer` with failing tests first**

A single shared parser (`extractWeakPhonemes`) works for both learn and
library since Task 2 aligned the JSON shape — write tests confirming
empty/null input returns an empty list, and a populated JSON returns the
expected phoneme strings.

- [ ] **Step 3: Add `generatePracticeFromAttempt`/`generatePracticeFromSection`, same TDD approach as Task 3 Steps 6-7**

For the library variant, find the relevant attempt(s) for a section
(likely: all `SpeakingLibraryAttempt` rows for that `sectionId`, unioning
their `weakPhonemesJson` lists) rather than a single attempt id, since
speaking-library scores per-sentence, not per-section — confirm this
approach makes sense by re-reading `SpeakingLibraryServiceImpl.finishSection`
from the prior plan first.

- [ ] **Step 4: Add controller endpoints**, mirroring Task 4 Step 5's shape for `speaking`.

- [ ] **Step 5: Run all speaking tests**

Run: `cd RemeLearning && ./mvnw -pl services/english-service -am test -Dtest=SpeakingMistakeAnalyzerTest,SpeakingLearnServiceImplTest,SpeakingLibraryServiceImplTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS

- [ ] **Step 6: Update docs** (same 5 obligations as Task 3 Step 10, for speaking)

- [ ] **Step 7: Commit**

```bash
git add RemeLearning/services/english-service/src/main/java/com/remelearning/english/speaking RemeLearning/services/english-service/src/test/java/com/remelearning/english/speaking RemeLearning/services/english-service/openapi.yaml RemeLearning/docs/API.md RemeLearning/docs/sequence/English_service RemeLearning/docs/flow/english-service-data-flow.md RemeLearning/services/english-service/README.md
git commit -m "feat(english-service): add speaking generate-from-attempt (learn + library)"
```

---

## Task 6: Merged history endpoint — Grammar

**Files:**
- Modify: `RemeLearning/services/english-service/src/main/java/com/remelearning/english/grammar/learn/service/GrammarLearnServiceImpl.java` (or add to `GrammarLibraryServiceImpl` — whichever already owns a `getHistory` you can extend; check both first)
- Create or modify a shared history DTO, e.g. `GrammarHistoryEntryDto.java` (new, in whichever package makes sense once you've read both existing `getHistory` methods)
- Modify: whichever controller should expose the merged endpoint (likely `GrammarLearnController`, as the "primary" tab)
- Test: extend the corresponding service test

**Interfaces:**
- Produces: a merged history method, e.g.
  `GrammarLearnService.getMergedHistory(String userId): List<GrammarHistoryEntryDto>`
  where `GrammarHistoryEntryDto` has: `source: "LEARN" | "LIBRARY"`,
  `attemptOrSessionId: Long`, `completedAt: Instant`, `score: Double` (or
  whatever score shape both existing history rows can be normalized to),
  and — only when `source == "LIBRARY"` — `topicId: Long` (for the FE's
  "Làm lại" navigation).

- [ ] **Step 1: Read both existing `getHistory`/history-list methods in full**

`GrammarLearnServiceImpl`'s (learn attempts, keyed by `practiceItemId`) and
`GrammarLibraryServiceImpl`'s (library sessions, keyed by `topicId`). Note
their exact return types/field names to design a DTO that both map into
without losing information the FE needs (at minimum: id, timestamp, score,
source, and `topicId` for library rows).

- [ ] **Step 2: Write the failing test for the merge+sort logic**

Test that given N learn-history rows and M library-history rows with mixed
timestamps, `getMergedHistory` returns all N+M entries sorted descending by
`completedAt`, each correctly tagged `source`, and library entries carry
`topicId` while learn entries have it `null`.

- [ ] **Step 3: Implement `getMergedHistory`**

```java
@Override
public List<GrammarHistoryEntryDto> getMergedHistory(String userId) {
    List<GrammarHistoryEntryDto> learnEntries = learnHistoryMapper.findByUserId(userId).stream()
            .map(row -> GrammarHistoryEntryDto.fromLearn(row)) // adapt to real row type/fields
            .toList();
    List<GrammarHistoryEntryDto> libraryEntries = grammarLibraryService.getHistory(userId).stream()
            .map(row -> GrammarHistoryEntryDto.fromLibrary(row))
            .toList();
    return Stream.concat(learnEntries.stream(), libraryEntries.stream())
            .sorted(Comparator.comparing(GrammarHistoryEntryDto::getCompletedAt).reversed())
            .toList();
}
```

Adjust to call the real sibling service/mapper method names found in Step
1 (this may require injecting `GrammarLibraryService` into
`GrammarLearnServiceImpl`, or vice versa, or introducing a small new
orchestrating class if neither service should depend on the other —
prefer injecting the interface, not the impl, to avoid a circular
dependency; if a genuine circular dependency risk exists between the two
services, put `getMergedHistory` in a new, separate
`GrammarHistoryService` that depends on both, and expose it from whichever
controller makes sense).

- [ ] **Step 4: Add the controller endpoint**

`GET /api/v1/learn/grammar/history/{userId}` (or extend the existing
history route if one already exists at a path the FE can reuse — check
first, don't create a duplicate route for the same data).

- [ ] **Step 5: Run tests**

Run the relevant test class(es) with
`-Dsurefire.failIfNoSpecifiedTests=false`.

- [ ] **Step 6: Update docs** (same 5 obligations, for this one new/changed endpoint)

- [ ] **Step 7: Commit**

```bash
git add RemeLearning/services/english-service/src/main/java/com/remelearning/english/grammar RemeLearning/services/english-service/src/test/java/com/remelearning/english/grammar RemeLearning/services/english-service/openapi.yaml RemeLearning/docs/API.md RemeLearning/docs/sequence/English_service RemeLearning/docs/flow/english-service-data-flow.md RemeLearning/services/english-service/README.md
git commit -m "feat(english-service): merge grammar learn+library history into one endpoint"
```

---

## Task 7: Merged history endpoint — Listening

**Files:** mirror Task 6's structure for the `listening` domain (learn
attempts + `listening_library_attempts`, tagging library entries with
`sectionId` instead of `topicId`).

- [ ] **Steps 1-7:** mirror Task 6 Steps 1-7 exactly, substituting
  `grammar`→`listening`, `topicId`→`sectionId`. Commit message:
  "feat(english-service): merge listening learn+library history into one endpoint".

---

## Task 8: Merged history endpoint — Speaking

**Files:** mirror Task 6's structure for the `speaking` domain (learn
attempts + `speaking_library_attempts` — note speaking-library history is
per-sentence-attempt, not per-section-completion; decide at code time
whether to surface one row per `finishSection` call (if such a completion
record exists) or roll up per-sentence attempts into one row per section —
re-read `SpeakingLibraryServiceImpl.getHistory` from the prior plan first to
see what it already returns, and merge at that same granularity rather than
inventing a new one).

- [ ] **Steps 1-7:** mirror Task 6 Steps 1-7, substituting
  `grammar`→`speaking`, `topicId`→`sectionId`. Commit message:
  "feat(english-service): merge speaking learn+library history into one endpoint".

---

## Task 9: `bff-service` — proxy all 9 new endpoints

**Files:**
- Modify: `RemeLearning/services/bff-service/src/main/java/com/remelearning/bff/client/EnglishServiceClient.java`
- Modify: `RemeLearning/services/bff-service/src/main/java/com/remelearning/bff/controller/LearnerController.java`
- Create: 9 new bff DTOs (or reuse existing ones where the response shape
  already matches — check `GrammarPracticeItemDto`/`ListeningPracticeItemDto`/
  `SpeakingPracticeItemDto` etc. already exist in bff-service per the prior
  plans; the 6 generate-from-attempt endpoints likely return the SAME shape
  as those existing practice-item-list DTOs, so no new DTO is needed for
  them — only the 3 merged-history endpoints need a new
  `GrammarHistoryEntryDto`/`ListeningHistoryEntryDto`/`SpeakingHistoryEntryDto`
  bff-side type)
- Modify: `openapi.yaml`, `docs/API.md`, a new/extended `docs/sequence/Bff_service/*.md`, `bff-service/README.md`

**Interfaces:**
- Consumes: all 9 endpoints from Tasks 3-8.

- [ ] **Step 1: Read the existing vocabulary/grammar/listening/speaking practice-item proxy methods in `EnglishServiceClient.java`**

Confirm whether the 6 generate-from-attempt endpoints' response shape
(a list of practice items) already has a matching bff DTO from earlier
plans — reuse it, don't duplicate.

- [ ] **Step 2: Add the 6 generate-from-attempt proxy methods + 3 merged-history proxy methods to `EnglishServiceClient`**

Follow the exact `WebClient` call/error-handling pattern already
established (per the prior plan's Task 6, confirmed to use `.doOnError` for
logging, not `.onErrorResume`).

- [ ] **Step 3: Add the 3 new bff DTOs (history entries) if Step 1 confirmed they're needed**

Plain data holders, fields matching the english-service history DTOs from
Tasks 6-8.

- [ ] **Step 4: Add routes to `LearnerController`**

`/api/v1/learners/{userId}/learn/{domain}/attempts/{attemptId}/ai-practice`,
`/api/v1/learners/{userId}/learn/{domain}/library/sessions-or-sections/{id}/ai-practice`,
`/api/v1/learners/{userId}/learn/{domain}/history` (merged) — match the
established path convention from the prior plan's bff task exactly (read it
again if unsure).

- [ ] **Step 5: Write/extend tests**

Follow `EnglishServiceClientTest.java`'s established `StepVerifier` + stub
`ExchangeFunction` pattern (from the prior plan's Task 6) for all 9 new
methods.

- [ ] **Step 6: Run bff-service tests**

Run: `cd RemeLearning && ./mvnw -pl services/bff-service -am test`
Expected: BUILD SUCCESS

- [ ] **Step 7: Update docs** (openapi.yaml, docs/API.md, sequence diagram, README)

- [ ] **Step 8: Commit**

```bash
git add RemeLearning/services/bff-service
git commit -m "feat(bff-service): proxy history-retry generate-from-attempt and merged-history endpoints"
```

---

## Task 10: FE — Grammar history retry buttons

**Files:**
- Modify: `src/features/learn/grammar/GrammarLearnPage.tsx` (or wherever the history tab/list currently lives — read first)
- Modify: `src/features/learn/grammar/hooks.ts`
- Modify: `src/api/learners.ts`
- Modify: `src/types/api.ts`

**Interfaces:**
- Consumes: `bff-service`'s merged-history + generate-from-attempt routes
  from Task 9.
- Produces: `useGrammarMergedHistory(userId)`,
  `useGenerateGrammarPracticeFromAttempt(userId)` (react-query hooks).

- [ ] **Step 1: Read `src/features/dictation/DictationPage.tsx`'s `HistorySection` in full, one more time, focused specifically on the two buttons' JSX/handlers**

This is the direct behavioral template for both buttons' gating/click
handlers (`hasClip` condition, `navigate(...)` for library retry,
`.mutate({attemptId, ...})` + `onSwitchTab` for AI retry) — confirmed
correct earlier in this session's research.

- [ ] **Step 2: Read the current grammar history tab's rendering code in full**

Find where `useGrammarLibraryHistory`/whatever existing learn-history hook
is called and rendered, to replace it with the new merged-history hook.

- [ ] **Step 3: Add API functions + hooks**

```typescript
// src/api/learners.ts
export function getGrammarMergedHistory(userId: string) {
  return apiClient.get(`/api/v1/learners/${userId}/learn/grammar/history`).then(unwrap);
}

export function generateGrammarPracticeFromAttempt(userId: string, attemptId: string) {
  return apiClient.post(`/api/v1/learners/${userId}/learn/grammar/attempts/${attemptId}/ai-practice`, {}).then(unwrap);
}

export function generateGrammarPracticeFromSession(userId: string, sessionId: string) {
  return apiClient.post(`/api/v1/learners/${userId}/learn/grammar/library/sessions/${sessionId}/ai-practice`, {}).then(unwrap);
}
```

(Confirm exact route paths against Task 9's real `LearnerController` routes
before finalizing — do not guess.)

```typescript
// src/features/learn/grammar/hooks.ts
export function useGrammarMergedHistory(userId: string) {
  return useQuery({
    queryKey: ["grammar-merged-history", userId],
    queryFn: () => getGrammarMergedHistory(userId),
  });
}

export function useGenerateGrammarPracticeFromAttempt(userId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (attemptId: string) => generateGrammarPracticeFromAttempt(userId, attemptId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["grammar-practice-items", userId] });
    },
  });
}

export function useGenerateGrammarPracticeFromSession(userId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (sessionId: string) => generateGrammarPracticeFromSession(userId, sessionId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["grammar-practice-items", userId] });
    },
  });
}
```

- [ ] **Step 4: Render the two buttons per history row**

```tsx
{entry.source === "LIBRARY" && (
  <Button variant="outline" size="sm" onClick={() => navigate(`/learn/grammar/library/topics/${entry.topicId}`)}>
    {t("learn.history.retryLibrary")}
  </Button>
)}
<Button
  variant="outline"
  size="sm"
  loading={
    entry.source === "LIBRARY"
      ? generateFromSession.isPending && generateFromSession.variables === String(entry.attemptOrSessionId)
      : generateFromAttempt.isPending && generateFromAttempt.variables === String(entry.attemptOrSessionId)
  }
  onClick={() =>
    entry.source === "LIBRARY"
      ? generateFromSession.mutate(String(entry.attemptOrSessionId))
      : generateFromAttempt.mutate(String(entry.attemptOrSessionId))
  }
>
  {t("learn.history.retryAi")}
</Button>
```

On mutation success, switch to the "practice"/"luyện tập" tab (the same tab
grammar's own learn-mode AI content already renders in — call whatever
tab-switch handler the page already exposes, per the spec's decision not to
add a separate "AI tab" the way dictation has one).

- [ ] **Step 5: Add i18n keys**

`learn.history.retryLibrary` ("Làm lại"), `learn.history.retryAi`
("Luyện tập với AI") in both `en.json`/`vi.json` — shared keys reusable by
Tasks 11-12 too (add them once here, don't redeclare per domain).

- [ ] **Step 6: Typecheck + lint**

Run: `npx tsc --noEmit -p tsconfig.app.json` and `npx oxlint` on all changed
files.

- [ ] **Step 7: Manually verify in the browser** (no backend running in this
sandbox — skip and note in the report, per established convention from the
prior plan).

- [ ] **Step 8: Commit**

```bash
git add src/features/learn/grammar src/api/learners.ts src/types/api.ts src/i18n/locales/en.json src/i18n/locales/vi.json
git commit -m "feat(grammar): add retry-with-AI/Library buttons to merged history list"
```

---

## Task 11: FE — Listening history retry buttons

**Files:** mirror Task 10's structure for the `listening` domain.

- [ ] **Steps 1-8:** mirror Task 10 Steps 1-8 exactly, substituting
  `grammar`→`listening`, `topicId`→`sectionId`, "sessions"→"sections" in
  route paths. Reuse the `learn.history.retryLibrary`/`retryAi` i18n keys
  Task 10 already added — do not redeclare them. Commit message:
  "feat(listening): add retry-with-AI/Library buttons to merged history list".

---

## Task 12: FE — Speaking history retry buttons

**Files:** mirror Task 10's structure for the `speaking` domain.

- [ ] **Steps 1-8:** mirror Task 10 Steps 1-8 exactly, substituting
  `grammar`→`speaking`, `topicId`→`sectionId`. Reuse the shared i18n keys.
  Commit message: "feat(speaking): add retry-with-AI/Library buttons to
  merged history list".

---

## Task 13: Business.md update

**Files:**
- Modify: `D:\Personal Project\RemeLearning_BA\Business.md`

- [ ] **Step 1: Read the existing Dictation history-retry business-doc section (added in an earlier plan) in full**

Match its tone/heading level.

- [ ] **Step 2: Add a section explaining, in Vietnamese business terms**

Learners can now retry directly from their Grammar/Listening/Speaking
history: reopening the exact same Library topic/section they came from, or
having AI generate a fresh practice set targeting exactly what they got
wrong last time (same idea as the existing Dictation feature) — for both
the free-form "AI practice" history and the structured "Library" history,
now shown together in one combined history list per skill.

- [ ] **Step 3: Edit on disk; commit only if the folder turns out to be a git repo (it wasn't, per prior sessions) — report accordingly.**

---

## Self-Review Notes

- **Spec coverage:** §1 (schema additions) → Tasks 1-2. §2 (6 generate
  endpoints) → Tasks 3-5. §3 (merged history + FE buttons) → Tasks 6-8 (BE
  merge), 10-12 (FE). §4/§5 (bff proxy, docs) → Task 9 + embedded per BE
  task. §4 testing → embedded per task.
- **Type consistency:** every new listening/speaking-library method uses
  `String userId`, matching the prior plan's established convention. The 6
  generate-from-attempt endpoints all return the SAME practice-item-list DTO
  shape their domain's existing "học thường" generate flow already returns
  — Task 9 explicitly checks for and reuses existing bff DTOs rather than
  duplicating them.
- **Known risk flagged inline:** Task 6/Task 8 both note a design decision
  point (avoiding a circular service dependency for the history merge;
  matching speaking-library's actual history granularity) that the
  implementer must resolve by reading real code rather than the plan
  guessing an answer — this is intentional given neither could be verified
  without the code these tasks themselves are producing.
