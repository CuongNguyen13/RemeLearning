# RemeLearning API Documentation

Tài liệu tổng hợp toàn bộ API (REST + Kafka event) đang thực sự tồn tại trong codebase tại thời điểm viết
tài liệu này. Các service Java còn ở dạng skeleton (chưa có controller nào) được liệt kê ở cuối để dễ theo dõi
tiến độ.

> Nguồn: tổng hợp trực tiếp từ code (`@RestController`, FastAPI routes, `@KafkaListener`, Kafka handlers) và
> đối chiếu với `english-service/openapi.yaml`. Cập nhật tài liệu này khi thêm/sửa endpoint hoặc topic.

`vocabulary-service`, `grammar-service`, `pronunciation-service` đã được gộp thành một service duy nhất
**`english-service`** (modular monolith — mỗi domain một package con: `vocabulary/`, `grammar/`,
`pronunciation/`), vì cả ba đều chỉ phân tích các `category` khác nhau của cùng một event
`learning.gap.analyzed`. Port (8085), tên bảng/migration/mapper của domain `vocabulary` giữ nguyên; chỉ
package Java đổi (`com.remelearning.vocabulary.*` → `com.remelearning.english.vocabulary.*`).
`grammar`/`pronunciation` hiện chưa có controller/entity nào (xem mục 6).

## Mục lục

- [1. english-service (Java) — REST](#1-english-service-java--rest)
- [2. ai-service (Python) — REST](#2-ai-service-python--rest)
- [3. Kafka — english-service (consumer)](#3-kafka--english-service-consumer)
- [4. Kafka — ai-service (consumer/producer)](#4-kafka--ai-service-consumerproducer)
- [5. Bảng tổng hợp](#5-bảng-tổng-hợp)
- [6. Các service/domain chưa có API](#6-các-servicedomain-chưa-có-api)

---

## 1. `english-service` (Java) — REST

Base path: `http://localhost:8085`. Toàn bộ response bọc trong `ApiResponse<T>`
(`common/src/main/java/com/remelearning/common/response/ApiResponse.java`):

```json
{
  "success": true,
  "data": { },
  "errorCode": null,
  "message": null,
  "timestamp": "2026-07-13T10:00:00Z"
}
```

Các endpoint dưới đây đều thuộc domain `vocabulary` (package `com.remelearning.english.vocabulary`) —
domain duy nhất đã có controller tại thời điểm viết tài liệu này. Spec đầy đủ (OpenAPI):
`RemeLearning/services/english-service/openapi.yaml`. Swagger UI khi service chạy:
`http://localhost:8085/swagger-ui.html`.

### GET `/api/v1/transcripts/{recordingId}`

Trả về transcript đã lưu (nhận qua Kafka topic `transcript.ready`) của một recording, kèm các segment đã gán
speaker, sắp theo thứ tự thời gian.

- **Path param**: `recordingId` (string) — ví dụ `rec-0001`
- **Response `data`** — `TranscriptResponse`:

| Field | Type | Mô tả |
|---|---|---|
| `recordingId` | string | |
| `userId` | string | |
| `fullText` | string | Toàn bộ transcript |
| `segments` | `TranscriptSegment[]` | Danh sách segment theo `segmentOrder` |

`TranscriptSegment`:

| Field | Type | Mô tả |
|---|---|---|
| `id` | long | |
| `transcriptId` | long | |
| `speaker` | string | |
| `content` | string | |
| `startSeconds` | double | |
| `endSeconds` | double | |
| `segmentOrder` | int | |

- **Lỗi**: `404` nếu chưa có transcript cho `recordingId` → `ApiResponse{success:false, errorCode:"NOT_FOUND", message:"Transcript not found for recordingId=..."}`

### GET `/api/v1/vocabulary/weak-points/{userId}`

Danh sách các "điểm yếu" từ vựng (từ/cụm hay bị quên) của một learner, sắp giảm dần theo `forgettingScore`.

- **Path param**: `userId` (string)
- **Query param** (tùy chọn): `type` — `VocabularyType` enum: `NOUN | VERB | ADJECTIVE | ADVERB | PHRASAL_VERB | COLLOCATION | IDIOM | OTHER`
- **Response `data`** — `VocabularyWeakPoint[]`:

| Field | Type | Mô tả |
|---|---|---|
| `id` | long | |
| `recordingId` | string | |
| `userId` | string | |
| `itemId` | string | |
| `label` | string | Từ/cụm từ |
| `vocabularyType` | `VocabularyType` | Phân loại (rule-based hoặc LLM) |
| `forgettingScore` | double | Điểm suy giảm ghi nhớ (Ebbinghaus-style) |
| `recommendation` | string | Gợi ý học lại |
| `updatedAt` | Instant | |

### GET `/api/v1/vocabulary/weak-points/{userId}/grouped`

Giống endpoint trên nhưng nhóm theo `VocabularyType` (không có query param `type`).

- **Path param**: `userId` (string)
- **Response `data`**: `Map<VocabularyType, VocabularyWeakPoint[]>`, ví dụ:

```json
{
  "ADJECTIVE": [ { "...": "VocabularyWeakPoint" } ],
  "PHRASAL_VERB": [ { "...": "VocabularyWeakPoint" } ]
}
```

---

## 2. `ai-service` (Python) — REST

Base path: `http://localhost:8000` (mặc định FastAPI/uvicorn). Không có OpenAPI tĩnh check-in — Swagger tự
sinh tại `/docs` khi service chạy (`uvicorn app.main:app --reload`).

### GET `/health`

Liveness check. Response: `{"status": "ok"}`.

### POST `/api/v1/transcribe`

Chạy STT + speaker diarization đồng bộ cho một recording đã có trong S3 (bản đồng bộ của luồng Kafka
`recording.uploaded` → `transcript.ready`).

- **Request body** — `RecordingUploadedEvent`:

| Field | Type | Mô tả |
|---|---|---|
| `recording_id` | string | |
| `user_id` | string | |
| `s3_bucket` | string | |
| `s3_key` | string | |
| `language_code` | string | mặc định `"en"` |

- **Response** — `TranscriptionResult`:

| Field | Type | Mô tả |
|---|---|---|
| `full_text` | string | |
| `segments` | `Segment[]` | `speaker`, `text`, `start_seconds`, `end_seconds` |

### POST `/api/v1/upload`

Nhận trực tiếp file audio/video qua `multipart/form-data` — dùng khi chưa wire Kafka/S3.

- **Request** (multipart form):
  - `file`: `UploadFile` (bắt buộc)
  - `language_code`: string, mặc định `"en"`
- **Response**: `TranscriptionResult` (giống endpoint trên)

### POST `/api/v1/analyze`

Chạy phân tích lỗi/quên (forgetting pattern) đồng bộ (bản đồng bộ của luồng Kafka
`learning.gap.analysis.requested` → `learning.gap.analyzed`).

- **Request body** — `AnalysisRequestedEvent`:

| Field | Type | Mô tả |
|---|---|---|
| `recording_id` | string | |
| `user_id` | string | |
| `segments` | `Segment[]` | |
| `history` | `MistakeHistoryItem[]` | `item_id`, `category`, `label`, `occurrence_count`, `last_seen_days_ago` |

- **Response** — `LearningGapAnalyzedEvent`:

| Field | Type | Mô tả |
|---|---|---|
| `recording_id` | string | |
| `user_id` | string | |
| `weak_points` | `WeakPoint[]` | `item_id`, `category`, `label`, `forgetting_score`, `recommendation` |

Xử lý bằng `RuleBasedAnalyzer` (`app/analysis/rule_based_analyzer.py`), không publish Kafka.

---

## 3. Kafka — `english-service` (consumer)

Payload từ ai-service là JSON snake_case thô (pydantic `model_dump()`, không có envelope) — decode bằng
`EventCodec` (Jackson snake_case dedicated mapper), không dùng `ObjectMapper` mặc định (camelCase) của REST.
Cả hai consumer dưới đây thuộc domain `vocabulary` (package `com.remelearning.english.vocabulary.kafka`).

### `TranscriptReadyConsumer` — topic `transcript.ready`

- File: `english-service/.../vocabulary/kafka/TranscriptReadyConsumer.java`
- Payload — `TranscriptReadyEvent`: `recordingId`, `userId`, `fullText`, `segments: SegmentPayload[]`
  (`speaker`, `text`, `startSeconds`, `endSeconds`)
- Xử lý: `TranscriptService.saveTranscript(event)` — idempotent (bỏ qua nếu `recordingId` đã tồn tại, do
  Kafka at-least-once). Lỗi decode/xử lý được log, không rethrow.

### `LearningGapAnalyzedConsumer` — topic `learning.gap.analyzed`

- File: `english-service/.../vocabulary/kafka/LearningGapAnalyzedConsumer.java`
- Payload — `LearningGapAnalyzedEvent`: `recordingId`, `userId`, `weakPoints: WeakPointPayload[]`
  (`itemId`, `category`, `label`, `forgettingScore`, `recommendation`)
- Xử lý: `VocabularyWeakPointService.saveWeakPoints(event)` — chỉ giữ item có `category == "vocabulary"`
  (payload chứa cả grammar/pronunciation, các category khác bị bỏ qua vì domain đó trong english-service
  chưa có persistence riêng); phân loại `VocabularyType` qua `VocabularyClassifier` (rule-based hoặc LLM).
  Lỗi được log, không rethrow.

---

## 4. Kafka — `ai-service` (consumer/producer)

Chỉ chạy khi `KAFKA_ENABLED=true` (mặc định `false`, xem `app/config.py`).

### `handle_recording_uploaded`

- File: `ai-service/app/kafka/handlers/recording_uploaded.py`
- **Consume**: `recording.uploaded` — `RecordingUploadedEvent` (`recording_id`, `user_id`, `s3_bucket`,
  `s3_key`, `language_code`)
- **Xử lý**: tải audio từ S3 → convert WAV (PyAV) → Whisper transcription + diarization
- **Publish**: `transcript.ready` (key = `recording_id`) — `TranscriptReadyEvent` (`recording_id`, `user_id`,
  `full_text`, `segments`)

### `handle_analysis_requested`

- File: `ai-service/app/kafka/handlers/analysis_requested.py`
- **Consume**: `learning.gap.analysis.requested` — `AnalysisRequestedEvent` (`recording_id`, `user_id`,
  `segments`, `history`)
- **Xử lý**: `RuleBasedAnalyzer().analyze(...)`
- **Publish**: `learning.gap.analyzed` (key = `recording_id`) — `LearningGapAnalyzedEvent` (`recording_id`,
  `user_id`, `weak_points`)

### Danh sách topic (nguồn: `common/.../constants/KafkaTopics.java`, mirror ở `ai-service/app/kafka/topics.py`)

| Topic | Producer | Consumer | Trạng thái |
|---|---|---|---|
| `recording.uploaded` | recording-service (dự kiến) | ai-service | ⚠️ recording-service chưa có producer (skeleton) |
| `transcript.ready` | ai-service | vocabulary-service | ✅ hoạt động |
| `learning.gap.analysis.requested` | backend service (dự kiến) | ai-service | ⚠️ chưa có producer cụ thể |
| `learning.gap.analyzed` | ai-service | vocabulary-service | ✅ hoạt động |
| `pronunciation.analyzed` | english-service/pronunciation (dự kiến) | — | ⚠️ chỉ tồn tại tên hằng số, chưa có producer/consumer |
| `grammar.analyzed` | english-service/grammar (dự kiến) | — | ⚠️ chỉ tồn tại tên hằng số, chưa có producer/consumer |
| `vocabulary.analyzed` | english-service/vocabulary (dự kiến) | — | ⚠️ chỉ tồn tại tên hằng số, chưa có producer/consumer |
| `recommendation.generated` | recommendation-service (dự kiến) | — | ⚠️ chỉ tồn tại tên hằng số, chưa có producer/consumer |

---

## 5. Bảng tổng hợp

| Service | Loại | Method/Topic | Path/Tên | Ghi chú |
|---|---|---|---|---|
| english-service (vocabulary) | REST | GET | `/api/v1/transcripts/{recordingId}` | 404 nếu chưa có transcript |
| english-service (vocabulary) | REST | GET | `/api/v1/vocabulary/weak-points/{userId}` | filter `?type=` tùy chọn |
| english-service (vocabulary) | REST | GET | `/api/v1/vocabulary/weak-points/{userId}/grouped` | nhóm theo `VocabularyType` |
| ai-service | REST | GET | `/health` | liveness |
| ai-service | REST | POST | `/api/v1/transcribe` | STT đồng bộ từ S3 event |
| ai-service | REST | POST | `/api/v1/upload` | STT đồng bộ từ multipart upload |
| ai-service | REST | POST | `/api/v1/analyze` | phân tích lỗi/quên đồng bộ |
| english-service (vocabulary) | Kafka in | `transcript.ready` | `TranscriptReadyConsumer` | lưu Transcript + Segments |
| english-service (vocabulary) | Kafka in | `learning.gap.analyzed` | `LearningGapAnalyzedConsumer` | lưu weak points (chỉ category vocabulary) |
| ai-service | Kafka in/out | `recording.uploaded` → `transcript.ready` | `handle_recording_uploaded` | pipeline STT + diarization |
| ai-service | Kafka in/out | `learning.gap.analysis.requested` → `learning.gap.analyzed` | `handle_analysis_requested` | pipeline phân tích lỗi/quên |

---

## 6. Các service/domain chưa có API

Các service Java sau vẫn ở dạng skeleton (chỉ có application class + OpenAPI config), **chưa có controller,
entity hay mapper nào** — không có gì để document:

- `bff-service` (8080) — có `WebClientConfig`/`DownstreamServicesProperties` nhưng chưa có route/gateway logic
- `user-service` (8081)
- `recording-service` (8082)
- `recommendation-service` (8086)
- `dashboard-service` (8087)

Trong `english-service` (8085), hai domain sau cũng chưa có gì ngoài `package-info.java` placeholder
(`com.remelearning.english.grammar`, `com.remelearning.english.pronunciation`) — chưa có controller,
entity hay mapper nào:

- `grammar` — grammar error detection via LLM analysis of transcripts
- `pronunciation` — pronunciation scoring and phoneme-level error detection

Khi một trong các domain/service trên được triển khai, nên theo mẫu của domain `vocabulary` trong
`english-service` (xem `CLAUDE.md` — mục "english-service (Java) — modular monolith, reference
implementation") và bổ sung phần tương ứng vào tài liệu này.
