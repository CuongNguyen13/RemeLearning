# GET /api/v1/transcripts/{recordingId}

Returns a transcript (with its segments) that was previously persisted from the Kafka
`transcript.ready` event. See `english-service`'s
`vocabulary/controller/TranscriptController.java`.

```mermaid
---
config:
  theme: base
  themeVariables:
    background: '#ffffff'
---
sequenceDiagram
    participant Caller
    participant Ctrl as TranscriptController
    participant Svc as TranscriptServiceImpl
    participant Mapper as TranscriptMapper (MyBatis)
    participant DB as reme_english DB

    Caller->>Ctrl: GET /api/v1/transcripts/{recordingId}
    Ctrl->>Svc: getByRecordingId(recordingId)
    Svc->>Mapper: findByRecordingId(recordingId)
    Mapper->>DB: SELECT transcripts WHERE recording_id = ?
    DB-->>Mapper: Transcript row (or none)

    alt not found
        Mapper-->>Svc: Optional.empty()
        Svc-->>Ctrl: throws BusinessException.notFound(...)
        Ctrl-->>Caller: 404 NOT_FOUND
    else found
        Mapper-->>Svc: Transcript
        Svc->>Mapper: findSegmentsByTranscriptId(transcript.id)
        Mapper->>DB: SELECT transcript_segments WHERE transcript_id = ?
        DB-->>Mapper: TranscriptSegment[]
        Mapper-->>Svc: TranscriptSegment[]
        Svc-->>Ctrl: TranscriptResponse<br/>{recordingId, userId, fullText, segments[]}
        Ctrl-->>Caller: 200 TranscriptResponse
    end
```

## Notes

- `segments[]` fields: `id, transcriptId, speaker, content, startSeconds, endSeconds, segmentOrder`.
- This data is written by the Kafka consumer `TranscriptReadyConsumer` — see
  [english-transcript-ready.md](english-transcript-ready.md).
