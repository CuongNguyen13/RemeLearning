# ai-service — Data Flow

Focuses on **what happens to the data** (transformations, formats, storage) as it moves through
`ai-service`, as opposed to the sequence diagrams in
[../sequence/Ai_service/](../sequence/Ai_service/) which focus on call order between components.

```mermaid
---
config:
  theme: base
  themeVariables:
    background: '#ffffff'
---
flowchart TD
    subgraph Input["Input"]
        MultipartUpload["multipart file upload<br/>(POST /api/v1/upload)"]
        RawFile["Raw video/audio file<br/>(mp4, mkv, mp3, ...)"]
        S3Obj["S3/MinIO object<br/>(s3_bucket + s3_key)"]
        EventIn["recording.uploaded event<br/>{recording_id, user_id, s3_bucket, s3_key, language_code?}"]
    end

    subgraph STT["STT + Diarization pipeline"]
        Convert["convert_to_wav (PyAV)<br/>-> 16kHz mono PCM WAV"]
        Diarize["DiarizationEngine.diarize (pyannote)<br/>-> SpeakerTurn[] (speaker label + start/end)"]
        Slice["slice_wav per turn<br/>-> one WAV clip per speaker turn"]
        Whisper["FasterWhisperEngine.transcribe_auto (per turn, concurrent)<br/>language_code=None auto-detects that turn's language<br/>-> RawSegment[] + detected language, per turn"]
        Merge["transcribe_turns_multilingual<br/>offset each turn's segments by turn.start_seconds, sort by start_seconds<br/>-> TranscriptionResult{full_text, segments[] (incl. language)}"]
    end

    subgraph Vision["Vision captioning (optional, VISION_ENABLED=true)"]
        FrameExtract["extract_frames (PyAV)<br/>-> FrameSample[] (sampled JPEG + timestamp, every VISION_FRAME_INTERVAL_SECONDS)"]
        GeminiVision["GeminiVisionEngine.caption_frames<br/>Gemini generateContent (inline image)<br/>-> FrameCaption[] (caption + timestamp)"]
        CaptionMerge["merge_caption_segments<br/>wrap captions as Segment(speaker=\"vision\") + merge into<br/>TranscriptionResult, ordered by timestamp"]
    end

    subgraph FaceRec["Face recognition (optional, FACE_RECOGNITION_ENABLED=true)"]
        Enroll["ai.known_faces<br/>(EnrolledFace[]: user_id, name, embedding - enrolled via<br/>POST /api/v1/faces/enroll, sourced from user-service's photoUrl)"]
        TurnFrames["extract_frames_in_range per SpeakerTurn (PyAV)<br/>-> FrameSample[] (FACE_FRAMES_PER_TURN per turn)"]
        FaceDetect["InsightFaceEngine.detect_faces (ArcFace, buffalo_l)<br/>-> DetectedFace[] (512-d embedding + det confidence), per frame"]
        FaceMatchStep["best_match: cosine similarity vs ai.known_faces<br/>majority vote per speaker across sampled frames<br/>-> FaceMatch (user_id, name, similarity) per speaker label"]
    end

    subgraph VoiceAuth["Voice authenticity (optional, VOICE_AUTHENTICITY_ENABLED=true)"]
        TurnWav["slice_wav per SpeakerTurn<br/>-> one WAV clip per speaker turn (reused from STT slicing)"]
        Heuristic["HeuristicVoiceAuthenticityAnalyzer<br/>pitch jitter (librosa.pyin) + shimmer (RMS) + spectral flatness<br/>+ pause-length regularity -> NOT a trained model, see module docstring"]
        AuthVerdict["SegmentAuthenticity (label: human/synthetic/uncertain, confidence)<br/>per speaker turn"]
    end

    subgraph Analysis["Forgetting-pattern analysis"]
        SegHist["segments[] + history[]<br/>(from learning.gap.analysis.requested,<br/>published by english-service/practice after a redo-exercise -<br/>see ../sequence/English_service/practice-redo.md)"]
        Rule["MistakeAnalyzer.analyze (ANALYSIS_SCORER)<br/>rule-based: occurrence x Ebbinghaus | scoring-engine: Java-consistent composite (app/scoring/)"]
        WeakPoints["weak_points[]<br/>{category, item_id, label, forgetting_score, recommendation}"]
    end

    subgraph Output["Output"]
        TranscriptReady["transcript.ready event<br/>{recording_id, user_id, full_text, segments[]}<br/>(no speaker_identities/voice_authenticity - see note below)"]
        GapAnalyzed["learning.gap.analyzed event<br/>{recording_id, user_id, weak_points[]}"]
        RestResp1["REST response<br/>TranscriptionResult JSON<br/>(+ speaker_identities[], voice_authenticity[] when enabled)"]
        RestResp2["REST response<br/>LearningGapAnalyzedEvent JSON"]
        AiDb[("Postgres reme_ai, schema ai<br/>known_faces / face_recognition_results /<br/>voice_authenticity_results")]
    end

    RawFile --> Convert
    MultipartUpload -."streamed in UPLOAD_CHUNK_SIZE_BYTES (1 MiB) chunks, not buffered whole".-> RawFile
    S3Obj -.download_to_tempfile.-> RawFile
    EventIn -. carries s3_bucket/s3_key .-> S3Obj

    Convert --> Diarize
    Diarize --> Slice
    Slice --> Whisper
    Whisper --> Merge

    RawFile --> FrameExtract
    FrameExtract --> GeminiVision
    Merge --> CaptionMerge
    GeminiVision --> CaptionMerge

    CaptionMerge --> TranscriptReady
    CaptionMerge --> RestResp1
    Merge -.VISION_ENABLED=false, skip Vision subgraph.-> TranscriptReady
    Merge -.VISION_ENABLED=false, skip Vision subgraph.-> RestResp1

    Diarize --> TurnFrames
    Enroll --> FaceMatchStep
    TurnFrames --> FaceDetect
    FaceDetect --> FaceMatchStep
    FaceMatchStep -.FACE_RECOGNITION_ENABLED=true.-> RestResp1
    FaceMatchStep -.FACE_RECOGNITION_ENABLED=true.-> AiDb

    Diarize --> TurnWav
    TurnWav --> Heuristic
    Heuristic --> AuthVerdict
    AuthVerdict -.VOICE_AUTHENTICITY_ENABLED=true.-> RestResp1
    AuthVerdict -.VOICE_AUTHENTICITY_ENABLED=true.-> AiDb

    SegHist --> Rule
    Rule --> WeakPoints
    WeakPoints --> GapAnalyzed
    WeakPoints --> RestResp2
```

## Data shape at each stage

| Stage | Format | Notes |
|---|---|---|
| Raw file | binary, any container `faster-whisper` can decode | via S3 download, or direct multipart upload streamed in `UPLOAD_CHUNK_SIZE_BYTES` (1 MiB) chunks (not buffered whole in memory) |
| WAV | 16kHz mono PCM | required because pyannote's soundfile backend can't read video containers directly |
| SpeakerTurn[] | `{speaker, start, end}` | no text yet; diarization now runs *before* STT |
| Per-turn WAV clip | 16kHz mono PCM, `[turn.start, turn.end)` | one per speaker turn, via `slice_wav`; removed after that turn's transcription |
| RawSegment[] + detected language (per turn) | `{text, start, end}`, `str` | `start`/`end` are relative to the clip, not the full recording; language is auto-detected (`language_code=None`) or forced (`language_code` given) |
| TranscriptionResult | `{full_text, segments: [{speaker, text, start_seconds, end_seconds, language}]}` | segment timestamps offset by their turn's `start_seconds`, then sorted by `start_seconds`; a recording with zero detected speech falls back to one whole-file turn |
| FrameSample[] | `{image_path, timestamp_seconds}` | only when `VISION_ENABLED=true`; sampled JPEGs removed after captioning |
| FrameCaption[] | `{caption, timestamp_seconds}` | Gemini vision caption per sampled frame |
| TranscriptionResult (with vision) | same shape as above, plus extra `segments` entries with `speaker == "vision"` | ordered by `start_seconds` alongside spoken-word segments |
| weak_points[] | `{category, item_id, label, forgetting_score, recommendation}` | covers all categories (grammar/vocabulary/pronunciation); only `english-service`'s `vocabulary` domain persists them today |
| EnrolledFace[] | `{user_id, name, embedding: float[512]}` | loaded from `ai.known_faces`; embedding never leaves ai-service (API responses only expose `EnrolledFaceResponse{user_id, name}`) |
| FrameSample[] (per turn) | `{image_path, timestamp_seconds}` | only when `FACE_RECOGNITION_ENABLED=true`; up to `FACE_FRAMES_PER_TURN` per speaker turn, spread across that turn's time range; removed after detection |
| DetectedFace[] | `{embedding: float[512], detection_confidence}` | one per face found in a sampled frame |
| FaceMatch (per speaker) | `{user_id, name, similarity}` | best cosine-similarity match across all of a speaker's sampled frames, only kept if >= `FACE_MATCH_SIMILARITY_THRESHOLD`; absent entirely if no enrolled face matches |
| Per-turn WAV clip (voice-auth) | 16kHz mono PCM, `[turn.start, turn.end)` | same `slice_wav` reused from STT, sliced again independently for `HeuristicVoiceAuthenticityAnalyzer` |
| SegmentAuthenticity (per turn) | `{speaker, start_seconds, end_seconds, label: human\|synthetic\|uncertain, confidence}` | heuristic, not a trained classifier - see `app/voice_auth/heuristic_analyzer.py`'s docstring for exactly which acoustic features drive the label and its known failure modes (e.g. misclassifying high-quality modern TTS) |

## Text-to-speech stage (`app/tts/`, synchronous REST)

- `POST /api/v1/tts/synthesize` `{text, lang, voice?}` -> `{audio_base64, mime_type, sample_rate}`.
  Backed by **Supertonic** (`app/tts/supertonic_engine.py`), a 99M-param ONNX model that runs on CPU
  (no GPU), 44.1kHz WAV output. Pure voice/lang resolution lives in `app/tts/base.py` (ML-free, unit
  tested); the model loads lazily on first call. Gated by `TTS_ENABLED` (default true).
- Consumed by **english-service**'s dictation "Luyện nghe với AI" section via
  `common.ai.tts.supertonic.SupertonicTtsClient` (`reme.tts.provider=supertonic`), which voices
  Gemini-suggested practice sentences. This replaces the earlier Google Cloud TTS path.

## Sentence alignment stage (`app/align/`, synchronous REST)

- `POST /api/v1/dictation/align-sentences` (multipart: `audio` file + `sentences` as a JSON-encoded
  array of strings, in script order) -> `[{start_ms, end_ms}]`, same order/length as `sentences`.
  Transcribes `audio` with word-level timestamps (`FasterWhisperEngine.transcribe_words`, distinct
  from the segment-level `transcribe`/`transcribe_auto` the STT pipeline above uses), then
  `app/align/sentence_aligner.py::align_sentences` walks the sentences in order, matching each one's
  tokens sequentially against the word timeline (cursor only moves forward, so no sentence can match
  words an earlier sentence already claimed). A sentence whose first token never appears in the
  remaining words comes back `{start_ms: null, end_ms: null}` rather than a guess.
- Pure matching logic (`sentence_aligner.py`) has no ML dependency and is unit tested directly
  (`tests/test_sentence_aligner.py`); only the Whisper transcription step needs the model.
- Consumed by **english-service**'s dictation `getClipDetail`, via
  `common.ai.align.aiservice.AiServiceSentenceAlignmentClient` (`reme.alignment.ai-service.*`), the
  first time a clip's `dictation_clip_sentences` rows are read with a null `start_ms`/`end_ms` - the
  caller reads the clip's own audio (`StorageClient`) and sends it plus the ordered sentence texts;
  whatever timings come back get persisted immediately, so a clip only ever needs aligning once.

## Where data leaves this service

- Kafka topics `transcript.ready` / `learning.gap.analyzed` -> consumed by `english-service`,
  see [../sequence/English_service/overview.md](../sequence/English_service/overview.md).
  **`transcript.ready` deliberately does not carry `speaker_identities`/`voice_authenticity`** - keeping
  these ai-service-local avoids rippling into `english-service`'s Kafka consumer contract
  (`SegmentPayload`/`TranscriptSegment`/`TranscriptMapper.xml`).
- REST responses for synchronous/manual callers - now include `speaker_identities[]`/
  `voice_authenticity[]` when the corresponding feature flag is enabled.
- **New**: `ai-service` now has its own database - Postgres `reme_ai`, schema `ai` (not the default
  `public`, per this feature's design), managed by Alembic (`ai-service/migrations/`, the Python
  equivalent of the Java services' Flyway). `known_faces` holds enrolled reference embeddings;
  `face_recognition_results`/`voice_authenticity_results` hold one row per recording per speaker
  label, keyed by `recording_id` (generated for ad-hoc `/api/v1/upload` calls, since those have no
  recordingId of their own).

## Where the two new optional stages get their input

- **Face recognition** sources its reference photo from `user-service` -
  `POST /api/v1/faces/enroll` calls `GET /api/v1/users/{userId}` (`app/clients/user_service_client.py`)
  and downloads `photoUrl`, so ai-service never stores raw user photos itself, only the derived
  embedding. See [../sequence/Ai_service/enroll-face.md](../sequence/Ai_service/enroll-face.md) and
  [../sequence/User_service/photo-upload.md](../sequence/User_service/photo-upload.md).
- **Voice authenticity** needs no external input beyond the recording's own audio.

## Where the `history[]` input comes from

`learning.gap.analysis.requested` (the event that feeds `SegHist` above) used to have no producer
anywhere in the repo. `english-service`'s `practice` package now publishes it after a learner submits
`POST /api/v1/practice/redo`: it grades each answer, maintains a durable `mistake_history` table
(`occurrence_count` + `last_seen_at` per item), and bundles the learner's full current history into
this event with `segments: []` (no transcript involved in a redo). See
[../sequence/English_service/practice-redo.md](../sequence/English_service/practice-redo.md) and
[english-service-data-flow.md](english-service-data-flow.md) for that side.
