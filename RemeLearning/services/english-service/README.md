# english-service

Modular monolith covering the three English-skill analysis domains — vocabulary, grammar,
pronunciation — each its own package (`com.remelearning.english.<domain>`). Merges what used to be
three separate services (`vocabulary-service`, `grammar-service`, `pronunciation-service`) since all
three just analyze different `category` values of the same `learning.gap.analyzed` event.

On top of the three analysis domains, four "Học & Luyện tập với AI" **learn** skills now generate and
grade practice content on demand: `vocabulary.learn`, `grammar.learn` (both under their existing
domain package), plus two entirely new domain packages, `listening` and `speaking`. See the
[Learn skills](#learn-skills-học--luyện-tập-với-ai) section below.

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

## Learn skills ("Học & Luyện tập với AI")

Four skills — vocabulary, grammar, listening, speaking — generate one AI practice item on demand and
grade the learner's attempt against it. `vocabulary`/`grammar` live inside their existing domain
package as a `learn` sub-package; `listening`/`speaking` are brand-new top-level domain packages
(`com.remelearning.english.listening`, `com.remelearning.english.speaking`), following the same
controller/service/mapper/domain/dto/generator/scoring layout as the analysis domains. All four reuse
`english.practice.service.PracticeService.redo(...)` to grade and feed weak points back into the
existing pipeline — there is no separate "weak-point feeder" per skill.

| Skill | Package | Migration | Controller | Base path |
|---|---|---|---|---|
| Vocabulary | `english.vocabulary.learn` | `V12__vocab_practice.sql` (`vocab_practice_items`, `vocab_practice_attempts`) | `VocabLearnController` | `/api/v1/learn/vocabulary` |
| Grammar | `english.grammar.learn` | `V13__grammar_practice.sql` (`grammar_practice_items`, `grammar_practice_attempts`) | `GrammarLearnController` | `/api/v1/learn/grammar` |
| Listening | `english.listening` (new domain) | `V14__listening_practice.sql` (`listening_practice_items`, `listening_attempts`) | `ListeningLearnController` | `/api/v1/learn/listening` |
| Speaking | `english.speaking` (new domain) | `V15__speaking_practice.sql` (`speaking_practice_items`, `speaking_attempts`) | `SpeakingLearnController` | `/api/v1/learn/speaking` |

Shared backbone, in the new `english.learn.common` package (used by all four generators/scorers):
- `AiContentClient` — thin wrapper around `common`'s `LlmClient`: strips Gemini's occasional
  ` ```json ` code fences and parses the response as JSON (`completeJson`), or returns raw text
  (`complete`), throwing `AiContentException` on any call/parse failure. Collapses plumbing that used
  to be duplicated per generator (mirrors dictation's older `LlmDictationAnalyzer`/
  `LlmDictationDialogueGenerator`, which still have their own copies).
- `DialogueAudioSynthesizer` (+ `DialogueLine`/`SynthesizedDialogue`) — generalizes multi-speaker
  Supertonic TTS synthesis (one random voice per distinct speaker, lines merged via dictation's
  existing `WavAudioMerger`) for any learn skill that needs voiced audio; currently used by
  `listening`. Dictation deliberately keeps its own already-tested, separate TTS/dialogue code path
  rather than being migrated onto this shared version.

**Client-side grading (contract change):** the practice-item question DTOs now ship the correct answer
so the FE can grade each question locally for instant feedback. `VocabQuestionDto`/`GrammarQuestionDto`
carry `answer` + `translation`; `ListeningQuestionDto` carries `answer` + `explanation`, with `answer`
null for `OPEN` questions (those are graded server-side by the LLM `OpenAnswerGrader` and must not be
revealed to the client). This applies to the generate, item-detail, and item-list responses of all three
skills. The authoritative score is still produced only by the submit-attempt endpoint (which persists
history + weak points and fires the Kafka flow) — client-side grading is display-only.

Per-skill notes:
- **Vocabulary** (`POST /api/v1/learn/vocabulary/{userId}/generate`) — AI-generated question set per
  target word, one of three shapes (`VocabQuestionType`): `CLOZE` (fill the blank in a context
  sentence), `MCQ` (pick the correct word among options), `MATCHING` (pick the correct meaning among
  options). Falls back to the learner's own top vocabulary weak points when `focusItems` is omitted,
  then to a generic level-appropriate set if the learner has none yet. Grading
  (`POST /attempts`) reuses `vocabulary_weak_points`/its existing Kafka consumer via
  `PracticeService.redo` — no new weak-point table.
- **Grammar** (`POST /api/v1/learn/grammar/{userId}/generate`) — same shape, one of four question
  types (`GrammarQuestionType`): `ERROR_CORRECTION` (rewrite a sentence with a grammar mistake),
  `FILL_TENSE` (put the bracketed verb in the correct tense/form), `TRANSFORM` (rewrite per an
  instruction, same meaning), `MCQ` (pick the correct structure). Reuses `grammar_weak_points` via
  `PracticeService.redo` — no new weak-point table. `POST /history/{userId}/{attemptId}/ai-practice`
  ("Luyện tập với AI" from a history row) diffs one past attempt's stored questions against its
  submitted answers via the pure `GrammarMistakeAnalyzer.extractMissedRules` (re-scored with the same
  `GrammarAttemptScorer` the original attempt used), generates a new set targeting only the missed
  `targetRule`s via the same `GrammarPracticeGenerator`, persists it into the same
  `grammar_practice_items` bank, and returns the learner's refreshed practice-set list; `404` if the
  attempt doesn't exist or belongs to someone else. The persist-and-refresh step
  (`generatePracticeForRules`) is also reused by Grammar Library's own "generate from session"
  endpoint below — one shared AI-practice bank per domain, regardless of which flow the mistake
  came from.
- **Listening** — a new domain, no pre-existing table to fall back to. Generation
  (`POST /api/v1/learn/listening/{userId}/generate`) produces a Gemini transcript + questions, then
  synthesizes multi-speaker audio via `DialogueAudioSynthesizer`; the transcript/translation and audio
  (`GET /items/{itemId}/audio`) stay hidden from the practice-item response until the attempt is
  submitted. Three question shapes (`ListeningQuestionType`): `MCQ` (main idea/detail/attitude, 4
  options), `KEYWORD` (fill the missed word/phrase, scored by WER like dictation), `OPEN` (free-text,
  LLM-graded 0..1 against a model answer). Because no english-service Kafka consumer persists a
  per-domain "listening" weak-point row today (category `listening` still just flows through the
  existing `learning.gap.analyzed` pipeline with no dedicated table), regeneration target-keyword
  selection reads this skill's own attempt history instead — the same pattern dictation uses for its
  own miss table.
- **Speaking** — also a new domain. Generation produces a target sentence/passage plus a Supertonic
  sample (model) recording (`GET /items/{itemId}/sample-audio`). Grading
  (`POST /api/v1/learn/speaking/{userId}/attempts`) is multipart (learner's recorded audio +
  `practiceItemId`), scored via `common`'s `PronunciationScoringClient`
  (`common.ai.pronunciation`) calling ai-service's wav2vec2 GOP-scoring endpoint, and feeds the
  existing `pronunciation_weak_points` table/Kafka consumer via `PracticeService.redo` — no new
  weak-point table.

## Vocabulary Library

`com.remelearning.english.vocabulary.library` extends the vocabulary **learn** skill above with a
persistent, topic-organized word bank plus a Duolingo/Anki-style "Section" practice mode (a queue of
words drilled with intra-session repetition — a word leaves the queue once answered correctly twice
in a row; a wrong answer resets its streak and requeues it sooner than a correct-but-not-yet-mastered
answer does). Six new endpoints under `VocabularyLibraryController`
(`/api/v1/learn/vocabulary/library`): browsing the fixed topic list, starting a Section
(`POST /{userId}/topics/{topicId}/sections`, generating new library words via Gemini + Supertonic TTS
whenever a topic is under-stocked), submitting an answer (`POST /sections/{sectionId}/answers`), and
finishing a Section early (`POST /sections/{sectionId}/finish`). Migration:
`V16__vocabulary_library.sql` (`vocabulary_topics`, `vocabulary_library_words`,
`vocabulary_section_attempts`, `vocabulary_section_answers`). Library words share one mastery record
per word with the ad-hoc "Học & Luyện tập" flow (`vocabulary_weak_points`, keyed by
`item_id = "vocab:" + word`) — no second mastery table — and grading feeds
`PracticeService.redo(...)` exactly like the other learn skills, except attempts are **not** deduped
by word, so a word drilled multiple times in one Section lets `WeakPointScoringEngine`'s
same-batch recurrence boost see the in-session repetition. See
[`docs/sequence/English_service/vocabulary-library.md`](../../../docs/sequence/English_service/vocabulary-library.md)
and [`docs/flow/english-service-data-flow.md`](../../../docs/flow/english-service-data-flow.md).

## Grammar Library

`com.remelearning.english.grammar.library` is a fixed, hand-seeded catalog of **60 grammar topics**
(beginner → intermediate → advanced, `V17__grammar_library.sql`), each with an AI-generated theory
page (bilingual explanation + a markdown-table/mermaid illustration + examples) plus an 8-10 question
pool — generated by Gemini exactly once per topic (on first `GET .../topics/{topicId}`) and reused
forever after, the same "generate once, persist, reuse" pattern as Vocabulary Library above, crossed
with `grammar.learn`'s question types (`GrammarQuestionType`) and `ExactMatchScorer` for grading.
Six endpoints under `GrammarLibraryController` (`/api/v1/learn/grammar/library`):
- `GET /{userId}/topics` — all 60 topics with the learner's own progression status
  (`LOCKED`/`UNLOCKED`/`IN_PROGRESS`/`PASSED`; the first topic auto-bootstraps to `UNLOCKED`).
- `GET /topics/{topicId}` — the theory page + question pool (AI-generates + persists on first read).
- `POST /{userId}/topics/{topicId}/sessions` — starts an `INITIAL` session from the full pool
  (`403` if the topic is `LOCKED`).
- `POST /sessions/{sessionId}/answers` — grades one submitted answer.
- `POST /sessions/{sessionId}/finish` — recomputes correctness for the whole session (unanswered =
  wrong), feeds every question into `PracticeService.redo(...)`, and either marks the topic `PASSED`
  + unlocks the next topic by `sequenceOrder`, or returns a fresh `RETRY` session covering only the
  questions still wrong (each replaced by one newly AI-generated question of the same type, stored
  inline in the session — never added to the shared question pool).
- `GET /{userId}/topics/{topicId}/history` — the learner's completed sessions for a topic.
- `POST /{userId}/sessions/{sessionId}/ai-practice` — "Luyện tập với AI" from a past session: verifies
  the session belongs to `userId`, diffs its stored `questionsJson` against its submitted answers via
  `GrammarMistakeAnalyzer.extractMissedRulesFromSession` (library questions carry no explicit rule tag
  of their own since a session is scoped to one topic, so each wrong question's own prompt is used as
  the tag), then **delegates the actual generate-and-persist step to
  `GrammarLearnService.generatePracticeForRules`** — the exact same pipeline `grammar.learn`'s own
  "generate from attempt" endpoint uses — so the regenerated content lands in the shared
  `grammar_practice_items` bank, not a Grammar-Library-only table. Returns the learner's refreshed
  `GrammarPracticeItemDto[]` (same shape as `grammar.learn`'s own listing); `404` if the session
  doesn't exist or belongs to someone else.

Migration: `V17__grammar_library.sql` (`grammar_library_topics`, `grammar_library_contents`,
`grammar_library_questions`, `grammar_topic_progress`, `grammar_library_sessions`,
`grammar_library_session_answers`). Grading always feeds `PracticeService.redo(...)`
(`itemId = "grammar:" + topicCode`, category `LearningCategories.GRAMMAR`), not deduped per question,
mirroring Vocabulary Library's Section-answer feeding. See
[`docs/sequence/English_service/grammar-library.md`](../../../docs/sequence/English_service/grammar-library.md)
and [`docs/flow/english-service-data-flow.md`](../../../docs/flow/english-service-data-flow.md).

## Listening Library

`com.remelearning.english.listening.library` is a fixed listening-topic catalog (same names/order as
Grammar Library's 60 topics, `V19__listening_library.sql`), crossing Grammar Library's
LOCKED/UNLOCKED/IN_PROGRESS/PASSED gating state machine with an AI-generated **Section** per topic —
a short passage plus Supertonic-synthesized audio, backed by a reusable 4-question multiple-choice
pool — generated by Gemini exactly once per topic (on first `POST .../sections`) and reused forever
after, the same "generate once, persist, reuse" pattern as Grammar/Vocabulary Library above. Four
endpoints under `ListeningLibraryController` (`/api/v1/learn/listening/library`):
- `GET /{userId}/topics` — every catalog topic with the learner's own progression status
  (`LOCKED`/`UNLOCKED`/`IN_PROGRESS`/`PASSED`; the first topic auto-bootstraps to `UNLOCKED`).
- `POST /{userId}/topics/{topicId}/sections` — starts a new Section (AI-generates passage + audio +
  question pool on first read only) or resumes the topic's most recent Section, then marks the topic
  `IN_PROGRESS` (`403` if the topic is `LOCKED`, `404` if the topic doesn't exist).
- `POST /{userId}/sections/{sectionId}/answers` — scores a submitted answer set (`404` if the section
  doesn't exist); a score ≥ 0.7 marks the topic `PASSED` and unlocks the next topic by
  `sequenceOrder`.
- `GET /{userId}/sections/history` — the learner's completed attempts across all topics/sections.

Migration: `V19__listening_library.sql` (`listening_library_topics`, `listening_library_sections`,
`listening_library_questions`, `listening_topic_progress`, `listening_library_attempts`). Unlike every
other library/learn skill, scoring here does **not** call `PracticeService.redo(...)` — it only writes
`listening_library_attempts`/`listening_topic_progress`, consistent with the pre-existing gap that
category `listening` has no dedicated weak-point table anywhere in the service (see
[Learn skills](#learn-skills-học--luyện-tập-với-ai) above). `bff-service` now proxies these four
endpoints too (via `EnglishServiceClient`/`LearnerController`), same as Vocabulary/Grammar Library. See
[`docs/sequence/English_service/listening-library.md`](../../../docs/sequence/English_service/listening-library.md)
and [`docs/flow/english-service-data-flow.md`](../../../docs/flow/english-service-data-flow.md).

## Speaking Library

`com.remelearning.english.speaking.library` is a fixed speaking-topic catalog (same names/order as
Grammar/Listening Library's 60 topics, `V20__speaking_library.sql`), crossing the same
LOCKED/UNLOCKED/IN_PROGRESS/PASSED gating state machine with an AI-generated **Section** per topic —
a pool of 5 sample sentences with IPA plus a Supertonic sample clip **per sentence** (unlike Listening
Library's single passage-level audio) — generated by Gemini exactly once per topic (on first
`POST .../sections`) and reused forever after. Unlike every other library skill, scoring is split into
two calls: sentences are scored **one at a time** against the same GOP (Goodness-of-Pronunciation)
scoring service `speaking.learn` already calls (`common.ai.pronunciation.PronunciationScoringClient`,
reused as-is, not reimplemented), and gating only advances via a separate `finish` call. Five endpoints
under `SpeakingLibraryController` (`/api/v1/learn/speaking/library`):
- `GET /{userId}/topics` — every catalog topic with the learner's own progression status
  (`LOCKED`/`UNLOCKED`/`IN_PROGRESS`/`PASSED`; the first topic auto-bootstraps to `UNLOCKED`).
- `POST /{userId}/topics/{topicId}/sections` — starts a new Section (AI-generates 5 sample sentences +
  IPA + per-sentence audio on first read only) or resumes the topic's most recent Section, then marks
  the topic `IN_PROGRESS` (`403` if the topic is `LOCKED`, `404` if the topic doesn't exist).
- `POST /{userId}/sections/{sectionId}/sentences/{sentenceId}/attempts` (multipart) — scores one
  recorded sentence attempt (`phonemeScore`/`wordScore`, each a plain average over the GOP response's
  per-word/per-phoneme breakdown) and persists it, along with the raw `weak_phonemes_json` (the GOP
  scorer's own `weak_phonemes` list, same field `speaking.learn`'s `SpeakingAttempt.weakPhonemesJson`
  persists verbatim — no separate threshold is computed here); does **not** itself touch topic
  progress (`404` if the section or sentence doesn't exist).
- `POST /{userId}/sections/{sectionId}/finish` — checks whether every sentence in the section has at
  least one attempt scoring ≥ 0.7 on both `phonemeScore` and `wordScore`; if so, marks the topic
  `PASSED` and unlocks the next topic by `sequenceOrder` (`404` if the section doesn't exist).
- `GET /{userId}/sections/history` — the learner's scored sentence attempts across all topics/sections.

Migration: `V20__speaking_library.sql` (`speaking_library_topics`, `speaking_library_sections`,
`speaking_library_sentences`, `speaking_topic_progress`, `speaking_library_attempts`), plus
`V22__speaking_library_weak_phonemes.sql` adding `speaking_library_attempts.weak_phonemes_json`. Like Listening
Library, scoring here does **not** call `PracticeService.redo(...)` — it only writes
`speaking_library_attempts`/`speaking_topic_progress`, not `pronunciation_weak_points` (unlike
`speaking.learn`, which does feed that table via the same scoring client). `bff-service` now proxies
these five endpoints too (via `EnglishServiceClient`/`LearnerController`), same as Listening Library. See
[`docs/sequence/English_service/speaking-library.md`](../../../docs/sequence/English_service/speaking-library.md)
and [`docs/flow/english-service-data-flow.md`](../../../docs/flow/english-service-data-flow.md).

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
- The four "Learn" skills (`vocabulary.learn`, `grammar.learn`, `listening`, `speaking`) are additive
  on top of that layout: `vocabulary.learn`/`grammar.learn` nest inside their existing domain package,
  while `listening`/`speaking` are new sibling domain packages that clone the same
  controller/service/mapper/domain/dto layout. All four share `english.learn.common`
  (`AiContentClient`, `DialogueAudioSynthesizer`) and reuse `english.practice.service.PracticeService.
  redo(...)` for grading/weak-point feedback instead of a per-skill feeder.
- `Boot4CompatConfig` (in `english/config/`) works around a local dependency gap between Boot 4 and
  `mybatis-spring-boot-starter:3.0.4` — see the gotcha in `CLAUDE.md` before assuming a new bug if you
  hit `UnsatisfiedDependencyException` for `SqlSessionFactory`/`KafkaTemplate`/`ObjectMapper`/
  `RestClient.Builder`.
