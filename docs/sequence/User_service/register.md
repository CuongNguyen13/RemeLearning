# POST /api/v1/auth/register

Registers a new learner account (hashing the password, persisting the row) and issues a signed
JWT so the caller is authenticated immediately. See `user-service`'s
`controller/AuthController.java` and `service/impl/UserServiceImpl.java`.

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
    Ctrl->>Ctrl: @Valid: email present + well-formed,<br/>password >= 8 chars, name not blank
    Ctrl->>Svc: register(RegisterRequest)
    Svc->>Mapper: findByEmail(email)
    Mapper->>DB: SELECT users WHERE email = ?

    alt email already registered
        Svc-->>Ctrl: throws BusinessException.conflict(...)
        Ctrl-->>Caller: 409 CONFLICT
    else email free
        Svc->>Encoder: encode(rawPassword)
        Encoder-->>Svc: passwordHash
        Svc->>Svc: userId = UUID.randomUUID().toString()<br/>role = UserRole.LEARNER
        Svc->>Mapper: insert(User{userId, email, passwordHash, name, role})
        Mapper->>DB: INSERT INTO users (...)
        Note over Svc,DB: @Transactional
        Svc->>Jwt: generateToken(userId, {email, role})
        Jwt-->>Svc: signed JWT (HMAC, expires reme.jwt.expiration-minutes)
        Svc-->>Ctrl: AuthResponse{token, user: UserResponse}
        Ctrl-->>Caller: 200 AuthResponse
    end
```

## External calls

| # | Call | From -> To | Notes |
|---|------|-----------|-------|
| 1 | Postgres SELECT | user-service -> `reme_user` DB | duplicate-email check via `idx_users_email` |
| 2 | Postgres INSERT | user-service -> `reme_user` DB | writes the `users` row, `role` defaults to `LEARNER` |

## Notes

- No downstream Kafka event is published on registration — `user-service` has no producers yet.
- Bean-validation (`@NotBlank`, `@Email`, `@Size(min = 8)`) rejects malformed input before the
  service layer runs, via Spring's default `MethodArgumentNotValidException` -> 400 mapping (see
  `common/web/GlobalExceptionHandler`).
- Password is only ever handled as `char`/`String` in memory long enough to hash it; only
  `passwordHash` is persisted, and no response DTO ever echoes password or passwordHash.
