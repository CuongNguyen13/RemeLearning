# POST /api/v1/upload

Accepts a video/audio file directly (`multipart/form-data`) and runs STT + diarization
synchronously. For environments without Kafka/S3 wired up yet — the Java side (or a manual caller)
sends the raw file instead of publishing `recording.uploaded`. See `app/api/routes.py::upload`.

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
    participant FS as Local tempfile
    participant Conv as convert_to_wav (PyAV)
    participant Diar as DiarizationEngine (pyannote)
    participant Pool as ThreadPoolExecutor (per turn)
    participant Whisper as FasterWhisperEngine
    participant Vision as GeminiVisionEngine
    participant Face as InsightFaceEngine + FaceEnrollmentService
    participant VAuth as HeuristicVoiceAuthenticityAnalyzer
    participant AiDb as Postgres (reme_ai, schema ai)

    Caller->>AI: POST /api/v1/upload<br/>multipart file + language_code (default: omitted/null)
    loop until file.read() returns empty
        AI->>FS: read UPLOAD_CHUNK_SIZE_BYTES chunk, write to NamedTemporaryFile
    end
    Note right of FS: streamed in fixed-size chunks (1 MiB) instead of<br/>buffering the whole upload in memory at once
    AI->>Conv: convert_to_wav(audio_path)
    Conv-->>AI: wav_path (16kHz mono PCM WAV)

    AI->>Diar: diarize(wav_path)
    Note right of Diar: needs HF_TOKEN with access to<br/>pyannote/speaker-diarization-3.1
    Diar-->>AI: SpeakerTurn[] (speaker label + start/end)

    AI->>Pool: transcribe_turns_multilingual(wav_path, turns, language_code)
    Note right of Pool: STT_MAX_CONCURRENT_TRANSCRIPTIONS workers;<br/>falls back to one whole-file turn if diarization finds no speech
    par per speaker turn, concurrently
        Pool->>Pool: slice_wav(wav_path, turn.start, turn.end)
        Pool->>Whisper: transcribe_auto(turn_wav, language_code)
        Note right of Whisper: language_code omitted (null) auto-detects this<br/>turn's language independently - so a recording<br/>with speakers using different languages is<br/>analyzed correctly instead of one language<br/>being forced across the whole file
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
        AI->>AiDb: INSERT face_recognition_results (per matched speaker, keyed by generated recording_id)
    end

    opt VOICE_AUTHENTICITY_ENABLED=true
        AI->>VAuth: annotate_voice_authenticity(wav_path, turns)
        Note right of VAuth: slice_wav per turn, heuristic acoustic features<br/>(jitter/shimmer/spectral flatness/pause regularity) -<br/>NOT a trained model, see module docstring
        VAuth-->>AI: SegmentAuthenticity[] (label, confidence) per turn
        AI->>AiDb: INSERT voice_authenticity_results (per turn, keyed by generated recording_id)
    end

    AI->>FS: remove(audio_path), remove(wav_path)
    AI-->>Caller: 200 TranscriptionResult<br/>{full_text, segments: [...], recording_id (generated),<br/>speaker_identities: [...], voice_authenticity: [...]}
```

## External calls

| # | Call | From -> To | Notes |
|---|------|-----------|-------|
| 1 | HuggingFace Hub download | ai-service -> huggingface.co | diarization model, requires `HF_TOKEN`; cached after first call, no S3/Kafka involved |
| 2 | Gemini `generateContent` (vision) | ai-service -> generativelanguage.googleapis.com | only when `VISION_ENABLED=true`; requires `GEMINI_API_KEY`; one call per sampled frame |
| 3 | insightface model download | ai-service -> HTTP (insightface model zoo) | only when `FACE_RECOGNITION_ENABLED=true`; `buffalo_l` pack, cached under `~/.insightface` after first call |
| 4 | Postgres | ai-service -> `reme_ai` (schema `ai`) | only when `FACE_RECOGNITION_ENABLED`/`VOICE_AUTHENTICITY_ENABLED` are true |

## Notes

- No S3 or Kafka dependency — the only synchronous entry point that works with just a file in hand.
- Temp files are always cleaned up in a `finally` block, even if transcription/diarization raises.
- The upload itself is read in `UPLOAD_CHUNK_SIZE_BYTES` (1 MiB) chunks via `UploadFile.read(size)`
  rather than one unbounded `file.read()` — memory use stays flat regardless of file size instead of
  scaling with it.
- Diarizes first, then transcribes each speaker turn concurrently
  (`app/stt/pipeline.py::transcribe_turns_multilingual`, worker count via
  `STT_MAX_CONCURRENT_TRANSCRIPTIONS`, default 4). Omitting `language_code` (the default) is the
  multi-language path: each turn's language is auto-detected independently. Passing an explicit
  `language_code` forces that language for every turn instead — still runs turns concurrently, just
  skips per-turn detection.
- `VISION_ENABLED` (`app/config.py`, default `false`) gates the frame-captioning step
  (`app/vision/`). When on, sampled-frame JPEGs are always removed after captioning, even if the
  Gemini call raises.
- `FACE_RECOGNITION_ENABLED`/`VOICE_AUTHENTICITY_ENABLED` (`app/config.py`, default `false`) gate the
  two new optional stages. Since ad-hoc uploads have no recordingId of their own, one is generated
  (UUID) and returned in the response so results can be traced back to `ai.face_recognition_results`/
  `ai.voice_authenticity_results` - see [enroll-face.md](enroll-face.md) for enrollment and
  [../../flow/ai-service-data-flow.md](../../flow/ai-service-data-flow.md) for the full data shape.
