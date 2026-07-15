# recording-service

Entry point of the analysis pipeline: accepts an uploaded recording (audio/video), stores it in S3
(MinIO locally), persists metadata, and publishes `recording.uploaded` for `ai-service` to pick up
(STT + speaker diarization).

- Port: **8082**
- Database: `reme_recording`

## Endpoints

Full spec: [`openapi.yaml`](openapi.yaml) / `/swagger-ui.html` when running. Details + JSON shapes:
[`docs/API.md` §3](../../../docs/API.md#3-recording-service-java--rest).

| Method | Path | Notes |
|---|---|---|
| POST | `/api/v1/recordings` | multipart (`file`, `userId`, optional `languageCode`) → S3 upload + publish `recording.uploaded` |
| GET | `/api/v1/recordings/{recordingId}` | `404` if not found |
| GET | `/api/v1/recordings/user/{userId}` | list of recordings for a user |

## Kafka

Produces `recording.uploaded` (see [`docs/API.md` §9](../../../docs/API.md#9-kafka--recording-service-producer))
— serialized as flat snake_case JSON (no envelope) via `common.event.EventCodec`, matching the plain
pydantic model `ai-service` expects. Deliberately bypasses `common.queue.EventPublisher`/`BaseEvent`,
since that envelope is for Java-to-Java events only.

## Run locally

```bash
cd RemeLearning
./mvnw -pl services/recording-service -am spring-boot:run
./mvnw -pl services/recording-service -am test
```

Needs S3/MinIO reachable at `reme.s3.endpoint` (defaults to `http://localhost:9000`, matches the
`docker-compose.yml` MinIO block) and a bucket named by `reme.s3.recording-bucket` (default
`reme-recordings`) — nothing in this repo auto-creates the bucket, provision it once yourself.
