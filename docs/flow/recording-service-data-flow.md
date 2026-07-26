# recording-service — Data Flow

Focuses on **what happens to the data** (transformations, formats, storage) as it moves through
`recording-service`, as opposed to the sequence diagrams in
[../sequence/Recording_service/](../sequence/Recording_service/) which focus on call order between
components.

```mermaid
---
config:
  theme: base
  themeVariables:
    background: '#ffffff'
---
flowchart TD
    subgraph Input["Input (client upload)"]
        MultipartReq["multipart/form-data<br/>{file, userId, languageCode?}"]
    end

    subgraph UploadFlow["Upload"]
        Validate{"userId blank<br/>or file empty?"}
        Reject["400 VALIDATION_ERROR<br/>(BusinessException.badRequest)"]
        GenId["generate recordingId (UUID)"]
        AudioCheck{"content-type<br/>starts with audio/?"}
        Transcode["AudioTranscodeClient.toOpus(...)<br/>(-> ai-service POST /api/v1/audio/transcode/opus)<br/>s3Key = userId/recordingId/basename.opus"]
        NoTranscode["s3Key = userId/recordingId/originalFilename<br/>(video kept as-is - vision/face-recognition need the video track)"]
        S3Put["S3StorageClient.upload(bucket, key, stream, length)"]
        S3Fail{"S3 upload<br/>fails?"}
        S3Error["502 EXTERNAL_SERVICE_ERROR"]
        Insert["RecordingMapper.insert<br/>(status = UPLOADED)"]
        BuildEvent["build RecordingUploadedEvent<br/>{recordingId, userId, s3Bucket, s3Key, languageCode}"]
        Publish["EventCodec.writeValueAsString<br/>-> snake_case JSON -> Kafka recording.uploaded"]
    end

    subgraph Storage["Storage"]
        S3[("S3 / MinIO bucket<br/>reme.s3.recording-bucket")]
        DB[("reme_recording DB<br/>recordings table")]
    end

    subgraph Output["Output"]
        KafkaTopic["recording.uploaded event<br/>(consumed by ai-service)"]
        RecordingResp["RecordingResponse<br/>{recordingId, userId, status, s3Bucket, s3Key, createdAt}"]
    end

    subgraph ReadOut["Read-out (REST)"]
        GetById["GET /api/v1/recordings/{recordingId}<br/>-> RecordingResponse"]
        GetByUser["GET /api/v1/recordings/user/{userId}<br/>-> List[RecordingResponse]"]
    end

    MultipartReq --> Validate
    Validate -->|yes| Reject
    Validate -->|no| GenId --> AudioCheck
    AudioCheck -->|yes| Transcode --> S3Put
    AudioCheck -->|no| NoTranscode --> S3Put
    S3Put --> S3Fail
    S3Fail -->|yes| S3Error
    S3Fail -->|no| Insert
    S3Put --> S3
    Insert --> DB
    Insert --> BuildEvent --> Publish --> KafkaTopic
    Insert --> RecordingResp

    DB --> GetById
    DB --> GetByUser
```

## Data shape at each stage

| Stage | Format | Notes |
|---|---|---|
| Multipart request | `{file (binary), userId, languageCode?}` | `languageCode` defaults to `"en"` when absent/blank |
| S3 object | audio-only uploads (`content-type` starts with `audio/`): transcoded to Ogg/Opus via `AudioTranscodeClient`, key = `{userId}/{recordingId}/{basename}.opus`, `content_type = audio/ogg`; video uploads: raw file bytes as-is, key = `{userId}/{recordingId}/{originalFilename}` (video kept untouched since `ai-service`'s vision/face-recognition steps need the original video track) | bucket = `reme.s3.recording-bucket` (env `S3_RECORDING_BUCKET`, default `reme-recordings`); pre-existing mp3/wav/video objects from before this transcode step was added are never re-processed |
| `recordings` row | `{id, recording_id, user_id, s3_bucket, s3_key, language_code, original_filename, content_type, status, created_at}` | `status` fixed to `UPLOADED` on insert; no update path today |
| `RecordingUploadedEvent` | `{recordingId, userId, s3Bucket, s3Key, languageCode}` (Java, camelCase) | serialized via `EventCodec` (snake_case `ObjectMapper`) before publishing |
| `recording.uploaded` Kafka payload | `{recording_id, user_id, s3_bucket, s3_key, language_code}` (snake_case JSON) | matches `ai-service`'s pydantic `RecordingUploadedEvent` exactly, no envelope |
| `RecordingResponse` | `{recordingId, userId, status, s3Bucket, s3Key, createdAt}` | returned by all three REST endpoints; omits `originalFilename`/`contentType` |

## Where data comes from / where it can go next

- Input is a direct client multipart upload — no upstream Kafka event feeds this service.
- `recording.uploaded` is consumed by `ai-service`, which downloads the file from S3 and runs
  STT + diarization — see [ai-service-data-flow.md](ai-service-data-flow.md) and
  [../sequence/Ai_service/overview.md](../sequence/Ai_service/overview.md) for what happens next.
- `recording_id` (the UUID generated here) is the correlation key threaded through the rest of the
  pipeline: `ai-service`'s `transcript.ready`/`learning.gap.analyzed` events and `english-service`'s
  `transcripts`/`*_weak_points` tables all key off the same value.
