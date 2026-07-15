# recording-service — Overview

`recording-service` (Java/Spring Boot, port 8082, `reme_recording` DB) is the entry point of the
whole pipeline: a client uploads an audio/video file, the service stores it in S3 (MinIO locally),
persists metadata, and publishes `recording.uploaded` for `ai-service` to consume for STT +
diarization. Single flattened package `com.remelearning.recording.*` (no domain nesting, unlike
`english-service`, since this service has only one domain). See
`RemeLearning/services/recording-service/src/main/java/com/remelearning/recording/`.

This file covers `recording-service`'s own internals only. `recording.uploaded` is consumed
downstream by `ai-service` — for that side's internal handling, see
[../Ai_service/overview.md](../Ai_service/overview.md). Per-endpoint detail lives in
[upload.md](upload.md), [get-by-id.md](get-by-id.md), [get-by-user.md](get-by-user.md).

## 1. Upload + publish (write path)

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
    participant Kafka

    Caller->>Ctrl: POST /api/v1/recordings<br/>multipart file + userId + languageCode (default "en")
    Ctrl->>Svc: upload(file, userId, languageCode)
    Svc->>Svc: validate userId not blank, file not empty<br/>(else BusinessException.badRequest)
    Svc->>Svc: generate recordingId (UUID), build s3Key = userId/recordingId/filename
    Svc->>S3: upload(recordingBucket, s3Key, file.getInputStream(), file.getSize())
    Svc->>Mapper: insert(recording, status=UPLOADED)
    Mapper->>DB: INSERT INTO recordings
    Note over Svc,DB: @Transactional
    Svc->>Producer: publish(RecordingUploadedEvent)
    Producer->>Kafka: recording.uploaded<br/>{recording_id, user_id, s3_bucket, s3_key, language_code}
    Svc-->>Ctrl: RecordingResponse
    Ctrl-->>Caller: 200 RecordingResponse
```

## 2. Read-out (REST)

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
    participant Mapper as RecordingMapper (MyBatis)
    participant DB as reme_recording DB

    Caller->>Ctrl: GET /api/v1/recordings/{recordingId}
    Ctrl->>Svc: getByRecordingId(recordingId)
    Svc->>Mapper: findByRecordingId(recordingId)
    Mapper->>DB: SELECT recordings WHERE recording_id = ?
    alt not found
        Svc-->>Ctrl: throws BusinessException.notFound(...)
        Ctrl-->>Caller: 404 NOT_FOUND
    else found
        Svc-->>Ctrl: RecordingResponse
        Ctrl-->>Caller: 200 RecordingResponse
    end

    Caller->>Ctrl: GET /api/v1/recordings/user/{userId}
    Ctrl->>Svc: getByUserId(userId)
    Svc->>Mapper: findByUserId(userId)
    Mapper->>DB: SELECT recordings WHERE user_id = ? ORDER BY created_at DESC
    Mapper-->>Svc: Recording[]
    Svc-->>Ctrl: List<RecordingResponse>
    Ctrl-->>Caller: 200 List<RecordingResponse>
```

## Notes

- No idempotency key on write today: each `POST /api/v1/recordings` call always generates a fresh
  `recordingId` (UUID) and inserts a new row — unlike `english-service`'s consumers, there's no
  at-least-once Kafka redelivery to guard against here since the upload is a synchronous REST call.
- `reme.s3.recording-bucket` (env `S3_RECORDING_BUCKET`, default `reme-recordings`) is a new
  configuration convention introduced by this service — no other service had an S3 bucket property
  before it.
- For how `ai-service` consumes `recording.uploaded` (S3 download, Whisper, diarization), see
  [../Ai_service/overview.md](../Ai_service/overview.md).
