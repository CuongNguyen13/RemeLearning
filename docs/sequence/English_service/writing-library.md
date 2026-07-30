# Writing library: fixed catalogue over three independent taxonomy axes

Covers `com.remelearning.english.writing.library`
(`WritingLibraryController`/`WritingLibraryServiceImpl`), the "Thư viện" side of the writing skill —
see [writing-learn.md](writing-learn.md) for the "học thường" side and for why this domain has no
weak-point table of its own.

Structurally this clones `listening-library.md`'s LOCKED/UNLOCKED/IN_PROGRESS/PASSED gating and its
lazily-generated content chain. **The one behavioural difference from every other library: three
independent axes.**

| `taxonomy` | Content | Topics |
|---|---|---|
| `grammar` | The same 60-topic taxonomy as `grammar_library_topics`/`listening_library_topics` (same codes/names/order, independent ids) — each task must actually use that structure | 60 |
| `genre` | Real-world text types: personal message, formal email, descriptive/narrative paragraph, opinion & pros-cons essay, IELTS Task 1/2, complaint letter, cover letter, report, argumentative essay | 12 |
| `vocab_theme` | The `vocabulary_topics` theme set (V16): daily-life, food, travel, business, technology, health, education, environment | 8 |

`writing_library_topics.sequence_order` is unique **per taxonomy only** and restarts at 1 on each
axis; `UNIQUE (taxonomy, code)` likewise. Everything axis-related is therefore scoped: bootstrapping
opens the first topic of *each* axis independently, and "unlock the next topic" only ever looks within
the passed topic's own axis. A global `findBySequenceOrder` (as the other libraries have) would jump
between axes here, which is why the mapper takes the taxonomy as part of the key.

A topic is a chain of **3–6 prompts** (`targetPromptCount`, derived deterministically from `topicId`
so it is stable across calls without needing a column). The catalogue ships topics, not content: each
prompt is LLM-generated the first time a learner reaches that position and then persisted, so leaving
and coming back shows the same task. A topic only flips to `PASSED` once the chain has reached full
length **and** every prompt in it is passed (score ≥ 0.7).

## 1. List topics on one axis (`GET /api/v1/learn/writing/library/{userId}/topics?taxonomy=...`)

```mermaid
sequenceDiagram
    participant Caller
    participant Ctrl as WritingLibraryController
    participant Svc as WritingLibraryServiceImpl
    participant TMapper as WritingLibraryTopicMapper
    participant PMapper as WritingLibraryPromptMapper
    participant PgMapper as WritingTopicProgressMapper
    participant AMapper as WritingLibraryAttemptMapper
    participant DB as reme_english DB

    Caller->>Ctrl: GET /{userId}/topics?taxonomy=grammar
    Ctrl->>Svc: getTopics(userId, taxonomy)
    Svc->>Svc: WritingTaxonomy.fromCode(taxonomy)
    alt unknown axis
        Svc-->>Ctrl: BusinessException.badRequest -> 400
        Note over Svc: a 400, not an empty list - an empty list would<br/>look like a missing catalogue
    end
    Svc->>TMapper: findByTaxonomyAndSequenceOrder(axis, 1)
    TMapper->>DB: SELECT ... WHERE taxonomy = ? AND sequence_order = 1
    Svc->>PgMapper: bootstrapFirstTopic(userId, firstTopicId)
    PgMapper->>DB: INSERT writing_topic_progress ... ON CONFLICT DO NOTHING
    Note over Svc,PgMapper: opens the first topic OF THIS AXIS only -<br/>each axis bootstraps independently
    Svc->>PgMapper: findByUserId(userId)
    Svc->>AMapper: findByUserId(userId)
    Svc->>Svc: passedPromptIds = attempts with score >= 0.7
    Svc->>TMapper: findByTaxonomy(axis)
    TMapper->>DB: SELECT ... WHERE taxonomy = ? ORDER BY sequence_order
    loop each topic on this axis
        Svc->>PMapper: findByTopicId(topicId)
        Svc->>Svc: status (no row -> LOCKED), passedPromptCount, targetPromptCount
    end
    Svc-->>Ctrl: List<WritingLibraryTopicDto>
```

## 2. Start or resume a prompt (`POST .../topics/{topicId}/prompts?taskType=...`)

```mermaid
sequenceDiagram
    participant Caller
    participant Ctrl as WritingLibraryController
    participant Svc as WritingLibraryServiceImpl
    participant PgMapper as WritingTopicProgressMapper
    participant PMapper as WritingLibraryPromptMapper
    participant AMapper as WritingLibraryAttemptMapper
    participant Gen as LlmWritingLibraryContentGenerator
    participant Ai as AiContentClient
    participant Gemini as Gemini API
    participant DB as reme_english DB

    Caller->>Ctrl: POST /{userId}/topics/{topicId}/prompts?taskType=COMPOSE
    Ctrl->>Svc: startOrResumePrompt(userId, topicId, taskType)
    Svc->>PgMapper: findByUserIdAndTopicId(userId, topicId)
    alt status is LOCKED (or no row at all)
        Svc-->>Ctrl: BusinessException.forbidden -> 403
    end
    Svc->>PMapper: findByTopicId(topicId)
    Svc->>AMapper: findByUserId(userId)
    Svc->>Svc: passed = attempts with score >= 0.7
    alt a prompt in the chain is not yet passed
        Svc->>Svc: serve that one (resume)
    else chain shorter than targetPromptCount
        Svc->>Gen: generatePrompt(topic, taskType)
        Gen->>Ai: completeJson(systemPrompt with the topic's AXIS, temp=0.6, maxTokens=1400)
        Ai->>Gemini: LlmClient.complete(...)
        Gemini-->>Ai: {promptText, referenceAnswer, minWords, explanation}
        alt LLM/parse failure, or blank promptText
            Gen->>Gen: fallback built from the topic itself, with an axis-specific<br/>requirement (use this structure / write this genre / use this theme's words)<br/>and its Vietnamese instruction line
        end
        Gen->>PMapper: insert(WritingLibraryPrompt)
        PMapper->>DB: INSERT writing_library_prompts
        Note over Gen,DB: persisted immediately, so the prompt becomes a stable part<br/>of the chain rather than being re-invented on the next visit
    else every prompt already passed
        Svc->>Svc: serve the last one (review only)
    end
    Svc->>PgMapper: markInProgress(userId, topicId)
    PgMapper->>DB: UPDATE ... SET status='IN_PROGRESS' WHERE status <> 'PASSED'
    Svc-->>Ctrl: WritingLibraryPromptDto{position, targetPromptCount, minWords, no referenceAnswer}
```

The axis is part of the generation prompt, so a `grammar` topic forces its structure, a `genre` topic
produces a real piece of that text type, and a `vocab_theme` topic pushes that theme's vocabulary.

## 3. Submit + advance (`POST .../prompts/{promptId}/submit`)

```mermaid
sequenceDiagram
    participant Caller
    participant Ctrl as WritingLibraryController
    participant Svc as WritingLibraryServiceImpl
    participant Grader as LlmWritingGrader
    participant Pipe as WritingErrorPipeline
    participant Practice as PracticeService
    participant AMapper as WritingLibraryAttemptMapper
    participant PMapper as WritingLibraryPromptMapper
    participant TMapper as WritingLibraryTopicMapper
    participant PgMapper as WritingTopicProgressMapper
    participant DB as reme_english DB

    Caller->>Ctrl: POST /{userId}/prompts/{promptId}/submit {submittedText}
    Ctrl->>Svc: submitAnswer(userId, promptId, request)
    Svc->>PMapper: findById(promptId)
    Svc->>TMapper: findById(prompt.topicId)
    Svc->>Grader: grade(taskType, promptText, referenceAnswer, submittedText)
    Note over Svc,Grader: the SAME grader the learn tab uses (writing-learn.md section 3)
    Grader-->>Svc: WritingGrade
    Svc->>Pipe: averageCriteria(criteria) -> score
    Svc->>AMapper: insert(WritingLibraryAttempt{criteriaJson, errorsJson, startedAt, completedAt})
    AMapper->>DB: INSERT writing_library_attempts
    Svc->>Pipe: feedWeakPoints(userId, errors)
    Pipe->>Practice: redo(...) - same routing as the learn tab
    Svc->>PMapper: findByTopicId(topicId)
    Svc->>AMapper: findByUserId(userId)
    alt score >= 0.7 AND chain is full length AND every prompt passed
        Svc->>PgMapper: markPassed(userId, topicId)
        Svc->>TMapper: findByTaxonomyAndSequenceOrder(topic.taxonomy, topic.sequenceOrder + 1)
        Note over Svc,TMapper: axis-scoped - sequence_order only orders within one taxonomy,<br/>so a global lookup would unlock a topic on a different axis
        Svc->>PgMapper: unlockIfLocked(userId, nextTopicId)
        PgMapper->>DB: INSERT ... ON CONFLICT DO UPDATE WHERE status = 'LOCKED'
        Svc->>PgMapper: findByUserIdAndTopicId(userId, nextTopicId)
        Note over Svc: re-read after the upsert so nextTopicUnlocked is reported honestly
    end
    Svc-->>Ctrl: SubmitWritingLibraryAnswerResponse{score, passed, criteria, errors,<br/>referenceAnswer, passedPromptCount, topicPassed, nextTopicId, nextTopicUnlocked}
```

## 4. Retry from a library attempt (`POST .../attempts/{attemptId}/ai-practice`)

Mirrors `ListeningLibraryServiceImpl#generatePracticeFromSection`: verifies the attempt belongs to
this learner, extracts its error labels via the pure `WritingMistakeAnalyzer`, then delegates to
`WritingLearnService#generatePracticeForLabels` so the regenerated task lands in the same
`writing_practice_items` bank the learn tab uses. An attempt with no mistakes returns an empty list
rather than generating something unrelated.

## External calls

| Target | Call | Failure handling |
|---|---|---|
| Gemini (via `AiContentClient`) | 1 completion per newly generated prompt; 1 per submission (grading) | Generation falls back to a topic-derived template; grading falls back to a neutral score with no errors |
| `reme_english` DB | topics/prompts/progress/attempts | `@Transactional` on every mutating method |
| `practice` package (in-process) | `PracticeService#redo` via `WritingErrorPipeline` | See [practice-redo.md](practice-redo.md) |
| `writing` package (in-process) | `WritingLearnService#generatePracticeForLabels` for the retry action | Propagates the learn tab's own fallbacks |
