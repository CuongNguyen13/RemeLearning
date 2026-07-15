# POST /api/v1/users/{userId}/photo

Uploads/replaces a user's profile photo to S3 and persists its key + public URL. This is the
reference photo `ai-service`'s face-recognition enrollment (`POST /api/v1/faces/enroll`, see
[../Ai_service/enroll-face.md](../Ai_service/enroll-face.md)) later fetches by `userId`. See
`user-service`'s `controller/UserController.java` and `service/impl/UserServiceImpl.java`.

```mermaid
---
config:
  theme: base
  themeVariables:
    background: '#ffffff'
---
sequenceDiagram
    participant Caller
    participant Ctrl as UserController
    participant Svc as UserServiceImpl
    participant Mapper as UserMapper (MyBatis)
    participant DB as reme_user DB
    participant S3 as S3StorageClient (common)

    Caller->>Ctrl: POST /api/v1/users/{userId}/photo<br/>multipart file
    Ctrl->>Svc: uploadPhoto(userId, MultipartFile)
    Svc->>Mapper: findByUserId(userId)
    Mapper->>DB: SELECT users WHERE user_id = ?
    alt not found
        Svc-->>Ctrl: throws BusinessException.notFound(...)
        Ctrl-->>Caller: 404 NOT_FOUND
    else file empty
        Svc-->>Ctrl: throws BusinessException.badRequest(...)
        Ctrl-->>Caller: 400 BAD_REQUEST
    else found + file present
        Svc->>S3: upload(userPhotoBucket, "{userId}/photo/{filename}", inputStream, size)
        alt S3 upload fails
            Svc-->>Ctrl: throws BusinessException (EXTERNAL_SERVICE_ERROR)
            Ctrl-->>Caller: 502 BAD_GATEWAY
        else upload succeeds
            Svc->>S3: objectUrl(userPhotoBucket, key)
            S3-->>Svc: photoUrl
            Svc->>Mapper: updatePhoto(userId, s3Key, photoUrl)
            Mapper->>DB: UPDATE users SET photo_s3_key = ?, photo_url = ?, updated_at = now()
            Svc->>Mapper: findByUserId(userId)
            Mapper-->>Svc: refreshed User
            Svc-->>Ctrl: UserResponse (incl. photoUrl)
            Ctrl-->>Caller: 200 UserResponse
        end
    end
```

## External calls

| # | Call | From -> To | Notes |
|---|------|-----------|-------|
| 1 | Postgres SELECT/UPDATE | user-service -> `reme_user` DB | existence check, then `photo_s3_key`/`photo_url` update, then re-read |
| 2 | S3 PutObject | user-service -> S3/MinIO (`reme.s3.user-photo-bucket`, default `reme-user-photos`) | via `common`'s `S3StorageClient`, same client `recording-service` uses |

## Notes

- Re-uploading with the same filename overwrites the previous photo at the same S3 key; a different
  filename creates a new object (the old one is orphaned in S3, not deleted) - no cleanup logic
  exists for this today.
- No content-type/size validation beyond "not empty" - same level of validation
  `recording-service`'s upload has for its own file parameter.
- This is the only known producer of `photoUrl`; `ai-service`'s `UserServiceClient` is the only known
  consumer today (see [../Ai_service/enroll-face.md](../Ai_service/enroll-face.md)).
