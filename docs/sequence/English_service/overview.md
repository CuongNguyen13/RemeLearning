# english-service — Overview

`english-service` (Java/Spring Boot) is a modular monolith covering three domains — `vocabulary`,
`grammar`, `pronunciation` (each `com.remelearning.english.<domain>`) — all now built out. Only
`vocabulary` owns the `TranscriptReadyConsumer`/transcript persistence: the `transcripts`/
`transcript_segments` tables are a cross-domain concern written once, and `grammar`/`pronunciation`
read them back via the shared `GET /api/v1/transcripts/{recordingId}` endpoint instead of
re-ingesting `transcript.ready`. All three domains do have their own `LearningGapAnalyzedConsumer`,
each filtering `learning.gap.analyzed` to its own `category` and each on its own Kafka `groupId`
(`english-service`, `english-service-grammar`, `english-service-pronunciation`) — necessary because
Kafka splits partitions between consumers sharing one `groupId` on the same topic. See
`RemeLearning/services/english-service/src/main/java/com/remelearning/english/`.

This file covers `english-service`'s own internals only. The Kafka topics it consumes
(`transcript.ready`, `learning.gap.analyzed`) are published upstream by `ai-service` — for that
side's internal handling, see [../Ai_service/overview.md](../Ai_service/overview.md). Per-endpoint/
per-consumer detail lives in [english-get-transcript.md](english-get-transcript.md),
[english-get-weak-points.md](english-get-weak-points.md) (vocabulary),
[english-get-grammar-weak-points.md](english-get-grammar-weak-points.md),
[english-get-pronunciation-weak-points.md](english-get-pronunciation-weak-points.md),
[english-transcript-ready.md](english-transcript-ready.md),
[english-learning-gap-analyzed.md](english-learning-gap-analyzed.md) (vocabulary),
[english-learning-gap-analyzed-grammar.md](english-learning-gap-analyzed-grammar.md),
[english-learning-gap-analyzed-pronunciation.md](english-learning-gap-analyzed-pronunciation.md).

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
    participant TRConsumer as TranscriptReadyConsumer (vocabulary)
    participant TSvc as TranscriptServiceImpl
    participant TMapper as TranscriptMapper (MyBatis)
    participant LGAVocab as LearningGapAnalyzedConsumer (vocabulary, groupId=english-service)
    participant LGAGrammar as LearningGapAnalyzedConsumer (grammar, groupId=english-service-grammar)
    participant LGAPron as LearningGapAnalyzedConsumer (pronunciation, groupId=english-service-pronunciation)
    participant WSvc as domain WeakPointServiceImpl
    participant Cls as domain Classifier (rule-based / LLM)
    participant Gemini as Gemini API (LLM mode only)
    participant WMapper as domain WeakPointMapper (MyBatis)
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

    par vocabulary consumer group
        Kafka->>LGAVocab: learning.gap.analyzed<br/>(published by ai-service, see ../Ai_service/overview.md)
    and grammar consumer group
        Kafka->>LGAGrammar: learning.gap.analyzed (same message, own groupId)
    and pronunciation consumer group
        Kafka->>LGAPron: learning.gap.analyzed (same message, own groupId)
    end

    LGAVocab->>WSvc: saveWeakPoints(event) [VocabularyWeakPointServiceImpl]
    LGAGrammar->>WSvc: saveWeakPoints(event) [GrammarWeakPointServiceImpl]
    LGAPron->>WSvc: saveWeakPoints(event) [PronunciationWeakPointServiceImpl]
    loop each weak point, per domain service
        alt category doesn't match this domain
            WSvc->>WSvc: skip (handled by one of the other two domains' own consumer)
        else category matches this domain
            WSvc->>Cls: classify(label)
            opt <domain>.classifier.mode = llm
                Cls->>Gemini: generateContent(prompt with label)
                Gemini-->>Cls: classification response
            end
            Cls-->>WSvc: domain type enum (e.g. VocabularyType/GrammarType/PronunciationType)
            WSvc->>WMapper: upsert(userId, itemId, ...)
            WMapper->>DB: INSERT ... ON CONFLICT (user_id, item_id) DO UPDATE
            Note over WSvc,DB: @Transactional
        end
    end

    Note over TRConsumer,LGAPron: exceptions caught + logged in each handler,<br/>not rethrown to Kafka (no DLQ/retry)
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
    participant TCtrl as TranscriptController (vocabulary)
    participant TSvc as TranscriptServiceImpl
    participant WCtrl as domain WeakPointController (Vocabulary/Grammar/Pronunciation)
    participant WSvc as domain WeakPointServiceImpl
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

    Caller->>WCtrl: GET /api/v1/{vocabulary|grammar|pronunciation}/weak-points/{userId}[?type=][/grouped]
    WCtrl->>WSvc: getWeakPoints(userId, type)
    WSvc->>Mapper: findByUserId(userId, type)
    Mapper->>DB: SELECT {domain}_weak_points WHERE user_id = ? [AND {domain}_type = ?]
    Mapper-->>WSvc: List[domain WeakPoint]
    WSvc-->>WCtrl: List[domain WeakPoint]
    opt /grouped variant
        WCtrl->>WCtrl: Collectors.groupingBy(domain type enum)
    end
    WCtrl-->>Caller: 200 (list or map keyed by the domain's type enum)
```

## Notes

- Idempotency keys: `recording_id` for transcripts, `(user_id, item_id)` for weak points — both
  needed because Kafka delivers at-least-once.
- `grammar`/`pronunciation` each persist to their own table (`grammar_weak_points`,
  `pronunciation_weak_points`) via their own `LearningGapAnalyzedConsumer`, filtered to their own
  `category` and running on their own Kafka `groupId` so all three domains get every message
  instead of splitting partitions between them.
- No outbound Kafka event is published by `english-service` today (`vocabulary.analyzed`,
  `grammar.analyzed`, `pronunciation.analyzed` topic constants exist but have no producer yet).
- For where these Kafka messages come from (S3 download, Whisper, pyannote diarization,
  `RuleBasedAnalyzer`), see [../Ai_service/overview.md](../Ai_service/overview.md).
