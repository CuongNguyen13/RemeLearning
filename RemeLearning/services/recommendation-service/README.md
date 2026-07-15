# recommendation-service

Consumes `learning.gap.analyzed` (the same event `english-service` consumes) **without** filtering by
category — every weak point from every domain (vocabulary/grammar/pronunciation) becomes a
"recommendation" row — and publishes `recommendation.generated` for `dashboard-service` to pick up.

- Port: **8086**
- Database: `reme_recommendation`

## Endpoints

Full spec: [`openapi.yaml`](openapi.yaml) / `/swagger-ui.html` when running. Details + JSON shapes:
[`docs/API.md` §4](../../../docs/API.md#4-recommendation-service-java--rest).

| Method | Path | Notes |
|---|---|---|
| GET | `/api/v1/recommendations/{userId}` | optional `?category=` filter, sorted by `forgettingScore` desc |
| GET | `/api/v1/recommendations/{userId}/grouped` | grouped by category |

## Kafka

- **Consumes** `learning.gap.analyzed` (own `groupId`: `recommendation-service`, separate from
  `english-service`'s three groups on the same topic) — upserts every weak point into
  `recommendations`, keyed `(user_id, item_id)`.
- **Produces** `recommendation.generated` via `common.queue.EventPublisher`/`BaseEvent` — the first
  real usage of that shared infra in the repo (every other producer in the repo bypasses it, since
  their events cross the Java↔Python boundary and can't carry the envelope).

See [`docs/API.md` §10](../../../docs/API.md#10-kafka--recommendation-service-consumerproducer).

## Run locally

```bash
cd RemeLearning
./mvnw -pl services/recommendation-service -am spring-boot:run
./mvnw -pl services/recommendation-service -am test
```

Needs Kafka reachable to actually receive `learning.gap.analyzed` events; the REST endpoints work
against whatever's already in `reme_recommendation` even without Kafka running.
