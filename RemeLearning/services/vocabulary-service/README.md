# vocabulary-service

Persists transcripts and forgotten/recurring vocabulary items produced by `ai-service`,
classifies each vocabulary item by word type / phrase type, and exposes read APIs.

## Kafka events consumed

| Topic | Published by | Payload | Handler |
|---|---|---|---|
| `transcript.ready` | ai-service (`app/kafka/handlers/recording_uploaded.py`) | `recording_id`, `user_id`, `full_text`, `segments[]` | `TranscriptReadyConsumer` → stores transcript + segments |
| `learning.gap.analyzed` | ai-service (`app/kafka/handlers/analysis_requested.py`) | `recording_id`, `user_id`, `weak_points[]` (`category` may be grammar/vocabulary/pronunciation) | `LearningGapAnalyzedConsumer` → keeps only `category == "vocabulary"`, classifies each via `VocabularyClassifier`, upserts |

Both events are plain JSON (pydantic `model_dump()`, snake_case keys, no envelope) — decoded
with a dedicated snake_case `ObjectMapper` (`event/EventCodec.java`), separate from the
app's default camelCase Jackson config used by REST controllers.

## Vocabulary classification

`VocabularyClassifier` (interface) → `RuleBasedVocabularyClassifier` (current impl): a rule-based
heuristic with no LLM cost, following the same swappable-behind-an-interface pattern as
ai-service's `MistakeAnalyzer`/`RuleBasedAnalyzer`. Classifies a label (e.g. `"word: reluctant"`,
`"phrase: give up"`) into a `VocabularyType`:

- Single word → part-of-speech by suffix heuristic (NOUN/VERB/ADJECTIVE/ADVERB, else OTHER)
- Multi-word → PHRASAL_VERB (verb + common particle/preposition), COLLOCATION (2-3 words),
  or IDIOM (4+ words)

A future LLM-backed classifier can replace `RuleBasedVocabularyClassifier` without touching
callers.

## REST API

See [openapi.yaml](openapi.yaml) for the full spec, or run the service and open
`/swagger-ui.html` (springdoc, generated live from the controllers).

| Method | Path | Description |
|---|---|---|
| GET | `/api/v1/transcripts/{recordingId}` | Stored transcript + segments |
| GET | `/api/v1/vocabulary/weak-points/{userId}?type=` | Forgotten vocabulary items, optional type filter, sorted by forgetting score desc |
| GET | `/api/v1/vocabulary/weak-points/{userId}/grouped` | Same, grouped by `VocabularyType` |

## Local dev

```bash
cd RemeLearning
./mvnw -pl services/vocabulary-service -am spring-boot:run
./mvnw -pl services/vocabulary-service -am test
```

Requires Postgres (`reme_vocabulary`, Flyway-managed) and Kafka reachable via the properties
in `application.yml` (`KAFKA_BOOTSTRAP_SERVERS`, `DB_USERNAME`/`DB_PASSWORD`, ...).
