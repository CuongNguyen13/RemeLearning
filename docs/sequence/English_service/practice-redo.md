# Practice / redo-exercise: seed history, grade a redo, re-trigger analysis

Covers the `practice` package (`com.remelearning.english.practice`), which closes the loop the other
three domains leave open: when a learner **redoes an exercise**, the system must grade each answer,
re-evaluate how forgotten that item is, and re-propose recommendations. This package doesn't own a
weak-point table of its own — it maintains `mistake_history` (occurrence count + recency per item,
across all three domains) and is the first real producer of `learning.gap.analysis.requested`
(previously only a topic-name constant with no publisher — see
[../Ai_service/overview.md](../Ai_service/overview.md) for the `handle_analysis_requested` Kafka
handler that consumes it and runs `RuleBasedAnalyzer`).

## 1. Seeding `mistake_history` (`MistakeHistorySeedConsumer`)

Runs on its own `groupId` (`english-service-practice`), same topic as the other three domain
consumers (`english-service`, `english-service-grammar`, `english-service-pronunciation`) — see
[overview.md](overview.md). Unlike them it does **not** filter by category (same pattern as
recommendation-service/dashboard-service): every weak point, from every domain, seeds history the
first time it's ever seen.

```mermaid
---
config:
  theme: base
  themeVariables:
    background: '#ffffff'
---
sequenceDiagram
    participant Kafka
    participant Consumer as MistakeHistorySeedConsumer
    participant Codec as EventCodec (snake_case ObjectMapper)
    participant Mapper as MistakeHistoryMapper (MyBatis)
    participant DB as reme_english DB

    Kafka->>Consumer: learning.gap.analyzed payload<br/>{recording_id, user_id, weak_points[]}
    Consumer->>Codec: decode(payload) -> LearningGapAnalyzedEvent
    Codec-->>Consumer: LearningGapAnalyzedEvent{recordingId, userId, weakPoints[]}
    loop each weak point (no category filter)
        Consumer->>Mapper: seedIfAbsent({userId, itemId, category, label, occurrenceCount=1})
        Mapper->>DB: INSERT INTO mistake_history ... ON CONFLICT (user_id, item_id) DO NOTHING
        Note right of DB: no-op if history already exists -<br/>only PracticeService.redo updates it after this point
    end
    Note over Consumer: exceptions caught + logged, not rethrown to Kafka (no DLQ/retry)
```

## 2. Grading a redo (`POST /api/v1/practice/redo`)

Since this update, each attempt is scored **twice, by design**: immediately in Java (so the owning
domain's weak-point row is fresh with no Kafka round-trip), and — unchanged — asynchronously by
`ai-service` afterward, kept mainly so `recommendation-service`/`dashboard-service` (which only
consume `learning.gap.analyzed`) stay in sync. A `score_source` guard on each domain's upsert stops
the slower Python-sourced write from clobbering the fresher Java one.

```mermaid
---
config:
  theme: base
  themeVariables:
    background: '#ffffff'
---
sequenceDiagram
    participant Caller
    participant Ctrl as PracticeController
    participant Svc as PracticeServiceImpl
    participant AMapper as PracticeAttemptMapper (MyBatis)
    participant Orch as WeakPointScoringOrchestratorImpl
    participant HMapper as MistakeHistoryMapper (MyBatis)
    participant DMapper as ItemDifficultyStatsMapper (MyBatis)
    participant Engine as common.scoring.WeakPointScoringEngine
    participant Domain as Vocabulary/Grammar/Pronunciation<br/>WeakPointService
    participant DB as reme_english DB
    participant Producer as AnalysisRequestedProducer
    participant Codec as EventCodec (snake_case ObjectMapper)
    participant Kafka

    Caller->>Ctrl: POST /api/v1/practice/redo<br/>{userId, attempts: [{itemId, category, label, correct}]}
    Ctrl->>Svc: redo(request)
    activate Svc
    Svc->>Svc: recordingId = "practice-" + UUID (one per batch)
    loop each attempt in the batch (sequential, not parallel)
        Svc->>AMapper: insert({userId, itemId, category, label, correct})
        AMapper->>DB: INSERT INTO practice_attempts (audit log only, never read back)
        Svc->>Orch: scoreAttempt(userId, recordingId, attempt,<br/>recurredInBatch = item already seen earlier in this loop)
        activate Orch
        Orch->>HMapper: findOneForUpdate(userId, itemId)
        HMapper->>DB: SELECT ... FOR UPDATE (locks row, reads state BEFORE this attempt)
        HMapper-->>Orch: prior ScoringState (or cold-start defaults if first-ever attempt)
        Orch->>HMapper: recordAttempt(userId, itemId, category, label, correct)
        HMapper->>DB: occurrence_count += (correct ? 0 : 1), last_seen_at = now()
        Orch->>DMapper: find(category, labelKey)
        DMapper->>DB: SELECT correct_count, incorrect_count FROM item_difficulty_stats
        DMapper-->>Orch: PopulationStats (or empty/cold-start)
        Orch->>Engine: scoreAfterAttempt(priorState, outcome, populationStats, bktParams, now)
        Engine-->>Orch: ScoringResult{weakScore, updatedState, nextReviewAt}
        Orch->>HMapper: updateScoringState(easeFactor, halfLifeDays, mastery, leitnerBox, nextReviewAt, weakScore, labelKey)
        Orch->>DMapper: upsertIncrement(category, labelKey, correctDelta, incorrectDelta)
        Orch->>Domain: applyJavaComputedScore({itemId, category, label,<br/>weakScore, masteryLevel, nextReviewAt, scoreSource=JAVA_ENGINE})
        Domain->>DB: upsert vocabulary/grammar/pronunciation_weak_points<br/>(ON CONFLICT ... WHERE NOT (existing=JAVA_ENGINE AND incoming=PYTHON_LEGACY))
        deactivate Orch
    end
    Note over Svc,DB: @Transactional across the whole batch

    Svc->>HMapper: findByUserId(userId)
    HMapper->>DB: SELECT * FROM mistake_history WHERE user_id = ?
    HMapper-->>Svc: List[MistakeHistoryEntry] (every item, not just this batch's)
    Svc->>Svc: build AnalysisRequestedEvent{recordingId (same as above),<br/>userId, segments=[], history: MistakeHistoryItemPayload[]<br/>{itemId, category, label, occurrenceCount, lastSeenDaysAgo}}
    Svc->>Producer: publish(event)
    Producer->>Codec: encode(event) -> snake_case JSON
    Producer->>Kafka: send(learning.gap.analysis.requested, key=recordingId, payload)
    deactivate Svc
    Svc-->>Ctrl: void
    Ctrl-->>Caller: 200 ApiResponse{success:true, data:null}

    Note over Kafka: ai-service consumes this next (unchanged formula) -<br/>see ../Ai_service/overview.md (handle_analysis_requested)
```

## 3. Review queue (`GET /api/v1/practice/review-queue/{userId}`)

Surfaces the Leitner schedule the scoring engine maintains — items due for review now, soonest-first.

```mermaid
---
config:
  theme: base
  themeVariables:
    background: '#ffffff'
---
sequenceDiagram
    participant Caller
    participant Ctrl as PracticeController
    participant Svc as PracticeServiceImpl
    participant HMapper as MistakeHistoryMapper (MyBatis)
    participant DB as reme_english DB

    Caller->>Ctrl: GET /api/v1/practice/review-queue/{userId}
    Ctrl->>Svc: getReviewQueue(userId)
    Svc->>HMapper: findDueForReview(userId, now)
    HMapper->>DB: SELECT ... WHERE user_id = ? AND next_review_at <= ? ORDER BY next_review_at ASC
    HMapper-->>Svc: List[MistakeHistoryEntry]
    Svc-->>Ctrl: List[ReviewQueueItem]
    Ctrl-->>Caller: 200 ApiResponse{data: ReviewQueueItem[]}
```

## External calls

| # | Call | From -> To | Notes |
|---|------|-----------|-------|
| 1 | Kafka consume `learning.gap.analyzed` | Kafka broker -> english-service (`practice`) | seeds `mistake_history`, no category filter |
| 2 | Postgres INSERT/UPDATE | english-service -> `reme_english` DB | `practice_attempts` (audit), `mistake_history` (upsert + scoring-state update), `item_difficulty_stats` (upsert), and one of `vocabulary/grammar/pronunciation_weak_points` (guarded upsert) per attempt |
| 3 | Kafka produce `learning.gap.analysis.requested` | english-service -> Kafka broker | key = synthetic `recordingId`, snake_case via `EventCodec`, no envelope (same pattern as `RecordingUploadedProducer`) |

## Notes

- Idempotency: `mistake_history` is keyed on `(user_id, item_id)`. Seeding is `DO NOTHING` on
  conflict (never overwrites); grading is `DO UPDATE` and only increments `occurrence_count` when
  the answer was wrong, so the seed consumer and the redo flow never double-count the same mistake.
- `segments` is always empty in the published `AnalysisRequestedEvent` — there's no transcript in a
  redo-exercise round, so `ai-service`'s "recurs in this session" boost never applies to that async
  recompute (the Java-side `recurredInBatch` signal, by contrast, is based on the redo batch itself,
  not a transcript).
- One `learning.gap.analysis.requested` event is still published per redo batch, carrying the
  learner's **entire** current history, so ai-service re-scores everything consistently in one pass
  — this keeps `recommendation-service`/`dashboard-service` in sync even though they never see the
  new Java-direct path.
- `score_source` (`PYTHON_LEGACY` / `JAVA_ENGINE`) is a one-way ratchet on each domain's weak-point
  row: once Java has scored an item, only another Java write can update it again — the async
  `learning.gap.analyzed` consumer's write is silently skipped for that row otherwise. See
  `Business.md` §10 and `RemeLearning/common/src/main/java/com/remelearning/common/scoring/` for the
  full scoring-formula rationale (Ebbinghaus + adaptive half-life, Bayesian Knowledge Tracing, Rasch
  difficulty, Leitner scheduling).
- What happens after publish is entirely the existing pipeline, unchanged: ai-service's
  `RuleBasedAnalyzer` recomputes `forgettingScore` (still the single-formula Ebbinghaus calculation,
  not yet mirroring the Java engine's combined formula) and republishes `learning.gap.analyzed`,
  which the three domain consumers, `recommendation-service`, and `dashboard-service` all re-upsert
  by `(user_id, item_id)` — see [overview.md](overview.md) and
  [../Ai_service/overview.md](../Ai_service/overview.md).
