# RemeLearning

AI-powered English learning analysis platform. Learners upload a recording of themselves speaking
English; the platform transcribes it, diarizes speakers, tracks recurring/forgotten vocabulary,
grammar, and pronunciation mistakes (Ebbinghaus-style forgetting-curve scoring), and turns that into
personalized recommendations and a progress dashboard.

Built as Java/Spring Boot microservices (`RemeLearning/`) plus a Python AI service
(`RemeLearning/services/ai-service/`).

## Architecture at a glance

```
client → bff-service (8080) ─┬─→ user-service (8081)          [auth, profile]
                              ├─→ recording-service (8082)     [upload → S3 → recording.uploaded]
                              ├─→ english-service (8085)       [vocabulary/grammar/pronunciation weak points]
                              ├─→ recommendation-service (8086)
                              └─→ dashboard-service (8087)

recording-service --recording.uploaded--> ai-service --transcript.ready--> english-service
ai-service --learning.gap.analyzed--> english-service (x3) + recommendation-service + dashboard-service
recommendation-service --recommendation.generated--> dashboard-service
```

`bff-service` is a reactive (WebFlux) gateway — the only HTTP entry point a web/mobile client needs;
it fans out to the other services and shapes the combined response for the UI. Everything past that
point is plain REST + Kafka between backend services. See `CLAUDE.md` for the full architecture
writeup and `docs/API.md` for every REST endpoint and Kafka topic that actually exists in the code
today.

| Service | Port | DB | Responsibility |
|---|---|---|---|
| [bff-service](RemeLearning/services/bff-service) | 8080 | — (reactive, no DB) | Gateway: proxy + composite/aggregation endpoints for the UI |
| [user-service](RemeLearning/services/user-service) | 8081 | reme_user | Register/login (issues JWTs), user profile |
| [recording-service](RemeLearning/services/recording-service) | 8082 | reme_recording | Upload → S3 → publish `recording.uploaded` |
| [english-service](RemeLearning/services/english-service) | 8085 | reme_english | Vocabulary/grammar/pronunciation weak-point tracking (modular monolith) |
| [recommendation-service](RemeLearning/services/recommendation-service) | 8086 | reme_recommendation | Cross-domain learning recommendations |
| [dashboard-service](RemeLearning/services/dashboard-service) | 8087 | reme_dashboard | Aggregate read model (progress + recent recommendations) |
| [ai-service](RemeLearning/services/ai-service) | 8000 | — | Python/FastAPI: STT, diarization, forgetting-pattern analysis |
| [common](RemeLearning/common) | — | — | Shared Java library (not a deployable service) |

## Quick start

```bash
cd RemeLearning
./mvnw clean install                                     # build all 7 Java modules
./mvnw -pl services/<name>-service -am spring-boot:run    # run one service
```

Local infra (Kafka, Redis, MinIO, ai-service) via Docker:

```bash
cd RemeLearning
docker compose up kafka redis minio ai-service
```

Postgres is not dockerized — run it however you normally run it locally, one DB per service
(`reme_user`, `reme_recording`, `reme_english`, `reme_recommendation`, `reme_dashboard`), matching
each service's `application.yml`.

Each Java service has its own `README.md` with service-specific run instructions; `ai-service`'s
`README.md` has detailed Python/virtualenv setup steps (it needs Python 3.11/3.12, not the newest
interpreter).

## Where to look

- **`CLAUDE.md`** — architecture deep-dive: module layout, the `common` library, the Kafka pipeline,
  per-service implementation notes and gotchas. Read this first if you're making a non-trivial change.
- **`docs/API.md`** — every REST endpoint and Kafka topic that exists in the code today, kept in sync
  with the code on every change.
- **`docs/sequence/<Service>/`** — mermaid sequence diagrams, one per endpoint/event, per service.
- **`docs/flow/<service>-data-flow.md`** — data-shape flowcharts (what transforms into what).
- Each service's own `openapi.yaml` — hand-maintained REST spec (separate from the springdoc-generated
  `/swagger-ui.html`).

## Status

All 7 Java services + `ai-service` have real, working APIs — no service is a bare skeleton anymore.
Known gaps: nothing yet enforces the JWTs `user-service` issues (no service validates a token on
incoming requests); no producer exists yet for the `learning.gap.analysis.requested` Kafka topic
(the step that would bundle a transcript with a learner's historical mistake stats before asking
`ai-service` to analyze it). See `docs/API.md` §14 for the full up-to-date list.
