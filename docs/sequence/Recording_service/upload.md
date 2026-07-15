# POST /api/v1/recordings

Accepts a video/audio file (`multipart/form-data`), stores it in S3 (MinIO locally), persists its
metadata, and publishes `recording.uploaded` for `ai-service` to consume. See
`recording-service`'s `controller/RecordingController.java` and `service/impl/RecordingServiceImpl.java`.

```mermaid
---
config:
  theme: base
  themeVariables:
    background: '#ffffff'
---
sequenceDiagram
    participant Caller
    participant Ctrl as RecordingController
    participant Svc as RecordingServiceImpl
    participant S3 as S3StorageClient (MinIO locally)
    participant Mapper as RecordingMapper (MyBatis)
    participant DB as reme_recording DB
    participant Producer as RecordingUploadedProducer
    participant Codec as EventCodec (snake_case ObjectMapper)
    participant Kafka

    Caller->>Ctrl: POST /api/v1/recordings<br/>file + userId + languageCode (default "en")
    Ctrl->>Svc: upload(file, userId, languageCode)

    alt userId blank or file empty
        Svc-->>Ctrl: throws BusinessException.badRequest(...)
        Ctrl-->>Caller: 400 VALIDATION_ERROR
    else valid request
        Svc->>Svc: recordingId = UUID.randomUUID()<br/>s3Key = userId/recordingId/originalFilename
        Svc->>S3: upload(recordingBucket, s3Key, inputStream, contentLength)
        alt S3 upload fails
            S3-->>Svc: exception
            Svc-->>Ctrl: throws BusinessException(EXTERNAL_SERVICE_ERROR, 502)
            Ctrl-->>Caller: 502 EXTERNAL_SERVICE_ERROR
        else upload succeeds
            Svc->>Mapper: insert(Recording{status=UPLOADED})
            Mapper->>DB: INSERT INTO recordings
            Note over Svc,DB: @Transactional
            Svc->>Producer: publish(RecordingUploadedEvent)
            Producer->>Codec: writeValueAsString(event)
            Codec-->>Producer: snake_case JSON<br/>{recording_id, user_id, s3_bucket, s3_key, language_code}
            Producer->>Kafka: send(recording.uploaded, key=recordingId, payload)
            Svc-->>Ctrl: RecordingResponse
            Ctrl-->>Caller: 200 RecordingResponse
        end
    end
```

## External calls

| # | Call | From -> To | Notes |
|---|------|-----------|-------|
| 1 | S3 `PutObject` | recording-service -> S3/MinIO | key = `{userId}/{recordingId}/{originalFilename}`, bucket from `reme.s3.recording-bucket` |
| 2 | Postgres INSERT | recording-service -> `reme_recording` DB | writes the `recordings` row |
| 3 | Kafka publish `recording.uploaded` | recording-service -> Kafka broker | consumed by `ai-service`, see [../Ai_service/overview.md](../Ai_service/overview.md) |

## Notes

- `recordingId` is always freshly generated (`UUID.randomUUID()`) — there is no idempotency check
  on this write path, unlike `english-service`'s Kafka consumers which guard against at-least-once
  redelivery.
- The event payload's JSON keys are snake_case (`recording_id`, `s3_bucket`, ...) via `EventCodec`,
  matching `ai-service`'s pydantic `RecordingUploadedEvent` (`app/schemas/events.py`) exactly.
- If S3 upload fails, nothing is persisted and no event is published (the exception is thrown
  before the mapper insert) — the client gets a 502 and can retry the whole request.
