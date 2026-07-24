# Speaking learn: AI sentence + Supertonic sample, GOP-scored attempts

Covers `com.remelearning.english.speaking` (`SpeakingLearnController`/`SpeakingLearnServiceImpl`),
one of the four "Học &amp; Luyện tập với AI" skills (see `vocabulary-learn.md` for the shared
rationale). Generation is Gemini text + a single-voice Supertonic sample recording (simpler than
listening's multi-speaker dialogue - no `DialogueAudioSynthesizer`/`WavAudioMerger` needed, just one
direct `TtsClient.synthesize` call). Submission is a **multipart audio upload** (the learner's own
recording), scored via ai-service's wav2vec2 GOP (goodness-of-pronunciation) model through
`PronunciationScoringClient` - reusing the `pronunciation` domain's existing weak-point table/category
rather than introducing a new one (unlike listening's brand-new category). FE calls go through
`bff-service`'s `LearnerController` (`/api/v1/learners/{userId}/learn/speaking/...`); the submit
endpoint streams the uploaded `FilePart` straight through to english-service without buffering it in
bff-service, same convention as the recording-service multipart proxy - otherwise a pure pass-through,
omitted from the diagrams below as a separate hop.

This skill has no Kafka consumer/producer of its own for its request flow - grading reuses
`practice.service.PracticeService#redo`, which publishes `learning.gap.analysis.requested` exactly
like the other three skills.

## 1. Generate (`POST /api/v1/learn/speaking/{userId}/generate`)

```mermaid
sequenceDiagram
    participant Caller
    participant Ctrl as SpeakingLearnController
    participant Svc as SpeakingLearnServiceImpl
    participant WSvc as PronunciationWeakPointService
    participant Gen as LlmSpeakingPracticeGenerator
    participant Ai as AiContentClient (common.ai.LlmClient)
    participant Gemini as Gemini API
    participant SMapper as SpeakingMapper
    participant DB as reme_english DB
    participant Tts as TtsClient (Supertonic)
    participant AiSvc as ai-service /api/v1/tts/synthesize
    participant Store as StorageClient (common)

    Caller->>Ctrl: POST /{userId}/generate {focusItems?, level?, examType?}
    Ctrl->>Svc: generate(userId, request)
    alt focusItems provided
        Svc->>Svc: targetWords = focusItems
    else no focusItems
        Svc->>WSvc: getWeakPoints(userId, null)
        WSvc-->>Svc: all pronunciation weak points
        Svc->>Svc: sort by forgettingScore desc, limit 8 -> targetWords<br/>(empty list ok - generator picks its own topic)
    end
    Svc->>Gen: generate(targetWords, level, examType)
    Gen->>Ai: completeJson(systemPrompt, userPrompt, temp=0.6, maxTokens=400)
    Ai->>Gemini: LlmClient.complete(...) -> generateContent REST call
    Gemini-->>Ai: raw text (code-fence stripped)
    Ai-->>Gen: parsed JSON {topic, targetText, translation}
    alt LLM call fails, or parse fails, or targetText blank
        Gen->>Gen: fallback() - fixed template sentence, or one naming the target words
    end
    Gen-->>Svc: GeneratedSpeakingPractice{topic, targetText, translation}
    Svc->>SMapper: insertItem({userId, level, examType, topic, targetText, translation})
    SMapper->>DB: INSERT INTO speaking_practice_items
    Svc->>Tts: synthesize({text: targetText, languageCode, voice: ttsVoice}) - one fixed voice, no multi-speaker split
    Tts->>AiSvc: POST /api/v1/tts/synthesize
    AiSvc-->>Tts: {audio_base64, mime_type, sample_rate}
    Svc->>Store: write("speaking/sample/{userId}/{itemId}.wav", audioBytes)
    Svc->>SMapper: updateItemStorageKey(itemId, key)
    SMapper->>DB: UPDATE speaking_practice_items SET storage_key = ?
    Svc-->>Ctrl: SpeakingPracticeItemDto{practiceItemId, sampleAudioUrl, level, examType, topic, targetText, translation}<br/>(targetText/translation ARE revealed at generate time - unlike vocabulary/grammar/listening,<br/>the learner must read it aloud, so hiding it would be pointless)
    Ctrl-->>Caller: 200 ApiResponse
```

## 2. Submit attempt (`POST /api/v1/learn/speaking/{userId}/attempts`, multipart)

```mermaid
sequenceDiagram
    participant Caller
    participant Ctrl as SpeakingLearnController
    participant Svc as SpeakingLearnServiceImpl
    participant SMapper as SpeakingMapper
    participant DB as reme_english DB
    participant Store as StorageClient (common)
    participant Scoring as PronunciationScoringClient
    participant AiSvc as ai-service /api/v1/pronunciation/score
    participant PSvc as PracticeService (redo)
    participant Kafka as learning.gap.analysis.requested

    Caller->>Ctrl: POST /{userId}/attempts (multipart: practiceItemId, audio)
    Ctrl->>Svc: submit(userId, practiceItemId, audio)
    Svc->>SMapper: findItemById(practiceItemId)
    alt not found
        Svc-->>Ctrl: BusinessException.notFound -> 404
    else found
        Svc->>Store: write("speaking/attempts/{userId}/{uuid}.wav", audio.getInputStream())
        Svc->>Store: read(attemptKey)
        Svc->>Scoring: score(audioStream, filename, item.targetText, ttsLang)
        Scoring->>AiSvc: POST /api/v1/pronunciation/score (multipart: audio, expected_text, language_code)
        AiSvc-->>Scoring: {overall, words[{word,score,phonemes[{ipa,score}]}], transcript, weak_phonemes[]}<br/>(g2p_en + wav2vec2-lv-60-espeak-cv-ft GOP scoring)
        Scoring-->>Svc: PronunciationScore
        Svc->>SMapper: insertAttempt({practiceItemId, userId, audioStorageKey, overallScore, wordScoresJson, transcript, weakPhonemesJson})
        SMapper->>DB: INSERT INTO speaking_attempts
        Svc->>Svc: feedWeakPoints - dedupe by word,<br/>map each to PracticeAttemptRequest{itemId="pronunciation:<word>", category="pronunciation",<br/>label=word, correct=(score >= 0.6)}
        opt any attempts built
            Svc->>PSvc: redo(PracticeRedoRequest{userId, attempts[]})
            PSvc->>PSvc: log attempt + score via WeakPointScoringOrchestrator<br/>(BKT/Rasch/Ebbinghaus/Leitner) -> upsert pronunciation_weak_points directly
            PSvc->>Kafka: publish AnalysisRequestedEvent (bundled mistake_history)<br/>-> ai-service re-scores, republishes learning.gap.analyzed<br/>(feeds recommendation-service/dashboard-service)
        end
        Svc-->>Ctrl: SpeakingAttemptResultDto{overall, words[], transcript, weakPhonemes[], actionAdvice[]}
        Ctrl-->>Caller: 200 ApiResponse
    end
```

## 3. Generate from one past attempt's mistakes (`POST /api/v1/learn/speaking/history/{userId}/{attemptId}/ai-practice`)

```mermaid
sequenceDiagram
    participant Caller
    participant Ctrl as SpeakingLearnController
    participant Svc as SpeakingLearnServiceImpl
    participant SMapper as SpeakingMapper
    participant DB as reme_english DB
    participant Analyzer as SpeakingMistakeAnalyzer (pure)
    participant Gen as LlmSpeakingPracticeGenerator
    participant Tts as TtsClient (Supertonic)
    participant AiSvc as ai-service /api/v1/tts/synthesize
    participant Store as StorageClient (common)

    Caller->>Ctrl: POST /history/{userId}/{attemptId}/ai-practice
    Ctrl->>Svc: generatePracticeFromAttempt(userId, attemptId)
    Svc->>SMapper: findAttemptDetailByIdAndUserId(attemptId, userId)
    alt not found / not owned by userId
        Svc-->>Ctrl: BusinessException.notFound -> 404
    else found
        SMapper-->>Svc: SpeakingAttemptDetailRow{level, examType, topic, weakPhonemesJson}
        Svc->>Analyzer: extractWeakPhonemes(weakPhonemesJson)
        Note over Analyzer: weakPhonemesJson is already a flat JSON array of short IPA symbols<br/>(ai-service's GOP scorer already reduced each mistake to a crisp retry<br/>target) - no per-question OPEN-vs-KEYWORD diffing/fallback needed the<br/>way listening's resultsJson requires
        Analyzer-->>Svc: distinct mispronounced phonemes[]
        Svc->>Svc: generatePracticeForKeywords(userId, weakPhonemes, attempt.level, attempt.examType)
        Note over Svc: same generate-and-persist step "1. Generate" uses -<br/>generator.generate(...) -> synthesize sample -> insertItem -> listItems(userId)
        Svc->>Gen: generate(weakPhonemes, level, examType)
        Gen-->>Svc: GeneratedSpeakingPractice{topic, targetText, translation}
        Svc->>SMapper: insertItem({userId, level, examType, topic, targetText, translation})
        SMapper->>DB: INSERT INTO speaking_practice_items
        Svc->>Tts: synthesize({text: targetText, languageCode, voice: ttsVoice})
        Tts->>AiSvc: POST /api/v1/tts/synthesize
        AiSvc-->>Tts: {audio_base64, mime_type, sample_rate}
        Svc->>Store: write("speaking/sample/{userId}/{itemId}.wav", audioBytes)
        Svc->>SMapper: updateItemStorageKey(itemId, key)
        SMapper->>DB: UPDATE speaking_practice_items SET storage_key = ?
        Svc->>SMapper: findItemsByUserId(userId)
        SMapper-->>Svc: refreshed practice-item rows
        Svc-->>Ctrl: SpeakingPracticeItemDto[] (refreshed list)
        Ctrl-->>Caller: 200 ApiResponse
    end
```

## 4. Merged history (`GET /api/v1/learn/speaking/merged-history/{userId}`)

```mermaid
sequenceDiagram
    participant Caller
    participant Ctrl as SpeakingLearnController
    participant HSvc as SpeakingHistoryServiceImpl
    participant LearnSvc as SpeakingLearnServiceImpl
    participant LibSvc as SpeakingLibraryServiceImpl
    participant SMapper as SpeakingMapper
    participant LibAttemptMapper as SpeakingLibraryAttemptMapper
    participant DB as reme_english DB

    Caller->>Ctrl: GET /merged-history/{userId}
    Ctrl->>HSvc: getMergedHistory(userId)
    HSvc->>LearnSvc: getHistory(userId)
    LearnSvc->>SMapper: findHistoryByUserId(userId)
    SMapper->>DB: SELECT speaking_attempts JOIN speaking_practice_items
    SMapper-->>LearnSvc: SpeakingAttemptHistoryRow[]
    LearnSvc-->>HSvc: SpeakingAttemptHistoryEntryDto[] (score = overallScore)
    HSvc->>LibSvc: getHistory(userId)
    LibSvc->>LibAttemptMapper: findByUserId(userId) - one row per scored sentence attempt
    LibAttemptMapper->>DB: SELECT speaking_library_attempts WHERE user_id=?
    LibAttemptMapper-->>LibSvc: SpeakingLibraryAttempt[]
    LibSvc-->>HSvc: SpeakingLibraryAttempt[] (raw domain rows, score = avg(phonemeScore, wordScore))
    HSvc->>HSvc: normalize both into SpeakingHistoryEntryDto{source, attemptOrSessionId, completedAt, score, sectionId?}<br/>merge + sort descending by completedAt
    HSvc-->>Ctrl: SpeakingHistoryEntryDto[]
    Ctrl-->>Caller: 200 ApiResponse
```

Note: `SpeakingHistoryServiceImpl` is a standalone service, not folded into either
`SpeakingLearnServiceImpl` or `SpeakingLibraryServiceImpl`, for the same reason as
`GrammarHistoryServiceImpl`/`ListeningHistoryServiceImpl` (see `grammar-learn.md` section 4) -
`SpeakingLibraryServiceImpl` already depends on `SpeakingLearnService` (for
`generatePracticeFromSection`), so a reverse dependency would form a circular bean. Library rows are
merged at `SpeakingLibraryService#getHistory`'s existing granularity - one row per scored sentence
attempt, not rolled up per section (unlike Listening Library, which scores a whole section at once).

## External calls

| # | Call | From -> To | Notes |
|---|------|-----------|-------|
| 1 | HTTPS | english-service -> Gemini API | `LlmSpeakingPracticeGenerator` via `AiContentClient`/`LlmClient`; falls back to a template on any failure |
| 2 | HTTP | english-service -> ai-service `/api/v1/tts/synthesize` | Supertonic TTS, one fixed voice (`speaking.tts.voice`, default `F1`), single call (no per-speaker/per-line split like listening's dialogue) |
| 3 | HTTP (multipart) | english-service -> ai-service `/api/v1/pronunciation/score` | `AiServicePronunciationScoringClient` (`PronunciationScoringClient`); wav2vec2 GOP scoring against `item.targetText` |
| 4 | StorageClient write/read | english-service -> local FS (or S3) | both the Supertonic sample audio (generate/regenerate) and the learner's uploaded recording (submit) |
| 5 | Kafka produce | english-service -> `learning.gap.analysis.requested` | via `PracticeService#redo` -> `AnalysisRequestedProducer` |
| 6 | Postgres | english-service -> `reme_english` | `speaking_practice_items`, `speaking_attempts`, plus `pronunciation_weak_points` (upserted by `WeakPointScoringOrchestrator`) |

## Notes

- Speaking is the only one of the four "learn" skills whose `submit` is a multipart file upload
  rather than a JSON body of text answers - `bff-service`'s `LearnerController.submitSpeakingAttempt`
  takes a `FilePart` and relays it unbuffered.
- `targetText`/`translation` are revealed immediately in the `generate` response (not withheld until
  grading like vocabulary/grammar/listening) since the learner needs to read the sentence aloud - the
  only one of the four skills with this "reveal at generate" behavior.
- Reuses the `pronunciation` domain's existing weak-point table/service (`pronunciation_weak_points`,
  `PronunciationWeakPointService`) rather than adding a new one - the same category ai-service's
  original forgetting-pattern pipeline already produces, unlike listening's brand-new `"listening"`
  category (which - see `listening-learn.md`'s Notes - `WeakPointDispatcherImpl` doesn't actually
  route anywhere).
- Word-level correctness for the weak-point feed is binary (`score >= 0.6`), not the continuous GOP
  score itself - same simplification `ListeningLearnServiceImpl` applies to its KEYWORD questions.
- `generatePracticeForKeywords` (section 3) is the shared generate-and-persist step both
  `generatePracticeFromAttempt` and Speaking Library's own `generatePracticeFromSection` delegate to
  (see `speaking-library.md` section 3) - there is only one AI-practice destination
  (`speaking_practice_items`) per domain, regardless of which flow (learn attempt vs. library
  section) the mistake came from. Mirrors `listening-learn.md` section 3's same pattern.
- `SpeakingMistakeAnalyzer.extractWeakPhonemes` needs no OPEN-question-style fallback the way
  `ListeningMistakeAnalyzer.extractMissedTopics` does: `weakPhonemesJson` (both here and in Task 2's
  `SpeakingLibraryAttempt`) is already a flat JSON array of short IPA symbols computed by
  ai-service's GOP scorer (`WEAK_PHONEME_THRESHOLD`), never a full-sentence model answer, so it's
  always a crisp, generator-ready retry target on its own.
