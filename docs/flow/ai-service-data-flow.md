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
        RawFile["Raw video/audio file<br/>(mp4, mkv, mp3, ...)"]
        S3Obj["S3/MinIO object<br/>(s3_bucket + s3_key)"]
        EventIn["recording.uploaded event<br/>{recording_id, user_id, s3_bucket, s3_key, language_code}"]
    end

    subgraph STT["STT + Diarization pipeline"]
        Convert["convert_to_wav (PyAV)<br/>-> 16kHz mono PCM WAV"]
        Whisper["FasterWhisperEngine.transcribe<br/>-> RawSegment[] (text + start/end timestamps)"]
        Diarize["DiarizationEngine.diarize (pyannote)<br/>-> SpeakerTurn[] (speaker label + start/end)"]
        Merge["build_transcription_result<br/>merge STT + diarization by max time-overlap<br/>-> TranscriptionResult{full_text, segments[]}"]
    end

    subgraph Vision["Vision captioning (optional, VISION_ENABLED=true)"]
        FrameExtract["extract_frames (PyAV)<br/>-> FrameSample[] (sampled JPEG + timestamp, every VISION_FRAME_INTERVAL_SECONDS)"]
        GeminiVision["GeminiVisionEngine.caption_frames<br/>Gemini generateContent (inline image)<br/>-> FrameCaption[] (caption + timestamp)"]
        CaptionMerge["merge_caption_segments<br/>wrap captions as Segment(speaker=\"vision\") + merge into<br/>TranscriptionResult, ordered by timestamp"]
    end

    subgraph Analysis["Forgetting-pattern analysis"]
        SegHist["segments[] + history[]<br/>(mistake stats bundled by caller)"]
        Rule["RuleBasedAnalyzer.analyze<br/>frequency count + Ebbinghaus-style recency decay"]
        WeakPoints["weak_points[]<br/>{category, item_id, label, forgetting_score, recommendation}"]
    end

    subgraph Output["Output"]
        TranscriptReady["transcript.ready event<br/>{recording_id, user_id, full_text, segments[]}"]
        GapAnalyzed["learning.gap.analyzed event<br/>{recording_id, user_id, weak_points[]}"]
        RestResp1["REST response<br/>TranscriptionResult JSON"]
        RestResp2["REST response<br/>LearningGapAnalyzedEvent JSON"]
    end

    RawFile --> Convert
    S3Obj -.download_to_tempfile.-> RawFile
    EventIn -. carries s3_bucket/s3_key .-> S3Obj

    Convert --> Whisper
    Convert --> Diarize
    Whisper --> Merge
    Diarize --> Merge

    RawFile --> FrameExtract
    FrameExtract --> GeminiVision
    Merge --> CaptionMerge
    GeminiVision --> CaptionMerge

    CaptionMerge --> TranscriptReady
    CaptionMerge --> RestResp1
    Merge -.VISION_ENABLED=false, skip Vision subgraph.-> TranscriptReady
    Merge -.VISION_ENABLED=false, skip Vision subgraph.-> RestResp1

    SegHist --> Rule
    Rule --> WeakPoints
    WeakPoints --> GapAnalyzed
    WeakPoints --> RestResp2
```

## Data shape at each stage

| Stage | Format | Notes |
|---|---|---|
| Raw file | binary, any container `faster-whisper` can decode | via S3 download or direct multipart upload |
| WAV | 16kHz mono PCM | required because pyannote's soundfile backend can't read video containers directly |
| RawSegment[] | `{text, start, end}` | no speaker info yet |
| SpeakerTurn[] | `{speaker, start, end}` | no text yet |
| TranscriptionResult | `{full_text, segments: [{speaker, text, start_seconds, end_seconds}]}` | merged by max time-overlap between the two above |
| FrameSample[] | `{image_path, timestamp_seconds}` | only when `VISION_ENABLED=true`; sampled JPEGs removed after captioning |
| FrameCaption[] | `{caption, timestamp_seconds}` | Gemini vision caption per sampled frame |
| TranscriptionResult (with vision) | same shape as above, plus extra `segments` entries with `speaker == "vision"` | ordered by `start_seconds` alongside spoken-word segments |
| weak_points[] | `{category, item_id, label, forgetting_score, recommendation}` | covers all categories (grammar/vocabulary/pronunciation); only `english-service`'s `vocabulary` domain persists them today |

## Where data leaves this service

- Kafka topics `transcript.ready` / `learning.gap.analyzed` -> consumed by `english-service`,
  see [../sequence/English_service/overview.md](../sequence/English_service/overview.md).
- REST responses for synchronous/manual callers (no persistence on the ai-service side itself —
  it holds no database).
