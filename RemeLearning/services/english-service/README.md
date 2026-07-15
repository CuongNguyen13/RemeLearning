# english-service

Modular monolith covering the three English-skill analysis domains — vocabulary, grammar,
pronunciation — each its own package (`com.remelearning.english.<domain>`). Merges what used to be
three separate services (`vocabulary-service`, `grammar-service`, `pronunciation-service`) since all
three just analyze different `category` values of the same `learning.gap.analyzed` event.

- Port: **8085** (kept from the original `vocabulary-service`)
- Database: `reme_english`

## Endpoints

Full spec: [`openapi.yaml`](openapi.yaml) / `/swagger-ui.html` when running. Details + JSON shapes:
[`docs/API.md` §1](../../../docs/API.md#1-english-service-java--rest).

| Method | Path | Notes |
|---|---|---|
| GET | `/api/v1/transcripts/{recordingId}` | shared across all 3 domains, `404` if not found |
| GET | `/api/v1/vocabulary/weak-points/{userId}[/grouped]` | optional `?type=` filter (ungrouped only) |
| GET | `/api/v1/grammar/weak-points/{userId}[/grouped]` | optional `?type=` filter (ungrouped only) |
| GET | `/api/v1/pronunciation/weak-points/{userId}[/grouped]` | optional `?type=` filter (ungrouped only) |

## Kafka

- `TranscriptReadyConsumer` (`vocabulary` domain only) — consumes `transcript.ready`, persists
  `transcripts`/`transcript_segments` (shared across all 3 domains — `grammar`/`pronunciation` read it
  back via the REST endpoint above instead of re-ingesting).
- `LearningGapAnalyzedConsumer` — **one per domain**, each with its own `groupId`
  (`english-service`, `english-service-grammar`, `english-service-pronunciation`) consuming the same
  `learning.gap.analyzed` topic, each filtering to its own `category` and classifying the label via a
  rule-based or LLM classifier (`vocabulary.classifier.mode`/`grammar.classifier.mode`/
  `pronunciation.classifier.mode` in `application.yml`, default `rule-based`).

See [`docs/API.md` §8](../../../docs/API.md#8-kafka--english-service-consumer).

## Run locally

```bash
cd RemeLearning
./mvnw -pl services/english-service -am spring-boot:run
./mvnw -pl services/english-service -am test
```

For the LLM-backed classifier path (`*.classifier.mode=llm`), set `GEMINI_API_KEY` — never commit a
real key to `application.yml`.

## Notes

- `vocabulary` was built first and is the reference layout for `grammar`/`pronunciation` (and for the
  other single-domain services in this repo) — see `CLAUDE.md` for the full package-by-package
  breakdown.
- `Boot4CompatConfig` (in `english/config/`) works around a local dependency gap between Boot 4 and
  `mybatis-spring-boot-starter:3.0.4` — see the gotcha in `CLAUDE.md` before assuming a new bug if you
  hit `UnsatisfiedDependencyException` for `SqlSessionFactory`/`KafkaTemplate`/`ObjectMapper`/
  `RestClient.Builder`.
