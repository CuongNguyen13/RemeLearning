# user-service

Authentication and user profile management: register/login (issues real JWTs) and basic profile
CRUD. Class/course/enrollment management is explicitly out of scope for now — this service is auth +
profile only.

- Port: **8081**
- Database: `reme_user`

## Endpoints

Full spec: [`openapi.yaml`](openapi.yaml) / `/swagger-ui.html` when running. Details + JSON shapes:
[`docs/API.md` §2](../../../docs/API.md#2-user-service-java--rest).

| Method | Path | Notes |
|---|---|---|
| POST | `/api/v1/auth/register` | hashes password (BCrypt), mints a `userId` (UUID), issues a JWT. `409` on duplicate email. |
| POST | `/api/v1/auth/login` | issues a JWT. `401` for both "unknown email" and "wrong password" — same message either way, so a caller can't tell which one failed. |
| GET | `/api/v1/users/{userId}` | `404` if not found. |
| PATCH | `/api/v1/users/{userId}` | updates `name`. |

## Run locally

```bash
cd RemeLearning
./mvnw -pl services/user-service -am spring-boot:run
./mvnw -pl services/user-service -am test
```

## Notes

- Uses `common.security.JwtTokenProvider`/`JwtProperties` (`reme.jwt.*`) — this is the first and only
  real caller of that shared component today.
- **Tokens are issued but not enforced.** No service in the repo — including this one, on its own
  `PATCH` endpoint — validates a JWT on an incoming request yet. There is no `SecurityConfig` or
  Spring Security dependency anywhere; only `spring-security-crypto` is used here, for
  `BCryptPasswordEncoder` alone.
- `role` (`LEARNER`/`TEACHER`/`ADMIN`, default `LEARNER`) is stored but has no authorization logic
  built on top of it — data modeling only, for when that's actually needed.
