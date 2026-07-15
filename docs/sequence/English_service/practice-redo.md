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
    participant HMapper as MistakeHistoryMapper (MyBatis)
    participant DB as reme_english DB
    participant Producer as AnalysisRequestedProducer
    participant Codec as EventCodec (snake_case ObjectMapper)
    participant Kafka

    Caller->>Ctrl: POST /api/v1/practice/redo<br/>{userId, attempts: [{itemId, category, label, correct}]}
    Ctrl->>Svc: redo(request)
    activate Svc
    loop each attempt in the batch
        Svc->>AMapper: insert({userId, itemId, category, label, correct})
        AMapper->>DB: INSERT INTO practice_attempts (audit log only, never read back)
        Svc->>HMapper: recordAttempt(userId, itemId, category, label, correct)
        HMapper->>DB: INSERT ... ON CONFLICT (user_id, item_id) DO UPDATE<br/>occurrence_count += (correct ? 0 : 1), last_seen_at = now()
    end
    Note over Svc,DB: @Transactional across the whole batch

    Svc->>HMapper: findByUserId(userId)
    HMapper->>DB: SELECT * FROM mistake_history WHERE user_id = ?
    HMapper-->>Svc: List[MistakeHistoryEntry] (every item, not just this batch's)
    Svc->>Svc: build AnalysisRequestedEvent{recordingId="practice-<uuid>",<br/>userId, segments=[], history: MistakeHistoryItemPayload[]<br/>{itemId, category, label, occurrenceCount, lastSeenDaysAgo}}
    Svc->>Producer: publish(event)
    Producer->>Codec: encode(event) -> snake_case JSON
    Producer->>Kafka: send(learning.gap.analysis.requested, key=recordingId, payload)
    deactivate Svc
    Svc-->>Ctrl: void
    Ctrl-->>Caller: 200 ApiResponse{success:true, data:null}

    Note over Kafka: ai-service consumes this next -<br/>see ../Ai_service/overview.md (handle_analysis_requested)
```

## External calls

| # | Call | From -> To | Notes |
|---|------|-----------|-------|
| 1 | Kafka consume `learning.gap.analyzed` | Kafka broker -> english-service (`practice`) | seeds `mistake_history`, no category filter |
| 2 | Postgres INSERT | english-service -> `reme_english` DB | `practice_attempts` (audit) + `mistake_history` (upsert) |
| 3 | Kafka produce `learning.gap.analysis.requested` | english-service -> Kafka broker | key = synthetic `recordingId`, snake_case via `EventCodec`, no envelope (same pattern as `RecordingUploadedProducer`) |

## Notes

- Idempotency: `mistake_history` is keyed on `(user_id, item_id)`. Seeding is `DO NOTHING` on
  conflict (never overwrites); grading is `DO UPDATE` and only increments `occurrence_count` when
  the answer was wrong, so the seed consumer and the redo flow never double-count the same mistake.
- `segments` is always empty in the published `AnalysisRequestedEvent` — there's no transcript in a
  redo-exercise round, so `RuleBasedAnalyzer`'s "recurs in this session" boost never applies here
  (only the frequency/recency decay part of the score is affected by a redo).
- One `learning.gap.analysis.requested` event is published per redo batch, carrying the learner's
  **entire** current history (not just the items just answered), so ai-service re-scores everything
  consistently in one pass.
- What happens after publish is entirely the existing pipeline, unchanged: ai-service's
  `RuleBasedAnalyzer` recomputes `forgettingScore` and republishes `learning.gap.analyzed`, which the
  three domain consumers, `recommendation-service`, and `dashboard-service` all re-upsert by
  `(user_id, item_id)` — see [overview.md](overview.md) and
  [../Ai_service/overview.md](../Ai_service/overview.md).
