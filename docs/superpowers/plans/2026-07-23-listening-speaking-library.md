# Listening/Speaking Library Tabs Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a topic-based "Thư viện" (library) tab to the Listening and Speaking
learn pages, mirroring the existing Grammar/Vocabulary library (fixed 60-topic
taxonomy, LOCKED/IN_PROGRESS/PASSED gating, LLM-generated content banks,
Section-based practice), and extract the shared gating logic into `common`.

**Architecture:** Two new Java packages in `english-service`
(`listening.library`, `speaking.library`), structurally cloned from
`vocabulary.library`/`grammar.library` (migration → domain → mapper →
generator → service → controller). A new pure-logic class in `common`
(`TopicProgressCalculator`) replaces the duplicated gating code in all four
domains. `bff-service` proxies the new endpoints. `RemeLearning_FE` adds a
"Thư viện" tab + panel/runner/hooks per domain, cloned from the existing
grammar/vocabulary library FE code.

**Tech Stack:** Spring Boot 4.1 / Java 21, MyBatis, Flyway, PostgreSQL, Kafka
(no Kafka involvement in this feature), React + TypeScript + react-query
(existing FE stack).

## Global Constraints

- Java tests: plain JUnit 5 + AssertJ + `Mockito.mock(...)` — no `@Mock`, no
  `@ExtendWith(MockitoExtension.class)`, no `@SpringBootTest`, no integration
  tests. Match e.g. `GrammarWeakPointServiceImplTest`.
- Comment non-trivial code blocks/methods in both Java and TS (per
  `CLAUDE.md` — this repo overrides the "no comments" default).
- Every new/changed REST endpoint requires: `openapi.yaml` of the owning
  service updated, `docs/API.md` updated (mục lục + bảng + chi tiết), a
  `docs/sequence/<Service>/*.md` sequence diagram, a `docs/flow/<service>-data-flow.md`
  update, and the service's own `README.md` updated — all in the same commit
  as the code.
- Business-meaning changes must update `Business.md` in
  `D:\Personal Project\RemeLearning_BA\Business.md` (separate repo/folder,
  not gitignored-in-this-repo) in the same change.
- Flyway migrations are additive-only (`IF NOT EXISTS` not required here since
  these are brand-new tables) — pick the next unused `V__` version number by
  checking `RemeLearning/services/english-service/src/main/resources/db/migration/`.
- Every servlet-stack DB-backed service already has `Boot4CompatConfig` — no
  new one needed, these packages live inside `english-service`.
- `english-service`'s `@ComponentScan(basePackages = "com.remelearning")` and
  `@MapperScan` already cover new sub-packages under `com.remelearning.english.*`
  and `com.remelearning.common.*` — no `EnglishServiceApplication` changes
  needed unless a new mapper package is added (it is — see Task 6 & 10).

---

## Task 1: `TopicProgressCalculator` in `common`

**Files:**
- Create: `RemeLearning/common/src/main/java/com/remelearning/common/library/TopicStatus.java`
- Create: `RemeLearning/common/src/main/java/com/remelearning/common/library/TopicProgressCalculator.java`
- Test: `RemeLearning/common/src/test/java/com/remelearning/common/library/TopicProgressCalculatorTest.java`

**Interfaces:**
- Produces: `TopicStatus` enum (`LOCKED`, `IN_PROGRESS`, `PASSED`);
  `TopicProgressCalculator.compute(List<Long> topicIdsBySequenceOrder, Set<Long> passedTopicIds): Map<Long, TopicStatus>`
  — input list MUST already be sorted ascending by `sequence_order` by the
  caller; the calculator does not re-sort.

- [ ] **Step 1: Write the failing test**

```java
package com.remelearning.common.library;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;

class TopicProgressCalculatorTest {

    @Test
    void firstTopicIsInProgressWhenNothingPassed() {
        Map<Long, TopicStatus> result = TopicProgressCalculator.compute(
            List.of(1L, 2L, 3L), Set.of());

        assertThat(result.get(1L)).isEqualTo(TopicStatus.IN_PROGRESS);
        assertThat(result.get(2L)).isEqualTo(TopicStatus.LOCKED);
        assertThat(result.get(3L)).isEqualTo(TopicStatus.LOCKED);
    }

    @Test
    void topicUnlocksOnlyAfterPreviousPassed() {
        Map<Long, TopicStatus> result = TopicProgressCalculator.compute(
            List.of(1L, 2L, 3L), Set.of(1L));

        assertThat(result.get(1L)).isEqualTo(TopicStatus.PASSED);
        assertThat(result.get(2L)).isEqualTo(TopicStatus.IN_PROGRESS);
        assertThat(result.get(3L)).isEqualTo(TopicStatus.LOCKED);
    }

    @Test
    void allPassedMeansAllPassed() {
        Map<Long, TopicStatus> result = TopicProgressCalculator.compute(
            List.of(1L, 2L, 3L), Set.of(1L, 2L, 3L));

        assertThat(result.values()).containsOnly(TopicStatus.PASSED);
    }

    @Test
    void emptyTopicListReturnsEmptyMap() {
        assertThat(TopicProgressCalculator.compute(List.of(), Set.of())).isEmpty();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd RemeLearning && ./mvnw -pl common test -Dtest=TopicProgressCalculatorTest`
Expected: FAIL — compilation error, `TopicStatus`/`TopicProgressCalculator` do not exist.

- [ ] **Step 3: Write minimal implementation**

```java
package com.remelearning.common.library;

// Progress state of a single fixed library topic for one learner.
public enum TopicStatus {
    LOCKED,
    IN_PROGRESS,
    PASSED
}
```

```java
package com.remelearning.common.library;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

// Shared LOCKED/IN_PROGRESS/PASSED gating for topic-based libraries
// (vocabulary, grammar, listening, speaking): a topic unlocks only once the
// immediately preceding topic (by sequence_order) has been PASSED.
public final class TopicProgressCalculator {

    private TopicProgressCalculator() {
    }

    public static Map<Long, TopicStatus> compute(
            List<Long> topicIdsBySequenceOrder, Set<Long> passedTopicIds) {
        Map<Long, TopicStatus> result = new LinkedHashMap<>();
        boolean previousPassed = true;
        for (Long topicId : topicIdsBySequenceOrder) {
            if (!previousPassed) {
                result.put(topicId, TopicStatus.LOCKED);
                continue;
            }
            result.put(topicId, passedTopicIds.contains(topicId)
                    ? TopicStatus.PASSED
                    : TopicStatus.IN_PROGRESS);
            previousPassed = passedTopicIds.contains(topicId);
        }
        return result;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd RemeLearning && ./mvnw -pl common test -Dtest=TopicProgressCalculatorTest`
Expected: PASS (4 tests)

- [ ] **Step 5: Commit**

```bash
git add RemeLearning/common/src/main/java/com/remelearning/common/library RemeLearning/common/src/test/java/com/remelearning/common/library
git commit -m "feat(common): add TopicProgressCalculator for topic-library gating"
```

---

## Task 2: Refactor `GrammarLibraryServiceImpl` and `VocabularyLibraryServiceImpl` to use it

**Files:**
- Modify: `RemeLearning/services/english-service/src/main/java/com/remelearning/english/grammar/library/service/GrammarLibraryServiceImpl.java`
- Modify: `RemeLearning/services/english-service/src/main/java/com/remelearning/english/vocabulary/library/service/VocabularyLibraryServiceImpl.java`
- Test: matching existing test files for both classes (find via `find RemeLearning/services/english-service/src/test -iname "*LibraryServiceImplTest*"`)

**Interfaces:**
- Consumes: `TopicProgressCalculator.compute(List<Long>, Set<Long>)` from Task 1.

- [ ] **Step 1: Read the current gating code in both services**

Run: `grep -n -A 20 "LOCKED\|IN_PROGRESS\|PASSED" RemeLearning/services/english-service/src/main/java/com/remelearning/english/grammar/library/service/GrammarLibraryServiceImpl.java`

Read the matched block plus its containing method fully (use the Read tool)
before editing — the exact variable names (topic list variable, "passed
topic ids" source) differ from this plan's placeholder names and must be
preserved.

- [ ] **Step 2: Replace the inline gating block with a call to `TopicProgressCalculator.compute`**

Import `com.remelearning.common.library.TopicProgressCalculator` and
`com.remelearning.common.library.TopicStatus`. Replace the loop/if-chain that
computes each topic's status with:

```java
List<Long> orderedTopicIds = topics.stream()
        .sorted(Comparator.comparing(GrammarLibraryTopic::getSequenceOrder))
        .map(GrammarLibraryTopic::getId)
        .toList();
Map<Long, TopicStatus> statusByTopicId =
        TopicProgressCalculator.compute(orderedTopicIds, passedTopicIds);
```

(substitute the real domain type name — `GrammarLibraryTopic` vs whatever the
vocabulary equivalent is called — and the real variable holding "passed topic
ids" found in Step 1). Then use `statusByTopicId.get(topic.getId())` wherever
the old inline status was assigned, converting to whatever DTO field type the
controller/DTO expects (`TopicStatus.name()` if the DTO field is a `String`).

- [ ] **Step 3: Run the existing service test for both classes to confirm no behavior change**

Run: `cd RemeLearning && ./mvnw -pl services/english-service -am test -Dtest=GrammarLibraryServiceImplTest,VocabularyLibraryServiceImplTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS, same assertions as before (no test changes needed — this is
a pure refactor, same output for same input).

- [ ] **Step 4: Commit**

```bash
git add RemeLearning/services/english-service/src/main/java/com/remelearning/english/grammar/library/service/GrammarLibraryServiceImpl.java RemeLearning/services/english-service/src/main/java/com/remelearning/english/vocabulary/library/service/VocabularyLibraryServiceImpl.java
git commit -m "refactor(english-service): reuse common TopicProgressCalculator for gating"
```

---

## Task 3: `listening.library` — migration + domain + mapper

**Files:**
- Create: `RemeLearning/services/english-service/src/main/resources/db/migration/V<next>__listening_library.sql`
  (run `ls RemeLearning/services/english-service/src/main/resources/db/migration/` first to find the next free `V` number)
- Create: `RemeLearning/services/english-service/src/main/java/com/remelearning/english/listening/library/domain/ListeningLibraryTopic.java`
- Create: `RemeLearning/services/english-service/src/main/java/com/remelearning/english/listening/library/domain/ListeningLibrarySection.java`
- Create: `RemeLearning/services/english-service/src/main/java/com/remelearning/english/listening/library/domain/ListeningLibraryQuestion.java`
- Create: `RemeLearning/services/english-service/src/main/java/com/remelearning/english/listening/library/domain/ListeningLibraryTopicProgress.java`
- Create: `RemeLearning/services/english-service/src/main/java/com/remelearning/english/listening/library/domain/ListeningLibraryAttempt.java`
- Create: `RemeLearning/services/english-service/src/main/java/com/remelearning/english/listening/library/mapper/ListeningLibraryTopicMapper.java`
- Create: `RemeLearning/services/english-service/src/main/resources/mapper/listening/library/ListeningLibraryTopicMapper.xml`
- Create: `RemeLearning/services/english-service/src/main/java/com/remelearning/english/listening/library/mapper/ListeningLibrarySectionMapper.java`
- Create: `RemeLearning/services/english-service/src/main/resources/mapper/listening/library/ListeningLibrarySectionMapper.xml`
- Create: `RemeLearning/services/english-service/src/main/java/com/remelearning/english/listening/library/mapper/ListeningLibraryProgressMapper.java`
- Create: `RemeLearning/services/english-service/src/main/resources/mapper/listening/library/ListeningLibraryProgressMapper.xml`
- Create: `RemeLearning/services/english-service/src/main/java/com/remelearning/english/listening/library/mapper/ListeningLibraryAttemptMapper.java`
- Create: `RemeLearning/services/english-service/src/main/resources/mapper/listening/library/ListeningLibraryAttemptMapper.xml`

**Interfaces:**
- Produces: domain classes with plain getters/setters (Lombok `@Data` — check
  `GrammarLibraryTopic.java` for the Lombok annotations this codebase uses and
  match them exactly) and mapper interfaces `ListeningLibraryTopicMapper.findAll()`,
  `.findById(Long id)`; `ListeningLibrarySectionMapper.findByTopicId(Long topicId)`,
  `.insert(ListeningLibrarySection s)`, `.findById(Long id)`;
  `ListeningLibraryProgressMapper.findByUserId(Long userId)`,
  `.upsert(ListeningLibraryTopicProgress p)`;
  `ListeningLibraryAttemptMapper.insert(ListeningLibraryAttempt a)`,
  `.findByUserId(Long userId)`.

- [ ] **Step 1: Read the grammar library migration to copy the 60-topic seed content and table shape**

Read `RemeLearning/services/english-service/src/main/resources/db/migration/V17__grammar_library.sql`
in full (all ~75+ lines) — the topic `name`/`description`/`level`/`sequence_order`
values from this file are copied verbatim into the new listening topics table
(same topic taxonomy, independent table/ids, per the spec).

- [ ] **Step 2: Write the migration**

```sql
-- V<next>__listening_library.sql
-- Fixed 60-topic taxonomy for the Listening library, mirroring
-- grammar_library_topics' topic set (same names/order, independent ids/table).
CREATE TABLE listening_library_topics (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    level VARCHAR(16) NOT NULL,
    sequence_order INT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Copy all 60 INSERT rows from V17__grammar_library.sql here, same
-- code/name/description/level/sequence_order values, targeting this table.
-- (Read the source file in Step 1 and transcribe every row — do not
-- summarize or truncate the seed list.)

CREATE TABLE listening_library_sections (
    id BIGSERIAL PRIMARY KEY,
    topic_id BIGINT NOT NULL REFERENCES listening_library_topics(id),
    passage_text TEXT NOT NULL,
    audio_storage_key VARCHAR(512),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE listening_library_questions (
    id BIGSERIAL PRIMARY KEY,
    section_id BIGINT NOT NULL REFERENCES listening_library_sections(id),
    question_text TEXT NOT NULL,
    options_json TEXT NOT NULL,
    correct_option VARCHAR(8) NOT NULL,
    explanation TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE listening_library_topic_progress (
    user_id BIGINT NOT NULL,
    topic_id BIGINT NOT NULL REFERENCES listening_library_topics(id),
    status VARCHAR(16) NOT NULL,
    best_score DOUBLE PRECISION,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, topic_id)
);

CREATE TABLE listening_library_attempts (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    section_id BIGINT NOT NULL REFERENCES listening_library_sections(id),
    score DOUBLE PRECISION NOT NULL,
    correct_count INT NOT NULL,
    total_questions INT NOT NULL,
    started_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_listening_library_sections_topic ON listening_library_sections(topic_id);
CREATE INDEX idx_listening_library_questions_section ON listening_library_questions(section_id);
CREATE INDEX idx_listening_library_attempts_user ON listening_library_attempts(user_id);
```

- [ ] **Step 3: Read `GrammarLibraryTopic.java` to match its exact Lombok/annotation style**

Read `RemeLearning/services/english-service/src/main/java/com/remelearning/english/grammar/library/domain/GrammarLibraryTopic.java`
in full and copy its annotation pattern (e.g. `@Data`, field types for
timestamps) exactly for the new domain classes below.

- [ ] **Step 4: Write the domain classes**

```java
package com.remelearning.english.listening.library.domain;

import lombok.Data;
import java.time.OffsetDateTime;

@Data
public class ListeningLibraryTopic {
    private Long id;
    private String code;
    private String name;
    private String description;
    private String level;
    private Integer sequenceOrder;
    private OffsetDateTime createdAt;
}
```

```java
package com.remelearning.english.listening.library.domain;

import lombok.Data;
import java.time.OffsetDateTime;

@Data
public class ListeningLibrarySection {
    private Long id;
    private Long topicId;
    private String passageText;
    private String audioStorageKey;
    private OffsetDateTime createdAt;
}
```

```java
package com.remelearning.english.listening.library.domain;

import lombok.Data;
import java.time.OffsetDateTime;

@Data
public class ListeningLibraryQuestion {
    private Long id;
    private Long sectionId;
    private String questionText;
    private String optionsJson;
    private String correctOption;
    private String explanation;
    private OffsetDateTime createdAt;
}
```

```java
package com.remelearning.english.listening.library.domain;

import lombok.Data;
import java.time.OffsetDateTime;

@Data
public class ListeningLibraryTopicProgress {
    private Long userId;
    private Long topicId;
    private String status;
    private Double bestScore;
    private OffsetDateTime updatedAt;
}
```

```java
package com.remelearning.english.listening.library.domain;

import lombok.Data;
import java.time.OffsetDateTime;

@Data
public class ListeningLibraryAttempt {
    private Long id;
    private Long userId;
    private Long sectionId;
    private Double score;
    private Integer correctCount;
    private Integer totalQuestions;
    private OffsetDateTime startedAt;
    private OffsetDateTime completedAt;
}
```

- [ ] **Step 5: Read `GrammarLibraryTopicMapper.java`/`.xml` to match the mapper style (resultMap naming, namespace convention)**

Read both files in full, then write the four mapper interface + XML pairs
below following that exact structure (namespace = fully qualified mapper
interface name, resultMap ids matching domain field names via
`camelCase`/`snake_case` column mapping).

- [ ] **Step 6: Write mapper interfaces**

```java
package com.remelearning.english.listening.library.mapper;

import com.remelearning.english.listening.library.domain.ListeningLibraryTopic;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

@Mapper
public interface ListeningLibraryTopicMapper {
    List<ListeningLibraryTopic> findAll();
    ListeningLibraryTopic findById(Long id);
}
```

```java
package com.remelearning.english.listening.library.mapper;

import com.remelearning.english.listening.library.domain.ListeningLibrarySection;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

@Mapper
public interface ListeningLibrarySectionMapper {
    List<ListeningLibrarySection> findByTopicId(Long topicId);
    ListeningLibrarySection findById(Long id);
    void insert(ListeningLibrarySection section);
}
```

```java
package com.remelearning.english.listening.library.mapper;

import com.remelearning.english.listening.library.domain.ListeningLibraryTopicProgress;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

@Mapper
public interface ListeningLibraryProgressMapper {
    List<ListeningLibraryTopicProgress> findByUserId(Long userId);
    void upsert(ListeningLibraryTopicProgress progress);
}
```

```java
package com.remelearning.english.listening.library.mapper;

import com.remelearning.english.listening.library.domain.ListeningLibraryAttempt;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

@Mapper
public interface ListeningLibraryAttemptMapper {
    void insert(ListeningLibraryAttempt attempt);
    List<ListeningLibraryAttempt> findByUserId(Long userId);
}
```

Write the four matching `.xml` files under
`RemeLearning/services/english-service/src/main/resources/mapper/listening/library/`,
copying the `resultMap`/`insert`/`select` XML shape from
`GrammarLibraryTopicMapper.xml` field-for-field (column names = snake_case of
the domain field names listed above; `upsert` uses
`ON CONFLICT (user_id, topic_id) DO UPDATE` since the progress table's PK is
the composite `(user_id, topic_id)`).

- [ ] **Step 7: Verify the module compiles**

Run: `cd RemeLearning && ./mvnw -pl services/english-service -am compile`
Expected: BUILD SUCCESS

- [ ] **Step 8: Commit**

```bash
git add RemeLearning/services/english-service/src/main/resources/db/migration RemeLearning/services/english-service/src/main/java/com/remelearning/english/listening/library/domain RemeLearning/services/english-service/src/main/java/com/remelearning/english/listening/library/mapper RemeLearning/services/english-service/src/main/resources/mapper/listening/library
git commit -m "feat(english-service): add listening library schema, domain, mappers"
```

---

## Task 4: `listening.library` — LLM content generator

**Files:**
- Create: `RemeLearning/services/english-service/src/main/java/com/remelearning/english/listening/library/generator/LlmListeningLibraryGenerator.java`
- Test: `RemeLearning/services/english-service/src/test/java/com/remelearning/english/listening/library/generator/LlmListeningLibraryGeneratorTest.java`

**Interfaces:**
- Consumes: `com.remelearning.common.ai.LlmClient` (existing, see how
  `LlmVocabularyClassifier` or `LlmLibraryWordGenerator` inject and call it —
  read one of those files first), `com.remelearning.english.listening.library.domain.ListeningLibraryTopic`.
  Also consumes whatever TTS client the existing `listening` package already
  uses for audio — find it via `grep -rn "TtsClient\|DialogueAudioSynthesizer" RemeLearning/services/english-service/src/main/java/com/remelearning/english/listening/`
  and inject the same interface/class.
- Produces: `LlmListeningLibraryGenerator.generateSection(ListeningLibraryTopic topic): ListeningLibrarySection`
  (persists the section + its questions + synthesizes/stores audio, returns
  the fully populated section — question objects are inserted internally via
  `ListeningLibraryQuestionMapper`, not returned separately). **Note:** Task 3
  did not create `ListeningLibraryQuestionMapper` — add it now as part of
  this task (interface + XML, same style as the other four mappers, with
  `insert(ListeningLibraryQuestion q)` and `findBySectionId(Long sectionId)`).

- [ ] **Step 1: Read `LlmLibraryWordGenerator.java` in full**

This is the closest existing analog (LLM top-up into a library bank). Note
its constructor injection pattern, its prompt-building approach, and how it
parses the LLM's JSON response into domain objects — the new generator
follows the same shape.

- [ ] **Step 2: Add the missing `ListeningLibraryQuestionMapper`**

```java
package com.remelearning.english.listening.library.mapper;

import com.remelearning.english.listening.library.domain.ListeningLibraryQuestion;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

@Mapper
public interface ListeningLibraryQuestionMapper {
    void insert(ListeningLibraryQuestion question);
    List<ListeningLibraryQuestion> findBySectionId(Long sectionId);
}
```

Write the matching XML under
`RemeLearning/services/english-service/src/main/resources/mapper/listening/library/ListeningLibraryQuestionMapper.xml`,
same style as the other mappers in Task 3.

- [ ] **Step 3: Write the failing test**

```java
package com.remelearning.english.listening.library.generator;

import com.remelearning.common.ai.LlmClient;
import com.remelearning.common.ai.LlmResponse;
import com.remelearning.english.listening.library.domain.ListeningLibraryTopic;
import com.remelearning.english.listening.library.mapper.ListeningLibrarySectionMapper;
import com.remelearning.english.listening.library.mapper.ListeningLibraryQuestionMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class LlmListeningLibraryGeneratorTest {

    @Test
    void generateSectionPersistsPassageAndQuestions() {
        LlmClient llmClient = mock(LlmClient.class);
        ListeningLibrarySectionMapper sectionMapper = mock(ListeningLibrarySectionMapper.class);
        ListeningLibraryQuestionMapper questionMapper = mock(ListeningLibraryQuestionMapper.class);

        String llmJson = """
            {
              "passage": "A short passage about travel.",
              "questions": [
                {"question": "Where did they go?", "options": ["Paris", "Rome", "Tokyo", "Cairo"], "correctOption": "A", "explanation": "Stated in the passage."}
              ]
            }
            """;
        when(llmClient.generate(any())).thenReturn(new LlmResponse(llmJson));

        ListeningLibraryTopic topic = new ListeningLibraryTopic();
        topic.setId(1L);
        topic.setName("Travel");
        topic.setLevel("A2");

        LlmListeningLibraryGenerator generator =
                new LlmListeningLibraryGenerator(llmClient, sectionMapper, questionMapper);

        var section = generator.generateSection(topic);

        assertThat(section.getPassageText()).isEqualTo("A short passage about travel.");
        verify(sectionMapper).insert(any());
        verify(questionMapper).insert(any());
    }
}
```

Adjust the mocked `LlmClient`/`LlmResponse` shape to match the real
signatures found in Step 1 — if `LlmClient.generate` takes an `LlmRequest`
and returns a different response type/accessor than `LlmResponse(String)`
shown above, use the real ones (read `common`'s `ai/LlmClient.java` to
confirm before writing this test).

- [ ] **Step 4: Run test to verify it fails**

Run: `cd RemeLearning && ./mvnw -pl services/english-service -am test -Dtest=LlmListeningLibraryGeneratorTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL — class does not exist yet.

- [ ] **Step 5: Implement the generator**

```java
package com.remelearning.english.listening.library.generator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.remelearning.common.ai.LlmClient;
import com.remelearning.english.listening.library.domain.ListeningLibraryQuestion;
import com.remelearning.english.listening.library.domain.ListeningLibrarySection;
import com.remelearning.english.listening.library.domain.ListeningLibraryTopic;
import com.remelearning.english.listening.library.mapper.ListeningLibraryQuestionMapper;
import com.remelearning.english.listening.library.mapper.ListeningLibrarySectionMapper;
import org.springframework.stereotype.Component;

// Generates one Listening library section (a short passage + MCQ questions)
// for a topic via the LLM, persists it, and returns the populated section.
// Audio synthesis for the passage happens in the service layer once the
// section id is known (see ListeningLibraryServiceImpl), not here.
@Component
public class LlmListeningLibraryGenerator {

    private final LlmClient llmClient;
    private final ListeningLibrarySectionMapper sectionMapper;
    private final ListeningLibraryQuestionMapper questionMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public LlmListeningLibraryGenerator(
            LlmClient llmClient,
            ListeningLibrarySectionMapper sectionMapper,
            ListeningLibraryQuestionMapper questionMapper) {
        this.llmClient = llmClient;
        this.sectionMapper = sectionMapper;
        this.questionMapper = questionMapper;
    }

    public ListeningLibrarySection generateSection(ListeningLibraryTopic topic) {
        String prompt = buildPrompt(topic);
        String rawJson = llmClient.generate(prompt).text();
        JsonNode root = parseJson(rawJson);

        ListeningLibrarySection section = new ListeningLibrarySection();
        section.setTopicId(topic.getId());
        section.setPassageText(root.get("passage").asText());
        sectionMapper.insert(section);

        for (JsonNode q : root.get("questions")) {
            ListeningLibraryQuestion question = new ListeningLibraryQuestion();
            question.setSectionId(section.getId());
            question.setQuestionText(q.get("question").asText());
            question.setOptionsJson(q.get("options").toString());
            question.setCorrectOption(q.get("correctOption").asText());
            question.setExplanation(q.has("explanation") ? q.get("explanation").asText() : null);
            questionMapper.insert(question);
        }
        return section;
    }

    private String buildPrompt(ListeningLibraryTopic topic) {
        return "Generate a short English listening passage (3-5 sentences, CEFR level "
                + topic.getLevel() + ") about the topic \"" + topic.getName()
                + "\", plus 4-5 multiple-choice comprehension questions with 4 options "
                + "(A-D), the correct option letter, and a one-sentence explanation. "
                + "Respond as JSON: {\"passage\": string, \"questions\": "
                + "[{\"question\": string, \"options\": [string,string,string,string], "
                + "\"correctOption\": \"A\"|\"B\"|\"C\"|\"D\", \"explanation\": string}]}";
    }

    private JsonNode parseJson(String rawJson) {
        try {
            return objectMapper.readTree(rawJson);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse LLM listening library response", e);
        }
    }
}
```

Adjust `llmClient.generate(prompt).text()` to the real `LlmClient` method
name/return-type accessor confirmed in Step 1/3 before finalizing — do not
guess if it differs.

- [ ] **Step 6: Run test to verify it passes**

Run: `cd RemeLearning && ./mvnw -pl services/english-service -am test -Dtest=LlmListeningLibraryGeneratorTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add RemeLearning/services/english-service/src/main/java/com/remelearning/english/listening/library/generator RemeLearning/services/english-service/src/main/java/com/remelearning/english/listening/library/mapper/ListeningLibraryQuestionMapper.java RemeLearning/services/english-service/src/main/resources/mapper/listening/library/ListeningLibraryQuestionMapper.xml RemeLearning/services/english-service/src/test/java/com/remelearning/english/listening/library/generator
git commit -m "feat(english-service): add LLM-backed listening library section generator"
```

---

## Task 5: `listening.library` — service layer (topics, start section, submit answers, history)

**Files:**
- Create: `RemeLearning/services/english-service/src/main/java/com/remelearning/english/listening/library/service/ListeningLibraryService.java`
- Create: `RemeLearning/services/english-service/src/main/java/com/remelearning/english/listening/library/service/ListeningLibraryServiceImpl.java`
- Create: `RemeLearning/services/english-service/src/main/java/com/remelearning/english/listening/library/dto/ListeningLibraryTopicDto.java`
- Create: `RemeLearning/services/english-service/src/main/java/com/remelearning/english/listening/library/dto/ListeningLibrarySectionDto.java`
- Create: `RemeLearning/services/english-service/src/main/java/com/remelearning/english/listening/library/dto/SubmitListeningAnswersRequest.java`
- Create: `RemeLearning/services/english-service/src/main/java/com/remelearning/english/listening/library/dto/SubmitListeningAnswersResponse.java`
- Test: `RemeLearning/services/english-service/src/test/java/com/remelearning/english/listening/library/service/ListeningLibraryServiceImplTest.java`

**Interfaces:**
- Consumes: `TopicProgressCalculator.compute` (Task 1), all mappers from
  Task 3/4, `LlmListeningLibraryGenerator.generateSection` (Task 4), the
  existing `listening` package's TTS client identified in Task 4 Step 1.
- Produces:
  `ListeningLibraryService.getTopics(Long userId): List<ListeningLibraryTopicDto>`,
  `.startOrResumeSection(Long userId, Long topicId): ListeningLibrarySectionDto`,
  `.submitAnswers(Long userId, Long sectionId, SubmitListeningAnswersRequest req): SubmitListeningAnswersResponse`,
  `.getHistory(Long userId): List<ListeningLibraryAttempt>` (reuse the
  attempt domain object directly as the history read model — no separate
  history DTO needed, matching how `GrammarLibraryServiceImpl.getHistory`
  does it; confirm this by reading that method before implementing).

- [ ] **Step 1: Read `GrammarLibraryServiceImpl.java` in full**

This is the direct structural template: note its `getTopics` method (how it
loads topics, computes progress, joins in per-topic stats), its
start-session method (creates or reuses an in-progress session), and its
`getHistory` method.

- [ ] **Step 2: Write DTOs**

```java
package com.remelearning.english.listening.library.dto;

public class ListeningLibraryTopicDto {
    private Long id;
    private String name;
    private String level;
    private String status;
    private Double bestScore;

    public ListeningLibraryTopicDto(Long id, String name, String level, String status, Double bestScore) {
        this.id = id;
        this.name = name;
        this.level = level;
        this.status = status;
        this.bestScore = bestScore;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getLevel() { return level; }
    public String getStatus() { return status; }
    public Double getBestScore() { return bestScore; }
}
```

```java
package com.remelearning.english.listening.library.dto;

import java.util.List;

public class ListeningLibrarySectionDto {
    private Long sectionId;
    private String passageText;
    private String audioUrl;
    private List<QuestionView> questions;

    public ListeningLibrarySectionDto(Long sectionId, String passageText, String audioUrl, List<QuestionView> questions) {
        this.sectionId = sectionId;
        this.passageText = passageText;
        this.audioUrl = audioUrl;
        this.questions = questions;
    }

    public Long getSectionId() { return sectionId; }
    public String getPassageText() { return passageText; }
    public String getAudioUrl() { return audioUrl; }
    public List<QuestionView> getQuestions() { return questions; }

    public record QuestionView(Long questionId, String questionText, List<String> options) {
    }
}
```

```java
package com.remelearning.english.listening.library.dto;

import java.util.List;

public class SubmitListeningAnswersRequest {
    private Long userId;
    private List<AnswerItem> answers;

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public List<AnswerItem> getAnswers() { return answers; }
    public void setAnswers(List<AnswerItem> answers) { this.answers = answers; }

    public record AnswerItem(Long questionId, String selectedOption) {
    }
}
```

```java
package com.remelearning.english.listening.library.dto;

public class SubmitListeningAnswersResponse {
    private double score;
    private int correctCount;
    private int totalQuestions;
    private boolean topicPassed;

    public SubmitListeningAnswersResponse(double score, int correctCount, int totalQuestions, boolean topicPassed) {
        this.score = score;
        this.correctCount = correctCount;
        this.totalQuestions = totalQuestions;
        this.topicPassed = topicPassed;
    }

    public double getScore() { return score; }
    public int getCorrectCount() { return correctCount; }
    public int getTotalQuestions() { return totalQuestions; }
    public boolean isTopicPassed() { return topicPassed; }
}
```

- [ ] **Step 3: Write the failing test for `getTopics` and `submitAnswers`**

```java
package com.remelearning.english.listening.library.service;

import com.remelearning.english.listening.library.domain.*;
import com.remelearning.english.listening.library.dto.SubmitListeningAnswersRequest;
import com.remelearning.english.listening.library.generator.LlmListeningLibraryGenerator;
import com.remelearning.english.listening.library.mapper.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ListeningLibraryServiceImplTest {

    @Test
    void getTopicsMarksFirstTopicInProgressWhenNothingPassed() {
        ListeningLibraryTopicMapper topicMapper = mock(ListeningLibraryTopicMapper.class);
        ListeningLibrarySectionMapper sectionMapper = mock(ListeningLibrarySectionMapper.class);
        ListeningLibraryQuestionMapper questionMapper = mock(ListeningLibraryQuestionMapper.class);
        ListeningLibraryProgressMapper progressMapper = mock(ListeningLibraryProgressMapper.class);
        ListeningLibraryAttemptMapper attemptMapper = mock(ListeningLibraryAttemptMapper.class);
        LlmListeningLibraryGenerator generator = mock(LlmListeningLibraryGenerator.class);

        ListeningLibraryTopic topic1 = new ListeningLibraryTopic();
        topic1.setId(1L); topic1.setName("Travel"); topic1.setLevel("A1"); topic1.setSequenceOrder(1);
        ListeningLibraryTopic topic2 = new ListeningLibraryTopic();
        topic2.setId(2L); topic2.setName("Food"); topic2.setLevel("A1"); topic2.setSequenceOrder(2);

        when(topicMapper.findAll()).thenReturn(List.of(topic1, topic2));
        when(progressMapper.findByUserId(10L)).thenReturn(List.of());

        ListeningLibraryServiceImpl service = new ListeningLibraryServiceImpl(
                topicMapper, sectionMapper, questionMapper, progressMapper, attemptMapper, generator, null);

        var result = service.getTopics(10L);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getStatus()).isEqualTo("IN_PROGRESS");
        assertThat(result.get(1).getStatus()).isEqualTo("LOCKED");
    }

    @Test
    void submitAnswersComputesScoreAndMarksTopicPassedAboveThreshold() {
        ListeningLibraryTopicMapper topicMapper = mock(ListeningLibraryTopicMapper.class);
        ListeningLibrarySectionMapper sectionMapper = mock(ListeningLibrarySectionMapper.class);
        ListeningLibraryQuestionMapper questionMapper = mock(ListeningLibraryQuestionMapper.class);
        ListeningLibraryProgressMapper progressMapper = mock(ListeningLibraryProgressMapper.class);
        ListeningLibraryAttemptMapper attemptMapper = mock(ListeningLibraryAttemptMapper.class);
        LlmListeningLibraryGenerator generator = mock(LlmListeningLibraryGenerator.class);

        ListeningLibrarySection section = new ListeningLibrarySection();
        section.setId(100L); section.setTopicId(1L);
        when(sectionMapper.findById(100L)).thenReturn(section);

        ListeningLibraryQuestion q1 = new ListeningLibraryQuestion();
        q1.setId(1L); q1.setSectionId(100L); q1.setCorrectOption("A");
        ListeningLibraryQuestion q2 = new ListeningLibraryQuestion();
        q2.setId(2L); q2.setSectionId(100L); q2.setCorrectOption("B");
        when(questionMapper.findBySectionId(100L)).thenReturn(List.of(q1, q2));

        SubmitListeningAnswersRequest req = new SubmitListeningAnswersRequest();
        req.setUserId(10L);
        req.setAnswers(List.of(
                new SubmitListeningAnswersRequest.AnswerItem(1L, "A"),
                new SubmitListeningAnswersRequest.AnswerItem(2L, "B")));

        ListeningLibraryServiceImpl service = new ListeningLibraryServiceImpl(
                topicMapper, sectionMapper, questionMapper, progressMapper, attemptMapper, generator, null);

        var response = service.submitAnswers(10L, 100L, req);

        assertThat(response.getCorrectCount()).isEqualTo(2);
        assertThat(response.getScore()).isEqualTo(1.0);
        assertThat(response.isTopicPassed()).isTrue();
        verify(attemptMapper).insert(any());
        verify(progressMapper).upsert(any());
    }
}
```

- [ ] **Step 4: Run test to verify it fails**

Run: `cd RemeLearning && ./mvnw -pl services/english-service -am test -Dtest=ListeningLibraryServiceImplTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL — `ListeningLibraryServiceImpl` does not exist.

- [ ] **Step 5: Write the service interface and implementation**

```java
package com.remelearning.english.listening.library.service;

import com.remelearning.english.listening.library.domain.ListeningLibraryAttempt;
import com.remelearning.english.listening.library.dto.*;
import java.util.List;

public interface ListeningLibraryService {
    List<ListeningLibraryTopicDto> getTopics(Long userId);
    ListeningLibrarySectionDto startOrResumeSection(Long userId, Long topicId);
    SubmitListeningAnswersResponse submitAnswers(Long userId, Long sectionId, SubmitListeningAnswersRequest req);
    List<ListeningLibraryAttempt> getHistory(Long userId);
}
```

```java
package com.remelearning.english.listening.library.service;

import com.remelearning.common.storage.S3StorageClient;
import com.remelearning.english.listening.library.domain.*;
import com.remelearning.english.listening.library.dto.*;
import com.remelearning.english.listening.library.generator.LlmListeningLibraryGenerator;
import com.remelearning.english.listening.library.mapper.*;
import com.remelearning.common.library.TopicProgressCalculator;
import com.remelearning.common.library.TopicStatus;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

// Fixed-topic listening library: exposes topic progress, starts/resumes a
// Section (a passage + MCQ questions, top-up via LLM when the topic has no
// section yet), scores submitted answers, and updates topic PASSED status.
@Service
public class ListeningLibraryServiceImpl implements ListeningLibraryService {

    private static final double PASS_THRESHOLD = 0.7;

    private final ListeningLibraryTopicMapper topicMapper;
    private final ListeningLibrarySectionMapper sectionMapper;
    private final ListeningLibraryQuestionMapper questionMapper;
    private final ListeningLibraryProgressMapper progressMapper;
    private final ListeningLibraryAttemptMapper attemptMapper;
    private final LlmListeningLibraryGenerator generator;
    private final S3StorageClient storageClient;

    public ListeningLibraryServiceImpl(
            ListeningLibraryTopicMapper topicMapper,
            ListeningLibrarySectionMapper sectionMapper,
            ListeningLibraryQuestionMapper questionMapper,
            ListeningLibraryProgressMapper progressMapper,
            ListeningLibraryAttemptMapper attemptMapper,
            LlmListeningLibraryGenerator generator,
            S3StorageClient storageClient) {
        this.topicMapper = topicMapper;
        this.sectionMapper = sectionMapper;
        this.questionMapper = questionMapper;
        this.progressMapper = progressMapper;
        this.attemptMapper = attemptMapper;
        this.generator = generator;
        this.storageClient = storageClient;
    }

    @Override
    public List<ListeningLibraryTopicDto> getTopics(Long userId) {
        List<ListeningLibraryTopic> topics = topicMapper.findAll().stream()
                .sorted(Comparator.comparing(ListeningLibraryTopic::getSequenceOrder))
                .toList();
        Map<Long, ListeningLibraryTopicProgress> progressByTopic = progressMapper.findByUserId(userId).stream()
                .collect(Collectors.toMap(ListeningLibraryTopicProgress::getTopicId, p -> p));
        Set<Long> passedTopicIds = progressByTopic.values().stream()
                .filter(p -> "PASSED".equals(p.getStatus()))
                .map(ListeningLibraryTopicProgress::getTopicId)
                .collect(Collectors.toSet());

        List<Long> orderedIds = topics.stream().map(ListeningLibraryTopic::getId).toList();
        Map<Long, TopicStatus> statusByTopicId = TopicProgressCalculator.compute(orderedIds, passedTopicIds);

        return topics.stream()
                .map(t -> new ListeningLibraryTopicDto(
                        t.getId(), t.getName(), t.getLevel(),
                        statusByTopicId.get(t.getId()).name(),
                        progressByTopic.containsKey(t.getId()) ? progressByTopic.get(t.getId()).getBestScore() : null))
                .toList();
    }

    @Override
    public ListeningLibrarySectionDto startOrResumeSection(Long userId, Long topicId) {
        ListeningLibraryTopic topic = topicMapper.findById(topicId);
        List<ListeningLibrarySection> existing = sectionMapper.findByTopicId(topicId);
        ListeningLibrarySection section = existing.isEmpty()
                ? generator.generateSection(topic)
                : existing.get(existing.size() - 1);

        List<ListeningLibraryQuestion> questions = questionMapper.findBySectionId(section.getId());
        List<ListeningLibrarySectionDto.QuestionView> questionViews = questions.stream()
                .map(q -> new ListeningLibrarySectionDto.QuestionView(
                        q.getId(), q.getQuestionText(), parseOptions(q.getOptionsJson())))
                .toList();

        String audioUrl = section.getAudioStorageKey() != null
                ? storageClient.presignedUrl(section.getAudioStorageKey())
                : null;

        return new ListeningLibrarySectionDto(section.getId(), section.getPassageText(), audioUrl, questionViews);
    }

    @Override
    public SubmitListeningAnswersResponse submitAnswers(Long userId, Long sectionId, SubmitListeningAnswersRequest req) {
        ListeningLibrarySection section = sectionMapper.findById(sectionId);
        List<ListeningLibraryQuestion> questions = questionMapper.findBySectionId(sectionId);
        Map<Long, String> correctByQuestionId = questions.stream()
                .collect(Collectors.toMap(ListeningLibraryQuestion::getId, ListeningLibraryQuestion::getCorrectOption));

        int correctCount = 0;
        for (var answer : req.getAnswers()) {
            if (Objects.equals(correctByQuestionId.get(answer.questionId()), answer.selectedOption())) {
                correctCount++;
            }
        }
        int total = questions.size();
        double score = total == 0 ? 0.0 : (double) correctCount / total;
        boolean passed = score >= PASS_THRESHOLD;

        ListeningLibraryAttempt attempt = new ListeningLibraryAttempt();
        attempt.setUserId(userId);
        attempt.setSectionId(sectionId);
        attempt.setScore(score);
        attempt.setCorrectCount(correctCount);
        attempt.setTotalQuestions(total);
        attempt.setStartedAt(OffsetDateTime.now());
        attempt.setCompletedAt(OffsetDateTime.now());
        attemptMapper.insert(attempt);

        ListeningLibraryTopicProgress progress = new ListeningLibraryTopicProgress();
        progress.setUserId(userId);
        progress.setTopicId(section.getTopicId());
        progress.setStatus(passed ? "PASSED" : "IN_PROGRESS");
        progress.setBestScore(score);
        progress.setUpdatedAt(OffsetDateTime.now());
        progressMapper.upsert(progress);

        return new SubmitListeningAnswersResponse(score, correctCount, total, passed);
    }

    @Override
    public List<ListeningLibraryAttempt> getHistory(Long userId) {
        return attemptMapper.findByUserId(userId);
    }

    private List<String> parseOptions(String optionsJson) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper()
                    .readValue(optionsJson, new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {});
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse question options", e);
        }
    }
}
```

Confirm `S3StorageClient`'s presigned-URL method name before finalizing (grep
`RemeLearning/common/src/main/java/com/remelearning/common/storage/S3StorageClient.java`)
— substitute the real method name if `presignedUrl` doesn't match.

- [ ] **Step 6: Run test to verify it passes**

Run: `cd RemeLearning && ./mvnw -pl services/english-service -am test -Dtest=ListeningLibraryServiceImplTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS (2 tests)

- [ ] **Step 7: Commit**

```bash
git add RemeLearning/services/english-service/src/main/java/com/remelearning/english/listening/library/service RemeLearning/services/english-service/src/main/java/com/remelearning/english/listening/library/dto RemeLearning/services/english-service/src/test/java/com/remelearning/english/listening/library/service
git commit -m "feat(english-service): add listening library service layer"
```

---

## Task 6: `listening.library` — controller + docs

**Files:**
- Create: `RemeLearning/services/english-service/src/main/java/com/remelearning/english/listening/library/controller/ListeningLibraryController.java`
- Modify: `RemeLearning/services/english-service/openapi.yaml`
- Modify: `RemeLearning/docs/API.md`
- Modify: `RemeLearning/docs/sequence/english-service/overview.md` (add link to new file)
- Create: `RemeLearning/docs/sequence/english-service/listening-library.md`
- Modify: `RemeLearning/docs/flow/english-service-data-flow.md`
- Modify: `RemeLearning/services/english-service/README.md`

**Interfaces:**
- Consumes: `ListeningLibraryService` from Task 5.
- Produces: REST endpoints listed in Step 1 below, response bodies wrapped
  in `com.remelearning.common.response.ApiResponse<T>` (check how
  `GrammarLibraryController` wraps its responses and match exactly).

- [ ] **Step 1: Read `GrammarLibraryController.java` in full**

Match its `@RestController`/`@RequestMapping`/`ApiResponse.success(...)`
wrapping style exactly.

- [ ] **Step 2: Write the controller**

```java
package com.remelearning.english.listening.library.controller;

import com.remelearning.common.response.ApiResponse;
import com.remelearning.english.listening.library.dto.*;
import com.remelearning.english.listening.library.service.ListeningLibraryService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/learn/listening/library")
public class ListeningLibraryController {

    private final ListeningLibraryService service;

    public ListeningLibraryController(ListeningLibraryService service) {
        this.service = service;
    }

    @GetMapping("/{userId}/topics")
    public ApiResponse<List<ListeningLibraryTopicDto>> getTopics(@PathVariable Long userId) {
        return ApiResponse.success(service.getTopics(userId));
    }

    @PostMapping("/{userId}/topics/{topicId}/sections")
    public ApiResponse<ListeningLibrarySectionDto> startSection(
            @PathVariable Long userId, @PathVariable Long topicId) {
        return ApiResponse.success(service.startOrResumeSection(userId, topicId));
    }

    @PostMapping("/sections/{sectionId}/answers")
    public ApiResponse<SubmitListeningAnswersResponse> submitAnswers(
            @PathVariable Long sectionId, @RequestBody SubmitListeningAnswersRequest req) {
        return ApiResponse.success(service.submitAnswers(req.getUserId(), sectionId, req));
    }

    @GetMapping("/{userId}/sections/history")
    public ApiResponse<?> getHistory(@PathVariable Long userId) {
        return ApiResponse.success(service.getHistory(userId));
    }
}
```

Confirm `ApiResponse.success(...)` is the real factory method name (check
`common/src/main/java/com/remelearning/common/response/ApiResponse.java`) —
substitute if different.

- [ ] **Step 3: Verify compile**

Run: `cd RemeLearning && ./mvnw -pl services/english-service -am compile`
Expected: BUILD SUCCESS

- [ ] **Step 4: Update `openapi.yaml`**

Read the existing `/api/v1/learn/grammar/library/...` block in
`RemeLearning/services/english-service/openapi.yaml` for the YAML shape, and
add an equivalent block for the 4 endpoints in Step 2 under
`/api/v1/learn/listening/library/...`, with request/response schemas matching
the DTOs from Task 5.

- [ ] **Step 5: Update `docs/API.md`**

Read `RemeLearning/docs/API.md`'s mục lục and the existing grammar-library
section for the exact heading/table format, then add a new "Listening
Library" section (mục lục entry + summary table row + detailed endpoint
docs) for the 4 endpoints.

- [ ] **Step 6: Add sequence diagram**

Read one existing file under `RemeLearning/docs/sequence/english-service/`
(e.g. the grammar library one) for the mermaid `sequenceDiagram` format, then
write `RemeLearning/docs/sequence/english-service/listening-library.md`
covering: `GET topics` → topic/progress read; `POST sections` →
generator top-up path (LLM + TTS) vs. reuse-existing-section path; `POST
answers` → scoring + progress upsert. Add a link to this file from
`RemeLearning/docs/sequence/english-service/overview.md`.

- [ ] **Step 7: Update data-flow doc**

Add a row/section to `RemeLearning/docs/flow/english-service-data-flow.md`
describing the transform: `ListeningLibraryTopic` (DB) → `ListeningLibraryTopicDto`
(with computed `status`); LLM JSON → `ListeningLibrarySection` +
`ListeningLibraryQuestion[]` (persisted); submitted answers →
`SubmitListeningAnswersResponse` + `ListeningLibraryAttempt` (persisted) +
`ListeningLibraryTopicProgress` upsert.

- [ ] **Step 8: Update `english-service/README.md`**

Add the 4 new endpoints and the `listening.library` package to whatever
section already lists `listening`'s endpoints/packages.

- [ ] **Step 9: Commit**

```bash
git add RemeLearning/services/english-service/src/main/java/com/remelearning/english/listening/library/controller RemeLearning/services/english-service/openapi.yaml RemeLearning/docs/API.md RemeLearning/docs/sequence/english-service RemeLearning/docs/flow/english-service-data-flow.md RemeLearning/services/english-service/README.md
git commit -m "feat(english-service): expose listening library REST API + docs"
```

---

## Task 7: `speaking.library` — migration + domain + mapper + generator + service + controller + docs

**Files:** mirror every file from Tasks 3-6, substituting `listening` →
`speaking`, `passage`/`questions` → `sentences`, MCQ scoring → phoneme/word
scoring.
- Create: `RemeLearning/services/english-service/src/main/resources/db/migration/V<next+1>__speaking_library.sql`
- Create: `.../speaking/library/domain/{SpeakingLibraryTopic,SpeakingLibrarySection,SpeakingLibrarySentence,SpeakingLibraryTopicProgress,SpeakingLibraryAttempt}.java`
- Create: `.../speaking/library/mapper/{SpeakingLibraryTopicMapper,SpeakingLibrarySectionMapper,SpeakingLibrarySentenceMapper,SpeakingLibraryProgressMapper,SpeakingLibraryAttemptMapper}.java` + matching XML under `mapper/speaking/library/`
- Create: `.../speaking/library/generator/LlmSpeakingLibraryGenerator.java`
- Create: `.../speaking/library/service/{SpeakingLibraryService,SpeakingLibraryServiceImpl}.java` + DTOs
- Create: `.../speaking/library/controller/SpeakingLibraryController.java`
- Modify: `openapi.yaml`, `docs/API.md`, `docs/sequence/english-service/speaking-library.md` (new), `docs/flow/english-service-data-flow.md`, `english-service/README.md`
- Test: `SpeakingLibraryServiceImplTest.java`, `LlmSpeakingLibraryGeneratorTest.java` (mirroring Tasks 4/5's tests)

**Interfaces:**
- Consumes: `TopicProgressCalculator` (Task 1); the existing `speaking`
  package's phoneme/word scoring service — find it via
  `grep -rn "class.*ScoringService\|phonemeScore\|wordScore" RemeLearning/services/english-service/src/main/java/com/remelearning/english/speaking/`
  and reuse it directly (inject the interface, do not reimplement scoring).
- Produces: `SpeakingLibraryService.getTopics(Long userId)`,
  `.startOrResumeSection(Long userId, Long topicId): SpeakingLibrarySectionDto`
  (returns sentence list + sample audio URLs),
  `.submitSentenceAttempt(Long userId, Long sectionId, Long sentenceId, MultipartFile recordedAudio): SentenceAttemptResultDto`,
  `.finishSection(Long userId, Long sectionId): FinishSectionResponse` (marks
  topic PASSED if every sentence in the section has an attempt scoring above
  threshold), `.getHistory(Long userId)`.

- [ ] **Step 1: Read the phoneme/word scoring service used by `speaking`'s existing (non-library) attempt flow in full**

Find and read the class matched by the grep above, plus
`SpeakingLearnController`'s existing multipart `attempts` endpoint, to copy
the exact multipart request handling (`@RequestParam MultipartFile`, form
fields) and the scoring call signature.

- [ ] **Step 2: Write the migration**

```sql
-- V<next+1>__speaking_library.sql
-- Fixed 60-topic taxonomy for the Speaking library, mirroring
-- grammar_library_topics' topic set (same names/order, independent ids/table).
CREATE TABLE speaking_library_topics (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    level VARCHAR(16) NOT NULL,
    sequence_order INT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Copy all 60 INSERT rows from V17__grammar_library.sql here (same values
-- as the listening migration in Task 3 Step 2), targeting this table.

CREATE TABLE speaking_library_sections (
    id BIGSERIAL PRIMARY KEY,
    topic_id BIGINT NOT NULL REFERENCES speaking_library_topics(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE speaking_library_sentences (
    id BIGSERIAL PRIMARY KEY,
    section_id BIGINT NOT NULL REFERENCES speaking_library_sections(id),
    sentence_text TEXT NOT NULL,
    ipa VARCHAR(512),
    sample_audio_storage_key VARCHAR(512),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE speaking_library_topic_progress (
    user_id BIGINT NOT NULL,
    topic_id BIGINT NOT NULL REFERENCES speaking_library_topics(id),
    status VARCHAR(16) NOT NULL,
    best_score DOUBLE PRECISION,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, topic_id)
);

CREATE TABLE speaking_library_attempts (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    section_id BIGINT NOT NULL REFERENCES speaking_library_sections(id),
    sentence_id BIGINT NOT NULL REFERENCES speaking_library_sentences(id),
    phoneme_score DOUBLE PRECISION NOT NULL,
    word_score DOUBLE PRECISION NOT NULL,
    recorded_audio_storage_key VARCHAR(512),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_speaking_library_sentences_section ON speaking_library_sentences(section_id);
CREATE INDEX idx_speaking_library_attempts_user ON speaking_library_attempts(user_id);
CREATE INDEX idx_speaking_library_attempts_sentence ON speaking_library_attempts(sentence_id);
```

- [ ] **Step 3: Write domain classes**

Same `@Data` Lombok style as Task 3 Step 4, one class per table above with
fields matching each column (camelCase).

- [ ] **Step 4: Write mapper interfaces + XML**

Same shape as Task 3 Step 6, one mapper per table: `SpeakingLibraryTopicMapper.findAll/findById`,
`SpeakingLibrarySectionMapper.findByTopicId/findById/insert`,
`SpeakingLibrarySentenceMapper.findBySectionId/insert`,
`SpeakingLibraryProgressMapper.findByUserId/upsert`,
`SpeakingLibraryAttemptMapper.insert/findByUserId/findBySectionId`.

- [ ] **Step 5: Write the failing generator test, then the generator**

Mirror Task 4 Steps 3-6 exactly, with the LLM prompt asking for N sample
sentences (with IPA) for a topic/level instead of a passage+questions:

```java
private String buildPrompt(SpeakingLibraryTopic topic) {
    return "Generate 5 English sentences (CEFR level " + topic.getLevel()
            + ") a learner should practice reading aloud for the topic \""
            + topic.getName() + "\". For each sentence include its IPA "
            + "transcription. Respond as JSON: {\"sentences\": "
            + "[{\"text\": string, \"ipa\": string}]}";
}
```

The generator persists each sentence via `SpeakingLibrarySentenceMapper.insert`,
then synthesizes sample audio per sentence via the same TTS client used in
Task 4, storing the key on `sampleAudioStorageKey`.

- [ ] **Step 6: Write the failing service test, then the service**

Mirror Task 5 Steps 3-5. Key difference from listening: `submitSentenceAttempt`
scores one sentence at a time via the reused phoneme/word scoring service
(Step 1), inserting one `SpeakingLibraryAttempt` row per call — it does not
finalize the section. `finishSection` checks, for every sentence in the
section, whether at least one attempt scored above `PASS_THRESHOLD = 0.7` on
both `phonemeScore` and `wordScore`; if all sentences pass, upsert topic
progress to `PASSED` with `bestScore` = average of each sentence's best
attempt score.

- [ ] **Step 7: Write the controller**

Mirror Task 6 Step 2, with `submitSentenceAttempt` as a multipart endpoint
(`@RequestMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)`,
`@RequestParam("audio") MultipartFile audio`) matching the exact multipart
handling read in Step 1, plus a `POST /sections/{sectionId}/finish` endpoint.

- [ ] **Step 8: Update docs**

Mirror Task 6 Steps 4-8 for the speaking endpoints (`openapi.yaml`,
`docs/API.md`, new `docs/sequence/english-service/speaking-library.md` +
overview.md link, `docs/flow/english-service-data-flow.md`,
`english-service/README.md`).

- [ ] **Step 9: Run all new tests**

Run: `cd RemeLearning && ./mvnw -pl services/english-service -am test -Dtest=SpeakingLibraryServiceImplTest,LlmSpeakingLibraryGeneratorTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS

- [ ] **Step 10: Commit**

```bash
git add RemeLearning/services/english-service/src/main/resources/db/migration RemeLearning/services/english-service/src/main/java/com/remelearning/english/speaking/library RemeLearning/services/english-service/src/main/resources/mapper/speaking/library RemeLearning/services/english-service/src/test/java/com/remelearning/english/speaking/library RemeLearning/services/english-service/openapi.yaml RemeLearning/docs/API.md RemeLearning/docs/sequence/english-service RemeLearning/docs/flow/english-service-data-flow.md RemeLearning/services/english-service/README.md
git commit -m "feat(english-service): add speaking library (schema, generator, service, API, docs)"
```

---

## Task 8: `bff-service` — proxy listening + speaking library endpoints

**Files:**
- Modify: `RemeLearning/services/bff-service/src/main/java/com/remelearning/bff/client/EnglishServiceClient.java`
- Modify: `RemeLearning/services/bff-service/src/main/java/com/remelearning/bff/controller/LearnerController.java`
- Create: `RemeLearning/services/bff-service/src/main/java/com/remelearning/bff/dto/{ListeningLibraryTopicDto,ListeningLibrarySectionDto,SubmitListeningAnswersRequest,SubmitListeningAnswersResponse,SpeakingLibraryTopicDto,SpeakingLibrarySectionDto,SentenceAttemptResultDto,FinishSpeakingSectionResponse}.java`
- Modify: `RemeLearning/services/bff-service/openapi.yaml`
- Modify: `RemeLearning/docs/API.md`
- Create: `RemeLearning/docs/sequence/bff-service/listening-speaking-library.md`
- Modify: `RemeLearning/docs/flow/bff-service-data-flow.md` (if this file
  exists — check with `ls RemeLearning/docs/flow/`; if `bff-service` doesn't
  have its own data-flow doc, skip this file and note that in the commit
  message instead)
- Modify: `RemeLearning/services/bff-service/README.md`
- Test: find and mirror the existing vocabulary-library proxy test (added in
  commit `f67297a`) — locate it via
  `git log --all --oneline -- RemeLearning/services/bff-service/src/test | grep -i librar`

**Interfaces:**
- Consumes: the 4 listening + 6 speaking REST endpoints from Tasks 6/7.
- Produces: `EnglishServiceClient.getListeningLibraryTopics/startListeningSection/submitListeningAnswers/getListeningLibraryHistory`
  and the speaking equivalents, each returning `Mono<T>` (this is a WebFlux
  service — read `EnglishServiceClient`'s existing vocabulary-library proxy
  methods for the exact `WebClient` call pattern and `Mono` composition
  style before adding new ones).

- [ ] **Step 1: Read the existing vocabulary-library proxy methods in `EnglishServiceClient.java` and their routes in `LearnerController.java`**

This is the direct template — commit `f67297a` (per this repo's recent
history) added exactly this shape for vocabulary; copy its `WebClient.get()/post()`
call style, error handling (`.onErrorResume`), and how `LearnerController`
exposes each as a `/api/v1/learners/{userId}/...` route.

- [ ] **Step 2: Add the 4 listening + 6 speaking client methods to `EnglishServiceClient`**

Each method calls the corresponding downstream URL from Task 6/7 via the
existing `english` `WebClient` bean, decodes into the new bff-service DTOs
(Step 3), and wraps in `.onErrorResume` exactly like neighboring methods in
the same file.

- [ ] **Step 3: Write the bff-service DTOs**

One DTO class per response shape listed in the Files section, fields
matching the english-service DTOs from Tasks 5/7 (plain data holders, no
english-service class reuse — per the architecture rule that services never
share domain classes across the deployable boundary).

- [ ] **Step 4: Add routes to `LearnerController`**

Mirror the vocabulary-library routes' path structure
(`/api/v1/learners/{userId}/listening/library/...`,
`/api/v1/learners/{userId}/speaking/library/...`) and delegate to the new
`EnglishServiceClient` methods.

- [ ] **Step 5: Write/adapt tests**

Read the vocabulary-library proxy test found via the `git log` command
above, and write an equivalent test class for the listening/speaking client
methods — mocking the downstream client the same way (per
`bff-service`'s existing pattern of mocking `client/*Client` classes rather
than a real HTTP server).

- [ ] **Step 6: Run bff-service tests**

Run: `cd RemeLearning && ./mvnw -pl services/bff-service -am test`
Expected: BUILD SUCCESS, all tests pass.

- [ ] **Step 7: Update docs**

`openapi.yaml`, `docs/API.md`, new `docs/sequence/bff-service/listening-speaking-library.md`
(sequenceDiagram: FE → bff-service → english-service for each new route),
`bff-service/README.md`.

- [ ] **Step 8: Commit**

```bash
git add RemeLearning/services/bff-service
git commit -m "feat(bff-service): proxy listening/speaking library endpoints"
```

---

## Task 9: FE — Listening library tab

**Files:**
- Modify: `src/features/learn/listening/ListeningLearnPage.tsx` (in
  `RemeLearning_FE`)
- Create: `src/features/learn/listening/library/TopicLibraryPanel.tsx`
- Create: `src/features/learn/listening/library/SectionRunner.tsx`
- Create: `src/features/learn/listening/library/hooks.ts`
- Modify: `src/api/learners.ts` (add the 4 new API functions)

**Interfaces:**
- Consumes: `bff-service` routes from Task 8
  (`/api/v1/learners/{userId}/listening/library/topics`, etc.).
- Produces: `useListeningLibraryTopics(userId)`,
  `useStartListeningLibrarySection(userId)`, `useSubmitListeningAnswers(userId)`,
  `useListeningLibraryHistory(userId)` (react-query hooks, same naming
  convention as `useGrammarLibraryTopics` etc.).

- [ ] **Step 1: Read `src/features/learn/vocabulary/VocabularyLearnPage.tsx` and its `library/TopicLibraryPanel.tsx` in full**

This is the direct FE template — vocabulary's library tab starts a section
directly on topic click (no separate theory page), matching the spec's
decision for listening/speaking.

- [ ] **Step 2: Add API functions to `src/api/learners.ts`**

Follow the exact fetch-wrapper pattern already used for
`getGrammarLibraryTopics`/`startGrammarLibrarySession`/etc. in the same
file — add:

```typescript
export function getListeningLibraryTopics(userId: string) {
  return apiGet(`/api/v1/learners/${userId}/listening/library/topics`);
}

export function startListeningLibrarySection(userId: string, topicId: string) {
  return apiPost(`/api/v1/learners/${userId}/listening/library/topics/${topicId}/sections`, {});
}

export function submitListeningAnswers(
  userId: string,
  sectionId: string,
  answers: { questionId: string; selectedOption: string }[],
) {
  return apiPost(`/api/v1/learners/${userId}/listening/library/sections/${sectionId}/answers`, { answers });
}

export function getListeningLibraryHistory(userId: string) {
  return apiGet(`/api/v1/learners/${userId}/listening/library/sections/history`);
}
```

(Replace `apiGet`/`apiPost` with whatever the file's actual fetch-wrapper
functions are named — read the file first, do not assume these names.)

- [ ] **Step 3: Write `hooks.ts`**

```typescript
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  getListeningLibraryTopics,
  startListeningLibrarySection,
  submitListeningAnswers,
  getListeningLibraryHistory,
} from "@/api/learners";

export function useListeningLibraryTopics(userId: string) {
  return useQuery({
    queryKey: ["listening-library-topics", userId],
    queryFn: () => getListeningLibraryTopics(userId),
  });
}

export function useStartListeningLibrarySection(userId: string) {
  return useMutation({
    mutationFn: (topicId: string) => startListeningLibrarySection(userId, topicId),
  });
}

export function useSubmitListeningAnswers(userId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (vars: { sectionId: string; answers: { questionId: string; selectedOption: string }[] }) =>
      submitListeningAnswers(userId, vars.sectionId, vars.answers),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["listening-library-topics", userId] });
    },
  });
}

export function useListeningLibraryHistory(userId: string) {
  return useQuery({
    queryKey: ["listening-library-history", userId],
    queryFn: () => getListeningLibraryHistory(userId),
  });
}
```

(Match the exact react-query import path/version API already used in
`vocabulary/library/hooks.ts` — confirm `useMutation`/`useQuery` signatures
match this codebase's react-query major version before finalizing.)

- [ ] **Step 4: Write `TopicLibraryPanel.tsx`**

Clone `vocabulary/library/TopicLibraryPanel.tsx`'s JSX structure (card grid,
status badge, progress bar) verbatim, swapping its data hook for
`useListeningLibraryTopics` and its "start section" navigation/handler for
`useStartListeningLibrarySection`, landing on `SectionRunner` (Step 5) on
success instead of vocabulary's section runner.

- [ ] **Step 5: Write `SectionRunner.tsx`**

Renders the section's passage text, an `<audio>` element with `src={section.audioUrl}`,
and the MCQ question list (radio-button choice per question, matching the
existing MCQ rendering style used elsewhere in listening's non-library
practice flow — read `ListeningLearnPage.tsx`'s existing practice-mode MCQ
rendering for the exact component/props to reuse). On submit, calls
`useSubmitListeningAnswers`'s mutation and renders the score/correctCount
result plus a "Quay lại danh sách chủ đề" button.

- [ ] **Step 6: Add the "Thư viện" tab to `ListeningLearnPage.tsx`**

Read the file's current `Tabs` JSX (currently `practice`/`history` only, per
this session's earlier research) and add a third `TabsTrigger`/`TabsContent`
pair for `library`, in the same position/order grammar and vocabulary use
(practice, history, library), rendering `<TopicLibraryPanel userId={userId} />`.

- [ ] **Step 7: Manually verify in the browser**

Run: `cd RemeLearning_FE && npm run dev` (or the project's existing dev
script — check `package.json` if `npm run dev` isn't it), navigate to
`/learn/listening`, click the new "Thư viện" tab, confirm topics render with
correct LOCKED/IN_PROGRESS badges, click the first (unlocked) topic, confirm
a section loads with audio + questions, submit answers, confirm a score
displays and the topic's progress updates on return to the topic list.

- [ ] **Step 8: Commit**

```bash
cd RemeLearning_FE
git add src/features/learn/listening src/api/learners.ts
git commit -m "feat(listening): add Thư viện tab with topic-based sections"
```

---

## Task 10: FE — Speaking library tab

**Files:** mirror Task 9 exactly, substituting `listening` → `speaking`:
- Modify: `src/features/learn/speaking/SpeakingLearnPage.tsx`
- Create: `src/features/learn/speaking/library/TopicLibraryPanel.tsx`
- Create: `src/features/learn/speaking/library/SectionRunner.tsx`
- Create: `src/features/learn/speaking/library/hooks.ts`
- Modify: `src/api/learners.ts` (add the 6 new API functions — note the
  `submitSentenceAttempt` function uploads `FormData` with the recorded
  audio blob, not JSON — read how the existing non-library speaking
  practice attempt submission in `src/features/learn/speaking/hooks.ts`
  builds its `FormData` request and copy that exactly)

**Interfaces:**
- Consumes: `bff-service` routes from Task 8 for speaking.
- Produces: `useSpeakingLibraryTopics(userId)`,
  `useStartSpeakingLibrarySection(userId)`, `useSubmitSentenceAttempt(userId)`,
  `useFinishSpeakingSection(userId)`, `useSpeakingLibraryHistory(userId)`.

- [ ] **Step 1: Read `src/features/learn/speaking/hooks.ts`'s existing (non-library) attempt-submission function in full**

Copy its exact `FormData`/multipart upload pattern (recorder blob handling,
content-type) for the new `submitSentenceAttempt` function — this is the one
part of Task 9's mirror that differs materially (JSON body vs. multipart).

- [ ] **Step 2: Add the 6 API functions to `src/api/learners.ts`**

Mirror Task 9 Step 2's pattern for `getTopics`/`startSection`/`getHistory`;
for `submitSentenceAttempt`, build a `FormData` with the recorded audio blob
and post it (multipart), matching Step 1's pattern exactly. Add a 6th
function `finishSpeakingLibrarySection(userId, sectionId)`.

- [ ] **Step 3: Write `hooks.ts`**

Mirror Task 9 Step 3's shape with 5 hooks: `useSpeakingLibraryTopics`,
`useStartSpeakingLibrarySection`, `useSubmitSentenceAttempt`,
`useFinishSpeakingSection`, `useSpeakingLibraryHistory`.

- [ ] **Step 4: Write `TopicLibraryPanel.tsx`**

Mirror Task 9 Step 4.

- [ ] **Step 5: Write `SectionRunner.tsx`**

Renders each sentence in the section one at a time (sentence text + IPA +
sample-audio playback via `<audio src={sentence.sampleAudioUrl}>`), a record
button reusing the existing recorder component from speaking's non-library
practice flow (read `SpeakingLearnPage.tsx`'s practice-mode recorder usage
for the exact component name/props), submits via `useSubmitSentenceAttempt`
per sentence, shows phoneme/word score per sentence, and calls
`useFinishSpeakingSection` once every sentence has been attempted, then
shows the summary result.

- [ ] **Step 6: Add the "Thư viện" tab to `SpeakingLearnPage.tsx`**

Mirror Task 9 Step 6.

- [ ] **Step 7: Manually verify in the browser**

Navigate to `/learn/speaking`, click "Thư viện", start a section, record and
submit each sentence, confirm scores display and the section finishes with a
topic-progress update.

- [ ] **Step 8: Commit**

```bash
cd RemeLearning_FE
git add src/features/learn/speaking src/api/learners.ts
git commit -m "feat(speaking): add Thư viện tab with topic-based sections"
```

---

## Task 11: Business.md update

**Files:**
- Modify: `D:\Personal Project\RemeLearning_BA\Business.md` (separate
  repo/folder, not part of `RemeLearning_Project`)

- [ ] **Step 1: Read the existing Grammar/Vocabulary library section of `Business.md` in full**

Match its heading level, tone (plain-language business meaning, not a
technical contract), and level of detail.

- [ ] **Step 2: Add a "Thư viện Nghe hiểu" and "Thư viện Nói/phát âm" section**

Explain in Vietnamese, in business terms: learners now have a fixed 60-topic
curriculum for listening and speaking (same topic set as grammar/vocabulary,
for a consistent cross-skill learning path), must pass each topic in order
before the next unlocks, and each topic's practice content (passages/
questions for listening, sample sentences for speaking) is generated once
per topic by AI and reused across learners rather than regenerated per
attempt.

- [ ] **Step 3: Commit** (this is a separate git repo from `RemeLearning_Project`)

```bash
cd "D:\Personal Project\RemeLearning_BA"
git add Business.md
git commit -m "docs: document listening/speaking library business meaning"
```

---

## Self-Review Notes

- **Spec coverage:** §1 (common gating) → Tasks 1-2. §2 (listening schema) →
  Task 3. §2 (speaking schema) → Task 7. §3 (content generation) → Tasks 4,
  7. §4 (API) → Tasks 6, 7. §5 (bff proxy) → Task 8. §6 (FE) → Tasks 9-10.
  §7 (docs) → Tasks 6, 7, 8, 11. §8 (testing) → embedded per task (Tasks 1,
  2, 4, 5, 7, 8).
- **Type consistency:** `ListeningLibraryTopicDto`/`ListeningLibrarySectionDto`/
  `SubmitListeningAnswersRequest`/`SubmitListeningAnswersResponse` are defined
  once in Task 5 and reused as-is by Task 6's controller and Task 8's bff
  client (bff defines its own mirrored DTOs per the no-cross-service-Java-class
  rule — intentional, not a naming clash). `TopicProgressCalculator.compute`'s
  signature is fixed in Task 1 and consumed identically in Tasks 2, 5, 7 —
  no drift.
- **Known follow-up, not in scope of this plan:** sub-projects A (retry
  history entries into AI/Library mode across grammar/listening/speaking)
  and C (global loading overlay) from the original request — separate specs/
  plans, deliberately out of scope here per the earlier decomposition
  decision.
