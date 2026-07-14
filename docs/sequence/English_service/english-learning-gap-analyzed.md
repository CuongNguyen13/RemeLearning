# Kafka consumer: learning.gap.analyzed

`LearningGapAnalyzedConsumer` listens on the `learning.gap.analyzed` topic (published by
`ai-service` after forgetting-pattern analysis — see
[../Ai_service/overview.md](../Ai_service/overview.md) and
[../Ai_service/analyze.md](../Ai_service/analyze.md)), filters for the `vocabulary` category, and
persists weak points. See `english-service`'s
`vocabulary/kafka/LearningGapAnalyzedConsumer.java`.

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
    participant Svc as VocabularyWeakPointServiceImpl
    participant Cls as VocabularyClassifier (rule-based / LLM)
    participant Gemini as Gemini API (LLM mode only)
    participant Mapper as VocabularyWeakPointMapper (MyBatis)
    participant DB as reme_english DB

    Kafka->>Consumer: learning.gap.analyzed payload<br/>{recording_id, user_id, weak_points[]}
    Consumer->>Codec: decode(payload) -> LearningGapAnalyzedEvent
    Codec-->>Consumer: LearningGapAnalyzedEvent{recordingId, userId,<br/>weakPoints: WeakPointPayload[]{itemId, category, label, forgettingScore, recommendation}}

    Consumer->>Svc: saveWeakPoints(event)
    activate Svc
    loop each weak point
        alt category != "vocabulary" (case-insensitive)
            Svc->>Svc: skip (grammar/pronunciation not persisted yet)
        else category == "vocabulary"
            Svc->>Cls: classify(label)
            opt vocabulary.classifier.mode = llm
                Cls->>Gemini: generateContent(prompt with label)
                Gemini-->>Cls: classification response
                Note right of Cls: parse/call failure -> fallback to VocabularyType.OTHER
            end
            Cls-->>Svc: VocabularyType (NOUN/VERB/.../OTHER)
            Svc->>Mapper: upsert(userId, itemId, recordingId, label,<br/>vocabularyType, forgettingScore, recommendation)
            Mapper->>DB: INSERT ... ON CONFLICT (user_id, item_id)<br/>DO UPDATE SET forgetting_score, recommendation, ...
        end
    end
    Note over Svc,DB: @Transactional
    deactivate Svc

    Note over Consumer: exceptions caught + logged inside handler,<br/>not rethrown to Kafka (no DLQ/retry)
```

## External calls

| # | Call | From -> To | Notes |
|---|------|-----------|-------|
| 1 | Kafka consume `learning.gap.analyzed` | Kafka broker -> english-service | published by `ai-service`, see [../Ai_service/overview.md](../Ai_service/overview.md) |
| 2 | Gemini `generateContent` REST call | english-service -> Gemini API | only when `vocabulary.classifier.mode=llm`; the default `rule-based` mode makes no outbound call |
| 3 | Postgres UPSERT | english-service -> `reme_english` DB | writes/updates `vocabulary_weak_points` |

## Notes

- Idempotency key: `(user_id, item_id)` — re-analyzing the same item across sessions updates its
  score instead of creating a new row.
- Grammar/pronunciation categories are currently skipped since those domains have no persistence
  built out yet (`package-info.java` placeholder only) — route them to their own services once
  they're ready.
- No downstream event is published (the `vocabulary.analyzed` topic constant exists in
  `KafkaTopics.java` but has no producer yet — defined for future use only).
- For the producer side (`RuleBasedAnalyzer`) and the full cross-service picture, see
  [../Ai_service/overview.md](../Ai_service/overview.md).
