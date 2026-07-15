# RemeLearning API Documentation

Tài liệu tổng hợp toàn bộ API (REST + Kafka event) đang thực sự tồn tại trong codebase tại thời điểm viết
tài liệu này. Các service Java còn ở dạng skeleton (chưa có controller nào) được liệt kê ở cuối để dễ theo dõi
tiến độ.

> Nguồn: tổng hợp trực tiếp từ code (`@RestController`, FastAPI routes, `@KafkaListener`, Kafka handlers) và
> đối chiếu với từng `openapi.yaml` theo service. Cập nhật tài liệu này khi thêm/sửa endpoint hoặc topic.

`vocabulary-service`, `grammar-service`, `pronunciation-service` đã được gộp thành một service duy nhất
**`english-service`** (modular monolith — mỗi domain một package con: `vocabulary/`, `grammar/`,
`pronunciation/`), vì cả ba đều chỉ phân tích các `category` khác nhau của cùng một event
`learning.gap.analyzed`. Port (8085), tên bảng/migration/mapper của domain `vocabulary` giữ nguyên; chỉ
package Java đổi (`com.remelearning.vocabulary.*` → `com.remelearning.english.vocabulary.*`).
Cả ba domain (`vocabulary`, `grammar`, `pronunciation`) nay đều có controller/entity/mapper riêng (xem
mục 1, 6); chỉ `vocabulary` sở hữu `TranscriptReadyConsumer`/`TranscriptService` — bảng `transcripts`/
`transcript_segments` là dữ liệu dùng chung, `grammar`/`pronunciation` chỉ đọc lại qua
`GET /api/v1/transcripts/{recordingId}`, không tự ingest lần hai.

`english-service` nay có thêm package thứ tư, **`practice`** (redo-exercise), cắt ngang cả 3 domain thay vì
thuộc riêng domain nào: chấm điểm các câu trả lời khi learner làm lại bài tập, duy trì `mistake_history`
(số lần sai + lần gần nhất) cho từng item, rồi publish `learning.gap.analysis.requested` để `ai-service`
tính lại `forgettingScore` — đây chính là producer còn thiếu được nhắc ở mục 14 (bản cũ). Xem mục 1, 8.

`recording-service`, `recommendation-service`, `dashboard-service` nay cũng đã có API/Kafka thật (trước đây
chỉ là skeleton) — hoàn thiện đầu và cuối của pipeline: `recording-service` là điểm vào (upload → S3 →
`recording.uploaded`), `recommendation-service`/`dashboard-service` là điểm ra (tổng hợp `learning.gap.analyzed`
thành gợi ý học tập và một dashboard tổng hợp), toàn bộ theo hướng event-driven giữa các service backend.

`user-service` (8081) nay cũng có API thật — đăng ký/đăng nhập (phát hành JWT thật bằng
`common.security.JwtTokenProvider`, lần đầu tiên class này thực sự được gọi) và profile cơ bản. **Lưu ý
quan trọng**: user-service chỉ *phát hành* token — chưa có service nào (kể cả `bff-service`) *xác thực*
JWT trên request, toàn repo vẫn chưa có `SecurityConfig`/Spring Security nào cả (xem `CLAUDE.md`). Coi
như chưa có auth thật cho tới khi có filter xác thực được thêm vào.

`bff-service` (8080, reactive/WebFlux, không có DB) là điểm vào REST duy nhất dành cho client
web/mobile — compose (fan-out song song qua `WebClient`) các domain service phía trên và gộp thành response
theo hình dạng UI cần, thay vì client phải tự gọi từng service. Nay đã dùng tới `userServiceClient` (proxy
đăng ký/đăng nhập/profile, và gộp thêm profile vào `GET /api/v1/learners/{userId}/overview`).

## Mục lục

- [1. english-service (Java) — REST](#1-english-service-java--rest)
- [2. user-service (Java) — REST](#2-user-service-java--rest)
- [3. recording-service (Java) — REST](#3-recording-service-java--rest)
- [4. recommendation-service (Java) — REST](#4-recommendation-service-java--rest)
- [5. dashboard-service (Java) — REST](#5-dashboard-service-java--rest)
- [6. bff-service (Java) — REST](#6-bff-service-java--rest)
- [7. ai-service (Python) — REST](#7-ai-service-python--rest)
- [8. Kafka — english-service (consumer/producer)](#8-kafka--english-service-consumerproducer)
- [9. Kafka — recording-service (producer)](#9-kafka--recording-service-producer)
- [10. Kafka — recommendation-service (consumer/producer)](#10-kafka--recommendation-service-consumerproducer)
- [11. Kafka — dashboard-service (consumer)](#11-kafka--dashboard-service-consumer)
- [12. Kafka — ai-service (consumer/producer)](#12-kafka--ai-service-consumerproducer)
- [13. Bảng tổng hợp](#13-bảng-tổng-hợp)
- [14. Các service/domain chưa có API](#14-các-servicedomain-chưa-có-api)

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

Spec đầy đủ (OpenAPI): `RemeLearning/services/english-service/openapi.yaml`. Swagger UI khi service chạy:
`http://localhost:8085/swagger-ui.html`.

### Transcripts (shared, package `vocabulary`)

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
| `language` | string | Ngôn ngữ ai-service dùng để transcribe segment này (auto-detect hoặc ép theo `language_code`, xem mục 7) |

- **Lỗi**: `404` nếu chưa có transcript cho `recordingId` → `ApiResponse{success:false, errorCode:"NOT_FOUND", message:"Transcript not found for recordingId=..."}`

### Vocabulary weak points (package `vocabulary`)

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

### Grammar weak points (package `grammar`)

### GET `/api/v1/grammar/weak-points/{userId}`

Danh sách các lỗi ngữ pháp lặp lại/hay quên của một learner, sắp giảm dần theo `forgettingScore`.

- **Path param**: `userId` (string)
- **Query param** (tùy chọn): `type` — `GrammarType` enum: `TENSE | SUBJECT_VERB_AGREEMENT | ARTICLE | PREPOSITION | WORD_ORDER | PLURAL | PUNCTUATION | OTHER`
- **Response `data`** — `GrammarWeakPoint[]`:

| Field | Type | Mô tả |
|---|---|---|
| `id` | long | |
| `recordingId` | string | |
| `userId` | string | |
| `itemId` | string | |
| `label` | string | Mô tả lỗi ngữ pháp |
| `grammarType` | `GrammarType` | Phân loại (rule-based hoặc LLM) |
| `forgettingScore` | double | Điểm suy giảm ghi nhớ (Ebbinghaus-style) |
| `recommendation` | string | Gợi ý học lại |
| `updatedAt` | Instant | |

### GET `/api/v1/grammar/weak-points/{userId}/grouped`

Giống endpoint trên nhưng nhóm theo `GrammarType` (không có query param `type`).

- **Path param**: `userId` (string)
- **Response `data`**: `Map<GrammarType, GrammarWeakPoint[]>`

### Pronunciation weak points (package `pronunciation`)

### GET `/api/v1/pronunciation/weak-points/{userId}`

Danh sách các lỗi phát âm lặp lại/hay quên của một learner, sắp giảm dần theo `forgettingScore`.

- **Path param**: `userId` (string)
- **Query param** (tùy chọn): `type` — `PronunciationType` enum: `VOWEL | CONSONANT | STRESS | INTONATION | LINKING | RHYTHM | OTHER`
- **Response `data`** — `PronunciationWeakPoint[]`:

| Field | Type | Mô tả |
|---|---|---|
| `id` | long | |
| `recordingId` | string | |
| `userId` | string | |
| `itemId` | string | |
| `label` | string | Mô tả lỗi phát âm |
| `pronunciationType` | `PronunciationType` | Phân loại (rule-based hoặc LLM) |
| `forgettingScore` | double | Điểm suy giảm ghi nhớ (Ebbinghaus-style) |
| `recommendation` | string | Gợi ý luyện lại |
| `updatedAt` | Instant | |

### GET `/api/v1/pronunciation/weak-points/{userId}/grouped`

Giống endpoint trên nhưng nhóm theo `PronunciationType` (không có query param `type`).

- **Path param**: `userId` (string)
- **Response `data`**: `Map<PronunciationType, PronunciationWeakPoint[]>`

### Practice / redo-exercise (package `practice`)

### POST `/api/v1/practice/redo`

Nhận kết quả chấm điểm của một lần learner **làm lại bài tập** (redo exercise) — các câu hỏi có thể trộn
cả 3 domain (vocabulary/grammar/pronunciation) trong cùng một lần submit.

- **Request body** — `PracticeRedoRequest`:

| Field | Type | Mô tả |
|---|---|---|
| `userId` | string | bắt buộc |
| `attempts` | `PracticeAttemptRequest[]` | bắt buộc, ít nhất 1 phần tử |

`PracticeAttemptRequest`: `itemId` (string, bắt buộc), `category` (string, bắt buộc — `"vocabulary" |
"grammar" | "pronunciation"`, phải khớp category gốc của item), `label` (string, bắt buộc), `correct`
(boolean).

- **Xử lý** (`PracticeServiceImpl`):
  1. Với mỗi attempt: ghi log vào `practice_attempts` (audit, không đọc lại), rồi upsert
     `mistake_history` (khóa `(userId, itemId)`) — `occurrenceCount` chỉ tăng khi `correct=false`,
     `lastSeenAt` luôn được refresh về `now()`.
  2. Sau khi xử lý xong toàn bộ batch, đọc lại **toàn bộ** `mistake_history` hiện tại của `userId` (không
     chỉ các item vừa submit), build một `AnalysisRequestedEvent` (`recordingId` sinh ngẫu nhiên dạng
     `"practice-<uuid>"`, `segments` rỗng vì không có transcript) rồi publish `learning.gap.analysis.requested`
     (mục 8) — yêu cầu `ai-service` (mục 7, 12) tính lại `forgettingScore` bằng `RuleBasedAnalyzer` như cũ.
  3. `ai-service` publish lại `learning.gap.analyzed`, được 5 consumer hiện có (mục 8, 10, 11) tự động
     upsert lại — vòng lặp "chấm điểm → đánh giá độ quên → đề xuất lại" khép kín mà không cần sửa
     `ai-service` hay các consumer sẵn có.
- **Response `data`**: `null` (chỉ trả `success: true`).

`mistake_history` được seed lần đầu (occurrenceCount=1) bởi `MistakeHistorySeedConsumer` (mục 8) ngay khi
một item lần đầu xuất hiện trong `learning.gap.analyzed`, kể cả trước khi learner từng redo — nên
`GET .../weak-points` (đã sắp theo `forgettingScore`) luôn có sẵn dữ liệu để chọn ra bộ câu hỏi cần làm lại.

---

## 2. `user-service` (Java) — REST

Base path: `http://localhost:8081`. Response wrapper giống hệt mục 1 (`ApiResponse<T>`). Spec đầy đủ:
`RemeLearning/services/user-service/openapi.yaml`. **Chưa có enforcement**: các endpoint dưới đây phát
hành JWT thật nhưng không endpoint nào (ở service này hay service khác) kiểm tra token — xem lưu ý ở
đầu tài liệu.

### POST `/api/v1/auth/register`

Đăng ký user mới.

- **Request body** — `RegisterRequest`: `email` (bắt buộc, định dạng email), `password` (bắt buộc, tối
  thiểu 8 ký tự), `name` (bắt buộc).
- **Xử lý**: kiểm tra email trùng, hash password (BCrypt), sinh `userId` (UUID), insert bảng `users`
  (role mặc định `LEARNER`), phát hành JWT qua `common.security.JwtTokenProvider`.
- **Response `data`** — `AuthResponse`:

| Field | Type | Mô tả |
|---|---|---|
| `token` | string | JWT ký HMAC, hết hạn sau `reme.jwt.expiration-minutes` (mặc định 60 phút) |
| `user` | `UserResponse` | `userId, email, name, role, createdAt` |

- **Lỗi**: `409` nếu email đã tồn tại.

### POST `/api/v1/auth/login`

- **Request body** — `LoginRequest`: `email`, `password`.
- **Response `data`**: `AuthResponse` (giống hệt register).
- **Lỗi**: `401` nếu email không tồn tại HOẶC sai password — **cùng một thông báo lỗi cho cả hai
  trường hợp** (không tiết lộ email có tồn tại hay không, đây là chủ đích bảo mật chứ không phải thiếu sót).

### GET `/api/v1/users/{userId}`

Lấy profile theo `userId` (nay có thêm `photoUrl`). **Lỗi**: `404` nếu không tồn tại.

### PATCH `/api/v1/users/{userId}`

Cập nhật `name`. **Request body** — `UpdateProfileRequest`: `name` (bắt buộc). **Lỗi**: `404` nếu
`userId` không tồn tại.

### POST `/api/v1/users/{userId}/photo`

Upload/thay thế ảnh đại diện của một user (`multipart/form-data`) — đây là nguồn ảnh mà `ai-service`
(mục 7) fetch về để enroll khuôn mặt tham chiếu cho tính năng nhận diện người nói.

- **Path param**: `userId` (string)
- **Request** (multipart form): `file` (bắt buộc)
- **Xử lý**: lưu file vào S3 tại key `"{userId}/photo/{originalFilename}"` (bucket cấu hình qua
  `reme.s3.user-photo-bucket`, mặc định `reme-user-photos`), cập nhật `photo_s3_key`/`photo_url` trên
  bảng `users` (migration `V2__user_photo.sql`).
- **Response `data`**: `UserResponse` (kèm `photoUrl` mới).
- **Lỗi**: `400` nếu file rỗng/thiếu, `404` nếu `userId` không tồn tại, `502` nếu upload S3 lỗi.

---

## 3. `recording-service` (Java) — REST

Base path: `http://localhost:8082`. Response wrapper giống hệt mục 1 (`ApiResponse<T>`). Spec đầy đủ:
`RemeLearning/services/recording-service/openapi.yaml`. Đây là điểm vào của toàn bộ pipeline: nhận file ghi âm/
video, lưu lên S3 (MinIO cục bộ), lưu metadata, publish `recording.uploaded` cho `ai-service` (xem mục 7).

### POST `/api/v1/recordings`

Upload một file ghi âm/video (`multipart/form-data`).

- **Request** (multipart form): `file` (bắt buộc), `userId` (bắt buộc), `languageCode` (tùy chọn, mặc định `"en"`)
- **Xử lý**: sinh `recordingId` (UUID), lưu file vào S3 tại key `"{userId}/{recordingId}/{originalFilename}"`
  (bucket cấu hình qua `reme.s3.recording-bucket`, mặc định `reme-recordings`), insert bảng `recordings`
  (status `UPLOADED`), publish Kafka `recording.uploaded`.
- **Response `data`** — `RecordingResponse`:

| Field | Type | Mô tả |
|---|---|---|
| `recordingId` | string | UUID sinh bởi service |
| `userId` | string | |
| `status` | string | `UPLOADED` |
| `s3Bucket` | string | |
| `s3Key` | string | |
| `createdAt` | Instant | |

- **Lỗi**: `400` nếu `userId` rỗng/thiếu hoặc file rỗng.

### GET `/api/v1/recordings/{recordingId}`

Lấy metadata một recording theo id. **Lỗi**: `404` nếu không tồn tại.

### GET `/api/v1/recordings/user/{userId}`

Danh sách tất cả recording của một user — `RecordingResponse[]`.

---

## 4. `recommendation-service` (Java) — REST

Base path: `http://localhost:8086`. Response wrapper giống mục 1. Spec đầy đủ:
`RemeLearning/services/recommendation-service/openapi.yaml`. Service này consume `learning.gap.analyzed`
(mục 8) không lọc theo category (khác `english-service`, vốn mỗi domain chỉ giữ category của mình) — mọi
weak point từ mọi domain (vocabulary/grammar/pronunciation) đều được lưu thành một "recommendation".

### GET `/api/v1/recommendations/{userId}`

Danh sách gợi ý học tập của một learner, sắp giảm dần theo `forgettingScore`.

- **Path param**: `userId` (string)
- **Query param** (tùy chọn): `category` — `"vocabulary" | "grammar" | "pronunciation"` (string thô, không phải enum riêng — giá trị đến trực tiếp từ `WeakPointPayload.category` của `learning.gap.analyzed`)
- **Response `data`** — `Recommendation[]`:

| Field | Type | Mô tả |
|---|---|---|
| `id` | long | |
| `userId` | string | |
| `recordingId` | string | recording gần nhất sinh ra gợi ý này |
| `itemId` | string | |
| `category` | string | `vocabulary`/`grammar`/`pronunciation` |
| `label` | string | |
| `forgettingScore` | double | |
| `recommendationText` | string | |
| `updatedAt` | Instant | |

### GET `/api/v1/recommendations/{userId}/grouped`

Giống endpoint trên nhưng nhóm theo `category` — `Map<String, Recommendation[]>`.

---

## 5. `dashboard-service` (Java) — REST

Base path: `http://localhost:8087`. Response wrapper giống mục 1. Spec đầy đủ:
`RemeLearning/services/dashboard-service/openapi.yaml`. Service này KHÔNG gọi REST sang service khác — toàn
bộ dữ liệu đến từ hai Kafka topic nó tự consume (`learning.gap.analyzed`, `recommendation.generated` — mục 9).

### GET `/api/v1/dashboard/{userId}`

Tổng hợp tiến độ học tập của một learner trên cả 3 domain + các gợi ý gần nhất.

- **Path param**: `userId` (string)
- **Response `data`** — `DashboardSummaryResponse`:

| Field | Type | Mô tả |
|---|---|---|
| `userId` | string | |
| `categoryProgress` | `CategoryProgress[]` | Tính tại thời điểm đọc (SQL `GROUP BY category`), không phải counter duy trì sẵn |
| `recentRecommendations` | `RecommendationSnapshot[]` | Top 10 theo `receivedAt` giảm dần |

`CategoryProgress`:

| Field | Type | Mô tả |
|---|---|---|
| `category` | string | |
| `weakPointCount` | long | `COUNT(*)` từ `weak_points_snapshot` |
| `avgForgettingScore` | double | `AVG(forgetting_score)` |
| `lastUpdated` | Instant | `MAX(updated_at)` |

`RecommendationSnapshot`:

| Field | Type | Mô tả |
|---|---|---|
| `userId` | string | |
| `itemId` | string | |
| `category` | string | |
| `label` | string | |
| `recommendationText` | string | |
| `forgettingScore` | double | |
| `receivedAt` | Instant | |

---

## 6. `bff-service` (Java) — REST

Base path: `http://localhost:8080`. Reactive (WebFlux) — mọi endpoint trả `Mono<ApiResponse<T>>`, response
wrapper giống hệt mục 1 (`ApiResponse<T>`, tái dùng thẳng class trong `common`). Spec đầy đủ:
`RemeLearning/services/bff-service/openapi.yaml`. bff-service không có DB, không tự lưu trạng thái — mọi
endpoint bên dưới gọi sang service khác qua `WebClient` (cấu hình base URL ở `reme.services.*`,
`WebClientConfig`/`DownstreamServicesProperties`). Với các endpoint fan-out (gọi song song nhiều service),
mỗi nhánh gọi được bọc `.onErrorResume(...)` — nếu một downstream service down, response vẫn trả về đầy đủ
với phần dữ liệu của nhánh lỗi để rỗng/mặc định thay vì làm hỏng toàn bộ request.

### POST `/api/v1/auth/register` và POST `/api/v1/auth/login`

Proxy thuần túy sang `POST /api/v1/auth/register`/`login` của `user-service` (mục 2) — client chỉ cần
biết một base URL (8080) cho toàn bộ luồng auth. Request/response body giống hệt user-service
(`RegisterRequest`/`LoginRequest` → `AuthResponse`). bff-service không tự validate lại (validation xảy
ra ở user-service).

### GET `/api/v1/users/{userId}` và PATCH `/api/v1/users/{userId}`

Proxy thuần túy sang `user-service` (mục 2) — cùng field, cùng lỗi (404).

### POST `/api/v1/recordings`

Proxy thuần túy (multipart streaming, không buffer file trong bff-service) sang `POST /api/v1/recordings`
của `recording-service` (mục 3) — client chỉ cần biết một base URL (8080) cho toàn bộ luồng upload.

- **Request** (multipart form): `file`, `userId`, `languageCode` (tùy chọn) — giống hệt recording-service.
- **Response `data`**: `RecordingDto` — giống `RecordingResponse` của recording-service.

### GET `/api/v1/learners/{userId}/overview`

Composite: fan-out song song 3 chiều sang `user-service` (mục 2, profile), `GET /api/v1/dashboard/{userId}`
(dashboard-service, mục 5), và `GET /api/v1/recordings/user/{userId}` (recording-service, mục 3), gộp
thành một response cho màn hình tổng quan của learner. Đây là lần đầu tiên `userServiceClient` (tồn tại
từ lúc bff-service còn là skeleton) được dùng thật.

- **Response `data`** — `LearnerOverviewResponse`:

| Field | Type | Mô tả |
|---|---|---|
| `userId` | string | |
| `user` | `UserDto` (nullable) | profile từ user-service; `null` nếu user-service down — không làm hỏng cả response |
| `categoryProgress` | `CategoryProgressDto[]` | từ dashboard-service |
| `recentRecommendations` | `RecommendationSnapshotDto[]` | từ dashboard-service |
| `recentRecordings` | `RecordingDto[]` | từ recording-service |

### GET `/api/v1/learners/{userId}/weak-points`

Composite: fan-out song song sang cả 3 endpoint weak-points của `english-service` (mục 1) —
`/api/v1/vocabulary/weak-points/{userId}`, `/grammar/...`, `/pronunciation/...` — gộp theo `category` thành
một view duy nhất (english-service không tự gộp giữa 3 domain của chính nó; đây là nơi đầu tiên có view đó).

- **Response `data`**: `Map<string category, WeakPointDto[]>` — key là `"vocabulary"`/`"grammar"`/`"pronunciation"`.
  `WeakPointDto`: `itemId, label, category, forgettingScore, recommendation` (`category` được bff-service tự
  gán theo endpoint nguồn, vì response gốc của english-service không có field này).

### GET `/api/v1/learners/{userId}/recommendations`

Proxy thuần túy (không cần gộp, recommendation-service đã group sẵn) sang
`GET /api/v1/recommendations/{userId}/grouped` (recommendation-service, mục 4).

- **Response `data`**: `Map<string category, RecommendationDto[]>`.

### GET `/api/v1/learners/{userId}/practice/next`

Bộ "bài tập cần làm lại": gộp 3 endpoint weak-points của english-service (giống
`GET .../weak-points` ở trên, tái dùng cùng `WeakPointAggregationService`), rồi sắp giảm dần theo
`forgettingScore` trên toàn bộ 3 domain và lấy top `limit` — đây là danh sách item học lại có mức độ
quên cao nhất, dùng để dựng bài tập redo cho learner.

- **Query param** (tùy chọn): `limit` — mặc định `10`
- **Response `data`**: `WeakPointDto[]` (giống cấu trúc mục trên, đã gộp `category`), sắp giảm dần theo
  `forgettingScore`.

### POST `/api/v1/learners/{userId}/practice/redo`

Proxy thuần túy sang `POST /api/v1/practice/redo` của `english-service` (mục 1) — client chỉ cần biết
một base URL (8080) cho toàn bộ luồng làm lại bài tập. `userId` trong path được gán đè lên body trước khi
proxy, nên client không cần lặp lại nó trong `PracticeRedoRequestDto`.

- **Request body** — `PracticeRedoRequestDto`: `attempts: PracticeAttemptDto[]` (`itemId`, `category`,
  `label`, `correct`) — giống hệt `PracticeRedoRequest` của english-service.
- **Response `data`**: `null`.

---

## 7. `ai-service` (Python) — REST

Base path: `http://localhost:8000` (mặc định FastAPI/uvicorn). Không có OpenAPI tĩnh check-in — Swagger tự
sinh tại `/docs` khi service chạy (`uvicorn app.main:app --reload`).

`ai-service` nay có DB riêng lần đầu tiên: Postgres `reme_ai`, schema `ai` (không dùng schema mặc định
`public`, theo đúng convention của feature này) — SQLAlchemy + Alembic (`ai-service/migrations/`), tương
đương Flyway ở phía Java. Ba bảng: `known_faces` (embedding khuôn mặt đã enroll), `face_recognition_results`,
`voice_authenticity_results` (kết quả theo từng recording, xem chi tiết ở
[flow/ai-service-data-flow.md](flow/ai-service-data-flow.md)).

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
| `language_code` | string \| null | mặc định `"en"`. `null`/bỏ trống = tự động nhận diện ngôn ngữ theo từng speaker turn (xem bên dưới) |

- **Response** — `TranscriptionResult`:

| Field | Type | Mô tả |
|---|---|---|
| `full_text` | string | |
| `segments` | `Segment[]` | `speaker`, `text`, `start_seconds`, `end_seconds`, `language` |
| `recording_id` | string \| null | echo lại `recording_id` của request |
| `speaker_identities` | `SpeakerIdentity[]` | rỗng trừ khi `FACE_RECOGNITION_ENABLED=true`; xem bên dưới |
| `voice_authenticity` | `SegmentAuthenticity[]` | rỗng trừ khi `VOICE_AUTHENTICITY_ENABLED=true`; xem bên dưới |

Diarize (`DiarizationEngine.diarize`) chạy **trước**, sau đó mỗi speaker turn được cắt riêng
(`slice_wav`) và transcribe **song song** qua `ThreadPoolExecutor`
(`app/stt/pipeline.py::transcribe_turns_multilingual`, số worker cấu hình qua
`STT_MAX_CONCURRENT_TRANSCRIPTIONS`, mặc định 4). Nếu `language_code` là `null`/bỏ trống, mỗi turn tự
động nhận diện ngôn ngữ riêng (`FasterWhisperEngine.transcribe_auto`) — nhờ vậy một recording có nhiều
speaker nói các ngôn ngữ khác nhau vẫn được phân tích đúng cho từng người, thay vì ép toàn bộ file theo
một ngôn ngữ duy nhất. Nếu truyền `language_code` cụ thể, ngôn ngữ đó được ép dùng cho mọi turn (bỏ qua
bước tự nhận diện, vẫn chạy song song). Nếu diarization không phát hiện speech nào, toàn bộ file được
xử lý như một turn duy nhất.

Khi `VISION_ENABLED=true` (mặc định `false`, xem `app/config.py`), sau STT + diarization, service còn
sample frame từ video gốc mỗi `VISION_FRAME_INTERVAL_SECONDS` giây (mặc định 10s, `app/vision/
frame_extractor.py`, dùng PyAV) và gọi Gemini `generateContent` (image inline, `app/vision/
gemini_vision_engine.py`) để sinh caption mô tả nội dung hình ảnh (vật thể, hành động, chữ xuất hiện
trên màn hình). Mỗi caption được gộp vào `segments` như một `Segment` với `speaker == "vision"`
(`app/vision/pipeline.py`), theo thứ tự `start_seconds`, để `RuleBasedAnalyzer`/`MistakeAnalyzer` phân
tích từ vựng hay quên có thể tham chiếu cả nội dung nói lẫn nội dung hình ảnh xuất hiện trong video.
Cấu hình Gemini vision qua `GEMINI_API_KEY` / `GEMINI_VISION_MODEL` (mặc định `gemini-2.0-flash`).

Khi `FACE_RECOGNITION_ENABLED=true` (mặc định `false`), sau diarization, mỗi `SpeakerTurn` được lấy
mẫu vài frame video (`FACE_FRAMES_PER_TURN`, `app/vision/frame_extractor.py::extract_frames_in_range`),
detect khuôn mặt + tính embedding ArcFace (`app/face/insightface_engine.py`, model `buffalo_l` qua
`insightface`/`onnxruntime`), so khớp (cosine similarity, ngưỡng `FACE_MATCH_SIMILARITY_THRESHOLD`,
mặc định 0.45) với danh sách khuôn mặt đã enroll (`app/face/enrollment.py`, bảng `ai.known_faces`) —
kết quả (`speaker_identities`) vừa được trả về trong response vừa lưu vào `ai.face_recognition_results`.
**Lưu ý về độ chính xác**: đây là "khuôn mặt nào xuất hiện nhiều nhất khi SPEAKER_XX đang nói", KHÔNG
phải active-speaker detection thật (không phân tích cử động môi) — hoạt động tốt với layout
single-face/speaker-view, yếu hơn với layout gallery-view nhiều khuôn mặt cùng lúc (xem
[flow/ai-service-data-flow.md](flow/ai-service-data-flow.md)).

Khi `VOICE_AUTHENTICITY_ENABLED=true` (mặc định `false`), mỗi `SpeakerTurn` còn được chấm bằng
`HeuristicVoiceAuthenticityAnalyzer` (`app/voice_auth/heuristic_analyzer.py`) — phân biệt giọng người
thật với giọng máy/tổng hợp (TTS bot, phát lại ghi âm — hữu ích khi ghi âm buổi học qua Microsoft
Teams/Google Meet có thể có bot tham gia) dựa trên đặc trưng âm học thô (pitch jitter, shimmer,
spectral flatness, độ đều của khoảng lặng — qua `librosa`), **KHÔNG phải model đã huấn luyện** — xem
docstring của module đó để biết giới hạn. Kết quả (`voice_authenticity`) vừa trả về vừa lưu vào
`ai.voice_authenticity_results`.

### POST `/api/v1/upload`

Nhận trực tiếp file audio/video qua `multipart/form-data` — dùng khi chưa wire Kafka/S3.

- **Request** (multipart form):
  - `file`: `UploadFile` (bắt buộc)
  - `language_code`: string \| null, mặc định `null` (bỏ trống = tự động nhận diện ngôn ngữ theo
    từng speaker turn, song song — xem chi tiết ở `/api/v1/transcribe` bên trên; endpoint này dùng
    chung logic `transcribe_turns_multilingual`)
- **Response**: `TranscriptionResult` (giống endpoint trên, cũng gộp caption hình ảnh/nhận diện khuôn
  mặt/kiểm tra giọng nói nếu các flag tương ứng bật). `recording_id` được **sinh mới** (UUID) vì upload
  ad-hoc không có sẵn recordingId — dùng để tra lại `ai.face_recognition_results`/
  `ai.voice_authenticity_results`.

File upload được đọc theo chunk (`UPLOAD_CHUNK_SIZE_BYTES`, mặc định 1 MiB) qua `UploadFile.read(size)`
và ghi tuần tự xuống temp file, thay vì gọi `file.read()` không tham số (buffer toàn bộ file vào RAM
một lần) — nhờ vậy dung lượng RAM dùng không phụ thuộc kích thước file upload.

### POST `/api/v1/faces/enroll`

Enroll một `user_id` làm khuôn mặt đã biết, dùng để nhận diện người nói ở `/api/v1/upload`/
`/api/v1/transcribe` khi `FACE_RECOGNITION_ENABLED=true`.

- **Request** (multipart form): `user_id` (bắt buộc), `name` (tùy chọn — nếu bỏ trống và fetch được từ
  user-service thì dùng `name` của user-service), `image` (tùy chọn — file ảnh trực tiếp, dùng để test
  cục bộ không cần user-service đang chạy)
- **Xử lý**: nếu không truyền `image`, gọi `GET /api/v1/users/{userId}` (mục 2) lấy `photoUrl` rồi tải
  ảnh về — đây là luồng chính, đúng như yêu cầu "ảnh lấy từ user-service". Detect khuôn mặt (giữ khuôn
  mặt có độ tin cậy detect cao nhất nếu ảnh có nhiều khuôn mặt), tính embedding, upsert vào `ai.known_faces`
  (khóa `user_id`).
- **Response `data`** — `EnrolledFaceResponse`: `user_id`, `name` (không trả embedding thô).
- **Lỗi**: `422` nếu không detect được khuôn mặt nào trong ảnh; `404` nếu `user_id` không tồn tại ở
  user-service (và không truyền `image` trực tiếp).

### GET `/api/v1/faces`

Danh sách toàn bộ khuôn mặt đã enroll — dùng để xác nhận enrollment trước khi test `/api/v1/upload`.

- **Response `data`**: `EnrolledFaceResponse[]`.

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

## 8. Kafka — `english-service` (consumer/producer)

Payload từ ai-service là JSON snake_case thô (pydantic `model_dump()`, không có envelope) — decode bằng
`EventCodec` (Jackson snake_case dedicated mapper, một bản riêng cho mỗi domain package), không dùng
`ObjectMapper` mặc định (camelCase) của REST.

Cả ba domain đều lắng nghe topic `learning.gap.analyzed`, mỗi domain filter theo `category` của riêng
mình và bỏ qua các category khác. **Quan trọng**: ba `@KafkaListener` này dùng ba `groupId` khác nhau
(`english-service`, `english-service-grammar`, `english-service-pronunciation`) dù cùng consume một
topic — nếu dùng chung một `groupId`, Kafka sẽ chia partition cho từng consumer trong group thay vì
phát cho tất cả, khiến mỗi domain chỉ nhận được một phần message thay vì toàn bộ. `recommendation-service`
(mục 10) và `dashboard-service` (mục 11) cũng consume topic này, mỗi bên với `groupId` riêng của mình.

Chỉ `vocabulary` có `TranscriptReadyConsumer`; `grammar`/`pronunciation` không tự ingest `transcript.ready`
lần hai — bảng `transcripts`/`transcript_segments` là dữ liệu dùng chung, được `vocabulary` lưu một lần
và cả ba domain đều đọc lại qua `GET /api/v1/transcripts/{recordingId}` khi cần.

### `TranscriptReadyConsumer` — topic `transcript.ready` (domain `vocabulary`)

- File: `english-service/.../vocabulary/kafka/TranscriptReadyConsumer.java`
- `groupId`: `english-service`
- Payload — `TranscriptReadyEvent`: `recordingId`, `userId`, `fullText`, `segments: SegmentPayload[]`
  (`speaker`, `text`, `startSeconds`, `endSeconds`, `language` — ngôn ngữ được ai-service tự nhận
  diện/ép dùng cho từng speaker turn, xem mục 7)
- Xử lý: `TranscriptService.saveTranscript(event)` — idempotent (bỏ qua nếu `recordingId` đã tồn tại, do
  Kafka at-least-once). Lỗi decode/xử lý được log, không rethrow.

### `LearningGapAnalyzedConsumer` — topic `learning.gap.analyzed` (domain `vocabulary`)

- File: `english-service/.../vocabulary/kafka/LearningGapAnalyzedConsumer.java`
- `groupId`: `english-service`
- Payload — `LearningGapAnalyzedEvent`: `recordingId`, `userId`, `weakPoints: WeakPointPayload[]`
  (`itemId`, `category`, `label`, `forgettingScore`, `recommendation`)
- Xử lý: `VocabularyWeakPointService.saveWeakPoints(event)` — chỉ giữ item có `category == "vocabulary"`;
  phân loại `VocabularyType` qua `VocabularyClassifier` (rule-based hoặc LLM). Lỗi được log, không rethrow.

### `LearningGapAnalyzedConsumer` — topic `learning.gap.analyzed` (domain `grammar`)

- File: `english-service/.../grammar/kafka/LearningGapAnalyzedConsumer.java`
- `groupId`: `english-service-grammar`
- Payload: giống hệt bản của `vocabulary` (bản sao DTO riêng trong package `grammar.event`)
- Xử lý: `GrammarWeakPointService.saveWeakPoints(event)` — chỉ giữ item có `category == "grammar"`;
  phân loại `GrammarType` qua `GrammarClassifier` (rule-based hoặc LLM). Lỗi được log, không rethrow.

### `LearningGapAnalyzedConsumer` — topic `learning.gap.analyzed` (domain `pronunciation`)

- File: `english-service/.../pronunciation/kafka/LearningGapAnalyzedConsumer.java`
- `groupId`: `english-service-pronunciation`
- Payload: giống hệt bản của `vocabulary` (bản sao DTO riêng trong package `pronunciation.event`)
- Xử lý: `PronunciationWeakPointService.saveWeakPoints(event)` — chỉ giữ item có
  `category == "pronunciation"`; phân loại `PronunciationType` qua `PronunciationClassifier` (rule-based
  hoặc LLM). Lỗi được log, không rethrow.

### `MistakeHistorySeedConsumer` — topic `learning.gap.analyzed` (domain `practice`)

- File: `english-service/.../practice/kafka/MistakeHistorySeedConsumer.java`
- `groupId`: `english-service-practice` (riêng, giống 2 domain kia — nếu không sẽ chia sẻ partition thay
  vì nhận toàn bộ message)
- Payload: giống hệt các consumer khác của topic này
- Xử lý: **không lọc category** (khác 3 domain kia, giống recommendation-service/dashboard-service) —
  mọi `WeakPointPayload` được `INSERT ... ON CONFLICT (user_id, item_id) DO NOTHING` vào `mistake_history`
  qua `MistakeHistoryMapper.seedIfAbsent`, chỉ khi item chưa từng có lịch sử (seed một lần duy nhất; các
  lần sau chỉ được cập nhật bởi `PracticeService.redo` khi learner thực sự làm lại bài, không bị consumer
  này ghi đè/đếm trùng). Lỗi được log, không rethrow.

### `AnalysisRequestedProducer` — topic `learning.gap.analysis.requested` (domain `practice`)

- File: `english-service/.../practice/kafka/AnalysisRequestedProducer.java`
- **Publish khi**: `PracticeService.redo(...)` xử lý xong một `POST /api/v1/practice/redo` (mục 1) — một
  lần publish cho cả batch attempt vừa chấm, mang theo toàn bộ `mistake_history` hiện tại của learner đó
  (không chỉ các item vừa submit).
- **Key**: `recordingId` (giá trị sinh dạng `"practice-<uuid>"`, không gắn với recording thật nào)
- Payload — `AnalysisRequestedEvent`, serialize snake_case qua `EventCodec` (giống
  `RecordingUploadedProducer`, mục 9 — KHÔNG dùng `common.EventPublisher`/`BaseEvent`, vì event này băng
  qua biên Java↔Python, phải khớp đúng pydantic model mà `ai-service` parse, mục 7/12):

| Field JSON | Field Java | Mô tả |
|---|---|---|
| `recording_id` | `recordingId` | |
| `user_id` | `userId` | |
| `segments` | `segments` | luôn rỗng — không có transcript trong luồng redo-exercise |
| `history` | `history` | `MistakeHistoryItemPayload[]`: `itemId`, `category`, `label`, `occurrenceCount`, `lastSeenDaysAgo` (tính từ `mistake_history.last_seen_at` tới thời điểm publish) |

Đây chính là producer còn thiếu của `learning.gap.analysis.requested` (xem mục 12, 14) — trước đây chỉ có
consumer (`ai-service`) mà không service Java nào publish topic này.

---

## 9. Kafka — `recording-service` (producer)

### `RecordingUploadedProducer` — topic `recording.uploaded`

- File: `recording-service/.../recording/kafka/RecordingUploadedProducer.java`
- **Publish khi**: `POST /api/v1/recordings` upload thành công lên S3 và insert xong bảng `recordings`.
- **Key**: `recordingId`
- Payload — `RecordingUploadedEvent`, serialize snake_case qua `EventCodec` riêng của service (KHÔNG dùng
  `common`'s `EventPublisher`/`BaseEvent` — event này băng qua biên Java↔Python nên phải là JSON snake_case
  phẳng, không có envelope, để khớp đúng pydantic model `RecordingUploadedEvent` mà `ai-service` parse
  (mục 7)):

| Field JSON | Field Java | Mô tả |
|---|---|---|
| `recording_id` | `recordingId` | |
| `user_id` | `userId` | |
| `s3_bucket` | `s3Bucket` | |
| `s3_key` | `s3Key` | |
| `language_code` | `languageCode` | |

---

## 10. Kafka — `recommendation-service` (consumer/producer)

### `LearningGapAnalyzedConsumer` — topic `learning.gap.analyzed` (consume)

- File: `recommendation-service/.../recommendation/kafka/LearningGapAnalyzedConsumer.java`
- `groupId`: `recommendation-service` (riêng, không dùng chung với các consumer khác của cùng topic — xem
  lưu ý ở mục 6)
- Payload — `LearningGapAnalyzedEvent` (bản sao DTO snake_case riêng, giống hệt `vocabulary.event.*`)
- Xử lý: `RecommendationService.handleLearningGapAnalyzed(event)` — **không lọc theo category** (khác
  `english-service`), mọi `WeakPointPayload` đều được upsert vào bảng `recommendations` (khóa
  `(user_id, item_id)`). Sau khi upsert xong, publish `recommendation.generated`. Lỗi được log, không rethrow.

### `RecommendationGeneratedEvent` — topic `recommendation.generated` (produce)

- File: `recommendation-service/.../recommendation/event/RecommendationGeneratedEvent.java`
- **Publish khi**: sau khi xử lý xong một `learning.gap.analyzed` event (một lần publish cho cả batch weak
  point vừa nhận, không phải một event/item).
- **Key**: `userId`
- Đây là event Java↔Java thuần túy (producer và consumer — dashboard-service, mục 9 — đều là Java), nên
  dùng thẳng `common`'s `EventPublisher`/`BaseEvent`/`KafkaEventPublisher` (lần đầu tiên hạ tầng này thực sự
  được dùng trong repo) — JSON camelCase mặc định, có envelope:

| Field | Type | Mô tả |
|---|---|---|
| `eventId` | string | từ `BaseEvent`, UUID tự sinh |
| `eventType` | string | `"recommendation.generated"` |
| `occurredAt` | Instant | từ `BaseEvent`, tự sinh |
| `recordingId` | string | |
| `userId` | string | |
| `recommendations` | `RecommendationPayload[]` | `itemId`, `category`, `label`, `recommendationText`, `forgettingScore` |

---

## 11. Kafka — `dashboard-service` (consumer)

Cả hai consumer dưới đây dùng `groupId`: `dashboard-service` (riêng, không dùng chung với consumer nào khác).
Không có producer — dashboard-service chỉ là read model.

### `LearningGapAnalyzedConsumer` — topic `learning.gap.analyzed`

- File: `dashboard-service/.../dashboard/kafka/LearningGapAnalyzedConsumer.java`
- Payload — `LearningGapAnalyzedEvent` (bản sao DTO snake_case, giống `vocabulary.event.*`), decode qua
  `EventCodec` riêng của service.
- Xử lý: mọi `WeakPointPayload` (không lọc category) được upsert vào bảng `weak_points_snapshot` (khóa
  `(user_id, item_id)`) — nguồn cho `categoryProgress` ở `GET /api/v1/dashboard/{userId}` (mục 5).

### `RecommendationGeneratedConsumer` — topic `recommendation.generated`

- File: `dashboard-service/.../dashboard/kafka/RecommendationGeneratedConsumer.java`
- Payload — `RecommendationGeneratedEvent` (bản sao camelCase, khớp với event Java↔Java ở mục 8), decode
  bằng `ObjectMapper` camelCase mặc định (KHÔNG dùng `EventCodec` snake_case, vì event này không băng qua
  Python).
- Xử lý: mọi `RecommendationPayload` được upsert vào bảng `recent_recommendations` (khóa
  `(user_id, item_id)`) — nguồn cho `recentRecommendations` ở `GET /api/v1/dashboard/{userId}`.

---

## 12. Kafka — `ai-service` (consumer/producer)

Chỉ chạy khi `KAFKA_ENABLED=true` (mặc định `false`, xem `app/config.py`).

### `handle_recording_uploaded`

- File: `ai-service/app/kafka/handlers/recording_uploaded.py`
- **Consume**: `recording.uploaded` — `RecordingUploadedEvent` (`recording_id`, `user_id`, `s3_bucket`,
  `s3_key`, `language_code` — `null`/bỏ trống nghĩa là tự nhận diện ngôn ngữ theo từng speaker turn)
  — nay được publish thật bởi `recording-service` (mục 3)
- **Xử lý**: tải audio từ S3 → convert WAV (PyAV) → diarize trước (`DiarizationEngine.diarize`) → cắt
  từng speaker turn (`slice_wav`) và transcribe **song song** qua `ThreadPoolExecutor`
  (`transcribe_turns_multilingual`, worker count qua `STT_MAX_CONCURRENT_TRANSCRIPTIONS`) — mỗi turn tự
  nhận diện ngôn ngữ riêng nếu `language_code` là `null`; nếu `VISION_ENABLED=true`, còn sample frame từ
  video gốc (`app/vision/frame_extractor.py`) và gọi Gemini vision
  (`app/vision/gemini_vision_engine.py`) để caption, gộp thành các `Segment` (`speaker == "vision"`) vào
  `segments` trước khi publish (`app/vision/pipeline.py`)
- **Publish**: `transcript.ready` (key = `recording_id`) — `TranscriptReadyEvent` (`recording_id`, `user_id`,
  `full_text`, `segments` — mỗi segment kèm `language`)

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
| `recording.uploaded` | recording-service | ai-service | ✅ hoạt động |
| `transcript.ready` | ai-service | english-service/vocabulary | ✅ hoạt động |
| `learning.gap.analysis.requested` | english-service (`practice`, khi learner làm lại bài tập) | ai-service | ✅ hoạt động |
| `learning.gap.analyzed` | ai-service | english-service (vocabulary/grammar/pronunciation), recommendation-service, dashboard-service | ✅ hoạt động (5 consumer, 5 `groupId` riêng) |
| `pronunciation.analyzed` | english-service/pronunciation (dự kiến) | — | ⚠️ chỉ tồn tại tên hằng số, chưa có producer/consumer |
| `grammar.analyzed` | english-service/grammar (dự kiến) | — | ⚠️ chỉ tồn tại tên hằng số, chưa có producer/consumer |
| `vocabulary.analyzed` | english-service/vocabulary (dự kiến) | — | ⚠️ chỉ tồn tại tên hằng số, chưa có producer/consumer |
| `recommendation.generated` | recommendation-service | dashboard-service | ✅ hoạt động |

---

## 13. Bảng tổng hợp

| Service | Loại | Method/Topic | Path/Tên | Ghi chú |
|---|---|---|---|---|
| bff-service | REST | POST | `/api/v1/auth/register` | proxy sang user-service |
| bff-service | REST | POST | `/api/v1/auth/login` | proxy sang user-service |
| bff-service | REST | GET | `/api/v1/users/{userId}` | proxy sang user-service |
| bff-service | REST | PATCH | `/api/v1/users/{userId}` | proxy sang user-service |
| bff-service | REST | POST | `/api/v1/recordings` | proxy multipart streaming sang recording-service |
| bff-service | REST | GET | `/api/v1/learners/{userId}/overview` | fan-out user-service + dashboard-service + recording-service |
| bff-service | REST | GET | `/api/v1/learners/{userId}/weak-points` | fan-out 3 endpoint weak-points của english-service |
| bff-service | REST | GET | `/api/v1/learners/{userId}/recommendations` | proxy `/recommendations/{userId}/grouped` |
| bff-service | REST | GET | `/api/v1/learners/{userId}/practice/next` | fan-out 3 endpoint weak-points, top N theo `forgettingScore` |
| bff-service | REST | POST | `/api/v1/learners/{userId}/practice/redo` | proxy sang english-service `/api/v1/practice/redo` |
| user-service | REST | POST | `/api/v1/auth/register` | hash password, phát JWT, `409` nếu email trùng |
| user-service | REST | POST | `/api/v1/auth/login` | phát JWT, `401` chung cho email sai/không tồn tại |
| user-service | REST | GET | `/api/v1/users/{userId}` | 404 nếu không tồn tại |
| user-service | REST | PATCH | `/api/v1/users/{userId}` | cập nhật `name` |
| user-service | REST | POST | `/api/v1/users/{userId}/photo` | upload ảnh đại diện → S3, nguồn ảnh cho ai-service enroll khuôn mặt |
| english-service (vocabulary) | REST | GET | `/api/v1/transcripts/{recordingId}` | 404 nếu chưa có transcript |
| english-service (vocabulary) | REST | GET | `/api/v1/vocabulary/weak-points/{userId}` | filter `?type=` tùy chọn |
| english-service (vocabulary) | REST | GET | `/api/v1/vocabulary/weak-points/{userId}/grouped` | nhóm theo `VocabularyType` |
| english-service (grammar) | REST | GET | `/api/v1/grammar/weak-points/{userId}` | filter `?type=` tùy chọn |
| english-service (grammar) | REST | GET | `/api/v1/grammar/weak-points/{userId}/grouped` | nhóm theo `GrammarType` |
| english-service (pronunciation) | REST | GET | `/api/v1/pronunciation/weak-points/{userId}` | filter `?type=` tùy chọn |
| english-service (pronunciation) | REST | GET | `/api/v1/pronunciation/weak-points/{userId}/grouped` | nhóm theo `PronunciationType` |
| english-service (practice) | REST | POST | `/api/v1/practice/redo` | chấm điểm redo-exercise, refresh mistake history, trigger re-analysis |
| recording-service | REST | POST | `/api/v1/recordings` | multipart upload → S3 + publish `recording.uploaded` |
| recording-service | REST | GET | `/api/v1/recordings/{recordingId}` | 404 nếu không tồn tại |
| recording-service | REST | GET | `/api/v1/recordings/user/{userId}` | danh sách theo user |
| recommendation-service | REST | GET | `/api/v1/recommendations/{userId}` | filter `?category=` tùy chọn |
| recommendation-service | REST | GET | `/api/v1/recommendations/{userId}/grouped` | nhóm theo `category` |
| dashboard-service | REST | GET | `/api/v1/dashboard/{userId}` | tổng hợp category progress + gợi ý gần nhất |
| ai-service | REST | GET | `/health` | liveness |
| ai-service | REST | POST | `/api/v1/transcribe` | STT đồng bộ từ S3 event |
| ai-service | REST | POST | `/api/v1/upload` | STT đồng bộ từ multipart upload |
| ai-service | REST | POST | `/api/v1/analyze` | phân tích lỗi/quên đồng bộ |
| ai-service | REST | POST | `/api/v1/faces/enroll` | enroll khuôn mặt (ảnh từ user-service hoặc upload trực tiếp) |
| ai-service | REST | GET | `/api/v1/faces` | danh sách khuôn mặt đã enroll |
| english-service (vocabulary) | Kafka in | `transcript.ready` | `TranscriptReadyConsumer` | lưu Transcript + Segments |
| english-service (vocabulary) | Kafka in | `learning.gap.analyzed` | `LearningGapAnalyzedConsumer` (groupId `english-service`) | lưu weak points (chỉ category vocabulary) |
| english-service (grammar) | Kafka in | `learning.gap.analyzed` | `LearningGapAnalyzedConsumer` (groupId `english-service-grammar`) | lưu weak points (chỉ category grammar) |
| english-service (pronunciation) | Kafka in | `learning.gap.analyzed` | `LearningGapAnalyzedConsumer` (groupId `english-service-pronunciation`) | lưu weak points (chỉ category pronunciation) |
| english-service (practice) | Kafka in | `learning.gap.analyzed` | `MistakeHistorySeedConsumer` (groupId `english-service-practice`) | seed `mistake_history` lần đầu, không lọc category |
| english-service (practice) | Kafka out | `learning.gap.analysis.requested` | `AnalysisRequestedProducer` | key `recordingId` giả (`practice-<uuid>`), snake_case, không envelope |
| recording-service | Kafka out | `recording.uploaded` | `RecordingUploadedProducer` | key `recordingId`, snake_case, không envelope |
| recommendation-service | Kafka in | `learning.gap.analyzed` | `LearningGapAnalyzedConsumer` (groupId `recommendation-service`) | upsert `recommendations`, không lọc category |
| recommendation-service | Kafka out | `recommendation.generated` | `RecommendationGeneratedEvent` via `common.EventPublisher` | key `userId`, camelCase + envelope |
| dashboard-service | Kafka in | `learning.gap.analyzed` | `LearningGapAnalyzedConsumer` (groupId `dashboard-service`) | upsert `weak_points_snapshot` |
| dashboard-service | Kafka in | `recommendation.generated` | `RecommendationGeneratedConsumer` (groupId `dashboard-service`) | upsert `recent_recommendations` |
| ai-service | Kafka in/out | `recording.uploaded` → `transcript.ready` | `handle_recording_uploaded` | pipeline STT + diarization |
| ai-service | Kafka in/out | `learning.gap.analysis.requested` → `learning.gap.analyzed` | `handle_analysis_requested` | pipeline phân tích lỗi/quên |

---

## 14. Các service/domain chưa có API

Không còn service Java nào ở dạng skeleton — cả 7 service (`english-service` 8085 cả 3 domain,
`user-service` 8081, `recording-service` 8082, `recommendation-service` 8086, `dashboard-service` 8087,
`bff-service` 8080) đều đã có controller/route thật (xem mục 1–6, 8–11).

Producer cho `learning.gap.analysis.requested` trước đây còn thiếu nay đã có: `english-service`'s
`practice` package publish topic này mỗi khi learner làm lại bài tập (`POST /api/v1/practice/redo`, xem
mục 1, 8) — bó lịch sử sai sót (`mistake_history`) của learner thay vì transcript (transcript vẫn là
nguồn duy nhất khi phân tích một recording thật, nhưng luồng đó chưa có producer transcript+history kết
hợp — vẫn cần nếu muốn tự động re-analyze ngay sau một recording mới thay vì chỉ khi learner chủ động
redo). Còn thiếu: enforcement JWT ở bất kỳ đâu (`user-service` mới chỉ phát hành token — xem lưu ý
đầu mục 2).
