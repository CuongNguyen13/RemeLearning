# GET /api/v1/users/{userId} and PATCH /api/v1/users/{userId}

Profile read and update. See `user-service`'s `controller/UserController.java` and
`service/impl/UserServiceImpl.java`.

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
```

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

    Caller->>Ctrl: PATCH /api/v1/users/{userId}<br/>{name}
    Ctrl->>Svc: updateProfile(userId, UpdateProfileRequest)
    Svc->>Mapper: findByUserId(userId)
    Mapper->>DB: SELECT users WHERE user_id = ?
    alt not found
        Svc-->>Ctrl: throws BusinessException.notFound(...)
        Ctrl-->>Caller: 404 NOT_FOUND
    else found
        Svc->>Mapper: updateName(userId, name)
        Mapper->>DB: UPDATE users SET name = ?, updated_at = now() WHERE user_id = ?
        Svc->>Mapper: findByUserId(userId)
        Mapper->>DB: SELECT users WHERE user_id = ?
        Mapper-->>Svc: refreshed User
        Svc-->>Ctrl: UserResponse
        Ctrl-->>Caller: 200 UserResponse
    end
```

## External calls

| # | Call | From -> To | Notes |
|---|------|-----------|-------|
| 1 | Postgres SELECT | user-service -> `reme_user` DB | `GET` and the pre-check + post-check on `PATCH` |
| 2 | Postgres UPDATE | user-service -> `reme_user` DB | `PATCH` only, updates `name` + `updated_at` |

## Notes

- `UpdateProfileRequest` only carries `name` today — no email/role/password change endpoint exists
  in this pass (out of scope per the confirmed spec).
- `updateProfile` does an existence check, then the update, then re-reads the row so the returned
  `UserResponse` reflects the new `updated_at`/`name` rather than stale in-memory state.
