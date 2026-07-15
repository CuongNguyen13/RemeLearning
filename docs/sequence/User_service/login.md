# POST /api/v1/auth/login

Authenticates with email + password and issues a fresh signed JWT. See `user-service`'s
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

    Caller->>Ctrl: POST /api/v1/auth/login<br/>{email, password}
    Ctrl->>Svc: login(LoginRequest)
    Svc->>Mapper: findByEmail(email)
    Mapper->>DB: SELECT users WHERE email = ?

    alt email not found
        Svc-->>Ctrl: throws BusinessException.unauthorized("Invalid email or password")
        Ctrl-->>Caller: 401 UNAUTHORIZED
    else email found
        Svc->>Encoder: matches(rawPassword, storedHash)
        alt password does not match
            Encoder-->>Svc: false
            Svc-->>Ctrl: throws BusinessException.unauthorized("Invalid email or password")
            Ctrl-->>Caller: 401 UNAUTHORIZED
        else password matches
            Encoder-->>Svc: true
            Svc->>Jwt: generateToken(userId, {email, role})
            Jwt-->>Svc: signed JWT
            Svc-->>Ctrl: AuthResponse{token, user: UserResponse}
            Ctrl-->>Caller: 200 AuthResponse
        end
    end
```

## External calls

| # | Call | From -> To | Notes |
|---|------|-----------|-------|
| 1 | Postgres SELECT | user-service -> `reme_user` DB | looks up by email |

## Notes

- **Deliberate security property:** "email not found" and "password does not match" throw the
  exact same `BusinessException.unauthorized("Invalid email or password")` — the client can never
  distinguish an unregistered email from a wrong password. Covered explicitly by
  `UserServiceImplTest.loginWithUnknownEmailThrowsUnauthorized` and
  `loginWithWrongPasswordThrowsSameUnauthorizedAsUnknownEmail`.
- Login issues a brand-new JWT on every successful call (no refresh-token / session-reuse concept
  exists yet).
- Nothing in the repo validates this (or any) JWT yet — see `overview.md`'s scope note.
