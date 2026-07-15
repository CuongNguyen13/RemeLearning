# Kafka consumer: transcript.ready

`TranscriptReadyConsumer` listens on the `transcript.ready` topic (published by `ai-service` after
STT+diarization — see [../Ai_service/overview.md](../Ai_service/overview.md)) and persists the
transcript. See `english-service`'s `vocabulary/kafka/TranscriptReadyConsumer.java`.

```mermaid
---
config:
  theme: base
  themeVariables:
    background: '#ffffff'
---
sequenceDiagram
    participant Kafka
    participant Consumer as TranscriptReadyConsumer
    participant Codec as EventCodec (snake_case ObjectMapper)
    participant Svc as TranscriptServiceImpl
    participant Mapper as TranscriptMapper (MyBatis)
    participant DB as reme_english DB

    Kafka->>Consumer: transcript.ready payload<br/>{recording_id, user_id, full_text, segments[]}
    Consumer->>Codec: decode(payload) -> TranscriptReadyEvent
    Codec-->>Consumer: TranscriptReadyEvent{recordingId, userId, fullText,<br/>segments: SegmentPayload[]{speaker, text, startSeconds, endSeconds}}

    Consumer->>Svc: saveTranscript(event)
    activate Svc
    Svc->>Mapper: findByRecordingId(recordingId)
    Mapper->>DB: SELECT transcripts WHERE recording_id = ?

    alt already exists (at-least-once redelivery)
        Mapper-->>Svc: Transcript found
        Svc-->>Consumer: skip (log + return, no duplicate insert)
    else not found
        Mapper-->>Svc: not found
        Svc->>Mapper: insertTranscript(recordingId, userId, fullText)
        Mapper->>DB: INSERT INTO transcripts
        loop each segment (segmentOrder = incrementing index)
            Svc->>Mapper: insertSegment(transcriptId, speaker, content, startSeconds, endSeconds, segmentOrder)
            Mapper->>DB: INSERT INTO transcript_segments
        end
        Note over Svc,DB: @Transactional - all inserts atomic
        Svc-->>Consumer: done
    end
    deactivate Svc

    Note over Consumer: exceptions caught + logged inside handler,<br/>not rethrown to Kafka (no DLQ/retry)
```

## External calls

| # | Call | From -> To | Notes |
|---|------|-----------|-------|
| 1 | Kafka consume `transcript.ready` | Kafka broker -> english-service | published by `ai-service`, see [../Ai_service/overview.md](../Ai_service/overview.md) |
| 2 | Postgres INSERT | english-service -> `reme_english` DB | writes transcript + segments |

## Notes

- Idempotency key: `recording_id` — required since Kafka delivers at-least-once.
- `segments[]` may include `speaker == "vision"` entries when ai-service's `VISION_ENABLED=true`
  (Gemini frame-captioning, see [../Ai_service/overview.md](../Ai_service/overview.md)) — persisted
  the same as any other segment, no special-casing needed here.
- No downstream event is published from the english-service side.
- For the producer side (S3, Whisper, diarization) and the full cross-service picture, see
  [../Ai_service/overview.md](../Ai_service/overview.md).
