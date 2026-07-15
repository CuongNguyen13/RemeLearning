# dashboard-service — Overview

`dashboard-service` (Java/Spring Boot, port 8087, `reme_dashboard` DB) builds a cross-domain read
model for a learner's progress. Unlike `english-service` (one weak-point table per domain, filtered
by category), it consumes `learning.gap.analyzed` **unfiltered** into a single unified
`weak_points_snapshot` table (category kept as a plain column), plus a second, Java-to-Java-only
event, `recommendation.generated` (published by `recommendation-service`), into
`recent_recommendations`. It exposes one read endpoint that aggregates both — no REST calls to any
other service, matching the rest of the architecture (`bff-service` has no gateway routes yet). See
`RemeLearning/services/dashboard-service/src/main/java/com/remelearning/dashboard/`.

Per-event/per-endpoint detail lives in
[learning-gap-analyzed.md](learning-gap-analyzed.md),
[recommendation-generated.md](recommendation-generated.md),
[get-dashboard-summary.md](get-dashboard-summary.md).

## 1. Kafka consumers (ingestion)

```mermaid
---
config:
  theme: base
  themeVariables:
    background: '#ffffff'
---
sequenceDiagram
    participant Kafka
    participant LGAConsumer as LearningGapAnalyzedConsumer (groupId=dashboard-service)
    participant Codec as EventCodec (snake_case ObjectMapper)
    participant RGConsumer as RecommendationGeneratedConsumer (groupId=dashboard-service)
    participant PlainMapper as plain camelCase ObjectMapper + JavaTimeModule
    participant Svc as DashboardServiceImpl
    participant WMapper as WeakPointSnapshotMapper (MyBatis)
    participant RMapper as RecentRecommendationMapper (MyBatis)
    participant DB as reme_dashboard DB

    Kafka->>LGAConsumer: learning.gap.analyzed<br/>(published by ai-service, see ../Ai_service/overview.md)
    LGAConsumer->>Codec: decode(payload) -> LearningGapAnalyzedEvent
    LGAConsumer->>Svc: recordWeakPoint(event)
    loop each weak point (all categories, no filtering)
        Svc->>WMapper: upsert(userId, itemId, recordingId, category,<br/>label, forgettingScore, recommendation)
        WMapper->>DB: INSERT ... ON CONFLICT (user_id, item_id) DO UPDATE
    end
    Note over Svc,DB: @Transactional

    Kafka->>RGConsumer: recommendation.generated<br/>(published by recommendation-service via common's EventPublisher/BaseEvent)
    RGConsumer->>PlainMapper: decode(payload) -> RecommendationGeneratedEvent
    RGConsumer->>Svc: recordRecommendations(event)
    loop each recommendation
        Svc->>RMapper: upsert(userId, itemId, category,<br/>label, recommendationText, forgettingScore)
        RMapper->>DB: INSERT ... ON CONFLICT (user_id, item_id) DO UPDATE
    end
    Note over Svc,DB: @Transactional

    Note over LGAConsumer,RGConsumer: exceptions caught + logged in each handler,<br/>not rethrown to Kafka (no DLQ/retry)
```

## 2. REST controller (read-out)

```mermaid
---
config:
  theme: base
  themeVariables:
    background: '#ffffff'
---
sequenceDiagram
    participant Caller
    participant Ctrl as DashboardController
    participant Svc as DashboardServiceImpl
    participant WMapper as WeakPointSnapshotMapper
    participant RMapper as RecentRecommendationMapper
    participant DB as reme_dashboard DB

    Caller->>Ctrl: GET /api/v1/dashboard/{userId}
    Ctrl->>Svc: getSummary(userId)
    Svc->>WMapper: selectProgressSummary(userId)
    WMapper->>DB: SELECT category, COUNT(*), AVG(forgetting_score), MAX(updated_at)<br/>FROM weak_points_snapshot WHERE user_id = ? GROUP BY category
    WMapper-->>Svc: List[CategoryProgress]
    Svc->>RMapper: findRecentByUserId(userId, 10)
    RMapper->>DB: SELECT * FROM recent_recommendations<br/>WHERE user_id = ? ORDER BY received_at DESC LIMIT 10
    RMapper-->>Svc: List[RecommendationSnapshot]
    Svc-->>Ctrl: DashboardSummaryResponse{userId, categoryProgress[], recentRecommendations[]}
    Ctrl-->>Caller: 200 ApiResponse[DashboardSummaryResponse]
```

## Notes

- Idempotency key for both tables: `(user_id, item_id)` — Kafka delivers at-least-once.
- `learning.gap.analyzed` is consumed on its own Kafka `groupId` (`dashboard-service`), distinct from
  `english-service`'s groupIds (`english-service`, `english-service-grammar`,
  `english-service-pronunciation`), so dashboard-service receives its own full copy of every message.
- Per-category counts/avg forgetting score are **computed at read time** via `GROUP BY category`,
  never maintained as a running counter — avoids a second write path that could drift from the
  underlying rows.
- `recommendation.generated` has no producer yet (`recommendation-service` is being built
  concurrently); this consumer is ready to receive it once that service starts publishing.
- For where `learning.gap.analyzed` comes from (S3 download, Whisper, pyannote diarization,
  `RuleBasedAnalyzer`), see [../Ai_service/overview.md](../Ai_service/overview.md).
