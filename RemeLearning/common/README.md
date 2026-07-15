# common

Shared Java library used by all RemeLearning microservices — not a deployable service itself, every
other module in this reactor depends on it (see root `d:\Personal Project\RemeLearning_Project\CLAUDE.md`
for the full architecture writeup; this file is a short index of what lives here).

## Packages

| Package | What it's for |
|---|---|
| `ai/` | Vendor-neutral `LlmClient`/`SttClient` contracts. `ai/gemini/` has the one real implementation (`GeminiLlmClient`, calls Gemini's `generateContent` REST API), gated behind `reme.llm.provider=gemini` (the default). |
| `event/` | The shared Kafka event codec + DTOs: `EventCodec` (snake_case `ObjectMapper`, for the plain-JSON no-envelope events that cross the Java↔`ai-service` boundary), `LearningGapAnalyzedEvent`/`WeakPointPayload` (ai-service → Java), `RecommendationGeneratedEvent`/`RecommendationPayload` (Java → Java). Every Kafka consumer/producer of these events imports from here instead of redeclaring its own copy. |
| `queue/` | `EventPublisher` interface + `BaseEvent` envelope (`eventId`/`eventType`/`occurredAt`), implemented by `queue/kafka/KafkaEventPublisher`. Publish through the interface, not `KafkaTemplate` directly. |
| `constants/KafkaTopics.java` | Single source of truth for topic names, mirrored in `ai-service/app/kafka/topics.py`. |
| `cache/` | `CacheClient` interface, two implementations selected via `reme.cache.provider`: `cache/redis/RedisCacheClient` and `cache/inmemory/InMemoryCacheClient` (single-JVM fallback). See `cache/README.md`. |
| `storage/` | `S3StorageClient` + `S3ClientConfig` (`reme.s3.*`, MinIO-compatible via the `endpoint` override). |
| `security/` | `JwtTokenProvider`/`JwtProperties` — issues/validates HMAC JWTs. `user-service` is the only caller today; no service enforces/validates a token on incoming requests yet. |
| `response/`, `exception/`, `web/` | `ApiResponse<T>` envelope, `BusinessException`/`ErrorCode`, `GlobalExceptionHandler` — the standard success/error shape every controller in every service returns. |

## Using this module

- Depend on interfaces (`CacheClient`, `LlmClient`, `EventPublisher`), never a concrete implementation.
- `GlobalExceptionHandler` and other `common` beans live in `com.remelearning.common.*`, a sibling of
  each service's own base package — add `@ComponentScan(basePackages = "com.remelearning")` to your
  service's `*Application` class or they won't be picked up.
- `common` has no MyBatis/Flyway/Postgres dependency of its own (it's not DB-backed) — each service
  brings its own `Boot4CompatConfig` copy for the `SqlSessionFactory`/`KafkaTemplate`/`ObjectMapper`/
  `RestClient.Builder` beans Boot 4 doesn't autoconfigure in this environment (see `CLAUDE.md`).

## Build / test

```bash
cd RemeLearning
./mvnw -pl common -am install   # other modules resolve it from the local Maven repo afterward
```

No unit tests exist in this module yet.
