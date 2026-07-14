# english-service — Data Flow

Focuses on **what happens to the data** (transformations, formats, storage) as it moves through
`english-service`'s `vocabulary` domain, as opposed to the sequence diagrams in
[../sequence/English_service/](../sequence/English_service/) which focus on call order between
components.

```mermaid
---
config:
  theme: base
  themeVariables:
    background: '#ffffff'
---
flowchart TD
    subgraph Input["Input (from ai-service via Kafka)"]
        TREvent["transcript.ready event<br/>{recording_id, user_id, full_text, segments[]}"]
        LGAEvent["learning.gap.analyzed event<br/>{recording_id, user_id, weak_points[]}"]
    end

    subgraph TranscriptFlow["Transcript ingestion"]
        Decode1["EventCodec decode<br/>snake_case JSON -> TranscriptReadyEvent"]
        Idem1{"findByRecordingId<br/>already exists?"}
        Skip1["skip (at-least-once redelivery)"]
        Insert1["insertTranscript + insertSegment per item<br/>(segmentOrder = incrementing index)"]
    end

    subgraph WeakPointFlow["Weak point ingestion"]
        Decode2["EventCodec decode<br/>snake_case JSON -> LearningGapAnalyzedEvent"]
        Filter{"category == vocabulary?"}
        Discard["discard<br/>(grammar/pronunciation not built out yet)"]
        Classify["VocabularyClassifier.classify(label)<br/>rule-based heuristics, or LLM (Gemini) with<br/>fallback to OTHER on failure"]
        Upsert["upsert keyed on (user_id, item_id)"]
    end

    subgraph Storage["reme_english DB"]
        T1[("transcripts")]
        T2[("transcript_segments")]
        T3[("vocabulary_weak_points")]
    end

    subgraph ReadOut["Read-out (REST)"]
        GetTranscript["GET /api/v1/transcripts/{recordingId}<br/>-> TranscriptResponse{fullText, segments[]}"]
        GetWeak["GET /api/v1/vocabulary/weak-points/{userId}[/grouped]<br/>-> List or Map[VocabularyType, List]"]
    end

    TREvent --> Decode1 --> Idem1
    Idem1 -->|yes| Skip1
    Idem1 -->|no| Insert1
    Insert1 --> T1
    Insert1 --> T2

    LGAEvent --> Decode2 --> Filter
    Filter -->|no| Discard
    Filter -->|yes| Classify
    Classify --> Upsert
    Upsert --> T3

    T1 --> GetTranscript
    T2 --> GetTranscript
    T3 --> GetWeak
```

## Data shape at each stage

| Stage | Format | Notes |
|---|---|---|
| `TranscriptReadyEvent` | `{recordingId, userId, fullText, segments: [{speaker, text, startSeconds, endSeconds}]}` | decoded from ai-service's snake_case JSON via `EventCodec` |
| `transcripts` row | `{id, recording_id, user_id, full_text}` | one row per recording, idempotent on `recording_id` |
| `transcript_segments` rows | `{id, transcript_id, speaker, content, start_seconds, end_seconds, segment_order}` | one row per segment, ordered |
| `LearningGapAnalyzedEvent` | `{recordingId, userId, weakPoints: [{itemId, category, label, forgettingScore, recommendation}]}` | covers all categories; only `vocabulary` survives the filter |
| `vocabulary_weak_points` row | `{id, recording_id, user_id, item_id, label, vocabulary_type, forgetting_score, recommendation, updated_at}` | upserted on `(user_id, item_id)` — re-analysis updates score in place instead of duplicating |
| `VocabularyType` | enum `NOUN, VERB, ADJECTIVE, ADVERB, PHRASAL_VERB, COLLOCATION, IDIOM, OTHER` | assigned by `VocabularyClassifier` |

## Where data comes from / where it can go next

- Both input events are published by `ai-service` — see
  [../flow/ai-service-data-flow.md](ai-service-data-flow.md) for how that data was produced (S3 ->
  Whisper -> pyannote -> `RuleBasedAnalyzer`).
- No outbound Kafka event is produced by `english-service` today (`vocabulary.analyzed` topic
  constant exists but has no producer).
- `grammar`/`pronunciation` categories are received in `learning.gap.analyzed` but discarded —
  their own storage/read-out flow doesn't exist yet (`package-info.java` placeholders only).
