# Grammar learn: AI-generated practice sets + graded attempts

Covers `com.remelearning.english.grammar.learn` (`GrammarLearnController`/`GrammarLearnServiceImpl`),
structurally a clone of `vocabulary-learn.md`'s `VocabLearnServiceImpl` (see that file for the full
rationale) with "word" replaced by "grammar rule" and category `"grammar"` instead of `"vocabulary"`.
FE calls go through `bff-service`'s `LearnerController` (`/api/v1/learners/{userId}/learn/grammar/
...`), a pure pass-through (`EnglishServiceClient`) - omitted from the diagrams below as a separate
hop, same convention as `dictation-practice.md`'s generic `Caller`.

This skill is AI-only: `LlmGrammarPracticeGenerator` is the only `GrammarPracticeGenerator`, with a
static-template fallback on any LLM call/parse failure. No Kafka consumer/producer of its own -
grading reuses `practice.service.PracticeService#redo`, which publishes
`learning.gap.analysis.requested` (see `overview.md` section 3 / `practice-redo.md`).

## 1. Generate (`POST /api/v1/learn/grammar/{userId}/generate`)

```mermaid
sequenceDiagram
    participant Caller
    participant Ctrl as GrammarLearnController
    participant Svc as GrammarLearnServiceImpl
    participant WSvc as GrammarWeakPointService
    participant Gen as LlmGrammarPracticeGenerator
    participant Ai as AiContentClient (common.ai.LlmClient)
    participant Gemini as Gemini API
    participant GMapper as GrammarPracticeMapper
    participant DB as reme_english DB

    Caller->>Ctrl: POST /{userId}/generate {focusItems?, level?, examType?}
    Ctrl->>Svc: generate(userId, request)
    alt focusItems provided
        Svc->>Svc: targetRules = focusItems
    else no focusItems
        Svc->>WSvc: getTopWeakPoints(userId, 8)
        WSvc-->>Svc: top-8 most-forgotten grammar weak points
        Svc->>Svc: targetRules = weak points' labels (empty list ok - generator picks its own rules)
    end
    Svc->>Gen: generate(targetRules, level, examType)
    Gen->>Ai: completeJson(systemPrompt, userPrompt, temp, maxTokens)
    Ai->>Gemini: LlmClient.complete(...) -> generateContent REST call
    Gemini-->>Ai: raw text (code-fence stripped)
    Ai-->>Gen: parsed JSON {topic, items[{targetRule,type,prompt,options?,answer,translation}]}
    alt LLM call/parse fails
        Gen->>Gen: fallback() - templated item(s) per target rule
    end
    Gen-->>Svc: GeneratedGrammarPractice{topic, items[]}
    Svc->>GMapper: insertItem({userId, level, examType, topic, targetRulesJson, itemsJson})
    GMapper->>DB: INSERT INTO grammar_practice_items
    Svc-->>Ctrl: GrammarPracticeItemDto{practiceItemId, level, examType, topic, targetRules, questions[]}<br/>(questions now carry answer + translation too, so the client can grade each question locally)
    Ctrl-->>Caller: 200 ApiResponse
```

## 2. Submit attempt (`POST /api/v1/learn/grammar/attempts`)

```mermaid
sequenceDiagram
    participant Caller
    participant Ctrl as GrammarLearnController
    participant Svc as GrammarLearnServiceImpl
    participant GMapper as GrammarPracticeMapper
    participant DB as reme_english DB
    participant Scorer as GrammarAttemptScorer (pure)
    participant PSvc as PracticeService (redo)
    participant Kafka as learning.gap.analysis.requested

    Caller->>Ctrl: POST /attempts {userId, practiceItemId, answers[]}
    Ctrl->>Svc: submit(request)
    Svc->>GMapper: findItemById(practiceItemId)
    alt not found
        Svc-->>Ctrl: BusinessException.notFound -> 404
    else found
        Svc->>Scorer: score(questions, answers)
        Scorer-->>Svc: GrammarScoreResult{accuracy, perQuestionCorrect[]}
        Svc->>GMapper: insertAttempt({practiceItemId, userId, answersJson, score})
        GMapper->>DB: INSERT INTO grammar_practice_attempts
        Svc->>Svc: buildQuestionResults(questions, answers, score)
        Svc->>Svc: feedWeakPoints - dedupe target rules in this attempt,<br/>map each to PracticeAttemptRequest{itemId="grammar:<rule>", category="grammar", label, correct}
        opt any attempts built
            Svc->>PSvc: redo(PracticeRedoRequest{userId, attempts[]})
            PSvc->>PSvc: log attempt + score via WeakPointScoringOrchestrator<br/>(BKT/Rasch/Ebbinghaus/Leitner) -> upsert grammar_weak_points directly
            PSvc->>Kafka: publish AnalysisRequestedEvent (bundled mistake_history)<br/>-> ai-service re-scores, republishes learning.gap.analyzed<br/>(feeds recommendation-service/dashboard-service)
        end
        Svc-->>Ctrl: GrammarAttemptResultDto{accuracy, results[], actionAdvice[]}
        Ctrl-->>Caller: 200 ApiResponse
    end
```

## 3. Generate from one past attempt's mistakes (`POST /api/v1/learn/grammar/history/{userId}/{attemptId}/ai-practice`)

```mermaid
sequenceDiagram
    participant Caller
    participant Ctrl as GrammarLearnController
    participant Svc as GrammarLearnServiceImpl
    participant GMapper as GrammarPracticeMapper
    participant DB as reme_english DB
    participant Analyzer as GrammarMistakeAnalyzer (pure)
    participant Scorer as GrammarAttemptScorer (pure)
    participant Gen as LlmGrammarPracticeGenerator

    Caller->>Ctrl: POST /history/{userId}/{attemptId}/ai-practice
    Ctrl->>Svc: generatePracticeFromAttempt(userId, attemptId)
    Svc->>GMapper: findAttemptDetailByIdAndUserId(attemptId, userId)
    alt not found / not owned by userId
        Svc-->>Ctrl: BusinessException.notFound -> 404
    else found
        GMapper-->>Svc: GrammarAttemptDetailRow{level, examType, itemsJson, answersJson}
        Svc->>Analyzer: extractMissedRules(itemsJson, answersJson)
        Analyzer->>Scorer: score(items, answers) - same scoring the original attempt used
        Scorer-->>Analyzer: perQuestionCorrect[]
        Analyzer-->>Svc: distinct targetRule[] of every wrong question
        Svc->>Svc: generatePracticeForRules(userId, missedRules, attempt.level, attempt.examType)
        Note over Svc: same generate-and-persist step "1. Generate" uses -<br/>generator.generate(...) -> insertItem -> listItems(userId)
        Svc->>Gen: generate(missedRules, level, examType)
        Gen-->>Svc: GeneratedGrammarPractice{topic, items[]}
        Svc->>GMapper: insertItem({userId, level, examType, topic, targetRulesJson, itemsJson})
        GMapper->>DB: INSERT INTO grammar_practice_items
        Svc->>GMapper: findItemsByUserId(userId)
        GMapper-->>Svc: refreshed practice-item rows
        Svc-->>Ctrl: GrammarPracticeItemDto[] (refreshed list)
        Ctrl-->>Caller: 200 ApiResponse
    end
```

## External calls

| # | Call | From -> To | Notes |
|---|------|-----------|-------|
| 1 | HTTPS | english-service -> Gemini API | `LlmGrammarPracticeGenerator` via `AiContentClient`/`LlmClient`; falls back to a template on any failure |
| 2 | Kafka produce | english-service -> `learning.gap.analysis.requested` | via `PracticeService#redo` -> `AnalysisRequestedProducer`, same mechanism as the practice/redo flow (`practice-redo.md`) |
| 3 | Postgres | english-service -> `reme_english` | `grammar_practice_items`, `grammar_practice_attempts`, plus `grammar_weak_points` (upserted by `WeakPointScoringOrchestrator`) |

## Notes

- Structurally identical to `vocabulary-learn.md` - see that file's Notes for the shared rationale
  (client-side grading contract change, binary correct/incorrect feed, no dedicated Kafka
  consumer/producer for this package's own request flow). Like vocabulary, `GrammarQuestionDto` now
  carries `answer` + `translation` on the generate/`getItem`/`listItems` responses so the client grades
  each question locally for instant feedback; the authoritative score still comes only from `submit`.
- Not independently confirmed in code for this report: whether the FE ever calls `GET /items/{itemId}`
  directly outside the immediate `generate` response - both endpoints exist
  (`GrammarLearnController`) but only `generate`/`submit` are diagrammed above per the requested scope.
- `generatePracticeForRules` (section 3) is the shared generate-and-persist step both
  `generatePracticeFromAttempt` and Grammar Library's own `generatePracticeFromSession` delegate to
  (see `grammar-library.md` section 6) - there is only one AI-practice destination
  (`grammar_practice_items`) per domain, regardless of which flow (learn attempt vs. library session)
  the mistake came from. Mirrors `dictation-practice.md` section 2b's "from one history row"
  regeneration action, minus the audio-synthesis step (grammar practice has no audio).
