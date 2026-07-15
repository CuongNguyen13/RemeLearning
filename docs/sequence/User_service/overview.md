# user-service — Overview

`user-service` (Java/Spring Boot, port 8081, `reme_user` DB) is authentication + basic user
profile management: register/login issue a real signed JWT, and profile read/update is plain
CRUD. Single flattened package `com.remelearning.user.*` (no domain nesting, unlike
`english-service`, since this service has only one domain). See
`RemeLearning/services/user-service/src/main/java/com/remelearning/user/`.

**Scope note:** this service *issues* JWTs (via `common`'s `JwtTokenProvider`) but nothing —
not this service, not any other — validates/enforces them yet. There is no `SecurityConfig`, no
filter, no Spring Security starter anywhere in the repo (only `spring-security-crypto`, for
`BCryptPasswordEncoder`). Every endpoint here is unauthenticated at the HTTP layer today.

This file covers `user-service`'s own internals only. Per-endpoint detail lives in
[register.md](register.md), [login.md](login.md), [get-profile.md](get-profile.md),
[photo-upload.md](photo-upload.md).

`user-service` now also stores a profile photo (`photo_s3_key`/`photo_url` on `users`, via
`POST /api/v1/users/{userId}/photo`, S3-backed the same way `recording-service` stores its files) -
added so `ai-service`'s face-recognition feature has a reference image to enroll per userId, sourced
from this service rather than requiring a caller to supply the image directly (see
[photo-upload.md](photo-upload.md) and `../Ai_service/enroll-face.md`).

## 1. Register (write path)

```mermaid
---
config:
  theme: base
  themeVariables:
    background: '#ffffff'
---
sequenceDiagram
    participant Caller
    participant Ctrl as AuthController
    participant Svc as UserServiceImpl
    participant Mapper as UserMapper (MyBatis)
    participant DB as reme_user DB
    participant Encoder as PasswordEncoder (BCrypt)
    participant Jwt as JwtTokenProvider

    Caller->>Ctrl: POST /api/v1/auth/register<br/>{email, password, name}
    Ctrl->>Svc: register(RegisterRequest)
    Svc->>Mapper: findByEmail(email)
    Mapper->>DB: SELECT users WHERE email = ?
    alt email already registered
        Svc-->>Ctrl: throws BusinessException.conflict(...)
        Ctrl-->>Caller: 409 CONFLICT
    else email free
        Svc->>Encoder: encode(password)
        Svc->>Svc: userId = UUID.randomUUID(), role = LEARNER
        Svc->>Mapper: insert(User)
        Mapper->>DB: INSERT INTO users
        Note over Svc,DB: @Transactional
        Svc->>Jwt: generateToken(userId, {email, role})
        Jwt-->>Svc: signed JWT
        Svc-->>Ctrl: AuthResponse{token, user}
        Ctrl-->>Caller: 200 AuthResponse
    end
```

## 2. Login (read path)

```mermaid
---
config:
  theme: base
  themeVariables:
    background: '#ffffff'
---
sequenceDiagram
    participant Caller
    participant Ctrl as AuthController
    participant Svc as UserServiceImpl
    participant Mapper as UserMapper (MyBatis)
    participant DB as reme_user DB
    participant Encoder as PasswordEncoder (BCrypt)
    participant Jwt as JwtTokenProvider

    Caller->>Ctrl: POST /api/v1/auth/login<br/>{email, password}
    Ctrl->>Svc: login(LoginRequest)
    Svc->>Mapper: findByEmail(email)
    Mapper->>DB: SELECT users WHERE email = ?
    alt email not found OR Encoder.matches(password, hash) is false
        Svc-->>Ctrl: throws BusinessException.unauthorized("Invalid email or password")
        Ctrl-->>Caller: 401 UNAUTHORIZED
        Note over Svc,Ctrl: same exception/message for both cases -<br/>never reveal which one failed
    else credentials match
        Svc->>Jwt: generateToken(userId, {email, role})
        Jwt-->>Svc: signed JWT
        Svc-->>Ctrl: AuthResponse{token, user}
        Ctrl-->>Caller: 200 AuthResponse
    end
```

## 3. Profile read + update

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

    Caller->>Ctrl: GET /api/v1/users/{userId}
    Ctrl->>Svc: getByUserId(userId)
    Svc->>Mapper: findByUserId(userId)
    Mapper->>DB: SELECT users WHERE user_id = ?
    alt not found
        Svc-->>Ctrl: throws BusinessException.notFound(...)
        Ctrl-->>Caller: 404 NOT_FOUND
    else found
        Svc-->>Ctrl: UserResponse
        Ctrl-->>Caller: 200 UserResponse
    end

    Caller->>Ctrl: PATCH /api/v1/users/{userId}<br/>{name}
    Ctrl->>Svc: updateProfile(userId, UpdateProfileRequest)
    Svc->>Mapper: findByUserId(userId)
    alt not found
        Svc-->>Ctrl: throws BusinessException.notFound(...)
        Ctrl-->>Caller: 404 NOT_FOUND
    else found
        Svc->>Mapper: updateName(userId, name)
        Mapper->>DB: UPDATE users SET name = ?, updated_at = now()
        Svc->>Mapper: findByUserId(userId)
        Mapper-->>Svc: refreshed User
        Svc-->>Ctrl: UserResponse
        Ctrl-->>Caller: 200 UserResponse
    end
```

## 4. Photo upload (write path)

See [photo-upload.md](photo-upload.md) for the full sequence diagram - uploads to S3 via `common`'s
`S3StorageClient` (same client `recording-service` uses), then persists `photo_s3_key`/`photo_url`.

## Notes

- No Kafka producer/consumer in this service yet — everything here is a synchronous REST call
  against `reme_user`. Unlike `recording-service`/`english-service`, there is no event published
  when a user registers or updates their profile.
- `password`/`passwordHash` never appear in any response DTO (`UserResponse`, `AuthResponse`) — only
  `RegisterRequest`/`LoginRequest` carry the raw password, and only inbound.
- `JwtTokenProvider`/`JwtProperties` (`common/security/`) were already fully implemented before this
  service existed; `user-service` is their first real caller. `reme.jwt.secret`/
  `reme.jwt.expiration-minutes` (env `JWT_SECRET`) control signing.
