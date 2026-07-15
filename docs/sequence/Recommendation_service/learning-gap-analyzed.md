# Kafka consumer: learning.gap.analyzed -> recommendation.generated

`LearningGapAnalyzedConsumer` (package `recommendation.kafka`, `groupId: recommendation-service`)
listens on the `learning.gap.analyzed` topic (published by `ai-service` after forgetting-pattern
analysis — see [../Ai_service/overview.md](../Ai_service/overview.md) and
[../Ai_service/analyze.md](../Ai_service/analyze.md)). Unlike `english-service`'s per-domain
consumers, it does **not** filter by `category` — every weak point in the event becomes a
recommendation row. After persisting the batch it publishes a new `recommendation.generated` event.
See `recommendation-service`'s `kafka/LearningGapAnalyzedConsumer.java` and
`service/impl/RecommendationServiceImpl.java`.

```mermaid
---
config:
  theme: base
  themeVariables:
    background: '#ffffff'
---
sequenceDiagram
    participant Kafka
    participant Consumer as LearningGapAnalyzedConsumer
    participant Codec as EventCodec (snake_case ObjectMapper)
    participant Svc as RecommendationServiceImpl
    participant Mapper as RecommendationMapper (MyBatis)
    participant DB as reme_recommendation DB
    participant Publisher as KafkaEventPublisher (common)

    Kafka->>Consumer: learning.gap.analyzed payload<br/>{recording_id, user_id, weak_points[]}
    Consumer->>Codec: decode(payload) -> LearningGapAnalyzedEvent
    Codec-->>Consumer: LearningGapAnalyzedEvent{recordingId, userId,<br/>weakPoints: WeakPointPayload[]{itemId, category, label, forgettingScore, recommendation}}

    Consumer->>Svc: handleLearningGapAnalyzed(event)
    activate Svc
    loop each weak point (all categories: vocabulary, grammar, pronunciation)
        Svc->>Svc: build Recommendation{userId, recordingId, itemId, category,<br/>label, forgettingScore, recommendationText}
        Svc->>Mapper: upsert(recommendation)
        Mapper->>DB: INSERT ... ON CONFLICT (user_id, item_id)<br/>DO UPDATE SET forgetting_score, recommendation_text, ...
    end
    Note over Svc,DB: @Transactional over the upsert loop

    Svc->>Svc: build RecommendationGeneratedEvent(recordingId, userId,<br/>recommendations: RecommendationPayload[])
    Svc->>Publisher: publish(recommendation.generated, userId, event)
    Publisher->>Kafka: recommendation.generated<br/>{eventId, eventType, occurredAt, recordingId, userId, recommendations[]}
    deactivate Svc

    Note over Consumer: exceptions caught + logged inside handler,<br/>not rethrown to Kafka (no DLQ/retry)
```

## External calls

| # | Call | From -> To | Notes |
|---|------|-----------|-------|
| 1 | Kafka consume `learning.gap.analyzed` | Kafka broker -> recommendation-service | published by `ai-service`, see [../Ai_service/overview.md](../Ai_service/overview.md) |
| 2 | Postgres UPSERT | recommendation-service -> `reme_recommendation` DB | writes/updates `recommendations`, one row per weak point |
| 3 | Kafka publish `recommendation.generated` | recommendation-service -> Kafka broker | via `common`'s `EventPublisher`/`KafkaEventPublisher`; no consumer exists yet |

## Notes

- Idempotency key: `(user_id, item_id)` — re-analyzing the same item across sessions updates its
  score instead of creating a new row (same convention as `english-service`'s weak-point tables).
- No category filtering: `english-service`'s `vocabulary`/`grammar`/`pronunciation` domains each keep
  only their own category from this same event; `recommendation-service` persists all of them as
  recommendations, since its job is aggregation across domains rather than per-skill classification.
- Consumer `groupId` (`recommendation-service`) is distinct from `english-service`'s consumer groups
  on the same topic, so Kafka delivers a full copy of every message to each service instead of
  splitting partitions between them.
- `RecommendationGeneratedEvent` extends `common`'s `BaseEvent` (`eventId`, `eventType =
  "recommendation.generated"`, `occurredAt` auto-populated) and adds `recordingId`, `userId`,
  `recommendations: RecommendationPayload[]{itemId, category, label, recommendationText,
  forgettingScore}`.
- For the producer side (`RuleBasedAnalyzer`) and the full cross-service picture, see
  [../Ai_service/overview.md](../Ai_service/overview.md).
