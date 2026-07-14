# POST /api/v1/transcribe

Synchronous STT + diarization for manual/ad-hoc calls (the main pipeline uses Kafka's
`recording.uploaded` -> `transcript.ready` instead — see
[overview.md](overview.md)). Takes the same event shape as
`RecordingUploadedEvent`, but downloads from S3 and returns the result directly instead of
publishing it. See `app/api/routes.py::transcribe`.

```mermaid
---
config:
  theme: base
  themeVariables:
    background: '#ffffff'
---
sequenceDiagram
    participant Caller
    participant AI as ai-service
    participant S3
    participant FS as Local tempfile
    participant Conv as convert_to_wav (PyAV)
    participant Whisper as FasterWhisperEngine
    participant Diar as DiarizationEngine (pyannote)

    Caller->>AI: POST /api/v1/transcribe<br/>{recording_id, user_id, s3_bucket, s3_key, language_code}
    AI->>S3: download_to_tempfile(s3_bucket, s3_key)
    S3-->>AI: audio_path
    AI->>Conv: convert_to_wav(audio_path)
    Conv-->>AI: wav_path

    AI->>Whisper: transcribe(wav_path, language_code)
    Whisper-->>AI: RawSegment[]

    AI->>Diar: diarize(wav_path)
    Note right of Diar: first call loads pyannote/speaker-diarization-3.1,<br/>gated by HF_TOKEN
    Diar-->>AI: SpeakerTurn[]

    AI->>AI: build_transcription_result
    AI->>FS: remove(audio_path), remove(wav_path)
    AI-->>Caller: 200 TranscriptionResult<br/>{full_text, segments: [{speaker, text, start_seconds, end_seconds}]}
```

## External calls

| # | Call | From -> To | Notes |
|---|------|-----------|-------|
| 1 | S3 GetObject | ai-service -> S3/MinIO | requires `S3_ENDPOINT`/`S3_ACCESS_KEY`/`S3_SECRET_KEY` |
| 2 | HuggingFace Hub download | ai-service -> huggingface.co | diarization model, requires `HF_TOKEN`; cached after first call |

## Notes

- Requires S3 env vars (`S3_ENDPOINT`/`S3_ACCESS_KEY`/`S3_SECRET_KEY`) to actually be reachable —
  unlike `/api/v1/upload`, which needs no S3 at all.
- Request/response schemas mirror the Kafka `recording.uploaded` / `transcript.ready` events
  (`app/schemas/events.py`), snake_case JSON keys.
