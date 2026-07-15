# GET /api/v1/recordings/user/{userId}

Lists every recording uploaded by a given user, most recent first. See `recording-service`'s
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

    Caller->>Ctrl: GET /api/v1/recordings/user/{userId}
    Ctrl->>Svc: getByUserId(userId)
    Svc->>Mapper: findByUserId(userId)
    Mapper->>DB: SELECT recordings WHERE user_id = ? ORDER BY created_at DESC
    Mapper-->>Svc: Recording[]
    Svc->>Svc: map each Recording -> RecordingResponse
    Svc-->>Ctrl: List<RecordingResponse>
    Ctrl-->>Caller: 200 List<RecordingResponse>
```

## Notes

- No pagination yet — returns every recording for the user in one response. Fine for the current
  early/greenfield state; revisit if a user's recording count grows large.
- Empty list (not 404) is returned when the user has no recordings.
