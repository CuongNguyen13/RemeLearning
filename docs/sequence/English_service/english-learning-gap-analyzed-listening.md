# Kafka consumer: learning.gap.analyzed (listening.weakpoint)

`LearningGapAnalyzedConsumer` (package `listening.weakpoint.kafka`, `groupId:
english-service-listening`) listens on the same `learning.gap.analyzed` topic as
vocabulary/grammar/pronunciation's consumers, filters for the `listening` category, and persists
weak points. Unlike the other three domains, this topic carries only **one** of listening's two
sources today — dictation's dual-write (see `dictation-practice.md`) — so `sourceType` is
hard-coded to `DICTATION` here rather than inferred from the event; the other source
(`COMPREHENSION`) is written by a completely different path (`WeakPointDispatcherImpl`'s Java-direct
`applyJavaComputedScore`, see [practice-redo.md](practice-redo.md)), not through this consumer. See
`english-service`'s `listening/weakpoint/kafka/LearningGapAnalyzedConsumer.java`.

```mermaid
---
config:
  theme: base
  themeVariables:
    background: '#ffffff'
---
sequenceDiagram
    participant Kafka
    participant Consumer as LearningGapAnalyzedConsumer (listening.weakpoint)
    participant Codec as EventCodec (snake_case ObjectMapper)
    participant Svc as ListeningWeakPointServiceImpl
    participant Mapper as ListeningWeakPointMapper (MyBatis)
    participant DB as reme_english DB

    Kafka->>Consumer: learning.gap.analyzed payload<br/>{recording_id, user_id, weak_points[]}
    Consumer->>Codec: decode(payload) -> LearningGapAnalyzedEvent
    Codec-->>Consumer: LearningGapAnalyzedEvent{recordingId, userId,<br/>weakPoints: WeakPointPayload[]{itemId, category, label, forgettingScore, recommendation}}

    Consumer->>Svc: saveWeakPoints(event)
    activate Svc
    loop each weak point
        alt category != "listening" (case-insensitive)
            Svc->>Svc: skip (handled by vocabulary's/grammar's/pronunciation's own consumer instead)
        else category == "listening"
            Note right of Svc: sourceType hard-coded to DICTATION - this Kafka path today only<br/>ever carries dictation's dual-write (see DictationServiceImpl.publishWeakPoints)
            Svc->>Mapper: upsert(userId, itemId, recordingId, label,<br/>sourceType=DICTATION, forgettingScore, recommendation, scoreSource=PYTHON_LEGACY)
            Mapper->>DB: INSERT ... ON CONFLICT (user_id, item_id)<br/>DO UPDATE SET forgetting_score, recommendation, ...<br/>WHERE NOT (existing.score_source=JAVA_ENGINE AND incoming.score_source=PYTHON_LEGACY)
        end
    end
    Note over Svc,DB: @Transactional
    deactivate Svc

    Note over Consumer: exceptions caught + logged inside handler,<br/>not rethrown to Kafka (no DLQ/retry)
```

## External calls

| # | Call | From -> To | Notes |
|---|------|-----------|-------|
| 1 | Kafka consume `learning.gap.analyzed` | Kafka broker -> english-service | published by `english-service`'s own `dictation` package (dual-write), not by `ai-service`, for this category |
| 2 | Postgres UPSERT | english-service -> `reme_english` DB | writes/updates `listening_weak_points`, guarded by the same one-way `score_source` ratchet as the other three domains |

## Notes

- Uses a dedicated `groupId` (`english-service-listening`) distinct from the other three domains' -
  required because Kafka splits partitions between consumers sharing one `groupId` on the same
  topic, so a shared `groupId` would mean each domain only sees a subset of messages instead of
  every message.
- Idempotency key: `(user_id, item_id)` — re-analyzing the same item (e.g. the same word mistyped
  again in a later dictation attempt) updates its score instead of creating a new row.
- Unlike `grammar`/`pronunciation`, there is no classifier step here - `sourceType` is a hard-coded
  constant, not something derived from `label`, since this consumer only ever sees one kind of
  producer for category `"listening"` today. If a second Kafka-sourced producer of this category is
  ever added, this hard-coding needs revisiting.
- No downstream event is published (`listening.analyzed` doesn't exist as a topic - listening reuses
  the same `learning.gap.analyzed` topic both to receive dictation's dual-write and, indirectly via
  `practice.redo`, to keep `recommendation-service`/`dashboard-service` in sync).
