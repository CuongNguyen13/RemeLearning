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

Also present (own `dictation` package — listen-and-type dictation over a fixed real-audio library +
an AI-practice section): facet/session browsing, folder → file browsing (rev 2 —
`/api/v1/dictation/folders`, `.../folders/{folderId}/lessons`, `.../clips/{clipId}` for sentence-mode
detail — this last one also accepts an optional `?translationLang=`, see below), grading
(`POST /api/v1/dictation/attempts`, whose AI feedback is a root-cause-classified mistake analysis —
Vocabulary/Grammar/Phonology, via `DictationAnalyzer`/`dictation.analyzer.mode` below — not a flat
suggestions list), history (including full per-attempt detail via
`GET .../history/{userId}/{attemptId}`, and a `practiceType` field so the UI can badge each row
LIBRARY vs AI_PRACTICE), and AI-practice generation/audio — either aggregate
(`POST .../ai-practice/{userId}/generate`, from the learner's still-unsynthesized practice items, or
their most-missed words across recent attempts if none are pending) or scoped to one past attempt's
own mistakes (`POST .../history/{userId}/{attemptId}/ai-practice`, the "Luyện tập với AI" history
action — as of this update it calls the **same** generator as the aggregate endpoint, no longer a
separate single-sentence-per-item path, just without a `level`/`examType` selector). Both generation
endpoints accept an optional JSON body / query params — `GenerateAiPracticeRequest`
(`level?`, `examType?`, `translationLang?`) on the aggregate endpoint, `translationLang?` alone on the
from-attempt one — and both hand off to `LlmDictationDialogueGenerator` (always active, not gated by
`dictation.analyzer.mode`), which writes **one** monologue-or-dialogue passage covering the target
words, tags it with a short **topic label** it assigns itself, and (when `translationLang` isn't
`"en"`) a per-line translation; a random Supertonic voice is assigned per speaker, each line is
synthesized from the *exact* text that gets persisted/graded (a prior bug had multi-speaker audio
speak only the bare line while the graded/displayed text carried a `"Speaker: "` prefix, so the audio
never said the name it was graded against — now fixed) and the clips merged (`WavAudioMerger`) into
one audio file, replacing whatever was previously pending. `level`/`examType` may be a concrete value,
the literal `"RANDOM"` (server-resolved — level from a fixed CEFR pool `A1,A2,B1,B2,C1`; examType from
the library's own distinct exam types, falling back to `TOEIC,IELTS,TOEFL,General`), or omitted (no
preference, matching the original default). `GET .../ai-practice/items/{practiceItemId}/detail`
mirrors `.../clips/{clipId}` for AI-practice items: splits the item's `sentence_text`
(and `translation_text`, if any) into sentences (one per dialogue line, or by sentence-ending
punctuation for a monologue) so the same sentence-by-sentence runner drives both sections —
timestamps stay `null` since the passage's audio is one already-merged file; `level`/`examType`/`topic`
ride along on the response too. The
`.../clips/{clipId}` endpoint lazily AI-aligns any sentence still missing `startMs`/`endMs` before
responding, via `SentenceAlignmentClient` (`common.ai.align`) calling ai-service's
`POST /api/v1/dictation/align-sentences` (Whisper word-timestamps) — configured via
`reme.alignment.ai-service.base-url`/`read-timeout-seconds` in `application.yml`; it also lazily
translates any sentence still missing a `translation` (same shape, one batched LLM call for the whole
clip via `sentenceTranslator`) whenever `translationLang` is requested and isn't `"en"` (the content's
own language) — for both the library and AI-practice, translation is skipped entirely otherwise. Rev
3: the sentence-mode FE forces a correct retype before advancing (no auto-advance, no skipping a wrong
answer), so `POST /attempts` now also accepts an optional `sentenceMistakes[]` — every wrong check the
learner made along the way — scored the same way as the main transcript and folded into the same
`dictation_misses`/`learning.gap.analyzed` pipeline. Full list + JSON shapes in
[`docs/API.md`](../../../docs/API.md) and
[`docs/sequence/English_service/dictation-practice.md`](../../../docs/sequence/English_service/dictation-practice.md).

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

For the LLM-backed classifier/analyzer paths (`*.classifier.mode=llm`, `dictation.analyzer.mode=llm`),
set `LLM_PROVIDER` (`gemini` default, or `ollama`/`zen`) plus that provider's own env vars —
`GEMINI_API_KEY` (gemini), `OLLAMA_MODEL` (ollama, calls `http://localhost:11434` — no key needed),
or `ZEN_API_KEY`/`ZEN_MODEL` (zen, opencode.ai's OpenAI-compatible endpoint, default model
`big-pickle`). Never commit a real key to `application.yml`.

## Notes

- `vocabulary` was built first and is the reference layout for `grammar`/`pronunciation` (and for the
  other single-domain services in this repo) — see `CLAUDE.md` for the full package-by-package
  breakdown.
- `Boot4CompatConfig` (in `english/config/`) works around a local dependency gap between Boot 4 and
  `mybatis-spring-boot-starter:3.0.4` — see the gotcha in `CLAUDE.md` before assuming a new bug if you
  hit `UnsatisfiedDependencyException` for `SqlSessionFactory`/`KafkaTemplate`/`ObjectMapper`/
  `RestClient.Builder`.
