# Vocabulary learn: AI-generated practice sets + graded attempts

Covers `com.remelearning.english.vocabulary.learn` (`VocabLearnController`/`VocabLearnServiceImpl`),
one of four "Học &amp; Luyện tập với AI" skills built this session (vocabulary/grammar/listening/
speaking), all structurally identical: generate one AI practice set targeting the learner's own top
weak points (or an explicit focus list), grade a submitted attempt, and feed each graded word back
into the existing spaced-repetition/weak-point pipeline via `PracticeService#redo` instead of a
bespoke publisher. FE calls go through `bff-service`'s `LearnerController` (`/api/v1/learners/
{userId}/learn/vocabulary/...`), which is a pure pass-through (`EnglishServiceClient`, no
transformation beyond stamping `userId` onto the request body) — omitted from the diagrams below as
a separate hop, same as `dictation-practice.md`'s convention of a generic `Caller`.

This skill is AI-only: there's no rule-based generator, only `LlmVocabPracticeGenerator` with a
static-template fallback on any LLM call/parse failure, so generating a set never breaks. Unlike
`dictation`, this skill has no Kafka consumer/producer of its own for its request flow — grading
reuses `practice.service.PracticeService#redo`, which is what actually publishes
`learning.gap.analysis.requested` (see `overview.md` section 3 / `practice-redo.md`).

## 1. Generate (`POST /api/v1/learn/vocabulary/{userId}/generate`)

```mermaid
sequenceDiagram
    participant Caller
    participant Ctrl as VocabLearnController
    participant Svc as VocabLearnServiceImpl
    participant WSvc as VocabularyWeakPointService
    participant Gen as LlmVocabPracticeGenerator
    participant Ai as AiContentClient (common.ai.LlmClient)
    participant Gemini as Gemini API
    participant VMapper as VocabPracticeMapper
    participant DB as reme_english DB

    Caller->>Ctrl: POST /{userId}/generate {focusItems?, level?, examType?}
    Ctrl->>Svc: generate(userId, request)
    alt focusItems provided
        Svc->>Svc: targetWords = focusItems
    else no focusItems
        Svc->>WSvc: getTopWeakPoints(userId, 8)
        WSvc-->>Svc: top-8 most-forgotten vocabulary weak points
        Svc->>Svc: targetWords = weak points' labels (empty list ok - generator picks its own words)
    end
    Svc->>Gen: generate(targetWords, level, examType)
    Gen->>Ai: completeJson(systemPrompt, userPrompt, temp=0.6, maxTokens=1200)
    Ai->>Gemini: LlmClient.complete(...) -> generateContent REST call
    Gemini-->>Ai: raw text (code-fence stripped)
    Ai-->>Gen: parsed JSON {topic, items[{targetWord,type,prompt,options?,answer,translation}]}
    alt LLM call fails, or parse fails, or items[] empty
        Gen->>Gen: fallback() - one templated CLOZE item per target word<br/>(or one generic "practice" item if none given)
    end
    Gen-->>Svc: GeneratedVocabPractice{topic, items[]}
    Svc->>VMapper: insertItem({userId, level, examType, topic, targetWordsJson, itemsJson})
    VMapper->>DB: INSERT INTO vocab_practice_items
    Svc-->>Ctrl: VocabPracticeItemDto{practiceItemId, level, examType, topic, targetWords, questions[]}<br/>(questions now carry answer + translation too, so the client can grade each question locally)
    Ctrl-->>Caller: 200 ApiResponse
```

## 2. Submit attempt (`POST /api/v1/learn/vocabulary/attempts`)

```mermaid
sequenceDiagram
    participant Caller
    participant Ctrl as VocabLearnController
    participant Svc as VocabLearnServiceImpl
    participant VMapper as VocabPracticeMapper
    participant DB as reme_english DB
    participant Scorer as VocabAttemptScorer (pure)
    participant PSvc as PracticeService (redo)
    participant Kafka as learning.gap.analysis.requested

    Caller->>Ctrl: POST /attempts {userId, practiceItemId, answers[]}
    Ctrl->>Svc: submit(request)
    Svc->>VMapper: findItemById(practiceItemId)
    alt not found
        Svc-->>Ctrl: BusinessException.notFound -> 404
    else found
        Svc->>Scorer: score(questions, answers)
        Scorer-->>Svc: VocabScoreResult{accuracy, perQuestionCorrect[]}
        Svc->>VMapper: insertAttempt({practiceItemId, userId, answersJson, score})
        VMapper->>DB: INSERT INTO vocab_practice_attempts
        Svc->>Svc: buildQuestionResults(questions, answers, score)
        Svc->>Svc: feedWeakPoints - dedupe target words in this attempt,<br/>map each to PracticeAttemptRequest{itemId="vocab:<word>", category="vocabulary", label, correct}
        opt any attempts built
            Svc->>PSvc: redo(PracticeRedoRequest{userId, attempts[]})
            PSvc->>PSvc: log attempt + score via WeakPointScoringOrchestrator<br/>(BKT/Rasch/Ebbinghaus/Leitner) -> upsert vocabulary_weak_points directly
            PSvc->>Kafka: publish AnalysisRequestedEvent (bundled mistake_history)<br/>-> ai-service re-scores, republishes learning.gap.analyzed<br/>(feeds recommendation-service/dashboard-service)
        end
        Svc-->>Ctrl: VocabAttemptResultDto{accuracy, results[], actionAdvice[]}
        Ctrl-->>Caller: 200 ApiResponse
    end
```

## External calls

| # | Call | From -> To | Notes |
|---|------|-----------|-------|
| 1 | HTTPS | english-service -> Gemini API | `LlmVocabPracticeGenerator` via `AiContentClient`/`LlmClient`; falls back to a templated CLOZE item on any failure |
| 2 | Kafka produce | english-service -> `learning.gap.analysis.requested` | via `PracticeService#redo` -> `AnalysisRequestedProducer`, same mechanism as the practice/redo flow (`practice-redo.md`) |
| 3 | Postgres | english-service -> `reme_english` | `vocab_practice_items`, `vocab_practice_attempts`, plus `vocabulary_weak_points` (upserted by `WeakPointScoringOrchestrator`, not this package's own mapper) |

## Notes

- No new Kafka consumer/producer for this skill's own request flow - it reuses `vocabulary`'s
  existing `VocabularyWeakPointService` (read) and `practice.service.PracticeService#redo` (write +
  publish) in-process, same reuse pattern `dictation` and the other three "learn" skills follow.
- `feedWeakPoints` treats each question's target word as one binary correct/incorrect attempt (not a
  continuous score) - the same simplification `SpeakingLearnServiceImpl`/`ListeningLearnServiceImpl`
  apply to their own graded units.
- **Client-side grading (contract change):** the practice-set response (`generate`/`getItem`/
  `listItems`) now carries `answer` + `translation` per question, so the client checks each answer
  locally for instant feedback before ever calling `submit`. The call order above is unchanged - this
  is purely a client-side behaviour on top of the same payload - and the authoritative score is still
  produced only by the `submit` step (which persists history + weak points and fires Kafka).
- Not independently confirmed in code for this report: whether the FE ever calls `GET /items/{itemId}`
  directly versus always going through `generate` immediately followed by rendering its own response -
  both endpoints exist (`VocabLearnController`) but only `generate`/`submit` are diagrammed above per
  the requested scope.
