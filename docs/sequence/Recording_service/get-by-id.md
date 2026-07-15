# GET /api/v1/recordings/{recordingId}

Returns a single recording's stored metadata. See `recording-service`'s
`controller/RecordingController.java` and `service/impl/RecordingServiceImpl.java`.

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
    DB-->>Mapper: Recording row (or none)

    alt not found
        Mapper-->>Svc: Optional.empty()
        Svc-->>Ctrl: throws BusinessException.notFound(...)
        Ctrl-->>Caller: 404 NOT_FOUND
    else found
        Mapper-->>Svc: Recording
        Svc-->>Ctrl: RecordingResponse<br/>{recordingId, userId, status, s3Bucket, s3Key, createdAt}
        Ctrl-->>Caller: 200 RecordingResponse
    end
```

## Notes

- This data is written by `POST /api/v1/recordings` — see [upload.md](upload.md).
- `RecordingResponse` intentionally omits `originalFilename`/`contentType` (internal metadata not
  needed by callers today) — extend the DTO if a future caller needs them.
