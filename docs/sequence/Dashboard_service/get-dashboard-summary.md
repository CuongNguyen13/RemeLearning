# REST endpoint: GET /api/v1/dashboard/{userId}

`DashboardController.getSummary` (package `dashboard.controller`) is the single read endpoint the
service exposes — it aggregates both Kafka-fed tables (`weak_points_snapshot`,
`recent_recommendations`) into one response. No REST calls to other services are made; everything
comes from data dashboard-service has already ingested off Kafka.

```mermaid
---
config:
  theme: base
  themeVariables:
    background: '#ffffff'
---
sequenceDiagram
    participant Caller
    participant Ctrl as DashboardController
    participant Svc as DashboardServiceImpl
    participant WMapper as WeakPointSnapshotMapper (MyBatis)
    participant RMapper as RecentRecommendationMapper (MyBatis)
    participant DB as reme_dashboard DB

    Caller->>Ctrl: GET /api/v1/dashboard/{userId}
    Ctrl->>Svc: getSummary(userId)
    activate Svc
    Svc->>WMapper: selectProgressSummary(userId)
    WMapper->>DB: SELECT category, COUNT(*) AS weak_point_count,<br/>AVG(forgetting_score) AS avg_forgetting_score, MAX(updated_at) AS last_updated<br/>FROM weak_points_snapshot WHERE user_id = ? GROUP BY category
    WMapper-->>Svc: List[CategoryProgress]

    Svc->>RMapper: findRecentByUserId(userId, 10)
    RMapper->>DB: SELECT * FROM recent_recommendations<br/>WHERE user_id = ? ORDER BY received_at DESC LIMIT 10
    RMapper-->>Svc: List[RecommendationSnapshot]

    Svc-->>Ctrl: DashboardSummaryResponse{userId, categoryProgress[], recentRecommendations[]}
    deactivate Svc
    Ctrl-->>Caller: 200 ApiResponse[DashboardSummaryResponse]
```

## External calls

| # | Call | From -> To | Notes |
|---|------|-----------|-------|
| 1 | Postgres SELECT (aggregate) | dashboard-service -> `reme_dashboard` DB | `GROUP BY category` over `weak_points_snapshot`, computed at read time — not a maintained running counter |
| 2 | Postgres SELECT (recent) | dashboard-service -> `reme_dashboard` DB | top 10 rows from `recent_recommendations`, ordered by `received_at DESC` |

## Notes

- No pagination/filtering parameters today — always returns all categories present for the user and
  the 10 most recent recommendations (`RECENT_RECOMMENDATIONS_LIMIT` in `DashboardServiceImpl`).
- Deliberately event-driven only: this endpoint never calls `english-service` or
  `recommendation-service` directly, matching the rest of the architecture (`bff-service` has no
  gateway routes yet — see root `CLAUDE.md`).
- If a user has no rows in either table, both lists in the response are simply empty (no 404) — the
  endpoint doesn't distinguish "unknown user" from "no activity yet".
