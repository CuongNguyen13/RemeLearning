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
    participant Diar as DiarizationEngine (pyannote)
    participant Pool as ThreadPoolExecutor (per turn)
    participant Whisper as FasterWhisperEngine
    participant Vision as GeminiVisionEngine
    participant Face as InsightFaceEngine + FaceEnrollmentService
    participant VAuth as HeuristicVoiceAuthenticityAnalyzer
    participant AiDb as Postgres (reme_ai, schema ai)

    Caller->>AI: POST /api/v1/transcribe<br/>{recording_id, user_id, s3_bucket, s3_key, language_code?}
    AI->>S3: download_to_tempfile(s3_bucket, s3_key)
    S3-->>AI: audio_path
    AI->>Conv: convert_to_wav(audio_path)
    Conv-->>AI: wav_path

    AI->>Diar: diarize(wav_path)
    Note right of Diar: first call loads pyannote/speaker-diarization-3.1,<br/>gated by HF_TOKEN
    Diar-->>AI: SpeakerTurn[] (one per speaker segment)

    AI->>Pool: transcribe_turns_multilingual(wav_path, turns, language_code)
    Note right of Pool: STT_MAX_CONCURRENT_TRANSCRIPTIONS workers;<br/>falls back to one whole-file turn if diarization finds no speech
    par per speaker turn, concurrently
        Pool->>Pool: slice_wav(wav_path, turn.start, turn.end)
        Pool->>Whisper: transcribe_auto(turn_wav, language_code)
        Note right of Whisper: language_code=None auto-detects this turn's<br/>language independently, so speakers using<br/>different languages each decode correctly
        Whisper-->>Pool: RawSegment[] + detected language
    end
    Pool-->>AI: Segment[] (speaker, text, start/end offset by turn, language), sorted by start_seconds

    opt VISION_ENABLED=true
        AI->>AI: extract_frames(audio_path, VISION_FRAME_INTERVAL_SECONDS)<br/>(PyAV, sampled JPEGs from the original video)
        AI->>Vision: caption_frames(frames)
        Vision-->>AI: FrameCaption[] (Gemini generateContent, inline image)
        AI->>AI: merge_caption_segments<br/>(caption Segment[] with speaker="vision", ordered by timestamp)
        AI->>FS: remove(frame image files)
    end

    opt FACE_RECOGNITION_ENABLED=true
        AI->>Face: identify_speakers_by_face(audio_path, turns, known_faces)
        Note right of Face: samples FACE_FRAMES_PER_TURN frames per SpeakerTurn,<br/>detects+embeds faces (ArcFace/buffalo_l), matches by cosine<br/>similarity vs ai.known_faces (threshold FACE_MATCH_SIMILARITY_THRESHOLD)
        Face-->>AI: dict[speaker -> FaceMatch(user_id, name, similarity)]
        AI->>AiDb: INSERT face_recognition_results (per matched speaker)
    end

    opt VOICE_AUTHENTICITY_ENABLED=true
        AI->>VAuth: annotate_voice_authenticity(wav_path, turns)
        Note right of VAuth: slice_wav per turn, heuristic acoustic features<br/>(jitter/shimmer/spectral flatness/pause regularity) -<br/>NOT a trained model, see module docstring
        VAuth-->>AI: SegmentAuthenticity[] (label, confidence) per turn
        AI->>AiDb: INSERT voice_authenticity_results (per turn)
    end

    AI->>FS: remove(audio_path), remove(wav_path)
    AI-->>Caller: 200 TranscriptionResult<br/>{full_text, segments: [...], recording_id,<br/>speaker_identities: [...], voice_authenticity: [...]}
```

## External calls

| # | Call | From -> To | Notes |
|---|------|-----------|-------|
| 1 | S3 GetObject | ai-service -> S3/MinIO | requires `S3_ENDPOINT`/`S3_ACCESS_KEY`/`S3_SECRET_KEY` |
| 2 | HuggingFace Hub download | ai-service -> huggingface.co | diarization model, requires `HF_TOKEN`; cached after first call |
| 3 | Gemini `generateContent` (vision) | ai-service -> generativelanguage.googleapis.com | only when `VISION_ENABLED=true`; requires `GEMINI_API_KEY`; one call per sampled frame |
| 4 | insightface model download | ai-service -> HTTP (insightface model zoo) | only when `FACE_RECOGNITION_ENABLED=true`; `buffalo_l` pack, cached under `~/.insightface` after first call |
| 5 | Postgres | ai-service -> `reme_ai` (schema `ai`) | only when `FACE_RECOGNITION_ENABLED`/`VOICE_AUTHENTICITY_ENABLED` are true |

## Notes

- Requires S3 env vars (`S3_ENDPOINT`/`S3_ACCESS_KEY`/`S3_SECRET_KEY`) to actually be reachable —
  unlike `/api/v1/upload`, which needs no S3 at all.
- Request/response schemas mirror the Kafka `recording.uploaded` / `transcript.ready` events
  (`app/schemas/events.py`), snake_case JSON keys.
- `language_code: null`/omitted (the default) auto-detects each diarized speaker turn's language
  independently and transcribes turns concurrently (`STT_MAX_CONCURRENT_TRANSCRIPTIONS`, default 4)
  — the intended path for recordings where different speakers use different languages. Passing an
  explicit `language_code` forces that one language across every turn instead (skips per-turn
  detection; still runs turns concurrently). See `app/stt/pipeline.py::transcribe_turns_multilingual`.
- `VISION_ENABLED` (`app/config.py`, default `false`) gates the frame-captioning step
  (`app/vision/`). When on, sampled-frame JPEGs are always removed after captioning, even if the
  Gemini call raises.
- `FACE_RECOGNITION_ENABLED`/`VOICE_AUTHENTICITY_ENABLED` (`app/config.py`, default `false`) gate the
  two new optional stages (`app/face/`, `app/voice_auth/`). Both persist to the new `ai` schema in
  `reme_ai` (Alembic-managed, `ai-service/migrations/`) in addition to being attached to the response -
  see [../../flow/ai-service-data-flow.md](../../flow/ai-service-data-flow.md) and
  [enroll-face.md](enroll-face.md) for how faces get enrolled in the first place.
