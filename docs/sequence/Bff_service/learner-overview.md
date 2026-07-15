# GET /api/v1/learners/{userId}/overview

`LearnerController.getOverview` delegates to `LearnerOverviewService.getOverview`, which fans out to
`dashboard-service`, `recording-service`, and `user-service` in parallel via `Mono.zip` and
assembles a single `LearnerOverviewResponse`. See `bff-service`'s
`service/LearnerOverviewService.java` / `client/DashboardServiceClient.java` /
`client/RecordingServiceClient.java` / `client/UserServiceClient.java`.

```mermaid
---
config:
  theme: base
  themeVariables:
    background: '#ffffff'
---
sequenceDiagram
    participant Caller
    participant Ctrl as LearnerController
    participant Svc as LearnerOverviewService
    participant DashClient as DashboardServiceClient
    participant RecClient as RecordingServiceClient
    participant UserClient as UserServiceClient
    participant Dashboard as dashboard-service :8087
    participant Recording as recording-service :8082
    participant User as user-service :8081

    Caller->>Ctrl: GET /api/v1/learners/{userId}/overview
    Ctrl->>Svc: getOverview(userId)

    par dashboard slice
        Svc->>DashClient: getSummary(userId)
        DashClient->>Dashboard: GET /api/v1/dashboard/{userId}
        alt dashboard-service reachable
            Dashboard-->>DashClient: ApiResponse<DashboardSummaryResponse>
            DashClient-->>Svc: Mono<DashboardSummaryDto>
        else dashboard-service down / error response
            DashClient-->>Svc: Mono.error
            Svc->>Svc: onErrorResume -> empty DashboardSummaryDto<br/>{userId, categoryProgress: [], recentRecommendations: []}
        end
    and recordings slice
        Svc->>RecClient: getRecordingsByUser(userId)
        RecClient->>Recording: GET /api/v1/recordings/user/{userId}
        alt recording-service reachable
            Recording-->>RecClient: ApiResponse<List<RecordingResponse>>
            RecClient-->>Svc: Mono<List<RecordingDto>>
        else recording-service down / error response
            RecClient-->>Svc: Mono.error
            Svc->>Svc: onErrorResume -> empty List
        end
    and user slice
        Svc->>UserClient: getByUserId(userId)
        UserClient->>User: GET /api/v1/users/{userId}
        alt user-service reachable
            User-->>UserClient: ApiResponse<UserResponse>
            UserClient-->>Svc: Mono<UserDto>
        else user-service down / error response
            UserClient-->>Svc: Mono.error
            Svc->>Svc: onErrorResume -> Optional.empty() (user: null)
        end
    end

    Svc->>Svc: Mono.zip(dashboardMono, recordingsMono, userMono)<br/>-> LearnerOverviewResponse{userId, categoryProgress,<br/>recentRecommendations, recentRecordings, user}
    Svc-->>Ctrl: Mono<LearnerOverviewResponse>
    Ctrl-->>Caller: 200 ApiResponse<LearnerOverviewResponse>
```

## External calls

| # | Call | From -> To | Notes |
|---|------|-----------|-------|
| 1 | `GET /api/v1/dashboard/{userId}` | bff-service -> dashboard-service | defaults to an empty summary on failure, doesn't fail the request |
| 2 | `GET /api/v1/recordings/user/{userId}` | bff-service -> recording-service | defaults to an empty list on failure, doesn't fail the request |
| 3 | `GET /api/v1/users/{userId}` | bff-service -> user-service | defaults to a `null` `user` field on failure, doesn't fail the request |

## Notes

- All three calls run concurrently (`Mono.zip`), not sequentially — total latency is bounded by the
  slowest of the three, not their sum.
- Partial-failure resilience: any downstream being unreachable still returns `200` with that slice
  defaulted to empty (`null` for the `user` slice, since a single profile object has no natural
  "empty" value the way a list/summary does), logged at `warn` level with the userId (never at
  `error`, since this is an expected/handled degradation, not a bug).
- The user slice is wrapped in `Optional<UserDto>` internally so `Mono.zip` still has a value to
  zip even when user-service is down (`Mono.zip` completes empty, not with a null, if any source
  Mono is empty) - unwrapped back to a plain (possibly `null`) `UserDto` before building the
  response.
