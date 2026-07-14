# english-service — Overview

`english-service` (Java/Spring Boot) is a modular monolith; only the `vocabulary` domain
(`com.remelearning.english.vocabulary`) is built out so far. It has two entry points into the
domain: two Kafka consumers that ingest events from `ai-service`, and two REST controllers that
serve the persisted data back out. See
`RemeLearning/services/english-service/src/main/java/com/remelearning/english/vocabulary/`.

This file covers `english-service`'s own internals only. The Kafka topics it consumes
(`transcript.ready`, `learning.gap.analyzed`) are published upstream by `ai-service` — for that
side's internal handling, see [../Ai_service/overview.md](../Ai_service/overview.md). Per-endpoint/
per-consumer detail lives in [english-get-transcript.md](english-get-transcript.md),
[english-get-weak-points.md](english-get-weak-points.md),
[english-transcript-ready.md](english-transcript-ready.md),
[english-learning-gap-analyzed.md](english-learning-gap-analyzed.md).

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
    participant TRConsumer as TranscriptReadyConsumer
    participant TSvc as TranscriptServiceImpl
    participant TMapper as TranscriptMapper (MyBatis)
    participant LGAConsumer as LearningGapAnalyzedConsumer
    participant WSvc as VocabularyWeakPointServiceImpl
    participant Cls as VocabularyClassifier (rule-based / LLM)
    participant Gemini as Gemini API (LLM mode only)
    participant WMapper as VocabularyWeakPointMapper (MyBatis)
    participant DB as reme_english DB

    Kafka->>TRConsumer: transcript.ready<br/>(published by ai-service, see ../Ai_service/overview.md)
    TRConsumer->>TSvc: saveTranscript(event)
    TSvc->>TMapper: findByRecordingId(recordingId)
    TMapper->>DB: SELECT transcripts WHERE recording_id = ?
    alt already exists (at-least-once redelivery)
        TSvc-->>TRConsumer: skip (no duplicate insert)
    else not found
        TSvc->>TMapper: insertTranscript(...) + insertSegment(...) per segment
        TMapper->>DB: INSERT INTO transcripts / transcript_segments
        Note over TSvc,DB: @Transactional
    end

    Kafka->>LGAConsumer: learning.gap.analyzed<br/>(published by ai-service, see ../Ai_service/overview.md)
    LGAConsumer->>WSvc: saveWeakPoints(event)
    loop each weak point
        alt category != "vocabulary"
            WSvc->>WSvc: skip (grammar/pronunciation not persisted yet)
        else category == "vocabulary"
            WSvc->>Cls: classify(label)
            opt vocabulary.classifier.mode = llm
                Cls->>Gemini: generateContent(prompt with label)
                Gemini-->>Cls: classification response
            end
            Cls-->>WSvc: VocabularyType
            WSvc->>WMapper: upsert(userId, itemId, ...)
            WMapper->>DB: INSERT ... ON CONFLICT (user_id, item_id) DO UPDATE
            Note over WSvc,DB: @Transactional
        end
    end

    Note over TRConsumer,LGAConsumer: exceptions caught + logged in each handler,<br/>not rethrown to Kafka (no DLQ/retry)
```

## 2. REST controllers (read-out)

```mermaid
---
config:
  theme: base
  themeVariables:
    background: '#ffffff'
---
sequenceDiagram
    participant Caller
    participant TCtrl as TranscriptController
    participant TSvc as TranscriptServiceImpl
    participant WCtrl as VocabularyWeakPointController
    participant WSvc as VocabularyWeakPointServiceImpl
    participant Mapper as MyBatis mappers
    participant DB as reme_english DB

    Caller->>TCtrl: GET /api/v1/transcripts/{recordingId}
    TCtrl->>TSvc: getByRecordingId(recordingId)
    TSvc->>Mapper: findByRecordingId + findSegmentsByTranscriptId
    Mapper->>DB: SELECT transcripts / transcript_segments
    alt not found
        TSvc-->>TCtrl: throws BusinessException.notFound(...)
        TCtrl-->>Caller: 404 NOT_FOUND
    else found
        TSvc-->>TCtrl: TranscriptResponse
        TCtrl-->>Caller: 200 TranscriptResponse
    end

    Caller->>WCtrl: GET /api/v1/vocabulary/weak-points/{userId}[?type=][/grouped]
    WCtrl->>WSvc: getWeakPoints(userId, type)
    WSvc->>Mapper: findByUserId(userId, type)
    Mapper->>DB: SELECT vocabulary_weak_points WHERE user_id = ? [AND vocabulary_type = ?]
    Mapper-->>WSvc: List[VocabularyWeakPoint]
    WSvc-->>WCtrl: List[VocabularyWeakPoint]
    opt /grouped variant
        WCtrl->>WCtrl: Collectors.groupingBy(VocabularyType)
    end
    WCtrl-->>Caller: 200 (list or map keyed by VocabularyType)
```

## Notes

- Idempotency keys: `recording_id` for transcripts, `(user_id, item_id)` for weak points — both
  needed because Kafka delivers at-least-once.
- `grammar`/`pronunciation` domains are placeholders (`package-info.java` only) — their categories
  in `learning.gap.analyzed` are received but discarded until those domains are built out.
- No outbound Kafka event is published by `english-service` today (`vocabulary.analyzed` topic
  constant exists but has no producer yet).
- For where these Kafka messages come from (S3 download, Whisper, pyannote diarization,
  `RuleBasedAnalyzer`), see [../Ai_service/overview.md](../Ai_service/overview.md).
