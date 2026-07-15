# dashboard-service

A pure read model: aggregates a learner's progress across all three English-skill domains plus their
most recent recommendations, into one composite view. No producers, no REST calls to other services —
everything it serves is built from two Kafka topics it consumes itself.

- Port: **8087**
- Database: `reme_dashboard`

## Endpoints

Full spec: [`openapi.yaml`](openapi.yaml) / `/swagger-ui.html` when running. Details + JSON shapes:
[`docs/API.md` §5](../../../docs/API.md#5-dashboard-service-java--rest).

| Method | Path | Notes |
|---|---|---|
| GET | `/api/v1/dashboard/{userId}` | per-category progress (count + avg forgetting score) + up to 10 most recent recommendations |

Per-category progress is computed **at read time** via a `GROUP BY category` SQL query against
`weak_points_snapshot` — not maintained as a running counter, which avoids incremental-average bugs.

## Kafka

Two consumers, both with `groupId: dashboard-service` (own group, separate from any other consumer
of the same topics):
- `learning.gap.analyzed` — every weak point (no category filter) upserted into `weak_points_snapshot`.
- `recommendation.generated` — every recommendation upserted into `recent_recommendations`.

See [`docs/API.md` §11](../../../docs/API.md#11-kafka--dashboard-service-consumer).

## Run locally

```bash
cd RemeLearning
./mvnw -pl services/dashboard-service -am spring-boot:run
./mvnw -pl services/dashboard-service -am test
```

Needs Kafka reachable to actually populate `reme_dashboard`; the REST endpoint works against whatever
data is already there even without Kafka running (returns empty slices for a learner with no data yet).
