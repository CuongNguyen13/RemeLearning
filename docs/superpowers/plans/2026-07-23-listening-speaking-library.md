# Listening/Speaking Library Tabs Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a topic-based "Thư viện" (library) tab to the Listening and Speaking
learn pages, mirroring the existing Grammar library's topic gating and
Vocabulary library's content-bank/Section mechanics.

**Architecture:** Two new Java packages in `english-service`
(`listening.library`, `speaking.library`), structurally cloned from
`grammar.library` for the topic-gating state machine (persisted
LOCKED/UNLOCKED/IN_PROGRESS/PASSED, explicit transition writes) and from
`vocabulary.library` for the LLM content-bank/Section shape. `bff-service`
proxies the new endpoints. `RemeLearning_FE` adds a "Thư viện" tab +
panel/runner/hooks per domain, cloned from the existing grammar/vocabulary
library FE code.

**Tech Stack:** Spring Boot 4.1 / Java 21, MyBatis, Flyway, PostgreSQL, Kafka
(no Kafka involvement in this feature), React + TypeScript + react-query
(existing FE stack).

## Revision note (2026-07-23)

The plan originally had two additional tasks (extracting topic-gating logic
into a shared `common` pure-function calculator, then refactoring
`GrammarLibraryServiceImpl`/`VocabularyLibraryServiceImpl` to use it). That
was dropped after discovering, mid-implementation, that grammar's gating is
a **persisted 4-state state machine** with explicit transition writes
(`markInProgress`/`markPassed`/`unlockIfLocked` against a
`grammar_topic_progress` table), not a pure recomputed 3-state function —
and vocabulary has **no** topic gating at all. Forcing both onto a shared
3-state pure function would have changed grammar's observable behavior
(dropping the `UNLOCKED` state) and had nothing real to remove on the
vocabulary side. Decision: leave `grammar.library`/`vocabulary.library`
untouched; `listening.library`/`speaking.library` clone grammar's persisted
gating pattern directly (own enum, own progress table, own mapper, same six
method names/semantics) — see Task 1 below, which now includes the gating
schema alongside the content schema.

## Global Constraints

- Java tests: plain JUnit 5 + AssertJ + `Mockito.mock(...)` — no `@Mock`, no
  `@ExtendWith(MockitoExtension.class)`, no `@SpringBootTest`, no integration
  tests. Match e.g. `GrammarLibraryServiceImplTest`.
- Comment non-trivial code blocks/methods in both Java and TS (per
  `CLAUDE.md` — this repo overrides the "no comments" default).
- Every new/changed REST endpoint requires: `openapi.yaml` of the owning
  service updated, `docs/API.md` updated (mục lục + bảng + chi tiết), a
  `docs/sequence/<Service>/*.md` sequence diagram, a `docs/flow/<service>-data-flow.md`
  update, and the service's own `README.md` updated — all in the same commit
  as the code.
- Business-meaning changes must update `Business.md` in
  `D:\Personal Project\RemeLearning_BA\Business.md` (separate repo/folder,
  not gitignored-in-this-repo) in the same change. **Note (learned in a
  prior plan's Task 14):** that folder has no `.git` of its own in some
  environments — if `git add`/`git commit` there fails because it's not a
  repository, edit the file on disk and note in your report that no commit
  was possible; do not treat this as a task failure.
- Flyway migrations are additive-only (`IF NOT EXISTS` not required here since
  these are brand-new tables) — pick the next unused `V__` version number by
  checking `RemeLearning/services/english-service/src/main/resources/db/migration/`.
- Every servlet-stack DB-backed service already has `Boot4CompatConfig` — no
  new one needed, these packages live inside `english-service`.
- `english-service`'s `@ComponentScan(basePackages = "com.remelearning")` and
  `@MapperScan` already cover new sub-packages under `com.remelearning.english.*`
  — no `EnglishServiceApplication` changes needed unless a new mapper package
  is added (it is — see Task 1 & 5).

---

## Task 1: `listening.library` — migration + domain + mapper (content + gating)

**Files:**
- Create: `RemeLearning/services/english-service/src/main/resources/db/migration/V<next>__listening_library.sql`
  (run `ls RemeLearning/services/english-service/src/main/resources/db/migration/` first to find the next free `V` number)
- Create: `RemeLearning/services/english-service/src/main/java/com/remelearning/english/listening/library/domain/ListeningLibraryTopic.java`
- Create: `RemeLearning/services/english-service/src/main/java/com/remelearning/english/listening/library/domain/ListeningLibrarySection.java`
- Create: `RemeLearning/services/english-service/src/main/java/com/remelearning/english/listening/library/domain/ListeningLibraryQuestion.java`
- Create: `RemeLearning/services/english-service/src/main/java/com/remelearning/english/listening/library/domain/ListeningTopicStatus.java`
- Create: `RemeLearning/services/english-service/src/main/java/com/remelearning/english/listening/library/domain/ListeningTopicProgress.java`
- Create: `RemeLearning/services/english-service/src/main/java/com/remelearning/english/listening/library/domain/ListeningLibraryAttempt.java`
- Create: `RemeLearning/services/english-service/src/main/java/com/remelearning/english/listening/library/mapper/ListeningLibraryTopicMapper.java`
- Create: `RemeLearning/services/english-service/src/main/resources/mapper/listening/library/ListeningLibraryTopicMapper.xml`
- Create: `RemeLearning/services/english-service/src/main/java/com/remelearning/english/listening/library/mapper/ListeningLibrarySectionMapper.java`
- Create: `RemeLearning/services/english-service/src/main/resources/mapper/listening/library/ListeningLibrarySectionMapper.xml`
- Create: `RemeLearning/services/english-service/src/main/java/com/remelearning/english/listening/library/mapper/ListeningTopicProgressMapper.java`
- Create: `RemeLearning/services/english-service/src/main/resources/mapper/listening/library/ListeningTopicProgressMapper.xml`
- Create: `RemeLearning/services/english-service/src/main/java/com/remelearning/english/listening/library/mapper/ListeningLibraryAttemptMapper.java`
- Create: `RemeLearning/services/english-service/src/main/resources/mapper/listening/library/ListeningLibraryAttemptMapper.xml`

**Interfaces:**
- Produces: domain classes with plain getters/setters (match
  `GrammarLibraryTopic.java`'s exact Lombok annotations — read it first) and:
  - `ListeningLibraryTopicMapper.findAll(): List<ListeningLibraryTopic>`,
    `.findById(Long id): ListeningLibraryTopic`,
    `.findBySequenceOrder(int sequenceOrder): ListeningLibraryTopic`
  - `ListeningLibrarySectionMapper.findByTopicId(Long topicId): List<ListeningLibrarySection>`,
    `.insert(ListeningLibrarySection s)`, `.findById(Long id): ListeningLibrarySection`
  - `ListeningTopicProgressMapper.findByUserIdAndTopicId(String userId, Long topicId): ListeningTopicProgress`,
    `.findByUserId(String userId): List<ListeningTopicProgress>`,
    `.bootstrapFirstTopic(String userId, Long topicId)`,
    `.unlockIfLocked(String userId, Long topicId)`,
    `.markInProgress(String userId, Long topicId)`,
    `.markPassed(String userId, Long topicId)`
  - `ListeningLibraryAttemptMapper.insert(ListeningLibraryAttempt a)`,
    `.findByUserId(String userId): List<ListeningLibraryAttempt>`

- [ ] **Step 1: Read the grammar library migration and gating mapper XML in full**

Read `RemeLearning/services/english-service/src/main/resources/db/migration/V17__grammar_library.sql`
in full (topic seed rows are copied verbatim below), plus
`RemeLearning/services/english-service/src/main/java/com/remelearning/english/grammar/library/mapper/GrammarTopicProgressMapper.java`
and its XML counterpart
(`RemeLearning/services/english-service/src/main/resources/mapper/grammar_library/GrammarTopicProgressMapper.xml`)
— these are the exact templates for this task's progress table/mapper.

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
    sequence_order INT NOT NULL UNIQUE,
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

-- Persisted topic-gating state machine, structurally identical to
-- grammar_topic_progress (see GrammarTopicProgressMapper.xml): status is a
-- plain VARCHAR (the enum mapping/validation lives in Java, not the schema),
-- and (user_id, topic_id) is unique so the mapper's ON CONFLICT upserts work.
CREATE TABLE listening_topic_progress (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(100) NOT NULL,
    topic_id BIGINT NOT NULL REFERENCES listening_library_topics(id),
    status VARCHAR(20) NOT NULL,
    unlocked_at TIMESTAMPTZ,
    passed_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (user_id, topic_id)
);

CREATE INDEX idx_listening_topic_progress_user_id ON listening_topic_progress(user_id);

CREATE TABLE listening_library_attempts (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(100) NOT NULL,
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

Note `user_id` is `VARCHAR(100)` (matching `grammar_topic_progress.user_id`
and `GrammarTopicProgress.userId: String`), not `BIGINT` — follow this
exactly since the service/mapper code below takes `String userId`
throughout, consistent with how grammar does it.

- [ ] **Step 3: Write the domain classes**

Match `GrammarLibraryTopic.java`'s exact annotation style (read it first —
likely plain getters/setters or Lombok `@Data`, confirm before writing).

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

Match `GrammarTopicStatus.java` exactly (4 values, this order):

```java
package com.remelearning.english.listening.library.domain;

/**
 * Progression state of one learner against one {@link ListeningLibraryTopic}: {@code LOCKED} (not
 * reachable yet), {@code UNLOCKED} (reachable, no section started), {@code IN_PROGRESS} (a section
 * started but not yet passed), {@code PASSED} (a section's score met the pass threshold).
 */
public enum ListeningTopicStatus {
    LOCKED,
    UNLOCKED,
    IN_PROGRESS,
    PASSED
}
```

Match `GrammarTopicProgress.java` field-for-field:

```java
package com.remelearning.english.listening.library.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ListeningTopicProgress {
    private Long id;
    private String userId;
    private Long topicId;
    private ListeningTopicStatus status;
    private Instant unlockedAt;
    private Instant passedAt;
    private Instant updatedAt;
}
```

```java
package com.remelearning.english.listening.library.domain;

import lombok.Data;
import java.time.OffsetDateTime;

@Data
public class ListeningLibraryAttempt {
    private Long id;
    private String userId;
    private Long sectionId;
    private Double score;
    private Integer correctCount;
    private Integer totalQuestions;
    private OffsetDateTime startedAt;
    private OffsetDateTime completedAt;
}
```

- [ ] **Step 4: Write the content mapper interfaces + XML**

Match `GrammarLibraryTopicMapper.java`/`.xml`'s style (namespace = fully
qualified mapper interface name, resultMap columns = snake_case of domain
fields):

```java
package com.remelearning.english.listening.library.mapper;

import com.remelearning.english.listening.library.domain.ListeningLibraryTopic;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface ListeningLibraryTopicMapper {
    List<ListeningLibraryTopic> findAll();
    ListeningLibraryTopic findById(@Param("id") Long id);
    ListeningLibraryTopic findBySequenceOrder(@Param("sequenceOrder") int sequenceOrder);
}
```

```java
package com.remelearning.english.listening.library.mapper;

import com.remelearning.english.listening.library.domain.ListeningLibrarySection;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface ListeningLibrarySectionMapper {
    List<ListeningLibrarySection> findByTopicId(@Param("topicId") Long topicId);
    ListeningLibrarySection findById(@Param("id") Long id);
    void insert(ListeningLibrarySection section);
}
```

```java
package com.remelearning.english.listening.library.mapper;

import com.remelearning.english.listening.library.domain.ListeningLibraryAttempt;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface ListeningLibraryAttemptMapper {
    void insert(ListeningLibraryAttempt attempt);
    List<ListeningLibraryAttempt> findByUserId(@Param("userId") String userId);
}
```

Write the three matching `.xml` files under
`RemeLearning/services/english-service/src/main/resources/mapper/listening/library/`,
copying the `resultMap`/`insert`/`select` XML shape from
`GrammarLibraryTopicMapper.xml` field-for-field.

- [ ] **Step 5: Write `ListeningTopicProgressMapper` — copy `GrammarTopicProgressMapper` exactly, substituting table/type names**

```java
package com.remelearning.english.listening.library.mapper;

import com.remelearning.english.listening.library.domain.ListeningTopicProgress;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface ListeningTopicProgressMapper {

    ListeningTopicProgress findByUserIdAndTopicId(@Param("userId") String userId, @Param("topicId") Long topicId);

    List<ListeningTopicProgress> findByUserId(@Param("userId") String userId);

    void bootstrapFirstTopic(@Param("userId") String userId, @Param("topicId") Long topicId);

    void unlockIfLocked(@Param("userId") String userId, @Param("topicId") Long topicId);

    void markInProgress(@Param("userId") String userId, @Param("topicId") Long topicId);

    void markPassed(@Param("userId") String userId, @Param("topicId") Long topicId);
}
```

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN" "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.remelearning.english.listening.library.mapper.ListeningTopicProgressMapper">

    <select id="findByUserIdAndTopicId" resultType="com.remelearning.english.listening.library.domain.ListeningTopicProgress">
        SELECT id, user_id, topic_id, status, unlocked_at, passed_at, updated_at
        FROM listening_topic_progress WHERE user_id = #{userId} AND topic_id = #{topicId}
    </select>

    <select id="findByUserId" resultType="com.remelearning.english.listening.library.domain.ListeningTopicProgress">
        SELECT id, user_id, topic_id, status, unlocked_at, passed_at, updated_at
        FROM listening_topic_progress WHERE user_id = #{userId}
    </select>

    <insert id="bootstrapFirstTopic">
        INSERT INTO listening_topic_progress (user_id, topic_id, status, unlocked_at)
        VALUES (#{userId}, #{topicId}, 'UNLOCKED', now())
        ON CONFLICT (user_id, topic_id) DO NOTHING
    </insert>

    <insert id="unlockIfLocked">
        INSERT INTO listening_topic_progress (user_id, topic_id, status, unlocked_at)
        VALUES (#{userId}, #{topicId}, 'UNLOCKED', now())
        ON CONFLICT (user_id, topic_id) DO UPDATE
            SET status = 'UNLOCKED', unlocked_at = now()
            WHERE listening_topic_progress.status = 'LOCKED'
    </insert>

    <update id="markInProgress">
        UPDATE listening_topic_progress SET status = 'IN_PROGRESS', updated_at = now()
        WHERE user_id = #{userId} AND topic_id = #{topicId}
    </update>

    <update id="markPassed">
        UPDATE listening_topic_progress SET status = 'PASSED', passed_at = now(), updated_at = now()
        WHERE user_id = #{userId} AND topic_id = #{topicId}
    </update>

</mapper>
```

Verify the exact `resultType`/column mapping style (camelCase auto-mapping
vs. explicit `resultMap`) against `GrammarTopicProgressMapper.xml` and match
it — if that file uses an explicit `<resultMap>` instead of relying on
MyBatis's default underscore-to-camelCase mapping, do the same here for
consistency with the rest of the mapper package.

Also write `ListeningLibraryAttemptMapper.xml` (straightforward
insert/select, no upsert logic needed).

- [ ] **Step 6: Verify the module compiles**

Run: `cd RemeLearning && ./mvnw -pl services/english-service -am compile`
Expected: BUILD SUCCESS

- [ ] **Step 7: Commit**

```bash
git add RemeLearning/services/english-service/src/main/resources/db/migration RemeLearning/services/english-service/src/main/java/com/remelearning/english/listening/library/domain RemeLearning/services/english-service/src/main/java/com/remelearning/english/listening/library/mapper RemeLearning/services/english-service/src/main/resources/mapper/listening/library
git commit -m "feat(english-service): add listening library schema, domain, mappers (content + gating)"
```

---

## Task 2: `listening.library` — LLM content generator

**Files:**
- Create: `RemeLearning/services/english-service/src/main/java/com/remelearning/english/listening/library/generator/LlmListeningLibraryGenerator.java`
- Create: `RemeLearning/services/english-service/src/main/java/com/remelearning/english/listening/library/mapper/ListeningLibraryQuestionMapper.java`
- Create: `RemeLearning/services/english-service/src/main/resources/mapper/listening/library/ListeningLibraryQuestionMapper.xml`
- Test: `RemeLearning/services/english-service/src/test/java/com/remelearning/english/listening/library/generator/LlmListeningLibraryGeneratorTest.java`

**Interfaces:**
- Consumes: `com.remelearning.common.ai.LlmClient` (existing — read
  `common/src/main/java/com/remelearning/common/ai/LlmClient.java` for the
  real method name/signature before writing any code that calls it; do not
  assume `.generate(String).text()` shown below without confirming). Also
  consumes whatever TTS client the existing `listening` package already uses
  for audio — find it via
  `grep -rn "TtsClient\|DialogueAudioSynthesizer" RemeLearning/services/english-service/src/main/java/com/remelearning/english/listening/`
  and inject the same interface/class.
- Produces: `LlmListeningLibraryGenerator.generateSection(ListeningLibraryTopic topic): ListeningLibrarySection`
  (persists the section + its questions + synthesizes/stores audio, returns
  the fully populated section).

- [ ] **Step 1: Read `LlmLibraryWordGenerator.java` in full**

This is the closest existing analog (LLM top-up into a library bank). Note
its constructor injection pattern, prompt-building approach, and how it
parses the LLM's JSON response into domain objects.

- [ ] **Step 2: Add `ListeningLibraryQuestionMapper`**

```java
package com.remelearning.english.listening.library.mapper;

import com.remelearning.english.listening.library.domain.ListeningLibraryQuestion;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface ListeningLibraryQuestionMapper {
    void insert(ListeningLibraryQuestion question);
    List<ListeningLibraryQuestion> findBySectionId(@Param("sectionId") Long sectionId);
}
```

Write the matching XML, same style as Task 1's mappers.

- [ ] **Step 3: Write the failing test**

```java
package com.remelearning.english.listening.library.generator;

import com.remelearning.common.ai.LlmClient;
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
        // Replace this stubbing with the real LlmClient method/return-type signature
        // confirmed in Step 1 of this task — do not guess if it differs.
        when(llmClient.generate(any())).thenReturn(llmJson);

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
        String rawJson = callLlm(buildPrompt(topic));
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

    private String callLlm(String prompt) {
        // Replace with the real LlmClient call signature confirmed in Step 1.
        return llmClient.generate(prompt);
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

Adjust `callLlm`'s body to the real `LlmClient` method name/return-type
confirmed in Step 1 before finalizing — do not guess if it differs.

- [ ] **Step 6: Run test to verify it passes**

Run: `cd RemeLearning && ./mvnw -pl services/english-service -am test -Dtest=LlmListeningLibraryGeneratorTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add RemeLearning/services/english-service/src/main/java/com/remelearning/english/listening/library/generator RemeLearning/services/english-service/src/main/java/com/remelearning/english/listening/library/mapper/ListeningLibraryQuestionMapper.java RemeLearning/services/english-service/src/main/resources/mapper/listening/library/ListeningLibraryQuestionMapper.xml RemeLearning/services/english-service/src/test/java/com/remelearning/english/listening/library/generator
git commit -m "feat(english-service): add LLM-backed listening library section generator"
```

---

## Task 3: `listening.library` — service layer (topics, start section, submit answers, history)

**Files:**
- Create: `RemeLearning/services/english-service/src/main/java/com/remelearning/english/listening/library/service/ListeningLibraryService.java`
- Create: `RemeLearning/services/english-service/src/main/java/com/remelearning/english/listening/library/service/ListeningLibraryServiceImpl.java`
- Create: `RemeLearning/services/english-service/src/main/java/com/remelearning/english/listening/library/dto/ListeningLibraryTopicDto.java`
- Create: `RemeLearning/services/english-service/src/main/java/com/remelearning/english/listening/library/dto/ListeningLibrarySectionDto.java`
- Create: `RemeLearning/services/english-service/src/main/java/com/remelearning/english/listening/library/dto/SubmitListeningAnswersRequest.java`
- Create: `RemeLearning/services/english-service/src/main/java/com/remelearning/english/listening/library/dto/SubmitListeningAnswersResponse.java`
- Test: `RemeLearning/services/english-service/src/test/java/com/remelearning/english/listening/library/service/ListeningLibraryServiceImplTest.java`

**Interfaces:**
- Consumes: all mappers from Task 1/2, `LlmListeningLibraryGenerator.generateSection`
  (Task 2), the existing `listening` package's TTS client identified in Task 2 Step 1.
- Produces:
  `ListeningLibraryService.getTopics(String userId): List<ListeningLibraryTopicDto>`,
  `.startOrResumeSection(String userId, Long topicId): ListeningLibrarySectionDto`,
  `.submitAnswers(String userId, Long sectionId, SubmitListeningAnswersRequest req): SubmitListeningAnswersResponse`,
  `.getHistory(String userId): List<ListeningLibraryAttempt>`.

**Note on `userId` type:** `String`, not `Long` — matching
`GrammarLibraryServiceImpl`'s convention and the `listening_topic_progress`
migration's `user_id VARCHAR(100)` column from Task 1. Do not use `Long`
anywhere in this task's signatures for `userId`.

- [ ] **Step 1: Read `GrammarLibraryServiceImpl.java` in full, specifically `listTopics`, `startSession`/`requireUnlockedOrInProgress`, and `finishSession`/`buildPassedResponse`**

These three methods are the direct templates for `getTopics`,
`startOrResumeSection`, and the pass-handling half of `submitAnswers`
respectively — the gating calls (`bootstrapFirstTopic`, guard check,
`markInProgress`, `markPassed`, `unlockIfLocked`) must match this file's
call sequence and semantics exactly (see Task 1's spec note for the full
quoted method bodies if this file has since changed).

- [ ] **Step 2: Write DTOs**

```java
package com.remelearning.english.listening.library.dto;

public class ListeningLibraryTopicDto {
    private Long id;
    private String name;
    private String level;
    private String status;

    public ListeningLibraryTopicDto(Long id, String name, String level, String status) {
        this.id = id;
        this.name = name;
        this.level = level;
        this.status = status;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getLevel() { return level; }
    public String getStatus() { return status; }
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
    private List<AnswerItem> answers;

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
    private Long nextTopicId;
    private boolean nextTopicUnlocked;

    public SubmitListeningAnswersResponse(double score, int correctCount, int totalQuestions,
            boolean topicPassed, Long nextTopicId, boolean nextTopicUnlocked) {
        this.score = score;
        this.correctCount = correctCount;
        this.totalQuestions = totalQuestions;
        this.topicPassed = topicPassed;
        this.nextTopicId = nextTopicId;
        this.nextTopicUnlocked = nextTopicUnlocked;
    }

    public double getScore() { return score; }
    public int getCorrectCount() { return correctCount; }
    public int getTotalQuestions() { return totalQuestions; }
    public boolean isTopicPassed() { return topicPassed; }
    public Long getNextTopicId() { return nextTopicId; }
    public boolean isNextTopicUnlocked() { return nextTopicUnlocked; }
}
```

- [ ] **Step 3: Write the failing tests**

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
    void getTopicsBootstrapsFirstTopicAndDefaultsMissingRowsToLocked() {
        ListeningLibraryTopicMapper topicMapper = mock(ListeningLibraryTopicMapper.class);
        ListeningLibrarySectionMapper sectionMapper = mock(ListeningLibrarySectionMapper.class);
        ListeningLibraryQuestionMapper questionMapper = mock(ListeningLibraryQuestionMapper.class);
        ListeningTopicProgressMapper progressMapper = mock(ListeningTopicProgressMapper.class);
        ListeningLibraryAttemptMapper attemptMapper = mock(ListeningLibraryAttemptMapper.class);
        LlmListeningLibraryGenerator generator = mock(LlmListeningLibraryGenerator.class);

        ListeningLibraryTopic topic1 = new ListeningLibraryTopic();
        topic1.setId(1L); topic1.setName("Travel"); topic1.setLevel("A1"); topic1.setSequenceOrder(1);
        ListeningLibraryTopic topic2 = new ListeningLibraryTopic();
        topic2.setId(2L); topic2.setName("Food"); topic2.setLevel("A1"); topic2.setSequenceOrder(2);

        when(topicMapper.findAll()).thenReturn(List.of(topic1, topic2));
        when(topicMapper.findBySequenceOrder(1)).thenReturn(topic1);
        when(progressMapper.findByUserId("user-1")).thenReturn(List.of());

        ListeningLibraryServiceImpl service = new ListeningLibraryServiceImpl(
                topicMapper, sectionMapper, questionMapper, progressMapper, attemptMapper, generator, null);

        var result = service.getTopics("user-1");

        verify(progressMapper).bootstrapFirstTopic("user-1", 1L);
        assertThat(result).hasSize(2);
        assertThat(result.get(1).getStatus()).isEqualTo("LOCKED");
    }

    @Test
    void startOrResumeSectionThrowsWhenTopicIsLocked() {
        ListeningLibraryTopicMapper topicMapper = mock(ListeningLibraryTopicMapper.class);
        ListeningLibrarySectionMapper sectionMapper = mock(ListeningLibrarySectionMapper.class);
        ListeningLibraryQuestionMapper questionMapper = mock(ListeningLibraryQuestionMapper.class);
        ListeningTopicProgressMapper progressMapper = mock(ListeningTopicProgressMapper.class);
        ListeningLibraryAttemptMapper attemptMapper = mock(ListeningLibraryAttemptMapper.class);
        LlmListeningLibraryGenerator generator = mock(LlmListeningLibraryGenerator.class);

        ListeningTopicProgress progress = ListeningTopicProgress.builder()
                .userId("user-1").topicId(1L).status(ListeningTopicStatus.LOCKED).build();
        when(progressMapper.findByUserIdAndTopicId("user-1", 1L)).thenReturn(progress);

        ListeningLibraryServiceImpl service = new ListeningLibraryServiceImpl(
                topicMapper, sectionMapper, questionMapper, progressMapper, attemptMapper, generator, null);

        org.junit.jupiter.api.Assertions.assertThrows(
                com.remelearning.common.exception.BusinessException.class,
                () -> service.startOrResumeSection("user-1", 1L));
    }

    @Test
    void submitAnswersComputesScoreMarksPassedAndUnlocksNextTopicAboveThreshold() {
        ListeningLibraryTopicMapper topicMapper = mock(ListeningLibraryTopicMapper.class);
        ListeningLibrarySectionMapper sectionMapper = mock(ListeningLibrarySectionMapper.class);
        ListeningLibraryQuestionMapper questionMapper = mock(ListeningLibraryQuestionMapper.class);
        ListeningTopicProgressMapper progressMapper = mock(ListeningTopicProgressMapper.class);
        ListeningLibraryAttemptMapper attemptMapper = mock(ListeningLibraryAttemptMapper.class);
        LlmListeningLibraryGenerator generator = mock(LlmListeningLibraryGenerator.class);

        ListeningLibraryTopic topic = new ListeningLibraryTopic();
        topic.setId(1L); topic.setSequenceOrder(1);
        ListeningLibrarySection section = new ListeningLibrarySection();
        section.setId(100L); section.setTopicId(1L);
        when(sectionMapper.findById(100L)).thenReturn(section);
        when(topicMapper.findById(1L)).thenReturn(topic);

        ListeningLibraryTopic nextTopic = new ListeningLibraryTopic();
        nextTopic.setId(2L); nextTopic.setSequenceOrder(2);
        when(topicMapper.findBySequenceOrder(2)).thenReturn(nextTopic);
        when(progressMapper.findByUserIdAndTopicId("user-1", 2L)).thenReturn(
                ListeningTopicProgress.builder().userId("user-1").topicId(2L)
                        .status(ListeningTopicStatus.UNLOCKED).build());

        ListeningLibraryQuestion q1 = new ListeningLibraryQuestion();
        q1.setId(1L); q1.setSectionId(100L); q1.setCorrectOption("A");
        ListeningLibraryQuestion q2 = new ListeningLibraryQuestion();
        q2.setId(2L); q2.setSectionId(100L); q2.setCorrectOption("B");
        when(questionMapper.findBySectionId(100L)).thenReturn(List.of(q1, q2));

        SubmitListeningAnswersRequest req = new SubmitListeningAnswersRequest();
        req.setAnswers(List.of(
                new SubmitListeningAnswersRequest.AnswerItem(1L, "A"),
                new SubmitListeningAnswersRequest.AnswerItem(2L, "B")));

        ListeningLibraryServiceImpl service = new ListeningLibraryServiceImpl(
                topicMapper, sectionMapper, questionMapper, progressMapper, attemptMapper, generator, null);

        var response = service.submitAnswers("user-1", 100L, req);

        assertThat(response.getCorrectCount()).isEqualTo(2);
        assertThat(response.getScore()).isEqualTo(1.0);
        assertThat(response.isTopicPassed()).isTrue();
        assertThat(response.getNextTopicId()).isEqualTo(2L);
        verify(attemptMapper).insert(any());
        verify(progressMapper).markPassed("user-1", 1L);
        verify(progressMapper).unlockIfLocked("user-1", 2L);
    }
}
```

Confirm `com.remelearning.common.exception.BusinessException` is the real
exception type/package used by `requireUnlockedOrInProgress` in
`GrammarLibraryServiceImpl` (per Step 1) before finalizing this test.

- [ ] **Step 4: Run tests to verify they fail**

Run: `cd RemeLearning && ./mvnw -pl services/english-service -am test -Dtest=ListeningLibraryServiceImplTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL — `ListeningLibraryServiceImpl` does not exist.

- [ ] **Step 5: Write the service interface and implementation**

```java
package com.remelearning.english.listening.library.service;

import com.remelearning.english.listening.library.domain.ListeningLibraryAttempt;
import com.remelearning.english.listening.library.dto.*;
import java.util.List;

public interface ListeningLibraryService {
    List<ListeningLibraryTopicDto> getTopics(String userId);
    ListeningLibrarySectionDto startOrResumeSection(String userId, Long topicId);
    SubmitListeningAnswersResponse submitAnswers(String userId, Long sectionId, SubmitListeningAnswersRequest req);
    List<ListeningLibraryAttempt> getHistory(String userId);
}
```

```java
package com.remelearning.english.listening.library.service;

import com.remelearning.common.exception.BusinessException;
import com.remelearning.common.storage.S3StorageClient;
import com.remelearning.english.listening.library.domain.*;
import com.remelearning.english.listening.library.dto.*;
import com.remelearning.english.listening.library.generator.LlmListeningLibraryGenerator;
import com.remelearning.english.listening.library.mapper.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

// Fixed-topic listening library: exposes topic progress (gating cloned from
// GrammarLibraryServiceImpl's LOCKED/UNLOCKED/IN_PROGRESS/PASSED state
// machine), starts/resumes a Section (top-up via LLM when the topic has no
// section yet), scores submitted answers, and unlocks the next topic on pass.
@Service
public class ListeningLibraryServiceImpl implements ListeningLibraryService {

    private static final double PASS_THRESHOLD = 0.7;
    private static final int FIRST_SEQUENCE_ORDER = 1;

    private final ListeningLibraryTopicMapper topicMapper;
    private final ListeningLibrarySectionMapper sectionMapper;
    private final ListeningLibraryQuestionMapper questionMapper;
    private final ListeningTopicProgressMapper progressMapper;
    private final ListeningLibraryAttemptMapper attemptMapper;
    private final LlmListeningLibraryGenerator generator;
    private final S3StorageClient storageClient;

    public ListeningLibraryServiceImpl(
            ListeningLibraryTopicMapper topicMapper,
            ListeningLibrarySectionMapper sectionMapper,
            ListeningLibraryQuestionMapper questionMapper,
            ListeningTopicProgressMapper progressMapper,
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
    @Transactional
    public List<ListeningLibraryTopicDto> getTopics(String userId) {
        ListeningLibraryTopic firstTopic = topicMapper.findBySequenceOrder(FIRST_SEQUENCE_ORDER);
        if (firstTopic != null) {
            progressMapper.bootstrapFirstTopic(userId, firstTopic.getId());
        }
        Map<Long, ListeningTopicStatus> statusByTopicId = new HashMap<>();
        for (ListeningTopicProgress progress : progressMapper.findByUserId(userId)) {
            statusByTopicId.put(progress.getTopicId(), progress.getStatus());
        }
        return topicMapper.findAll().stream()
                .map(t -> new ListeningLibraryTopicDto(
                        t.getId(), t.getName(), t.getLevel(),
                        statusByTopicId.getOrDefault(t.getId(), ListeningTopicStatus.LOCKED).name()))
                .toList();
    }

    @Override
    @Transactional
    public ListeningLibrarySectionDto startOrResumeSection(String userId, Long topicId) {
        requireUnlockedOrInProgress(userId, topicId);
        ListeningLibraryTopic topic = topicMapper.findById(topicId);
        List<ListeningLibrarySection> existing = sectionMapper.findByTopicId(topicId);
        ListeningLibrarySection section = existing.isEmpty()
                ? generator.generateSection(topic)
                : existing.get(existing.size() - 1);
        progressMapper.markInProgress(userId, topicId);

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

    // Mirrors GrammarLibraryServiceImpl.requireUnlockedOrInProgress: only LOCKED
    // is rejected (a missing row counts as LOCKED); UNLOCKED/IN_PROGRESS/PASSED all pass through.
    private void requireUnlockedOrInProgress(String userId, Long topicId) {
        ListeningTopicProgress progress = progressMapper.findByUserIdAndTopicId(userId, topicId);
        ListeningTopicStatus status = progress == null ? ListeningTopicStatus.LOCKED : progress.getStatus();
        if (status == ListeningTopicStatus.LOCKED) {
            throw BusinessException.forbidden("Listening topic is locked for this learner: topicId=" + topicId);
        }
    }

    @Override
    @Transactional
    public SubmitListeningAnswersResponse submitAnswers(String userId, Long sectionId, SubmitListeningAnswersRequest req) {
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

        Long nextTopicId = null;
        boolean nextTopicUnlocked = false;
        if (passed) {
            ListeningLibraryTopic topic = topicMapper.findById(section.getTopicId());
            progressMapper.markPassed(userId, topic.getId());
            ListeningLibraryTopic nextTopic = topicMapper.findBySequenceOrder(topic.getSequenceOrder() + 1);
            if (nextTopic != null) {
                progressMapper.unlockIfLocked(userId, nextTopic.getId());
                ListeningTopicProgress nextProgress = progressMapper.findByUserIdAndTopicId(userId, nextTopic.getId());
                nextTopicUnlocked = nextProgress != null && nextProgress.getStatus() != ListeningTopicStatus.LOCKED;
                nextTopicId = nextTopic.getId();
            }
        }

        return new SubmitListeningAnswersResponse(score, correctCount, total, passed, nextTopicId, nextTopicUnlocked);
    }

    @Override
    public List<ListeningLibraryAttempt> getHistory(String userId) {
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

Confirm `BusinessException.forbidden(String)` and `S3StorageClient`'s
presigned-URL method name against the real classes (grep
`common/src/main/java/com/remelearning/common/exception/BusinessException.java`
and `common/src/main/java/com/remelearning/common/storage/S3StorageClient.java`)
— substitute the real method names if they differ from what's shown here.

- [ ] **Step 6: Run tests to verify they pass**

Run: `cd RemeLearning && ./mvnw -pl services/english-service -am test -Dtest=ListeningLibraryServiceImplTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS (3 tests)

- [ ] **Step 7: Commit**

```bash
git add RemeLearning/services/english-service/src/main/java/com/remelearning/english/listening/library/service RemeLearning/services/english-service/src/main/java/com/remelearning/english/listening/library/dto RemeLearning/services/english-service/src/test/java/com/remelearning/english/listening/library/service
git commit -m "feat(english-service): add listening library service layer (gating cloned from grammar)"
```

---

## Task 4: `listening.library` — controller + docs

**Files:**
- Create: `RemeLearning/services/english-service/src/main/java/com/remelearning/english/listening/library/controller/ListeningLibraryController.java`
- Modify: `RemeLearning/services/english-service/openapi.yaml`
- Modify: `RemeLearning/docs/API.md`
- Modify: `RemeLearning/docs/sequence/English_service/overview.md` (add link to new file — note the actual directory is `English_service`, capital E, per existing files; do not create a second `english-service` directory)
- Create: `RemeLearning/docs/sequence/English_service/listening-library.md`
- Modify: `RemeLearning/docs/flow/english-service-data-flow.md`
- Modify: `RemeLearning/services/english-service/README.md`

**Interfaces:**
- Consumes: `ListeningLibraryService` from Task 3.
- Produces: REST endpoints listed in Step 2 below, response bodies wrapped
  in `com.remelearning.common.response.ApiResponse<T>` (check how
  `GrammarLibraryController` wraps its responses and match exactly).

- [ ] **Step 1: Read `GrammarLibraryController.java` in full**

Match its `@RestController`/`@RequestMapping`/`ApiResponse` wrapping style
exactly.

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
    public ApiResponse<List<ListeningLibraryTopicDto>> getTopics(@PathVariable String userId) {
        return ApiResponse.success(service.getTopics(userId));
    }

    @PostMapping("/{userId}/topics/{topicId}/sections")
    public ApiResponse<ListeningLibrarySectionDto> startSection(
            @PathVariable String userId, @PathVariable Long topicId) {
        return ApiResponse.success(service.startOrResumeSection(userId, topicId));
    }

    @PostMapping("/{userId}/sections/{sectionId}/answers")
    public ApiResponse<SubmitListeningAnswersResponse> submitAnswers(
            @PathVariable String userId, @PathVariable Long sectionId,
            @RequestBody SubmitListeningAnswersRequest req) {
        return ApiResponse.success(service.submitAnswers(userId, sectionId, req));
    }

    @GetMapping("/{userId}/sections/history")
    public ApiResponse<?> getHistory(@PathVariable String userId) {
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
the DTOs from Task 3 (topic status values: `LOCKED`, `UNLOCKED`,
`IN_PROGRESS`, `PASSED`).

- [ ] **Step 5: Update `docs/API.md`**

Read `RemeLearning/docs/API.md`'s mục lục and the existing grammar-library
section for the exact heading/table format, then add a new "Listening
Library" section (mục lục entry + summary table row + detailed endpoint
docs) for the 4 endpoints.

- [ ] **Step 6: Add sequence diagram**

Read `RemeLearning/docs/sequence/English_service/grammar-library.md` for the
mermaid `sequenceDiagram` format, then write
`RemeLearning/docs/sequence/English_service/listening-library.md` covering:
`GET topics` → bootstrap-first-topic + progress read; `POST sections` →
gating guard, then generator top-up path (LLM + TTS) vs. reuse-existing-section
path, then `markInProgress`; `POST answers` → scoring, `markPassed` +
`unlockIfLocked` on the next topic when passed. Add a link to this file from
`RemeLearning/docs/sequence/English_service/overview.md`.

- [ ] **Step 7: Update data-flow doc**

Add a row/section to `RemeLearning/docs/flow/english-service-data-flow.md`
describing the transform: `ListeningLibraryTopic` (DB) + `ListeningTopicProgress`
(DB) → `ListeningLibraryTopicDto` (status computed via bootstrap + progress
lookup, defaulting missing rows to LOCKED); LLM JSON → `ListeningLibrarySection`
+ `ListeningLibraryQuestion[]` (persisted); submitted answers →
`SubmitListeningAnswersResponse` + `ListeningLibraryAttempt` (persisted) +
`ListeningTopicProgress` transition (`markPassed`/`unlockIfLocked`) on pass.

- [ ] **Step 8: Update `english-service/README.md`**

Add the 4 new endpoints and the `listening.library` package to whatever
section already lists `listening`'s endpoints/packages.

- [ ] **Step 9: Commit**

```bash
git add RemeLearning/services/english-service/src/main/java/com/remelearning/english/listening/library/controller RemeLearning/services/english-service/openapi.yaml RemeLearning/docs/API.md RemeLearning/docs/sequence/English_service RemeLearning/docs/flow/english-service-data-flow.md RemeLearning/services/english-service/README.md
git commit -m "feat(english-service): expose listening library REST API + docs"
```

---

## Task 5: `speaking.library` — full clone (schema, generator, service, controller, docs)

**Files:** mirror every file from Tasks 1-4, substituting `listening` →
`speaking`, `passage`/`questions` → `sentences`, MCQ scoring → phoneme/word
scoring.
- Create: `RemeLearning/services/english-service/src/main/resources/db/migration/V<next+1>__speaking_library.sql`
- Create: `.../speaking/library/domain/{SpeakingLibraryTopic,SpeakingLibrarySection,SpeakingLibrarySentence,SpeakingTopicStatus,SpeakingTopicProgress,SpeakingLibraryAttempt}.java`
- Create: `.../speaking/library/mapper/{SpeakingLibraryTopicMapper,SpeakingLibrarySectionMapper,SpeakingLibrarySentenceMapper,SpeakingTopicProgressMapper,SpeakingLibraryAttemptMapper}.java` + matching XML under `mapper/speaking/library/`
- Create: `.../speaking/library/generator/LlmSpeakingLibraryGenerator.java`
- Create: `.../speaking/library/service/{SpeakingLibraryService,SpeakingLibraryServiceImpl}.java` + DTOs
- Create: `.../speaking/library/controller/SpeakingLibraryController.java`
- Modify: `openapi.yaml`, `docs/API.md`, `docs/sequence/English_service/speaking-library.md` (new), `docs/flow/english-service-data-flow.md`, `english-service/README.md`
- Test: `SpeakingLibraryServiceImplTest.java`, `LlmSpeakingLibraryGeneratorTest.java` (mirroring Tasks 2/3's tests)

**Interfaces:**
- Consumes: the existing `speaking` package's phoneme/word scoring service —
  find it via
  `grep -rn "class.*ScoringService\|phonemeScore\|wordScore" RemeLearning/services/english-service/src/main/java/com/remelearning/english/speaking/`
  and reuse it directly (inject the interface, do not reimplement scoring).
- Produces: `SpeakingLibraryService.getTopics(String userId)`,
  `.startOrResumeSection(String userId, Long topicId): SpeakingLibrarySectionDto`
  (returns sentence list + sample audio URLs),
  `.submitSentenceAttempt(String userId, Long sectionId, Long sentenceId, MultipartFile recordedAudio): SentenceAttemptResultDto`,
  `.finishSection(String userId, Long sectionId): FinishSectionResponse` (marks
  topic PASSED + unlocks next topic — same `markPassed`/`unlockIfLocked` calls
  as Task 3 — if every sentence in the section has an attempt scoring above
  threshold), `.getHistory(String userId)`.

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
    sequence_order INT NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Copy all 60 INSERT rows from V17__grammar_library.sql here (same values
-- as the listening migration in Task 1 Step 2), targeting this table.

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

-- Persisted topic-gating state machine, identical shape to
-- listening_topic_progress (Task 1) / grammar_topic_progress.
CREATE TABLE speaking_topic_progress (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(100) NOT NULL,
    topic_id BIGINT NOT NULL REFERENCES speaking_library_topics(id),
    status VARCHAR(20) NOT NULL,
    unlocked_at TIMESTAMPTZ,
    passed_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (user_id, topic_id)
);

CREATE INDEX idx_speaking_topic_progress_user_id ON speaking_topic_progress(user_id);

CREATE TABLE speaking_library_attempts (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(100) NOT NULL,
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

One class per table above, same `@Data`/`@Builder` Lombok style as Task 1
Step 3 — `SpeakingTopicStatus` is the exact same 4 values
(`LOCKED, UNLOCKED, IN_PROGRESS, PASSED`) as `ListeningTopicStatus`, and
`SpeakingTopicProgress` has the exact same fields as `ListeningTopicProgress`
(`id, userId, topicId, status, unlockedAt, passedAt, updatedAt`).

- [ ] **Step 4: Write mapper interfaces + XML**

Same shape as Task 1 Steps 4-5, one mapper per table:
`SpeakingLibraryTopicMapper.findAll/findById/findBySequenceOrder`,
`SpeakingLibrarySectionMapper.findByTopicId/findById/insert`,
`SpeakingLibrarySentenceMapper.findBySectionId/insert`,
`SpeakingTopicProgressMapper` (identical six methods to
`ListeningTopicProgressMapper`, same XML upsert logic, table name swapped),
`SpeakingLibraryAttemptMapper.insert/findByUserId/findBySectionId`.

- [ ] **Step 5: Write the failing generator test, then the generator**

Mirror Task 2 Steps 3-6 exactly, with the LLM prompt asking for N sample
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
Task 2, storing the key on `sampleAudioStorageKey`.

- [ ] **Step 6: Write the failing service test, then the service**

Mirror Task 3 Steps 3-5, reusing the exact same `requireUnlockedOrInProgress`
guard shape (copy-paste with `Speaking*` types), `bootstrapFirstTopic` in
`getTopics`, `markInProgress` in `startOrResumeSection`. Key difference from
listening: `submitSentenceAttempt` scores one sentence at a time via the
reused phoneme/word scoring service (Step 1), inserting one
`SpeakingLibraryAttempt` row per call — it does not touch topic progress.
`finishSection` checks, for every sentence in the section, whether at least
one attempt scored above `PASS_THRESHOLD = 0.7` on both `phonemeScore` and
`wordScore`; if all sentences pass, call `progressMapper.markPassed(userId,
topicId)` then `unlockIfLocked` on the next topic — same two calls as
`submitAnswers`'s pass branch in Task 3.

- [ ] **Step 7: Write the controller**

Mirror Task 4 Step 2, with `submitSentenceAttempt` as a multipart endpoint
(`@RequestMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)`,
`@RequestParam("audio") MultipartFile audio`) matching the exact multipart
handling read in Step 1, plus a `POST /{userId}/sections/{sectionId}/finish` endpoint.

- [ ] **Step 8: Update docs**

Mirror Task 4 Steps 4-8 for the speaking endpoints (`openapi.yaml`,
`docs/API.md`, new `docs/sequence/English_service/speaking-library.md` +
overview.md link, `docs/flow/english-service-data-flow.md`,
`english-service/README.md`).

- [ ] **Step 9: Run all new tests**

Run: `cd RemeLearning && ./mvnw -pl services/english-service -am test -Dtest=SpeakingLibraryServiceImplTest,LlmSpeakingLibraryGeneratorTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS

- [ ] **Step 10: Commit**

```bash
git add RemeLearning/services/english-service/src/main/resources/db/migration RemeLearning/services/english-service/src/main/java/com/remelearning/english/speaking/library RemeLearning/services/english-service/src/main/resources/mapper/speaking/library RemeLearning/services/english-service/src/test/java/com/remelearning/english/speaking/library RemeLearning/services/english-service/openapi.yaml RemeLearning/docs/API.md RemeLearning/docs/sequence/English_service RemeLearning/docs/flow/english-service-data-flow.md RemeLearning/services/english-service/README.md
git commit -m "feat(english-service): add speaking library (schema, generator, service, API, docs)"
```

---

## Task 6: `bff-service` — proxy listening + speaking library endpoints

**Files:**
- Modify: `RemeLearning/services/bff-service/src/main/java/com/remelearning/bff/client/EnglishServiceClient.java`
- Modify: `RemeLearning/services/bff-service/src/main/java/com/remelearning/bff/controller/LearnerController.java`
- Create: `RemeLearning/services/bff-service/src/main/java/com/remelearning/bff/dto/{ListeningLibraryTopicDto,ListeningLibrarySectionDto,SubmitListeningAnswersRequest,SubmitListeningAnswersResponse,SpeakingLibraryTopicDto,SpeakingLibrarySectionDto,SentenceAttemptResultDto,FinishSpeakingSectionResponse}.java`
- Modify: `RemeLearning/services/bff-service/openapi.yaml`
- Modify: `RemeLearning/docs/API.md`
- Create: `RemeLearning/docs/sequence/bff-service/listening-speaking-library.md`
  (check the actual existing directory casing under `RemeLearning/docs/sequence/`
  first — mirror whatever the bff-service sequence docs directory is
  actually named, e.g. it may be `Bff_service` like `English_service`)
- Modify: `RemeLearning/docs/flow/bff-service-data-flow.md` (if this file
  exists — check with `ls RemeLearning/docs/flow/`; if `bff-service` doesn't
  have its own data-flow doc, skip this file and note that in the commit
  message instead)
- Modify: `RemeLearning/services/bff-service/README.md`
- Test: find and mirror the existing vocabulary-library proxy test (added in
  commit `f67297a`) — locate it via
  `git log --all --oneline -- RemeLearning/services/bff-service/src/test | grep -i librar`

**Interfaces:**
- Consumes: the 4 listening + 8 speaking (6 CRUD + finish + history, per
  Task 5) REST endpoints from Tasks 4/5.
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

Each method calls the corresponding downstream URL from Task 4/5 via the
existing `english` `WebClient` bean, decodes into the new bff-service DTOs
(Step 3), and wraps in `.onErrorResume` exactly like neighboring methods in
the same file.

- [ ] **Step 3: Write the bff-service DTOs**

One DTO class per response shape listed in the Files section, fields
matching the english-service DTOs from Tasks 3/5 (plain data holders, no
english-service class reuse — per the architecture rule that services never
share domain classes across the deployable boundary). Topic status field is
a `String` with possible values `LOCKED`/`UNLOCKED`/`IN_PROGRESS`/`PASSED`.

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

`openapi.yaml`, `docs/API.md`, new sequence doc (sequenceDiagram: FE →
bff-service → english-service for each new route), `bff-service/README.md`.

- [ ] **Step 8: Commit**

```bash
git add RemeLearning/services/bff-service
git commit -m "feat(bff-service): proxy listening/speaking library endpoints"
```

---

## Task 7: FE — Listening library tab

**Files:**
- Modify: `src/features/learn/listening/ListeningLearnPage.tsx` (in
  `RemeLearning_FE`)
- Create: `src/features/learn/listening/library/TopicLibraryPanel.tsx`
- Create: `src/features/learn/listening/library/SectionRunner.tsx`
- Create: `src/features/learn/listening/library/hooks.ts`
- Modify: `src/api/learners.ts` (add the 4 new API functions)

**Interfaces:**
- Consumes: `bff-service` routes from Task 6
  (`/api/v1/learners/{userId}/listening/library/topics`, etc.).
- Produces: `useListeningLibraryTopics(userId)`,
  `useStartListeningLibrarySection(userId)`, `useSubmitListeningAnswers(userId)`,
  `useListeningLibraryHistory(userId)` (react-query hooks, same naming
  convention as `useGrammarLibraryTopics` etc.).

- [ ] **Step 1: Read `src/features/learn/grammar/library/TopicLibraryPanel.tsx` and `hooks.ts` in full**

This is the direct FE template for topic status rendering (grammar has the
same 4-state LOCKED/UNLOCKED/IN_PROGRESS/PASSED badges this feature now
also uses — unlike vocabulary, which has no status badges at all). Note how
it renders each status as a badge/disabled-state and what happens on
clicking a `LOCKED` vs. unlocked card.

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
`grammar/library/hooks.ts` — confirm `useMutation`/`useQuery` signatures
match this codebase's react-query major version before finalizing.)

- [ ] **Step 4: Write `TopicLibraryPanel.tsx`**

Clone `grammar/library/TopicLibraryPanel.tsx`'s JSX structure (card grid,
4-state status badge including `UNLOCKED`, click-to-enter behavior) verbatim,
swapping its data hook for `useListeningLibraryTopics` and its "start
section" navigation/handler for `useStartListeningLibrarySection`, landing
on `SectionRunner` (Step 5) on success.

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
correct LOCKED/UNLOCKED/IN_PROGRESS badges, click the first (unlocked) topic,
confirm a section loads with audio + questions, submit answers, confirm a
score displays and the next topic unlocks on return to the topic list.

- [ ] **Step 8: Commit**

```bash
cd RemeLearning_FE
git add src/features/learn/listening src/api/learners.ts
git commit -m "feat(listening): add Thư viện tab with topic-based sections"
```

---

## Task 8: FE — Speaking library tab

**Files:** mirror Task 7 exactly, substituting `listening` → `speaking`:
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
- Consumes: `bff-service` routes from Task 6 for speaking.
- Produces: `useSpeakingLibraryTopics(userId)`,
  `useStartSpeakingLibrarySection(userId)`, `useSubmitSentenceAttempt(userId)`,
  `useFinishSpeakingSection(userId)`, `useSpeakingLibraryHistory(userId)`.

- [ ] **Step 1: Read `src/features/learn/speaking/hooks.ts`'s existing (non-library) attempt-submission function in full**

Copy its exact `FormData`/multipart upload pattern (recorder blob handling,
content-type) for the new `submitSentenceAttempt` function — this is the one
part of Task 7's mirror that differs materially (JSON body vs. multipart).

- [ ] **Step 2: Add the 6 API functions to `src/api/learners.ts`**

Mirror Task 7 Step 2's pattern for `getTopics`/`startSection`/`getHistory`;
for `submitSentenceAttempt`, build a `FormData` with the recorded audio blob
and post it (multipart), matching Step 1's pattern exactly. Add a 6th
function `finishSpeakingLibrarySection(userId, sectionId)`.

- [ ] **Step 3: Write `hooks.ts`**

Mirror Task 7 Step 3's shape with 5 hooks: `useSpeakingLibraryTopics`,
`useStartSpeakingLibrarySection`, `useSubmitSentenceAttempt`,
`useFinishSpeakingSection`, `useSpeakingLibraryHistory`.

- [ ] **Step 4: Write `TopicLibraryPanel.tsx`**

Mirror Task 7 Step 4.

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

Mirror Task 7 Step 6.

- [ ] **Step 7: Manually verify in the browser**

Navigate to `/learn/speaking`, click "Thư viện", start a section, record and
submit each sentence, confirm scores display and the section finishes with a
topic-progress update (next topic unlocked).

- [ ] **Step 8: Commit**

```bash
cd RemeLearning_FE
git add src/features/learn/speaking src/api/learners.ts
git commit -m "feat(speaking): add Thư viện tab with topic-based sections"
```

---

## Task 9: Business.md update

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
before the next unlocks (same lock/unlock mechanic as the Grammar library),
and each topic's practice content (passages/questions for listening, sample
sentences for speaking) is generated once per topic by AI and reused across
learners rather than regenerated per attempt.

- [ ] **Step 3: Commit if possible**

```bash
cd "D:\Personal Project\RemeLearning_BA"
git add Business.md
git commit -m "docs: document listening/speaking library business meaning"
```

If this folder has no `.git` repository (confirmed in a prior plan's Task
14), the edit still lands on disk — report that no commit was possible
rather than treating it as a failure.

---

## Self-Review Notes

- **Spec coverage:** Gating design (spec §1, cloned from grammar) → Tasks 1
  (schema), 3 (service). Content schema (spec §2) → Tasks 1 (listening), 5
  (speaking). Content generation (spec §3) → Tasks 2, 5. API (spec §4) →
  Tasks 4, 5. bff proxy (spec §5) → Task 6. FE (spec §6) → Tasks 7-8. Docs
  (spec §7) → Tasks 4, 5, 6, 9. Testing (spec §8) → embedded per task (Tasks
  1, 2, 3, 5, 6).
- **Type consistency:** `userId` is `String` everywhere in this plan
  (matching `grammar_topic_progress.user_id VARCHAR(100)` and
  `GrammarTopicProgress.userId: String`) — Task 1's migration, Task 3's
  service/DTOs, and Task 6's bff client all agree on this; earlier plan
  drafts inconsistently used `Long userId` in places, corrected here.
  `ListeningTopicStatus`/`SpeakingTopicStatus` both have the same 4 values
  in the same order as `GrammarTopicStatus`, and `ListeningTopicProgress`/
  `SpeakingTopicProgress` have the same field set as `GrammarTopicProgress`
  — Task 5 explicitly calls out this parity so the speaking implementer
  doesn't drift from Task 1/3's shape.
- **Known follow-up, not in scope of this plan:** sub-projects A (retry
  history entries into AI/Library mode across grammar/listening/speaking)
  and C (global loading overlay) from the original request — separate specs/
  plans, deliberately out of scope here per the earlier decomposition
  decision.
