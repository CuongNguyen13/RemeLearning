# ai-service — Overview

`ai-service` (Python/FastAPI) exposes the same two processing stages — **STT + diarization** and
**forgetting-pattern analysis** — through two parallel entry points: the async Kafka pipeline (used
when `KAFKA_ENABLED=true`) and synchronous REST endpoints (always available, for manual calls or
environments without Kafka/S3 wired up yet). See `RemeLearning/services/ai-service/app/main.py`,
`app/kafka/handlers/`, and `app/api/routes.py`.

This file covers `ai-service`'s own internals only. The Kafka topics it publishes to
(`transcript.ready`, `learning.gap.analyzed`) are consumed downstream by `english-service` — for
that side's internal handling, see
[../English_service/overview.md](../English_service/overview.md). Per-endpoint detail lives in
[health.md](health.md), [upload.md](upload.md), [transcribe.md](transcribe.md),
[analyze.md](analyze.md).

## 1. Kafka event-driven pipeline

```mermaid
---
config:
  theme: base
  themeVariables:
    background: '#ffffff'
---
sequenceDiagram
    participant Rec as recording-service (Java)
    participant Kafka
    participant AI as ai-service
    participant S3
    participant Whisper as FasterWhisperEngine
    participant Diar as DiarizationEngine (pyannote)
    participant Eng as english-service (Java)

    Rec->>Kafka: publish recording.uploaded<br/>{recording_id, user_id, s3_bucket, s3_key, language_code}
    Kafka->>AI: consume recording.uploaded<br/>(handle_recording_uploaded)
    AI->>S3: download_to_tempfile(s3_bucket, s3_key)
    AI->>AI: convert_to_wav (PyAV, any container -> 16kHz mono WAV)
    AI->>Whisper: transcribe(wav_path, language_code)
    Whisper-->>AI: RawSegment[] (text + timestamps)
    AI->>Diar: diarize(wav_path)
    Diar-->>AI: SpeakerTurn[] (HF_TOKEN-gated pyannote/speaker-diarization-3.1)
    AI->>AI: build_transcription_result<br/>(merge STT + diarization by max time-overlap)
    AI->>Kafka: publish transcript.ready<br/>{recording_id, user_id, full_text, segments[]}
    Kafka->>Eng: consume transcript.ready<br/>(handling detailed in english-service's own overview)

    Note over Eng,Kafka: later, a backend service bundles the transcript<br/>with the learner's historical mistake stats

    Eng->>Kafka: publish learning.gap.analysis.requested<br/>{recording_id, user_id, segments[], history[]}
    Kafka->>AI: consume learning.gap.analysis.requested<br/>(handle_analysis_requested)
    AI->>AI: RuleBasedAnalyzer.analyze(segments, history)<br/>(frequency + Ebbinghaus-style recency decay)
    AI->>Kafka: publish learning.gap.analyzed<br/>{recording_id, user_id, weak_points[] (category, item, score)}
    Kafka->>Eng: consume learning.gap.analyzed<br/>(handling detailed in english-service's own overview)
```

## 2. Synchronous REST fallback

Used when Kafka/S3 aren't available, or for ad-hoc/manual testing (e.g. via `curl` or Swagger UI at
`/docs`). Same underlying engines as the Kafka path, no event envelope.

```mermaid
---
config:
  theme: base
  themeVariables:
    background: '#ffffff'
---
sequenceDiagram
    participant Caller as Caller (curl / Java service / manual)
    participant AI as ai-service
    participant S3
    participant Whisper as FasterWhisperEngine
    participant Diar as DiarizationEngine

    alt POST /api/v1/upload (multipart file, no S3 needed)
        Caller->>AI: file + language_code
        AI->>AI: write to tempfile
    else POST /api/v1/transcribe (event payload, needs S3)
        Caller->>AI: {s3_bucket, s3_key, language_code, ...}
        AI->>S3: download_to_tempfile(s3_bucket, s3_key)
    end
    AI->>AI: convert_to_wav
    AI->>Whisper: transcribe(wav_path, language_code)
    Whisper-->>AI: RawSegment[]
    AI->>Diar: diarize(wav_path)
    Diar-->>AI: SpeakerTurn[]
    AI->>AI: build_transcription_result
    AI-->>Caller: 200 TranscriptionResult<br/>{full_text, segments[]}

    Caller->>AI: POST /api/v1/analyze<br/>{recording_id, user_id, segments[], history[]}
    AI->>AI: RuleBasedAnalyzer.analyze(segments, history)
    AI-->>Caller: 200 LearningGapAnalyzedEvent<br/>{recording_id, user_id, weak_points[]}
```

## Notes

- `KAFKA_ENABLED` (`app/config.py`) defaults to `false`; with it `false`, only the REST endpoints run
  and the two consumer tasks in `app/main.py`'s lifespan never start.
- The Kafka payloads (`app/schemas/events.py`) are plain pydantic models with **snake_case** JSON keys
  (`event.model_dump()`, no `BaseEvent` envelope) — Java consumers decode via a dedicated snake_case
  mapper (`english-service`'s `EventCodec`), not the default camelCase Jackson config.
- Diarization requires `HF_TOKEN` (HuggingFace token with access to
  `pyannote/speaker-diarization-3.1`) set in `ai-service/.env`.
- `english-service` (`vocabulary` domain) is currently the only Java-side consumer of
  `transcript.ready` / `learning.gap.analyzed` — see
  [../English_service/overview.md](../English_service/overview.md) for how it handles them.
