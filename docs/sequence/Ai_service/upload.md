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
    participant Whisper as FasterWhisperEngine
    participant Diar as DiarizationEngine (pyannote)
    participant Vision as GeminiVisionEngine

    Caller->>AI: POST /api/v1/upload<br/>multipart file + language_code (default "en")
    AI->>FS: write uploaded bytes to NamedTemporaryFile
    AI->>Conv: convert_to_wav(audio_path)
    Conv-->>AI: wav_path (16kHz mono PCM WAV)

    AI->>Whisper: transcribe(wav_path, language_code)
    Whisper-->>AI: RawSegment[] (text + start/end timestamps)

    AI->>Diar: diarize(wav_path)
    Note right of Diar: needs HF_TOKEN with access to<br/>pyannote/speaker-diarization-3.1
    Diar-->>AI: SpeakerTurn[] (speaker label + start/end)

    AI->>AI: build_transcription_result<br/>(merge STT + diarization by max time-overlap)

    opt VISION_ENABLED=true
        AI->>AI: extract_frames(audio_path, VISION_FRAME_INTERVAL_SECONDS)<br/>(PyAV, sampled JPEGs from the original video)
        AI->>Vision: caption_frames(frames)
        Vision-->>AI: FrameCaption[] (Gemini generateContent, inline image)
        AI->>AI: merge_caption_segments<br/>(caption Segment[] with speaker="vision", ordered by timestamp)
        AI->>FS: remove(frame image files)
    end

    AI->>FS: remove(audio_path), remove(wav_path)
    AI-->>Caller: 200 TranscriptionResult<br/>{full_text, segments: [{speaker, text, start_seconds, end_seconds}]}
```

## External calls

| # | Call | From -> To | Notes |
|---|------|-----------|-------|
| 1 | HuggingFace Hub download | ai-service -> huggingface.co | diarization model, requires `HF_TOKEN`; cached after first call, no S3/Kafka involved |
| 2 | Gemini `generateContent` (vision) | ai-service -> generativelanguage.googleapis.com | only when `VISION_ENABLED=true`; requires `GEMINI_API_KEY`; one call per sampled frame |

## Notes

- No S3 or Kafka dependency — the only synchronous entry point that works with just a file in hand.
- Temp files are always cleaned up in a `finally` block, even if transcription/diarization raises.
- `VISION_ENABLED` (`app/config.py`, default `false`) gates the frame-captioning step
  (`app/vision/`). When on, sampled-frame JPEGs are always removed after captioning, even if the
  Gemini call raises.
