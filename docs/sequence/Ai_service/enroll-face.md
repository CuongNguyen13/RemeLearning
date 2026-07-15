# POST /api/v1/faces/enroll

Enrolls a `user_id` as a known face for later speaker identification in
`/api/v1/upload`/`/api/v1/transcribe` (when `FACE_RECOGNITION_ENABLED=true`). By default fetches
the reference photo from user-service's stored `photoUrl` - the production path, so the reference
image genuinely comes from user-service as intended - rather than requiring the caller to hold the
image. See `app/api/routes.py::enroll_face`, `app/face/enrollment.py::FaceEnrollmentService.enroll`.

```mermaid
---
config:
  theme: base
  themeVariables:
    background: '#ffffff'
---
sequenceDiagram
    participant Caller
    participant AI as ai-service
    participant User as user-service
    participant S3
    participant Face as InsightFaceEngine
    participant AiDb as Postgres (reme_ai, schema ai)

    Caller->>AI: POST /api/v1/faces/enroll<br/>{user_id, name?} (multipart; image file optional override)

    alt image not provided (primary path)
        AI->>User: GET /api/v1/users/{userId}
        User-->>AI: UserResponse {name, photoUrl, ...}
        AI->>S3: GET photoUrl (public/presigned S3 URL)
        S3-->>AI: image bytes
    else image provided directly
        Note right of AI: local-testing override - skips user-service entirely
    end

    AI->>Face: detect_faces(image_path)
    Face-->>AI: DetectedFace[] (embedding + detection_confidence)
    Note right of AI: if multiple faces detected, keeps only the one<br/>with the highest detection_confidence
    AI->>AiDb: UPSERT ai.known_faces (user_id, name, embedding, source)
    AI-->>Caller: 200 EnrolledFaceResponse {user_id, name}
```

## External calls

| # | Call | From -> To | Notes |
|---|------|-----------|-------|
| 1 | REST GET | ai-service -> user-service (`GET /api/v1/users/{userId}`) | only when `image` isn't supplied directly |
| 2 | HTTP GET | ai-service -> S3/MinIO (via `photoUrl`) | fetches the actual photo bytes |
| 3 | Postgres | ai-service -> `reme_ai` (schema `ai`) | `known_faces` upsert, keyed by `user_id` |

## Notes

- The raw embedding never leaves ai-service in an API response - `EnrolledFaceResponse` only exposes
  `user_id`/`name`. `GET /api/v1/faces` lists all enrolled faces the same way, for verifying
  enrollment before testing `/api/v1/upload`.
- `422` if no face is detected in the reference photo; `404` if `user_id` doesn't exist in
  user-service (and no `image` override was given).
- See [../User_service/photo-upload.md](../User_service/photo-upload.md) for how the photo gets into
  user-service in the first place, and
  [../../flow/ai-service-data-flow.md](../../flow/ai-service-data-flow.md) for how the resulting
  embedding is later used during `/api/v1/upload`/`/api/v1/transcribe`.
